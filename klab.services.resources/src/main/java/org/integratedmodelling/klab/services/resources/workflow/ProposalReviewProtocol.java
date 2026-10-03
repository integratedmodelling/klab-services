package org.integratedmodelling.klab.services.resources.workflow;

import java.util.*;
import java.util.function.Function;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.*;

/** Pure preparation of review stage data. No store writes and no application side effects. */
final class ProposalReviewProtocol {
  static final String OPERATION = "proposalReviewOperation";
  private static final String PROPOSAL = "application/vnd.klab.proposal+yaml";
  private static final String ONTOLOGY = "application/vnd.klab.ontology";
  private final ProposalCandidateValidator validator;
  ProposalReviewProtocol(ProposalCandidateValidator validator) { this.validator = Objects.requireNonNull(validator); }

  static boolean enabled(Workflow workflow) {
    return workflow.getTransitions().values().stream().anyMatch(t -> t.getMetadata().containsKey(OPERATION));
  }

  void prepare(Flow flow, Workflow.TransitionSchema transition, Flow.State source,
      Flow.State target, Flow.TransitionRequest request, String actor,
      Function<String, byte[]> payload, boolean initialization) {
    Object configured = transition.getMetadata().get(OPERATION);
    if (configured == null) {
      if (request.getProposalReview() != null) fail("This transition does not support proposal review");
      return;
    }
    Operation operation;
    try { operation = Operation.valueOf(configured.toString()); }
    catch (IllegalArgumentException e) { throw new KlabIllegalStateException("Unknown proposal review operation"); }
    var command = request.getProposalReview();
    if (command == null || command.version() != ProposalReview.VERSION) fail("Proposal review command version 1 is required");
    if (!initialization && request.getExpectedRevision() < 0) fail("Proposal review requires expectedRevision");
    var prior = source.getProposalReview();
    Candidate candidate = command.candidate();
    if (candidate == null && initialization && operation == Operation.SUBMIT) {
      var proposals = source.getAttachments().stream().filter(a -> PROPOSAL.equals(a.getMediaType())).toList();
      var ontologies = source.getAttachments().stream().filter(a -> ONTOLOGY.equals(a.getMediaType())).toList();
      if (proposals.size() != 1 || ontologies.size() > 1) fail("Initial submission requires one unambiguous proposal and at most one ontology");
      candidate = readCandidate(reference(proposals.getFirst()),
          ontologies.isEmpty() ? null : reference(ontologies.getFirst()), payload);
    }
    if (candidate == null) fail("An exact candidate is required");
    var proposalAttachment = find(flow, candidate.proposal(), PROPOSAL, payload);
    Flow.Attachment ontologyAttachment = candidate.ontology() == null ? null : find(flow, candidate.ontology(), ONTOLOGY, payload);
    if (!candidate.equals(readCandidate(candidate.proposal(), candidate.ontology(), payload)))
      fail("Proposal identity, revision, actions or import context do not match the stored candidate");
    if (operation == Operation.SUBMIT) {
      if (!source.getId().equals(proposalAttachment.getStateId())) fail("Submit a proposal uploaded to this editing stage");
      if (prior != null) {
        if (!Objects.equals(prior.candidate().proposalId(), candidate.proposalId())
            || !Objects.equals(prior.candidate().revisionId(), candidate.supersedesRevision())
            || Objects.equals(prior.candidate().revisionId(), candidate.revisionId()))
          fail("Resubmission requires a fresh revision superseding the reviewed proposal");
      } else if (candidate.supersedesRevision() != null) fail("Initial proposal cannot supersede an unknown revision");
      for (var state : flow.getStates().values()) {
        var previous = state.getProposalReview();
        if (previous != null && candidate.revisionId().equals(previous.candidate().revisionId()))
          fail("A submitted proposal revision cannot be reused");
      }
      BootstrapDossierValidator.validate(command.dossier());
    } else {
      if (prior == null || prior.status() != Status.IN_REVIEW || !candidate.equals(prior.candidate()))
        fail("Decision does not match the exact candidate under review");
      if (command.dossier() != null && !Objects.equals(command.dossier(), prior.dossier()))
        fail("A decision cannot replace the reviewed dossier");
      if (command.rationale() == null || command.rationale().isBlank()) fail("A decision rationale is required");
    }
    var checks = new ArrayList<Check>(Objects.requireNonNull(validator.validate(candidate,
        payload.apply(candidate.proposal().attachmentId()),
        candidate.ontology() == null ? null : payload.apply(candidate.ontology().attachmentId()))));
    // Validator output cannot impersonate a human decision or an application/publication result.
    checks.removeIf(c -> c.kind() == CheckKind.SCIENTIFIC_REVIEW || c.kind() == CheckKind.APPLICATION || c.kind() == CheckKind.PR_HANDOFF);
    if (operation == Operation.ACCEPT) {
      if (ontologyAttachment == null) fail("Acceptance requires the exact reviewed ontology artifact");
      for (var kind : List.of(CheckKind.IMPORT_CONTEXT, CheckKind.DOCUMENT_SCHEMA, CheckKind.PARSER, CheckKind.REASONER)) {
        var matching = checks.stream().filter(c -> c.kind() == kind).toList();
        if (matching.size() != 1 || matching.getFirst().status() != CheckStatus.PASS)
          fail("Acceptance blocked: " + kind + " must pass for this candidate and current imports");
      }
      if (prior.dossier() != null && prior.dossier().unresolvedSemantics() != null
          && !prior.dossier().unresolvedSemantics().isEmpty()) fail("Acceptance blocked by unresolved semantics");
      target.getAttachments().add(alias(proposalAttachment, "final-proposal"));
      target.getAttachments().add(alias(ontologyAttachment, "accepted-ontology"));
    }
    Status status = switch (operation) {
      case SUBMIT, ADVANCE -> Status.IN_REVIEW;
      case REQUEST_CHANGES -> Status.CHANGES_REQUESTED;
      case ACCEPT -> Status.ACCEPTED;
      case REJECT -> Status.REJECTED;
    };
    checks.add(new Check(CheckKind.SCIENTIFIC_REVIEW,
        operation == Operation.ACCEPT ? CheckStatus.PASS : CheckStatus.NOT_RUN,
        List.of(operation == Operation.ACCEPT ? "Human decision by " + actor + ": " + command.rationale() : "No scientific acceptance recorded")));
    checks.add(new Check(CheckKind.APPLICATION, CheckStatus.BLOCKED, List.of("Ontology application is not implemented by this protocol")));
    checks.add(new Check(CheckKind.PR_HANDOFF, CheckStatus.BLOCKED, List.of("PR publication is not implemented by this protocol")));
    target.setProposalReview(new StageData(1, candidate, status, actor, command.rationale(), checks,
        operation == Operation.SUBMIT ? command.dossier() : prior.dossier()));
    // Keep actual source descriptors visible in review stages; their payloads remain immutable.
    if (operation == Operation.SUBMIT || operation == Operation.ADVANCE) {
      target.getAttachments().add(alias(proposalAttachment, "bootstrap-proposal"));
      if (ontologyAttachment != null) target.getAttachments().add(alias(ontologyAttachment, "candidate-ontology"));
    }
  }

  static Candidate readCandidate(Artifact proposal, Artifact ontology, Function<String, byte[]> payload) {
    return org.integratedmodelling.common.review.ProposalCandidateBinding.inspect(proposal, ontology, payload.apply(proposal.attachmentId()));
  }
  static String digest(byte[] bytes) {
    return org.integratedmodelling.common.review.ProposalCandidateBinding.digest(bytes);
  }
  private static Artifact reference(Flow.Attachment a) { return new Artifact(a.getId(), a.getChecksum()); }
  private static Flow.Attachment find(Flow flow, Artifact ref, String media, Function<String, byte[]> payload) {
    if (ref == null || ref.attachmentId() == null || ref.checksum() == null) fail("An artifact identity and checksum are required");
    var attachment = flow.getStates().values().stream().flatMap(s -> s.getAttachments().stream())
        .filter(a -> ref.attachmentId().equals(a.getId())).findFirst()
        .orElseThrow(() -> new KlabIllegalStateException("Candidate artifact does not belong to this flow"));
    byte[] bytes = payload.apply(ref.attachmentId());
    if (!media.equals(attachment.getMediaType()) || !ref.checksum().equals(attachment.getChecksum())
        || bytes == null || !ref.checksum().equals(digest(bytes))) fail("Candidate artifact media or checksum mismatch");
    return attachment;
  }
  private static Flow.Attachment alias(Flow.Attachment original, String type) {
    var copy = Utils.Json.parseObject(Utils.Json.asString(original), Flow.Attachment.class);
    copy.setType(type);
    return copy;
  }
  private static void fail(String message) { throw new KlabIllegalStateException(message); }
}
