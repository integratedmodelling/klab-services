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
    assertThrows(KlabResourceAccessException.class, () -> fixture.manager.getFlow(flow.getId(), citizen));
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
    var state = Flow.State.create(); state.setSchemaId("editing");
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
    view = fixture.manager.reviewCycle(flow.getId(), command(view.revision(), Operation.CLOSE_CYCLE,
        null, id, null, null, null), fixture.editor);
    var closed = view.cycles().getFirst(); byte[] original = fixture.store.attachments.get(proposal.getId()).clone();
    var json = new com.fasterxml.jackson.databind.ObjectMapper();
    var next = json.readTree(original);
    ((com.fasterxml.jackson.databind.node.ObjectNode) next.path("proposal")).put("title", "Integrated proposal");
    var reused = Flow.AttachmentUpload.create(); reused.setType("bootstrap-proposal"); reused.setFileName("changed-r1.json");
    reused.setMediaType("application/vnd.klab.proposal+yaml"); reused.setContent(json.writeValueAsBytes(next));
    var invalid = fixture.manager.addAttachment(flow.getId(), fixture.stage, reused, fixture.editor);
    long before = fixture.manager.getReviewCycles(flow.getId(), fixture.editor).revision();
    assertThrows(KlabIllegalStateException.class, () -> fixture.manager.reviewCycle(flow.getId(), command(before,
        Operation.OPEN_CYCLE, fixture.stage, null, null, invalid.getId(), null), fixture.editor));
    ((com.fasterxml.jackson.databind.node.ObjectNode) next.path("proposal"))
        .put("revision_id", "test-r2").put("supersedes_revision", "test-r1");
    var upload = Flow.AttachmentUpload.create(); upload.setType("bootstrap-proposal"); upload.setFileName("r2.json");
    upload.setMediaType("application/vnd.klab.proposal+yaml"); upload.setContent(json.writeValueAsBytes(next));
    var attachment = fixture.manager.addAttachment(flow.getId(), fixture.stage, upload, fixture.editor);
    view = fixture.manager.getReviewCycles(flow.getId(), fixture.editor);
    view = fixture.manager.reviewCycle(flow.getId(), command(view.revision(), Operation.OPEN_CYCLE,
        fixture.stage, null, null, attachment.getId(), null), fixture.editor);
    assertEquals(2, view.cycles().size()); assertEquals(closed, view.cycles().getFirst());
    assertEquals("test-r2", view.cycles().getLast().candidate().revisionId());
    assertArrayEquals(original, fixture.manager.getReviewArtifact(flow.getId(), closed.id(), proposal.getId(), fixture.editor));
    assertEquals(Flow.Status.ACTIVE, fixture.store.getFlow(flow.getId()).getStatus());
    assertNull(fixture.store.getFlow(flow.getId()).getStates().get(fixture.stage).getProposalReview());
  }
}
