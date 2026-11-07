package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.integratedmodelling.klab.api.exceptions.*;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.*;
import org.junit.jupiter.api.Test;

class OntologyReviewRoundIntegrationTest {
  private Command command(long revision, Operation op, String source, String cycle, Audience audience,
      String proposal, String ontology) {
    return new Command(revision, op, source, cycle, null, audience, null, null, false, proposal, ontology);
  }

  @Test void bootstrapOpensIndependentReviewAndCommunityCanReadOnlyItsExactCandidate() throws Exception {
    var fixture = new OntologyReviewBootstrapActionTest().new Fixture("# Context\nRationale");
    var flow = fixture.prepare();
    var attachments = flow.getStates().get(fixture.stage).getAttachments();
    var proposal = attachments.stream().filter(a -> "bootstrap-proposal".equals(a.getType())).findFirst().orElseThrow();
    var ontology = attachments.stream().filter(a -> "candidate-ontology".equals(a.getType())).findFirst().orElseThrow();
    var manifest = attachments.stream().filter(a -> "supporting-material".equals(a.getType())).findFirst().orElseThrow();
    var view = fixture.manager.reviewCycle(flow.getId(), command(flow.getRevision(), Operation.OPEN_CYCLE,
        fixture.stage, null, null, proposal.getId(), ontology.getId()), fixture.editor);
    var cycle = view.cycles().getFirst(); assertTrue(view.canManage());
    assertEquals(proposal.getChecksum(), cycle.candidate().proposal().checksum());
    var citizen = new ReviewCycleProtocolTest().community("citizen", true, false);
    view = fixture.manager.reviewCycle(flow.getId(), command(view.revision(), Operation.OPEN_ROUND,
        null, cycle.id(), Audience.REVIEWER, null, null), fixture.editor);
    assertThrows(KlabResourceAccessException.class, () -> fixture.manager.getReviewArtifact(flow.getId(), cycle.id(), proposal.getId(), citizen));
    view = fixture.manager.reviewCycle(flow.getId(), command(view.revision(), Operation.OPEN_ROUND,
        null, cycle.id(), Audience.COMMUNITY, null, null), fixture.editor);
    var publicView = fixture.manager.getReviewCycles(flow.getId(), citizen);
    assertFalse(publicView.canManage()); assertEquals("citizen", publicView.identity());
    assertEquals(1, publicView.contributableRoundIds().size());
    assertEquals(1, publicView.cycles().getFirst().rounds().size());
    var other = new ProposalReviewProtocolTest().scope("unrelated", "EDITOR");
    assertFalse(fixture.manager.getReviewCycles(flow.getId(), other).canManage());
    final long revision = view.revision();
    assertThrows(KlabResourceAccessException.class, () -> fixture.manager.reviewCycle(flow.getId(),
        command(revision, Operation.CLOSE_CYCLE, null, cycle.id(), null, null, null), other));
    assertFalse(fixture.manager.getFlow(flow.getId(), citizen).getStates().containsKey(fixture.stage));
    assertArrayEquals(fixture.store.attachments.get(proposal.getId()),
        fixture.manager.getReviewArtifact(flow.getId(), cycle.id(), proposal.getId(), citizen));
    assertArrayEquals(fixture.store.attachments.get(ontology.getId()),
        fixture.manager.getReviewArtifact(flow.getId(), cycle.id(), ontology.getId(), citizen));
    assertThrows(KlabResourceAccessException.class, () -> fixture.manager.getReviewArtifact(flow.getId(), cycle.id(), manifest.getId(), citizen));
    assertThrows(KlabIllegalStateException.class, () -> fixture.manager.deleteAttachment(flow.getId(), proposal.getId(), fixture.editor));
    assertThrows(KlabIllegalStateException.class, () -> fixture.manager.deleteFlow(flow.getId(), fixture.editor));
  }

  @Test void unrelatedWorkflowEditorCannotManageOrCreateAnAssignmentToEscalate() throws Exception {
    var fixture = new OntologyReviewBootstrapActionTest().new Fixture(null); var flow = fixture.prepare();
    var other = new ProposalReviewProtocolTest().scope("unrelated", "EDITOR");
    assertThrows(KlabResourceAccessException.class, () -> fixture.manager.getReviewCycles(flow.getId(), other));
    var state = Flow.State.create(); state.setSchemaId("bootstrap");
    assertThrows(KlabIllegalStateException.class, () -> fixture.manager.createState(flow.getId(), state, other));
    var proposal = flow.getStates().get(fixture.stage).getAttachments().stream().filter(a -> "bootstrap-proposal".equals(a.getType())).findFirst().orElseThrow();
    assertThrows(KlabResourceAccessException.class, () -> fixture.manager.reviewCycle(flow.getId(),
        command(flow.getRevision(), Operation.OPEN_CYCLE, fixture.stage, null, null, proposal.getId(), null), other));
  }

  @Test void closedCycleStaysImmutableWhileAnIntegratedDraftStartsTheNextCycle() throws Exception {
    var fixture = new OntologyReviewBootstrapActionTest().new Fixture(null); var flow = fixture.prepare();
    var proposal = flow.getStates().get(fixture.stage).getAttachments().stream().filter(a -> "bootstrap-proposal".equals(a.getType())).findFirst().orElseThrow();
    var view = fixture.manager.reviewCycle(flow.getId(), command(flow.getRevision(), Operation.OPEN_CYCLE,
        fixture.stage, null, null, proposal.getId(), null), fixture.editor);
    var id = view.cycles().getFirst().id();
    view = fixture.manager.reviewCycle(flow.getId(), command(view.revision(), Operation.ENTER_COALESCING,
        null, id, null, null, null), fixture.editor);
    var coalescing = view.editableStageId();
    var closed = view.cycles().getFirst(); byte[] original = fixture.store.attachments.get(proposal.getId()).clone();
    var json = new com.fasterxml.jackson.databind.ObjectMapper();
    var next = json.readTree(original);
    ((com.fasterxml.jackson.databind.node.ObjectNode) next.path("proposal")).put("title", "Integrated proposal");
    var reused = Flow.AttachmentUpload.create(); reused.setType("bootstrap-proposal"); reused.setFileName("changed-r1.json");
    reused.setMediaType("application/vnd.klab.proposal+yaml"); reused.setContent(json.writeValueAsBytes(next));
    var invalid = fixture.manager.addAttachment(flow.getId(), coalescing, reused, fixture.editor);
    long before = fixture.manager.getReviewCycles(flow.getId(), fixture.editor).revision();
    assertThrows(KlabIllegalStateException.class, () -> fixture.manager.reviewCycle(flow.getId(), command(before,
        Operation.OPEN_CYCLE, coalescing, null, null, invalid.getId(), null), fixture.editor));
    ((com.fasterxml.jackson.databind.node.ObjectNode) next.path("proposal"))
        .put("revision_id", "test-r2").put("supersedes_revision", "test-r1");
    var upload = Flow.AttachmentUpload.create(); upload.setType("bootstrap-proposal"); upload.setFileName("r2.json");
    upload.setMediaType("application/vnd.klab.proposal+yaml"); upload.setContent(json.writeValueAsBytes(next));
    var attachment = fixture.manager.addAttachment(flow.getId(), coalescing, upload, fixture.editor);
    view = fixture.manager.getReviewCycles(flow.getId(), fixture.editor);
    view = fixture.manager.reviewCycle(flow.getId(), command(view.revision(), Operation.OPEN_CYCLE,
        coalescing, null, null, attachment.getId(), null), fixture.editor);
    assertEquals(2, view.cycles().size()); assertEquals(closed, view.cycles().getFirst());
    assertEquals("test-r2", view.cycles().getLast().candidate().revisionId());
    assertArrayEquals(original, fixture.manager.getReviewArtifact(flow.getId(), closed.id(), proposal.getId(), fixture.editor));
    assertEquals(Flow.Status.ACTIVE, fixture.store.getFlow(flow.getId()).getStatus());
    assertNull(fixture.store.getFlow(flow.getId()).getStates().get(fixture.stage).getProposalReview());
  }

  @Test void stageLifecycleCapabilitiesBindingsAndTerminationAreAtomic() throws Exception {
    var fixture = new OntologyReviewBootstrapActionTest().new Fixture(null); var flow = fixture.prepare();
    var initial = fixture.manager.getReviewCycles(flow.getId(), fixture.editor);
    assertEquals(Phase.BOOTSTRAP, initial.phase()); assertTrue(initial.canOpenCycle());
    assertFalse(initial.canCoalesce()); assertFalse(initial.canTerminate());
    var proposal = flow.getStates().get(fixture.stage).getAttachments().stream()
        .filter(a -> "bootstrap-proposal".equals(a.getType())).findFirst().orElseThrow();
    var reviewing = fixture.manager.reviewCycle(flow.getId(), command(initial.revision(), Operation.OPEN_CYCLE,
        fixture.stage, null, Audience.COMMUNITY, proposal.getId(), null), fixture.editor);
    assertEquals(Phase.REVIEW, reviewing.phase()); assertNull(reviewing.editableStageId());
    assertFalse(reviewing.canOpenCycle()); assertTrue(reviewing.canCoalesce());
    var cycle = reviewing.cycles().getFirst(); var community = cycle.rounds().getFirst();
    assertEquals(Audience.COMMUNITY, community.audience()); assertNotNull(community.stageId());
    var stored = fixture.store.getFlow(flow.getId());
    assertEquals(Flow.StateStatus.CLOSED, stored.getStates().get(fixture.stage).getStatus());
    assertEquals("community-review", stored.getStates().get(community.stageId()).getSchemaId());
    var stale = command(initial.revision(), Operation.ENTER_COALESCING, null, null, null, null, null);
    assertThrows(KlabIllegalStateException.class, () -> fixture.manager.reviewCycle(flow.getId(), stale, fixture.editor));
    assertEquals(reviewing, fixture.manager.getReviewCycles(flow.getId(), fixture.editor));
    assertThrows(KlabIllegalStateException.class, () -> fixture.manager.reviewCycle(flow.getId(),
        command(reviewing.revision(), Operation.CLOSE_CYCLE, null, cycle.id(), null, null, null), fixture.editor));
    var coalesced = fixture.manager.reviewCycle(flow.getId(), command(reviewing.revision(), Operation.ENTER_COALESCING,
        null, null, null, null, null), fixture.editor);
    assertEquals(Phase.COALESCING, coalesced.phase()); assertTrue(coalesced.canOpenCycle());
    assertTrue(coalesced.canTerminate()); assertFalse(coalesced.canOpenRound());
    assertNotNull(coalesced.cycles().getFirst().closedAt());
    assertEquals(Flow.StateStatus.CLOSED, fixture.store.getFlow(flow.getId()).getStates().get(community.stageId()).getStatus());
    var terminated = fixture.manager.reviewCycle(flow.getId(), command(coalesced.revision(), Operation.TERMINATE,
        null, null, null, null, null), fixture.editor);
    assertEquals(Phase.TERMINATED, terminated.phase()); assertFalse(terminated.canOpenCycle());
    assertNull(terminated.editableStageId()); assertEquals(coalesced.cycles(), terminated.cycles());
    assertEquals(Flow.Status.CLOSED, fixture.store.getFlow(flow.getId()).getStatus());
    assertTrue(fixture.store.getFlow(flow.getId()).getCurrentStateIds().isEmpty());
    assertThrows(KlabIllegalStateException.class, () -> fixture.manager.reviewCycle(flow.getId(),
        command(terminated.revision(), Operation.OPEN_CYCLE, fixture.stage, null, null, proposal.getId(), null), fixture.editor));
  }

  @Test void bootstrapIsCreatorExclusiveEvenForAdminsAndPublicReadAndOwnershipIsBound() throws Exception {
    var fixture = new OntologyReviewBootstrapActionTest().new Fixture(null); var flow = fixture.prepare();
    var admin = new ProposalReviewProtocolTest().scope("administrator", "ADMIN");
    assertEquals(0, fixture.store.catalogWrites, "Private bootstrap must not leak into the shared asset catalog");
    // A public flag must never bypass this stage's confidentiality, including after handoff.
    var internal = fixture.store.getFlow(flow.getId()); internal.setPublicRead(true); fixture.store.putFlow(internal);
    assertThrows(KlabResourceAccessException.class, () -> fixture.manager.getFlow(flow.getId(), admin));
    assertThrows(KlabResourceAccessException.class, () -> fixture.manager.getReviewCycles(flow.getId(), admin));
    assertThrows(KlabIllegalStateException.class, () -> fixture.manager.reopenFlow(flow.getId(), admin));
    assertThrows(KlabResourceAccessException.class, () -> fixture.manager.getActions(flow.getId(), fixture.stage, admin));
    var proposal = flow.getStates().get(fixture.stage).getAttachments().stream()
        .filter(a -> "bootstrap-proposal".equals(a.getType())).findFirst().orElseThrow();
    assertThrows(KlabResourceAccessException.class, () -> fixture.manager.getAttachment(flow.getId(), proposal.getId(), admin));
    var view = fixture.manager.reviewCycle(flow.getId(), command(flow.getRevision(), Operation.OPEN_CYCLE,
        fixture.stage, null, null, proposal.getId(), null), fixture.editor);
    assertTrue(fixture.store.catalogWrites > 0, "Review handoff may publish its ordinary catalog reference");
    assertFalse(fixture.manager.getFlow(flow.getId(), admin).getStates().containsKey(fixture.stage));
    assertThrows(KlabResourceAccessException.class, () -> fixture.manager.getAttachment(flow.getId(), proposal.getId(), admin));
    assertArrayEquals(fixture.store.attachments.get(proposal.getId()), fixture.manager.getReviewArtifact(flow.getId(),
        view.cycles().getFirst().id(), proposal.getId(), admin));
  }


  @Test void genericEndpointsCannotBypassBootstrapOwnershipOrFrozenStages() throws Exception {
    var fixture = new OntologyReviewBootstrapActionTest().new Fixture(null); var flow = fixture.prepare();
    var identities = new ProposalReviewProtocolTest(); var admin = identities.scope("admin", "ADMIN");
    var revoked = new ReviewCycleProtocolTest().community("editor", true, false);
    assertThrows(KlabResourceAccessException.class, () -> fixture.manager.getFlow(flow.getId(), revoked));
    assertThrows(KlabResourceAccessException.class, () -> fixture.manager.getActions(flow.getId(), fixture.stage, revoked));
    assertThrows(KlabResourceAccessException.class, () -> fixture.manager.executeAction(flow.getId(), fixture.stage,
        "prepare-review-draft", fixture.request(flow.getRevision()), admin));
    var upload = Flow.AttachmentUpload.create(); upload.setType("supporting-material"); upload.setFileName("attempt.txt");
    upload.setMediaType("text/plain"); upload.setContent(new byte[]{1});
    assertThrows(KlabResourceAccessException.class, () -> fixture.manager.addAttachment(flow.getId(), fixture.stage, upload, admin));
    var state = Flow.State.create(); state.setSchemaId("bootstrap"); state.setOwner("admin");
    assertThrows(KlabResourceAccessException.class, () -> fixture.manager.updateState(flow.getId(), fixture.stage, state, admin));
    state.setOwner("editor"); state.setAssignees(java.util.Set.of("admin"));
    assertThrows(KlabIllegalStateException.class, () -> fixture.manager.updateState(flow.getId(), fixture.stage, state, fixture.editor));
    assertThrows(KlabIllegalStateException.class, () -> fixture.manager.createState(flow.getId(), state, fixture.editor));
    assertThrows(KlabIllegalStateException.class, () -> fixture.manager.deleteState(flow.getId(), fixture.stage, fixture.editor));
    var proposal = flow.getStates().get(fixture.stage).getAttachments().stream()
        .filter(a -> "bootstrap-proposal".equals(a.getType())).findFirst().orElseThrow();
    var view = fixture.manager.reviewCycle(flow.getId(), command(flow.getRevision(), Operation.OPEN_CYCLE,
        fixture.stage, null, null, proposal.getId(), null), fixture.editor);
    assertThrows(KlabIllegalStateException.class, () -> fixture.manager.addAttachment(flow.getId(), fixture.stage, upload, fixture.editor));
    assertThrows(KlabIllegalStateException.class, () -> fixture.manager.updateState(flow.getId(), fixture.stage, state, fixture.editor));
    view = fixture.manager.reviewCycle(flow.getId(), command(view.revision(), Operation.ENTER_COALESCING,
        null, null, null, null, null), fixture.editor);
    fixture.manager.reviewCycle(flow.getId(), command(view.revision(), Operation.TERMINATE,
        null, null, null, null, null), fixture.editor);
    assertThrows(KlabIllegalStateException.class, () -> fixture.manager.reopenFlow(flow.getId(), admin));
  }


  @Test void creationBindsBootstrapOwnerAndRejectsInjectedMembershipAndLifecycle() throws Exception {
    var fixture = new OntologyReviewBootstrapActionTest().new Fixture(null);
    var initial = Flow.State.create(); initial.setSchemaId("bootstrap"); initial.setOwner("intruder");
    initial.setAssignees(java.util.Set.of("intruder")); initial.setAssetUrn("test.ontology");
    initial.setAssetType(org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.ONTOLOGY);
    initial.setMetadata(java.util.Map.of("cycleId", "forged", "phase", "terminated"));
    var flow = fixture.manager.createFlow("ontology-review-pilot", initial, true, fixture.editor);
    var bootstrap = flow.getStates().values().iterator().next();
    assertEquals("editor", flow.getOwner()); assertEquals("editor", bootstrap.getOwner());
    assertTrue(bootstrap.getAssignees().isEmpty()); assertTrue(bootstrap.getMetadata().isEmpty());
    assertEquals(Phase.BOOTSTRAP, fixture.manager.getReviewCycles(flow.getId(), fixture.editor).phase());
    var intruder = new ProposalReviewProtocolTest().scope("intruder", "EDITOR");
    assertThrows(KlabResourceAccessException.class, () -> fixture.manager.getFlow(flow.getId(), intruder));
  }


  @Test void administrativeCoalescingPreservesCreatingEditorsOwnership() throws Exception {
    var fixture = new OntologyReviewBootstrapActionTest().new Fixture(null); var flow = fixture.prepare();
    var proposal = flow.getStates().get(fixture.stage).getAttachments().stream()
        .filter(a -> "bootstrap-proposal".equals(a.getType())).findFirst().orElseThrow();
    var review = fixture.manager.reviewCycle(flow.getId(), command(flow.getRevision(), Operation.OPEN_CYCLE,
        fixture.stage, null, null, proposal.getId(), null), fixture.editor);
    var admin = new ProposalReviewProtocolTest().scope("administrator", "ADMIN");
    var coalesced = fixture.manager.reviewCycle(flow.getId(), command(review.revision(), Operation.ENTER_COALESCING,
        null, null, null, null, null), admin);
    var state = fixture.store.getFlow(flow.getId()).getStates().get(coalesced.editableStageId());
    assertEquals("editor", state.getOwner());
    assertEquals("administrator", fixture.store.getFlow(flow.getId()).getHistory().getLast().getActor());
    var creatorView = fixture.manager.getReviewCycles(flow.getId(), fixture.editor);
    assertTrue(creatorView.stages().stream().filter(s -> s.id().equals(state.getId())).findFirst().orElseThrow().canEdit());
    var upload = Flow.AttachmentUpload.create(); upload.setType("supporting-material"); upload.setFileName("integration.txt");
    upload.setMediaType("text/plain"); upload.setContent("Creator can continue editorial work".getBytes(StandardCharsets.UTF_8));
    assertNotNull(fixture.manager.addAttachment(flow.getId(), state.getId(), upload, fixture.editor));
  }

}
