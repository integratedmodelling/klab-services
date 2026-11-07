package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.CheckStatus;
import org.junit.jupiter.api.Test;

class OntologyReviewBootstrapTest {
  private final ObjectMapper json = new ObjectMapper();
  private final OntologyReviewBootstrap.Options options = new OntologyReviewBootstrap.Options(
      "test-proposal", "test-r1", "test domain", "review declarations", 1, "test community", LocalDate.of(2026, 11, 6));

  private KimOntologyImpl ontology() {
    var ontology = new KimOntologyImpl(); ontology.setUrn("test.ontology");
    ontology.setSourceCode("ontology test.ontology;\nthing Sample;\n"); return ontology;
  }

  @Test void emptyInputsProduceSchemaValidDraftAndEmptyCommentsWithoutApproval() throws Exception {
    var result = OntologyReviewBootstrap.prepare(ontology(), null, options);
    var check = ProposalSchemaValidator.bundled().validate(result.proposalBytes());
    assertEquals(CheckStatus.PASS, check.status(), check.messages().toString());
    var proposal = json.readTree(result.proposalJson()).path("proposal");
    assertEquals("draft", proposal.path("status").asText());
    assertEquals("not_recorded", proposal.path("validation").path("semantic_review").asText());
    assertFalse(proposal.path("validation").path("references_resolve").asBoolean());
    assertTrue(proposal.path("assets").isEmpty());
    assertTrue(json.readTree(result.commentsJson()).path("comments").isEmpty());
    assertFalse(json.readTree(result.manifestJson()).has("bootstrap"));
  }

  @Test void preservesNamespaceAndNestedAnnotationsAndVerbatimBootstrapWithHashes() throws Exception {
    var ontology = ontology();
    ontology.setAnnotations(List.of(Annotation.of("review", "proposal", "old-id", "community_acceptance", "accepted")));
    var parent = new KimConceptStatementImpl(); parent.setUrn("test.ontology:Parent");
    parent.setOffsetInDocument(24); parent.setLength(13);
    parent.setAnnotations(List.of(Annotation.of("unrelated", "value", "ignored"),
        Annotation.of("review", "review_record", "http://127.0.0.1:1/private")));
    var child = new KimConceptStatementImpl(); child.setUrn("test.ontology:Child");
    child.setAnnotations(List.of(Annotation.of("review", "value", "why?\nλ")));
    parent.setChildren(List.of(child)); ontology.setStatements(List.of(parent));
    String text = "# Bootstrap\r\nλ quoted \"text\"\n<script>inert</script>";
    var bootstrap = new OntologyReviewBootstrap.BootstrapDocument("project/BOOTSTRAP.md", text);
    var result = OntologyReviewBootstrap.prepare(ontology, bootstrap, options);
    assertEquals(result, OntologyReviewBootstrap.prepare(ontology, bootstrap, options));
    var manifest = json.readTree(result.manifestJson());
    assertEquals(3, manifest.path("annotations").size());
    assertEquals("accepted", manifest.path("annotations").get(0).path("parameters").path("community_acceptance").asText());
    assertEquals(24, manifest.path("annotations").get(1).path("offset").asInt());
    assertEquals(org.integratedmodelling.common.review.ProposalCandidateBinding.digest(ontology.getSourceCode().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        manifest.path("source_sha256").asText());
    var proposal = json.readTree(result.proposalJson()).path("proposal");
    assertEquals(text, proposal.path("evidence").get(3).path("paraphrase").asText());
    assertEquals("test-proposal", proposal.path("id").asText());
    assertEquals("draft", proposal.path("status").asText());
    var check = ProposalSchemaValidator.bundled().validate(result.proposalBytes());
    assertEquals(CheckStatus.PASS, check.status(), check.messages().toString());
  }

  @Test void emptyBootstrapStillRecordsItsProvenanceAndSourceChangesChangeHash() throws Exception {
    var ontology = ontology();
    var bootstrap = new OntologyReviewBootstrap.BootstrapDocument("BOOTSTRAP.md", "");
    var first = OntologyReviewBootstrap.prepare(ontology, bootstrap, options);
    ontology.setSourceCode(ontology.getSourceCode() + "// changed\n");
    var second = OntologyReviewBootstrap.prepare(ontology, bootstrap, options);
    assertNotEquals(json.readTree(first.manifestJson()).path("source_sha256"), json.readTree(second.manifestJson()).path("source_sha256"));
    assertTrue(json.readTree(first.manifestJson()).has("bootstrap"));
  }

  @Test void rejectsInvalidScopeMissingSourceOversizedUtf8AndCyclicDeclarations() {
    assertThrows(IllegalArgumentException.class, () -> new OntologyReviewBootstrap.Options("bad id", "r1", "d", "p", 1, "c", LocalDate.now()));
    var missing = ontology(); missing.setSourceCode(null);
    assertThrows(IllegalArgumentException.class, () -> OntologyReviewBootstrap.prepare(missing, null, options));
    var oversized = new OntologyReviewBootstrap.BootstrapDocument("BOOTSTRAP.md", "λ".repeat(2 * 1024 * 1024 + 1));
    assertThrows(IllegalArgumentException.class, () -> OntologyReviewBootstrap.prepare(ontology(), oversized, options));
    var cyclic = ontology(); var statement = new KimConceptStatementImpl();
    statement.setChildren(List.of(statement)); cyclic.setStatements(List.of(statement));
    assertThrows(IllegalArgumentException.class, () -> OntologyReviewBootstrap.prepare(cyclic, null, options));
  }
  @Test void actionOptionsRejectInvalidTiersExactlyAndAcceptSupportedWholeNumbers() {
    var parameters = new java.util.LinkedHashMap<String, Object>(java.util.Map.of(
        "proposal_id", "p1", "revision_id", "r1", "domain", "d", "purpose", "p",
        "target_community", "c", "requested_tier", 1));
    for (Object value : java.util.Arrays.asList(null, 0, -1, 1.5, Long.MAX_VALUE,
        2147483648L, Double.NaN, Double.POSITIVE_INFINITY, "1", true)) {
      parameters.put("requested_tier", value);
      var invalid = assertThrows(OntologyReviewBootstrap.InvalidOption.class,
          () -> OntologyReviewBootstrap.actionOptions(parameters));
      assertEquals("requested_tier", invalid.field());
      assertEquals("Requested tier must be an integer of 1 or greater", invalid.getMessage());
      assertNull(invalid.getCause());
    }
    for (Number value : java.util.List.of(1, 1L, 1.0, new java.math.BigDecimal("1.0"), Integer.MAX_VALUE)) {
      parameters.put("requested_tier", value);
      assertEquals(value.intValue(), OntologyReviewBootstrap.actionOptions(parameters).requestedTier());
    }
  }

}
