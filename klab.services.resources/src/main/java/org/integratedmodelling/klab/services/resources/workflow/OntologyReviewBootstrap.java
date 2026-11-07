package org.integratedmodelling.klab.services.resources.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview;

/** Pure preparation; callers resolve authorized snapshots and publish through an editor action. */
public final class OntologyReviewBootstrap {
  private static final ObjectMapper JSON = new ObjectMapper();
  private OntologyReviewBootstrap() {}

  /** These are editorial choices, not values inferred from informative annotations. */
  public record Options(String proposalId, String revisionId, String domain, String purpose,
      int requestedTier, String targetCommunity, LocalDate generatedOn) {
    public Options {
      identifier(proposalId, "proposal_id", "Proposal ID");
      identifier(revisionId, "revision_id", "Revision ID");
      required(domain, "domain", "Domain");
      required(purpose, "purpose", "Purpose");
      required(targetCommunity, "target_community", "Target community");
      if (requestedTier < 1) throw invalidTier();
      Objects.requireNonNull(generatedOn, "Generation date is required");
    }
  }

  /** A fixed, client-safe validation message; never contains submitted values or nested causes. */
  public static final class InvalidOption extends IllegalArgumentException {
    private final String field;
    private InvalidOption(String field, String message) { super(message); this.field = field; }
    public String field() { return field; }
  }

  /** Validate the bundled action before crossing its asynchronous actor boundary. */
  static Options actionOptions(Map<String, Object> parameters) {
    return new Options(text(parameters.get("proposal_id")), text(parameters.get("revision_id")),
        text(parameters.get("domain")), text(parameters.get("purpose")),
        tier(parameters.get("requested_tier")), text(parameters.get("target_community")),
        LocalDate.now(java.time.ZoneOffset.UTC));
  }

  private static String text(Object value) { return value instanceof String text ? text : null; }

  private static int tier(Object value) {
    if (!(value instanceof Number number)) throw invalidTier();
    try {
      int tier = new java.math.BigDecimal(number.toString()).intValueExact();
      if (tier < 1) throw invalidTier();
      return tier;
    } catch (ArithmeticException | NumberFormatException invalid) {
      throw invalidTier();
    }
  }

  private static InvalidOption invalidTier() {
    return new InvalidOption("requested_tier", "Requested tier must be an integer of 1 or greater");
  }

  private static void identifier(String value, String field, String label) {
    if (value == null || !value.matches("[A-Za-z][A-Za-z0-9._-]*"))
      throw new InvalidOption(field,
          label + " must start with a letter and contain only letters, digits, dots, underscores or hyphens");
  }

  private static void required(String value, String field, String label) {
    if (value == null || value.isBlank()) throw new InvalidOption(field, label + " is required");
  }

  /** Text is supplied by authorized project access; this helper never follows the locator. */
  public record BootstrapDocument(String locator, String text) {
    public BootstrapDocument {
      if (locator == null || locator.isBlank() || text == null)
        throw new IllegalArgumentException("Bootstrap locator and text are required");
    }
  }

  /** JSON is valid YAML for the existing proposal media type. Return fresh bytes on every access. */
  public record Prepared(String proposalJson, String commentsJson, String manifestJson) {
    public byte[] proposalBytes() { return proposalJson.getBytes(StandardCharsets.UTF_8); }
    public byte[] commentsBytes() { return commentsJson.getBytes(StandardCharsets.UTF_8); }
    public byte[] manifestBytes() { return manifestJson.getBytes(StandardCharsets.UTF_8); }
  }

  public static Prepared prepare(KimOntology ontology, BootstrapDocument bootstrap, Options options) {
    Objects.requireNonNull(ontology); Objects.requireNonNull(options);
    if (ontology.getUrn() == null || ontology.getUrn().isBlank() || ontology.getSourceCode() == null)
      throw new IllegalArgumentException("An identified ontology source snapshot is required");
    bounded(ontology.getSourceCode(), ProposalReview.MAX_ONTOLOGY_BYTES);
    if (bootstrap != null) bounded(bootstrap.text(), ProposalReview.MAX_PROPOSAL_BYTES);
    try (var input = OntologyReviewBootstrap.class.getResourceAsStream("/workflows/ontology-review-draft.json")) {
      if (input == null) throw new IllegalStateException("Missing review draft template");
      var document = (ObjectNode) JSON.readTree(input);
      var proposal = (ObjectNode) document.path("proposal");
      proposal.put("id", options.proposalId()).put("revision_id", options.revisionId())
          .put("title", "Review of " + ontology.getUrn()).put("generated_at", options.generatedOn().toString());
      ((ObjectNode) proposal.path("scope")).put("domain", options.domain()).put("purpose", options.purpose())
          .put("requested_tier", options.requestedTier()).put("target_community", options.targetCommunity());
      var manifest = JSON.createObjectNode().put("version", 1).put("ontology", ontology.getUrn())
          .put("source_sha256", digest(ontology.getSourceCode()));
      manifest.putArray("annotations");
      source(proposal, "ontology-source", ontology.getUrn(), digest(ontology.getSourceCode()));
      annotations(proposal, manifest, ontology.getAnnotations(), ontology.getUrn(), ontology.getUrn(), -1, 0);
      var seen = Collections.newSetFromMap(new IdentityHashMap<KimConceptStatement, Boolean>());
      var pending = new ArrayDeque<KimConceptStatement>(ontology.getStatements());
      while (!pending.isEmpty()) {
        var statement = pending.removeFirst();
        if (!seen.add(statement)) throw new IllegalArgumentException("Cyclic or shared ontology declaration");
        if (seen.size() > ProposalReview.MAX_ACTIONS) throw new IllegalArgumentException("Too many ontology declarations");
        annotations(proposal, manifest, statement.getAnnotations(), ontology.getUrn(), statement.getUrn(),
            statement.getOffsetInDocument(), statement.getLength());
        // Insert children first to retain authored declaration order.
        var children = statement.getChildren();
        for (int i = children.size() - 1; i >= 0; i--) pending.addFirst(children.get(i));
      }
      if (bootstrap != null) {
        manifest.putObject("bootstrap").put("locator", bootstrap.locator()).put("sha256", digest(bootstrap.text()));
        source(proposal, "bootstrap-source", bootstrap.locator(), digest(bootstrap.text()));
        // Empty source documents are retained in provenance even when there is no evidence text.
        if (!bootstrap.text().isEmpty()) evidence(proposal, "bootstrap-text", "bootstrap-source",
            bootstrap.locator(), "BOOTSTRAP.md", bootstrap.text());
      }
      String proposalText = JSON.writeValueAsString(document);
      String manifestText = JSON.writeValueAsString(manifest);
      bounded(proposalText, ProposalReview.MAX_PROPOSAL_BYTES);
      bounded(manifestText, ProposalReview.MAX_UPLOAD_BYTES);
      String comments = JSON.writeValueAsString(Map.of("id", options.revisionId() + "-comments", "comments", List.of()));
      return new Prepared(proposalText, comments, manifestText);
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("Cannot prepare ontology review draft", failure);
    }
  }

  private static void annotations(ObjectNode proposal, ObjectNode manifest,
      Collection<org.integratedmodelling.klab.api.lang.Annotation> authored, String ontology,
      String declaration, int offset, int length) throws java.io.IOException {
    for (var annotation : authored) {
      if (!"review".equals(annotation.getName())) continue;
      var entries = manifest.withArray("annotations");
      if (entries.size() >= ProposalReview.MAX_DOSSIER_RECORDS)
        throw new IllegalArgumentException("Too many review annotations");
      var entry = entries.addObject().put("declaration", declaration).put("offset", offset).put("length", length);
      entry.set("parameters", JSON.valueToTree(new TreeMap<>(annotation)));
      evidence(proposal, "review-" + entries.size(), "ontology-source", ontology + "#offset=" + offset,
          Objects.toString(declaration, "unnamed declaration"), JSON.writeValueAsString(entry));
    }
  }

  private static void source(ObjectNode proposal, String id, String locator, String checksum) {
    proposal.withArray("sources").addObject().put("source_id", id).put("citation", locator)
        .put("version", "sha256:" + checksum).put("locator", locator)
        .put("authority_note", "Authored review input; no validation or acceptance inferred");
  }

  private static void evidence(ObjectNode proposal, String id, String source, String locator, String term, String text) {
    proposal.withArray("evidence").addObject().put("evidence_id", id).put("source_id", source)
        .put("locator", locator).put("source_term", term).put("paraphrase", text)
        .put("evidence_kind", "contested_claim")
        .put("interpretation", "Verbatim authored input for editor review; not an independent finding");
  }

  private static String digest(String text) {
    return org.integratedmodelling.common.review.ProposalCandidateBinding.digest(text.getBytes(StandardCharsets.UTF_8));
  }
  private static void bounded(String text, int limit) {
    if (text.length() > limit || text.getBytes(StandardCharsets.UTF_8).length > limit)
      throw new IllegalArgumentException("Review bootstrap exceeds its byte limit");
  }
}
