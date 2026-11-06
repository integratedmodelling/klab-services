package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import org.integratedmodelling.common.review.ProposalCandidateBinding;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.*;
import org.junit.jupiter.api.Test;

class ReviewContributionProposalTest {
  final ReviewCycleProtocolTest fixture = new ReviewCycleProtocolTest();
  ReviewContributionProposalTest() throws Exception {}

  static String document(String revision) {
    var ontology = new KimOntologyImpl(); ontology.setUrn("test.ontology"); ontology.setSourceCode("ontology test.ontology;");
    return OntologyReviewBootstrap.prepare(ontology, null, new OntologyReviewBootstrap.Options(
        "domain-proposal", revision, "domain", "review", 1, "community", LocalDate.of(2026, 11, 6))).proposalJson();
  }
  View save(Cycle cycle, Round round, String id, ProposalInput input, boolean submit, UserScope actor) {
    return fixture.manager.reviewCycle(fixture.flowId, new Command(
        fixture.manager.getReviewCycles(fixture.flowId, actor).revision(), Operation.SAVE_CONTRIBUTION,
        null, cycle.id(), round.id(), null, id, "", submit, null, null, input), actor);
  }
  @Test void incompleteDraftStaysPrivateAndSubmissionRequiresServerSchema() throws Exception {
    var cycle = fixture.open(); var round = fixture.round(cycle, Audience.REVIEWER);
    var alice = fixture.fixtures.scope("alice", "REVIEWER");
    var input = new ProposalInput("{unfinished", null);
    var draft = save(cycle, round, null, input, false, alice).cycles().getFirst().rounds().getFirst().contributions().getFirst();
    assertEquals(ProposalReview.CheckStatus.FAIL, draft.proposal().schemaCheck().status());
    assertNull(draft.proposal().proposalId());
    assertFalse(Utils.Json.asString(fixture.manager.getReviewCycles(fixture.flowId, fixture.editor)).contains("unfinished"));
    long revision = fixture.store.getFlow(fixture.flowId).getRevision();
    assertThrows(KlabIllegalStateException.class, () -> save(cycle, round, draft.id(), input, true, alice));
    assertEquals(revision, fixture.store.getFlow(fixture.flowId).getRevision());
  }
  @Test void submittedAlternativeIsAttributedBoundAndImmutableWithExactSource() throws Exception {
    var cycle = fixture.open(); var round = fixture.round(cycle, Audience.COMMUNITY);
    var alice = fixture.community("alice", true, false); var bob = fixture.community("bob", true, false);
    var input = new ProposalInput(document("r2"), "ontology test.ontology;\r\n// alternative");
    var contribution = save(cycle, round, null, input, true, alice).cycles().getFirst().rounds().getFirst().contributions().getFirst();
    var proposal = contribution.proposal();
    assertEquals("alice", contribution.owner()); assertEquals("r2", proposal.revisionId());
    assertEquals(input.document(), proposal.document()); assertEquals(input.ontologySource(), proposal.ontologySource());
    assertEquals(ProposalCandidateBinding.digest(input.document().getBytes(java.nio.charset.StandardCharsets.UTF_8)), proposal.documentChecksum());
    assertEquals(ProposalReview.CheckStatus.PASS, proposal.schemaCheck().status());
    assertEquals(contribution, fixture.manager.getReviewCycles(fixture.flowId, bob).cycles().getFirst().rounds().getFirst().contributions().getFirst());
    assertEquals(cycle.candidate(), fixture.store.getFlow(fixture.flowId).getReviewCycles().getFirst().candidate());
    assertThrows(KlabIllegalStateException.class, () -> save(cycle, round, contribution.id(), input, false, alice));
  }
  @Test void omittedSourceRetainsBaseAndRevisionLabelsCannotAliasChangedInputs() throws Exception {
    var cycle = fixture.open(); var round = fixture.round(cycle, Audience.REVIEWER);
    var alice = fixture.fixtures.scope("alice", "REVIEWER");
    var proposal = save(cycle, round, null, new ProposalInput(document("r2"), null), true, alice)
        .cycles().getFirst().rounds().getFirst().contributions().getFirst().proposal();
    assertEquals(cycle.candidate().ontology().checksum(), proposal.ontologyChecksum());
    assertNull(proposal.ontologySource());
    assertThrows(KlabIllegalStateException.class, () -> save(cycle, round, null, new ProposalInput(document("r1"), null), true, alice));
    assertThrows(KlabIllegalStateException.class, () -> save(cycle, round, null, new ProposalInput(document("r2"), "changed source"), true, alice));
  }
  @Test void payloadLimitsCountUtf8BytesAndRejectMalformedUnicode() throws Exception {
    var cycle = fixture.open(); var round = fixture.round(cycle, Audience.REVIEWER);
    var alice = fixture.fixtures.scope("alice", "REVIEWER");
    assertThrows(KlabIllegalStateException.class, () -> save(cycle, round, null,
        new ProposalInput("é".repeat(ProposalReview.MAX_PROPOSAL_BYTES / 2 + 1), null), false, alice));
    assertThrows(KlabIllegalStateException.class, () -> save(cycle, round, null,
        new ProposalInput("\uD800", null), false, alice));
    assertThrows(KlabIllegalStateException.class, () -> save(cycle, round, null,
        new ProposalInput(document("r2"), "x".repeat(ProposalReview.MAX_ONTOLOGY_BYTES + 1)), false, alice));
  }
}
