package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.*;
import org.junit.jupiter.api.Test;

class ProposalSchemaValidatorTest {
  private final ProposalSchemaValidator validator = ProposalSchemaValidator.bundled();
  private byte[] fixture() throws Exception {
    try (var input = getClass().getResourceAsStream("/review/hydrology/proposal.yaml")) {
      assertNotNull(input); return input.readAllBytes();
    }
  }
  private ObjectNode document() throws Exception {
    return (ObjectNode) new ObjectMapper(new YAMLFactory()).readTree(fixture());
  }
  private byte[] json(ObjectNode node) throws Exception { return new ObjectMapper().writeValueAsBytes(node); }
  private Check validate(String input) { return validator.validate(input.getBytes(StandardCharsets.UTF_8)); }

  @Test void validHydrologyYamlAndJsonPassWithByteBoundEvidence() throws Exception {
    var result = validator.validate(fixture());
    assertEquals(CheckStatus.PASS, result.status(), result.messages().toString());
    assertTrue(result.messages().getFirst().contains(
        org.integratedmodelling.common.review.ProposalCandidateBinding.digest(fixture())));
    assertEquals(CheckStatus.PASS, validator.validate(json(document())).status());
  }
  @Test void nestedMissingRequiredFieldFailsWithEditorVisibleLocation() throws Exception {
    var doc = document(); ((ObjectNode) doc.path("proposal").path("scope")).remove("domain");
    var result = validator.validate(json(doc));
    assertEquals(CheckStatus.FAIL, result.status());
    assertTrue(result.messages().stream().anyMatch(m -> m.contains("scope") && m.contains("domain")));
  }
  @Test void wrongTypesAndEnumsCannotMasqueradeAsValidStructure() throws Exception {
    var doc = document(); ((ObjectNode) doc.path("proposal")).put("iteration", "1").put("status", "invented");
    assertEquals(CheckStatus.FAIL, validator.validate(json(doc)).status());
  }
  @Test void unknownPropertiesAreRejected() throws Exception {
    var doc = document(); doc.put("unrecognized", true);
    assertEquals(CheckStatus.FAIL, validator.validate(json(doc)).status());
  }
  @Test void callerCannotSelectAnExternalSchema() throws Exception {
    var doc = document(); doc.put("proposal_schema", "http://127.0.0.1:1/private-schema");
    assertEquals(CheckStatus.FAIL, validator.validate(json(doc)).status());
  }
  @Test void duplicateKeysTrailingDocumentsAndMalformedInputFail() throws Exception {
    assertEquals(CheckStatus.FAIL, validate("a: 1\na: 2").status());
    assertEquals(CheckStatus.FAIL, validate(new String(fixture(), StandardCharsets.UTF_8) + "\n---\n{}").status());
    assertEquals(CheckStatus.FAIL, validate("{broken").status());
    assertEquals(CheckStatus.FAIL, validate("{} {}").status());
  }
  @Test void invalidUtf8EmptyAndOversizedInputFail() {
    assertEquals(CheckStatus.FAIL, validator.validate(new byte[]{(byte) 0xc3, 0x28}).status());
    assertEquals(CheckStatus.FAIL, validator.validate(null).status());
    assertEquals(CheckStatus.FAIL, validator.validate(new byte[0]).status());
    assertEquals(CheckStatus.FAIL, validator.validate(new byte[ProposalReview.MAX_PROPOSAL_BYTES + 1]).status());
  }
  @Test void draft202012PrefixItemsAndUnevaluatedPropertiesAreEnforced() {
    var dialect = new ProposalSchemaValidator("""
        {"$schema":"https://json-schema.org/draft/2020-12/schema",
         "type":"object", "allOf":[{"properties":{"tuple":{
           "type":"array","prefixItems":[{"type":"integer"}],"items":false}}}],
         "unevaluatedProperties":false}
        """);
    assertEquals(CheckStatus.PASS, dialect.validate("{\"tuple\":[1]}".getBytes(StandardCharsets.UTF_8)).status());
    for (String bad : new String[]{"{\"tuple\":[\"bad\"]}", "{\"tuple\":[1,2]}", "{\"extra\":true}"})
      assertEquals(CheckStatus.FAIL, dialect.validate(bad.getBytes(StandardCharsets.UTF_8)).status(), bad);
  }
  @Test void unavailableBrokenOrExternalReferenceSchemasBlockWithoutFallback() {
    for (String schema : new String[]{null, "{broken", "{\"$ref\":\"http://127.0.0.1:1/schema\"}",
        "{\"$ref\":\"file:///private/schema.json\"}"})
      assertEquals(CheckStatus.BLOCKED, new ProposalSchemaValidator(schema)
          .validate("{}".getBytes(StandardCharsets.UTF_8)).status());
  }
  @Test void validationClaimsInsideProposalDoNotReplaceIndependentChecks() throws Exception {
    var results = new IsolatedProposalCandidateValidator().validate(null, fixture(), null);
    assertEquals(CheckStatus.PASS, results.stream().filter(c -> c.kind() == CheckKind.DOCUMENT_SCHEMA).findFirst().orElseThrow().status());
    assertEquals(CheckStatus.BLOCKED, results.stream().filter(c -> c.kind() == CheckKind.REASONER).findFirst().orElseThrow().status());
  }
  @Test void actualSchemaFailurePreventsAcceptanceEvenWhenOtherTestGatesPass() throws Exception {
    var helper = new ProposalReviewProtocolTest(); var store = helper.store();
    // Only non-schema providers are synthetic: isolate the real production schema gate.
    var manager = new WorkflowManager(store, WorkflowStageLifecycleHandler.NO_OP, (c, p, o) -> {
      var checks = new ArrayList<>(ProposalReviewProtocolTest.PASS.validate(c, p, o));
      checks.removeIf(check -> check.kind() == CheckKind.DOCUMENT_SCHEMA);
      checks.add(validator.validate(p)); return checks;
    });
    var request = helper.initialization(true);
    var invalid = document(); ((ObjectNode) invalid.path("proposal").path("scope")).remove("domain");
    request.getAttachments().getFirst().setContent(json(invalid));
    var user = helper.scope("editor", "EDITOR");
    var flow = manager.initializeFlow(ProposalReviewProtocolTest.WORKFLOW, request, user);
    var review = helper.current(flow).getProposalReview();
    assertEquals(CheckStatus.FAIL, review.validation().stream().filter(c -> c.kind() == CheckKind.DOCUMENT_SCHEMA).findFirst().orElseThrow().status());
    assertThrows(KlabIllegalStateException.class, () -> manager.transition(flow.getId(),
        helper.command(flow, "accept-peer-review", review.candidate()), user));
    assertEquals(flow.getRevision(), store.getFlow(flow.getId()).getRevision());
    assertEquals(Flow.Status.ACTIVE, store.getFlow(flow.getId()).getStatus());

    var editing = manager.transition(flow.getId(),
        helper.command(flow, "request-changes", review.candidate()), user);
    var corrected = document();
    ((ObjectNode) corrected.path("proposal")).put("revision_id", "hydrology-r2")
        .put("supersedes_revision", review.candidate().revisionId());
    var upload = helper.upload("bootstrap-proposal", "application/vnd.klab.proposal+yaml", "");
    upload.setContent(json(corrected));
    var attachment = manager.addAttachment(editing.getId(), helper.current(editing).getId(), upload, user);
    manager.addAttachment(editing.getId(), helper.current(editing).getId(),
        helper.upload("bootstrap-comments", "application/vnd.klab.comments+json", "{}"), user);
    var revised = ProposalReviewProtocol.readCandidate(new Artifact(attachment.getId(), attachment.getChecksum()),
        review.candidate().ontology(), store::getWorkflowAttachment);
    editing = manager.getFlow(editing.getId(), user);
    var resubmitted = manager.transition(editing.getId(), helper.command(editing, "submit", revised), user);
    assertThrows(KlabIllegalStateException.class, () -> manager.transition(resubmitted.getId(),
        helper.command(resubmitted, "accept-peer-review", review.candidate()), user));
    var accepted = manager.transition(resubmitted.getId(), helper.command(resubmitted, "accept-peer-review", revised), user);
    assertEquals(Flow.Status.CLOSED, accepted.getStatus());
    assertTrue(accepted.getStates().values().stream().filter(s -> s.getProposalReview() != null)
        .anyMatch(s -> s.getProposalReview().status() == Status.ACCEPTED
            && revised.equals(s.getProposalReview().candidate())));
  }
}
