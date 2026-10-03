package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.*;
import org.junit.jupiter.api.Test;

class IsolatedProposalCandidateValidatorTest {
  byte[] fixture(String name) throws Exception {
    try (var input = getClass().getResourceAsStream("/review/hydrology/" + name)) {
      assertNotNull(input); return input.readAllBytes();
    }
  }
  Check check(List<Check> checks, CheckKind kind) {
    return checks.stream().filter(c -> c.kind() == kind).findFirst().orElseThrow();
  }
  @Test void actualParserAndAdapterRunInAFreshScopeWithoutStartingServices() {
    var source = "ontology isolated in domain root version 1.0.0; thing Entity;".getBytes(StandardCharsets.UTF_8);
    var checks = IsolatedProposalCandidateValidator.validateOntology(source);
    assertEquals(CheckStatus.PASS, check(checks, CheckKind.PARSER).status());
    assertEquals(CheckStatus.PASS, check(checks, CheckKind.ADAPTATION).status());
    var results = new IsolatedProposalCandidateValidator().validate(null, null, source);
    assertEquals(CheckStatus.BLOCKED, check(results, CheckKind.REASONER).status());
    assertEquals(CheckStatus.BLOCKED, check(results, CheckKind.IMPORT_CONTEXT).status());
  }
  @Test void corruptAndMalformedOntologyBytesCannotPass() {
    var invalidUtf8 = IsolatedProposalCandidateValidator.validateOntology(new byte[]{(byte) 0xC3, 0x28});
    assertEquals(CheckStatus.FAIL, check(invalidUtf8, CheckKind.PARSER).status());
    var badGrammar = IsolatedProposalCandidateValidator.validateOntology("this is not an ontology {".getBytes(StandardCharsets.UTF_8));
    assertEquals(CheckStatus.FAIL, check(badGrammar, CheckKind.PARSER).status());
    assertEquals(CheckStatus.NOT_RUN, check(badGrammar, CheckKind.ADAPTATION).status());
  }
  @Test void realHydrologySyntaxDoesNotDischargeMissingImportsOrScientificReview() throws Exception {
    var results = new IsolatedProposalCandidateValidator().validate(null, fixture("proposal.yaml"), fixture("candidate.kwv"));
    assertEquals(CheckStatus.PASS, check(results, CheckKind.PARSER).status());
    assertNotEquals(CheckStatus.PASS, check(results, CheckKind.ADAPTATION).status());
    assertEquals(CheckStatus.BLOCKED, check(results, CheckKind.IMPORT_CONTEXT).status());
    assertEquals(CheckStatus.BLOCKED, check(results, CheckKind.REASONER).status());
    System.out.println("Hydrology isolated checks: " + results);
  }
  @Test void realHydrologyCanEnterReviewButCannotBeAcceptedWithValidSyntaxAlone() throws Exception {
    var helper = new ProposalReviewProtocolTest();
    var store = helper.store(); var manager = new WorkflowManager(store); var editor = helper.scope("editor", "EDITOR");
    var request = helper.initialization(true);
    request.getAttachments().getFirst().setContent(fixture("proposal.yaml"));
    request.getAttachments().get(2).setContent(fixture("candidate.kwv"));
    var dossier = org.integratedmodelling.common.utils.Utils.Json.parseObject(
        new String(fixture("backend-dossier.sample.json"), StandardCharsets.UTF_8), BootstrapDossier.class);
    assertTrue(org.integratedmodelling.klab.api.services.resources.workflow.BootstrapDossierValidator.errors(dossier).isEmpty());
    assertFalse(dossier.unresolvedSemantics().isEmpty());
    request.getTransition().setProposalReview(new Command(1, null, "Unreviewed hydrology research fixture", dossier));
    var flow = manager.initializeFlow(ProposalReviewProtocolTest.WORKFLOW, request, editor);
    var review = helper.current(flow).getProposalReview();
    assertEquals(Status.IN_REVIEW, review.status());
    assertEquals(dossier, review.dossier());
    assertEquals(CheckStatus.PASS, check(review.validation(), CheckKind.PARSER).status());
    assertEquals(CheckStatus.NOT_RUN, check(review.validation(), CheckKind.SCIENTIFIC_REVIEW).status());
    assertThrows(KlabIllegalStateException.class, () -> manager.transition(flow.getId(),
        helper.command(flow, "accept-peer-review", review.candidate()), editor));
    assertEquals(flow.getRevision(), store.getFlow(flow.getId()).getRevision());
  }
  @Test void oneValidationCannotSeedTheNextCandidateScope() {
    IsolatedProposalCandidateValidator.validateOntology(
        "ontology first in domain root version 1.0.0; thing Entity;".getBytes(StandardCharsets.UTF_8));
    var next = IsolatedProposalCandidateValidator.validateOntology(
        "ontology next using first in domain root version 1.0.0; thing Child is first:Entity;".getBytes(StandardCharsets.UTF_8));
    assertEquals(CheckStatus.PASS, check(next, CheckKind.PARSER).status());
    assertNotEquals(CheckStatus.PASS, check(next, CheckKind.ADAPTATION).status());
  }
}
