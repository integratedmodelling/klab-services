package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.*;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.*;
import org.integratedmodelling.klab.services.base.ReviewNotificationOutbox;
import org.junit.jupiter.api.Test;

class ReviewSecurityAuditTest {
  @Test void foreignDraftOwnershipIsCheckedBeforeAlternativeProposalValidation() throws Exception {
    var f = new ReviewCycleProtocolTest(); var cycle = f.open(); var round = f.round(cycle, Audience.REVIEWER);
    var alice = f.fixtures.scope("alice", "REVIEWER"); var bob = f.fixtures.scope("bob", "REVIEWER");
    var draft = f.save(cycle, round, null, "private", false, alice).cycles().getFirst().rounds().getFirst().contributions().getFirst();
    long revision = f.store.getFlow(f.flowId).getRevision();
    assertThrows(KlabResourceAccessException.class, () -> f.manager.reviewCycle(f.flowId,
        new Command(revision, Operation.SAVE_CONTRIBUTION, null, cycle.id(), round.id(), null,
            draft.id(), "overwrite", true, null, null, new ProposalInput("{invalid", null)), bob));
    assertEquals(revision, f.store.getFlow(f.flowId).getRevision());
    assertEquals(draft, f.manager.getReviewCycles(f.flowId, alice).cycles().getFirst().rounds().getFirst().contributions().getFirst());
  }
  @Test void candidateCannotAliasAnAlreadySubmittedAlternativeRevision() throws Exception {
    var f = new OntologyReviewBootstrapActionTest().new Fixture(null); var flow = f.prepare();
    var attachments = flow.getStates().get(f.stage).getAttachments();
    String proposal = attachments.stream().filter(a -> a.getType().equals("bootstrap-proposal")).findFirst().orElseThrow().getId();
    String ontology = attachments.stream().filter(a -> a.getType().equals("candidate-ontology")).findFirst().orElseThrow().getId();
    var view = f.manager.reviewCycle(flow.getId(), new Command(flow.getRevision(), Operation.OPEN_CYCLE, f.stage,
        null, null, null, null, null, false, proposal, ontology), f.editor);
    var cycle = view.cycles().getFirst();
    view = f.manager.reviewCycle(flow.getId(), new Command(view.revision(), Operation.OPEN_ROUND, null,
        cycle.id(), null, Audience.REVIEWER, null, null, false), f.editor);
    var round = view.cycles().getFirst().rounds().getFirst(); var reviewer = new ProposalReviewProtocolTest().scope("alice", "REVIEWER");
    String alternative = ReviewContributionProposalTest.document("r2");
    view = f.manager.reviewCycle(flow.getId(), new Command(view.revision(), Operation.SAVE_CONTRIBUTION, null,
        cycle.id(), round.id(), null, null, "alternative", true, null, null, new ProposalInput(alternative, null)), reviewer);
    view = f.manager.reviewCycle(flow.getId(), new Command(view.revision(), Operation.ENTER_COALESCING, null,
        null, null, null, null, null, false), f.editor);
    var coalescing = view.editableStageId();
    var sourceUpload = Flow.AttachmentUpload.create(); sourceUpload.setType("candidate-ontology"); sourceUpload.setFileName("source.kwv");
    sourceUpload.setMediaType("application/vnd.klab.ontology"); sourceUpload.setContent(f.store.attachments.get(ontology));
    var nextOntology = f.manager.addAttachment(flow.getId(), coalescing, sourceUpload, f.editor).getId();
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper(); var changed = mapper.readTree(alternative);
    ((com.fasterxml.jackson.databind.node.ObjectNode) changed.path("proposal")).put("title", "Different bytes with reused revision");
    var upload = Flow.AttachmentUpload.create(); upload.setType("bootstrap-proposal"); upload.setFileName("aliased.json");
    upload.setMediaType("application/vnd.klab.proposal+yaml"); upload.setContent(mapper.writeValueAsBytes(changed));
    var stored = f.manager.addAttachment(flow.getId(), coalescing, upload, f.editor);
    long revision = f.manager.getReviewCycles(flow.getId(), f.editor).revision();
    assertThrows(KlabIllegalStateException.class, () -> f.manager.reviewCycle(flow.getId(), new Command(revision,
        Operation.OPEN_CYCLE, coalescing, null, null, null, null, null, false, stored.getId(), nextOntology), f.editor));
    assertEquals(revision, f.manager.getReviewCycles(flow.getId(), f.editor).revision());
    assertEquals(1, f.manager.getReviewCycles(flow.getId(), f.editor).cycles().size());
    upload.setFileName("exact-alternative.json"); upload.setContent(alternative.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var exact = f.manager.addAttachment(flow.getId(), coalescing, upload, f.editor);
    var adopted = f.manager.reviewCycle(flow.getId(), new Command(f.manager.getReviewCycles(flow.getId(), f.editor).revision(),
        Operation.OPEN_CYCLE, coalescing, null, null, null, null, null, false, exact.getId(), nextOntology), f.editor);
    assertEquals("r2", adopted.cycles().getLast().candidate().revisionId());
  }
  @Test void notificationEditorPrivilegeDoesNotAuthorizeUnknownStageCoordinates() throws Exception {
    var f = new ReviewCycleProtocolTest(); var cycle = f.open(); var round = f.round(cycle, Audience.REVIEWER);
    var event = new ReviewNotificationOutbox.Event("fixture", f.flowId, "round/unknown",
        ReviewNotificationOutbox.Kind.ROUND_OPENED, List.of(ReviewWorkflowNotifications.recipientId("editor")));
    assertFalse(ReviewWorkflowNotifications.admitted(f.store.getFlow(f.flowId),
        f.store.getWorkflow(ProposalReviewProtocolTest.WORKFLOW), event, WorkflowParticipant.from(f.editor)));
    java.util.function.BiFunction<String, ReviewNotificationOutbox.Kind, Boolean> allowed = (coordinate, kind) ->
        ReviewWorkflowNotifications.admitted(f.store.getFlow(f.flowId), f.store.getWorkflow(ProposalReviewProtocolTest.WORKFLOW),
            new ReviewNotificationOutbox.Event("fixture", f.flowId, coordinate, kind, event.recipients()), WorkflowParticipant.from(f.editor));
    assertFalse(allowed.apply("round/" + round.id(), ReviewNotificationOutbox.Kind.CYCLE_CLOSED));
    assertFalse(allowed.apply("cycle/" + cycle.id(), ReviewNotificationOutbox.Kind.ROUND_OPENED));
    assertFalse(allowed.apply("round/" + round.id(), ReviewNotificationOutbox.Kind.ROUND_CLOSED));
    assertFalse(allowed.apply("cycle/" + cycle.id(), ReviewNotificationOutbox.Kind.CYCLE_CLOSED));
    f.act(Operation.CLOSE_ROUND, cycle.id(), round.id(), null, null, null, false, f.editor);
    assertTrue(allowed.apply("round/" + round.id(), ReviewNotificationOutbox.Kind.ROUND_CLOSED));
    f.act(Operation.CLOSE_CYCLE, cycle.id(), null, null, null, null, false, f.editor);
    assertTrue(allowed.apply("cycle/" + cycle.id(), ReviewNotificationOutbox.Kind.CYCLE_CLOSED));
  }
  @Test void commandClaimsCannotPromoteAnAuthenticatedCommunityParticipant() throws Exception {
    var f = new ReviewCycleProtocolTest(); var cycle = f.open(); f.round(cycle, Audience.COMMUNITY);
    var citizen = f.community("citizen", true, true);
    var command = Utils.Json.parseObject("""
        {"expectedRevision":%d,"operation":"OPEN_ROUND","cycleId":"%s","audience":"REVIEWER",
         "canManage":true,"owner":"citizen","roles":["ADMIN","EDITOR"],"identity":"editor"}
        """.formatted(f.store.getFlow(f.flowId).getRevision(), cycle.id()), Command.class);
    assertTrue(WorkflowParticipant.from(citizen).getRoles().isEmpty());
    assertThrows(KlabResourceAccessException.class, () -> f.manager.reviewCycle(f.flowId, command, citizen));
    assertFalse(f.manager.getReviewCycles(f.flowId, citizen).canManage());
  }
}
