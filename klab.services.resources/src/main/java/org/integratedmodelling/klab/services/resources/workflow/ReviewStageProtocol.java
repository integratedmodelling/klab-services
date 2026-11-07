package org.integratedmodelling.klab.services.resources.workflow;

import java.time.Instant;
import java.util.*;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.*;

/** Lifecycle is derived from real aggregate stages, never a second editable phase flag. */
final class ReviewStageProtocol {
  private ReviewStageProtocol() {}
  static boolean enabled(Workflow workflow) { return Boolean.TRUE.equals(workflow.getMetadata().get("reviewStages")); }
  static Phase phase(Flow flow) {
    if (flow.getStatus() == Flow.Status.CLOSED) return Phase.TERMINATED;
    if (flow.getCurrentStateIds().stream().map(flow.getStates()::get).filter(Objects::nonNull)
        .anyMatch(s -> "bootstrap".equals(s.getSchemaId()))) return Phase.BOOTSTRAP;
    if (flow.getCurrentStateIds().stream().map(flow.getStates()::get).filter(Objects::nonNull)
        .anyMatch(s -> "coalescing".equals(s.getSchemaId()))) return Phase.COALESCING;
    return Phase.REVIEW;
  }
  static String editableStageId(Flow flow) {
    return flow.getCurrentStateIds().stream().map(flow.getStates()::get).filter(Objects::nonNull)
        .filter(s -> s.getStatus() == Flow.StateStatus.OPEN &&
            ("bootstrap".equals(s.getSchemaId()) || "coalescing".equals(s.getSchemaId())))
        .map(Flow.State::getId).findFirst().orElse(null);
  }
  static boolean creator(Flow flow, WorkflowParticipant actor) {
    return actor.isAuthenticated() && Objects.equals(flow.getOwner(), actor.getIdentity());
  }
  static boolean canSeeStage(Flow flow, Workflow workflow, Flow.State state, WorkflowParticipant actor) {
    if (!enabled(workflow)) return true;
    if ("bootstrap".equals(state.getSchemaId())) return creator(flow, actor) && ReviewCycleProtocol.editor(flow, workflow, actor);
    if (!actor.isAuthenticated()) return false;
    if (ReviewCycleProtocol.editor(flow, workflow, actor)) return true;
    return flow.getReviewCycles().stream().flatMap(c -> c.rounds().stream())
        .anyMatch(r -> Objects.equals(r.stageId(), state.getId()) && ReviewCycleProtocol.admitted(workflow, actor, r.audience()));
  }
  static void requireEditable(Flow flow, Workflow workflow, Flow.State state, WorkflowParticipant actor) {
    if (!enabled(workflow)) return;
    if ("bootstrap".equals(state.getSchemaId()) && !creator(flow, actor))
      throw new KlabResourceAccessException("Bootstrap belongs exclusively to its creating editor");
    ReviewCycleProtocol.requireEditor(flow, workflow, actor);
    if (!Objects.equals(editableStageId(flow), state.getId())) fail("Only the current bootstrap or coalescing stage is editable");
  }
  static View view(Flow flow, Workflow workflow, WorkflowParticipant actor) {
    var visible = ReviewCycleProtocol.visible(flow, workflow, actor);
    boolean manage = ReviewCycleProtocol.editor(flow, workflow, actor);
    var rounds = ReviewCycleProtocol.contributableRounds(visible, workflow, actor);
    if (!enabled(workflow)) return new View(flow.getRevision(), visible, manage, actor.getIdentity(), rounds);
    var phase = phase(flow);
    var stages = new ArrayList<Stage>();
    for (var state : flow.getStates().values()) {
      if (!canSeeStage(flow, workflow, state, actor)) continue;
      String cycleId = null, roundId = null;
      for (var cycle : visible) for (var round : cycle.rounds()) if (Objects.equals(round.stageId(), state.getId())) {
        cycleId = cycle.id(); roundId = round.id();
      }
      stages.add(new Stage(state.getId(), state.getSchemaId(), cycleId, roundId,
          state.getStatus() == Flow.StateStatus.OPEN, manage && Objects.equals(editableStageId(flow), state.getId()), rounds.contains(roundId)));
    }
    return new View(flow.getRevision(), visible, manage, actor.getIdentity(), rounds, phase,
        manage ? editableStageId(flow) : null, stages,
        manage && (phase == Phase.BOOTSTRAP || phase == Phase.COALESCING),
        manage && phase == Phase.REVIEW, manage && phase == Phase.COALESCING, manage && phase == Phase.REVIEW);
  }
  static void requireCycleSource(Flow flow, Workflow workflow, WorkflowParticipant actor, Flow.State source) {
    if (!enabled(workflow)) return;
    if (source == null) fail("A current editorial stage is required");
    requireEditable(flow, workflow, source, actor);
    if (flow.getReviewCycles().stream().anyMatch(c -> c.closedAt() == null)) fail("Coalesce before starting a new review cycle");
  }
  static void freezeSource(Flow flow, Workflow workflow, Flow.State source) {
    if (enabled(workflow)) closeStage(flow, source.getId());
  }
  static String openRoundStage(Flow flow, Workflow workflow, WorkflowParticipant actor, Cycle cycle, Audience audience) {
    if (!enabled(workflow)) return null;
    if (phase(flow) != Phase.REVIEW || flow.getStatus() != Flow.Status.ACTIVE) fail("Review rounds require an active review phase");
    return newStage(flow, audience == Audience.REVIEWER ? "peer-review" : "community-review", actor, cycle.sourceStateId(), "open-round").getId();
  }
  static void closeStage(Flow flow, String id) {
    if (id == null) return;
    var state = flow.getStates().get(id);
    if (state == null) fail("Review stage binding is missing");
    state.setStatus(Flow.StateStatus.CLOSED); state.setUpdatedAt(now()); flow.getCurrentStateIds().remove(id);
  }
  static void enterCoalescing(Flow flow, Workflow workflow, WorkflowParticipant actor) {
    requireLifecycle(flow, workflow, actor, Phase.REVIEW);
    String source = null;
    for (var cycle : List.copyOf(flow.getReviewCycles())) if (cycle.closedAt() == null) {
      source = cycle.sourceStateId();
      ReviewCycleProtocol.close(flow, workflow, actor, cycle.id(), null);
      var command = new Command(flow.getRevision(), Operation.CLOSE_CYCLE, null, cycle.id(), null, null, null, null, false);
      ReviewWorkflowNotifications.stage(flow, command); ReviewWorkflowArchives.stage(flow, command);
    }
    if (source == null) fail("An open cycle is required for coalescing");
    newStage(flow, "coalescing", actor, source, "enter-coalescing");
  }
  static void terminate(Flow flow, Workflow workflow, WorkflowParticipant actor) {
    requireLifecycle(flow, workflow, actor, Phase.COALESCING);
    var source = editableStageId(flow); closeStage(flow, source);
    var terminal = newStage(flow, "terminated", actor, source, "terminate");
    closeStage(flow, terminal.getId()); flow.setStatus(Flow.Status.CLOSED);
  }
  private static void requireLifecycle(Flow flow, Workflow workflow, WorkflowParticipant actor, Phase expected) {
    if (!enabled(workflow)) fail("This workflow does not use staged review");
    ReviewCycleProtocol.requireEditor(flow, workflow, actor);
    if (phase(flow) != expected) fail("Command requires phase " + expected);
  }
  private static Flow.State newStage(Flow flow, String schema, WorkflowParticipant actor, String sourceId, String transition) {
    var state = Flow.State.create(); state.setId(UUID.randomUUID().toString()); state.setFlowId(flow.getId());
    state.setSchemaId(schema); state.setOwner(flow.getOwner()); state.setAssetUrn(flow.getAssetUrn());
    state.setAssetType(flow.getAssetType()); state.setPermissionsOwnerUrn(flow.getPermissionsOwnerUrn());
    state.setServiceId(flow.getServiceId()); state.setCreatedAt(now()); state.setUpdatedAt(now()); state.setStatus(Flow.StateStatus.OPEN);
    flow.getStates().put(state.getId(), state); flow.getCurrentStateIds().add(state.getId());
    var event = new org.integratedmodelling.klab.api.services.resources.workflow.impl.FlowImpl.TransactionImpl();
    event.setId(UUID.randomUUID().toString()); event.setFlowId(flow.getId()); event.setTransitionId(transition);
    event.setSourceStateId(sourceId); event.setTargetStateId(state.getId()); event.setActor(actor.getIdentity()); event.setTimestamp(now());
    flow.getHistory().add(event); return state;
  }
  private static Instant now() { return Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS); }
  private static void fail(String message) { throw new KlabIllegalStateException(message); }
}
