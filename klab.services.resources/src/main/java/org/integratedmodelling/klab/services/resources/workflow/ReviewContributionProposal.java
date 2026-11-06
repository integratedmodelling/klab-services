package org.integratedmodelling.klab.services.resources.workflow;

import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.integratedmodelling.common.review.ProposalCandidateBinding;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.*;

/** Inline, immutable alternatives retain their exact bytes and server-derived schema evidence.
 * They are not stage attachments, accepted candidates, or semantic validation results. */
final class ReviewContributionProposal {
  private ReviewContributionProposal() {}

  static ProposalSnapshot snapshot(ProposalInput input, Cycle cycle, boolean submit) {
    if (input == null) return null;
    byte[] document = utf8(input.document(), ProposalReview.MAX_PROPOSAL_BYTES);
    byte[] source = input.ontologySource() == null ? null
        : utf8(input.ontologySource(), ProposalReview.MAX_ONTOLOGY_BYTES);
    var check = ProposalSchemaValidator.bundled().validate(document);
    ProposalReview.Candidate identity = null;
    if (check.status() == ProposalReview.CheckStatus.PASS) {
      try { identity = ProposalCandidateBinding.inspect(null, null, document); }
      catch (KlabIllegalStateException invalid) {
        check = new ProposalReview.Check(ProposalReview.CheckKind.DOCUMENT_SCHEMA,
            ProposalReview.CheckStatus.FAIL, List.of("Proposal identity cannot be bound"));
      }
    }
    if (submit && check.status() != ProposalReview.CheckStatus.PASS)
      throw new KlabIllegalStateException("Submitted alternative proposal must pass the bundled document schema");
    return new ProposalSnapshot(input.document(), input.ontologySource(), ProposalCandidateBinding.digest(document),
        source == null ? cycle.candidate().ontology() == null ? null : cycle.candidate().ontology().checksum()
            : ProposalCandidateBinding.digest(source),
        identity == null ? null : identity.proposalId(), identity == null ? null : identity.revisionId(),
        identity == null ? null : identity.supersedesRevision(), identity == null ? null : identity.contextDigest(),
        identity == null ? List.of() : identity.actionIds(), check);
  }

  static void requireFreshIdentity(Flow flow, ProposalSnapshot proposal, String replacingId) {
    if (proposal == null) return;
    for (var cycle : flow.getReviewCycles()) {
      var candidate = cycle.candidate();
      check(proposal, candidate.proposalId(), candidate.revisionId(), candidate.proposal().checksum(),
          candidate.ontology() == null ? null : candidate.ontology().checksum());
      for (var round : cycle.rounds()) for (var prior : round.contributions())
        if (prior.submitted() && prior.proposal() != null && !Objects.equals(prior.id(), replacingId)) {
          var other = prior.proposal();
          check(proposal, other.proposalId(), other.revisionId(), other.documentChecksum(), other.ontologyChecksum());
        }
    }
  }
  private static void check(ProposalSnapshot proposed, String id, String revision, String document, String source) {
    if (Objects.equals(proposed.proposalId(), id) && Objects.equals(proposed.revisionId(), revision)
        && (!Objects.equals(proposed.documentChecksum(), document) || !Objects.equals(proposed.ontologyChecksum(), source)))
      throw new KlabIllegalStateException("Changed proposal or ontology bytes require a fresh proposal revision ID");
  }
  static long bytes(Contribution contribution) {
    var proposal = contribution.proposal();
    return contribution.text().getBytes(StandardCharsets.UTF_8).length + (proposal == null ? 0
        : proposal.document().getBytes(StandardCharsets.UTF_8).length
            + (proposal.ontologySource() == null ? 0 : proposal.ontologySource().getBytes(StandardCharsets.UTF_8).length));
  }
  private static byte[] utf8(String value, int limit) {
    if (value == null || value.length() > limit) throw new KlabIllegalStateException("Proposal/source exceeds byte limit or is absent");
    try {
      var encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
      if (encoded.remaining() > limit) throw new KlabIllegalStateException("Proposal/source exceeds byte limit");
      var bytes = new byte[encoded.remaining()]; encoded.get(bytes); return bytes;
    } catch (java.nio.charset.CharacterCodingException invalid) {
      throw new KlabIllegalStateException("Proposal/source must be valid Unicode");
    }
  }
}
