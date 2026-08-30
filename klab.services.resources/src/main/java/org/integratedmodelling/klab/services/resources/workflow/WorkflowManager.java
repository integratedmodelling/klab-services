package org.integratedmodelling.klab.services.resources.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService.SubmissionMode;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowParticipant;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowRole;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowUrns;
import org.integratedmodelling.klab.resources.WorkflowStore;

/**
 * Authorization, validation and aggregate transaction layer for workflows.
 *
 * <p>All mutating methods are synchronized so a read/validate/write cycle is atomic within one
 * resources-service process. The expected revision on transition requests prevents lost updates
 * across clients. Nitrite persists each resulting aggregate in one repository operation.
 */
public class WorkflowManager {

  private static final String WORKFLOW_INDEX = "workflows/index.txt";
  private final WorkflowStore kbox;

  public WorkflowManager(WorkflowStore kbox) {
    this.kbox = kbox;
    loadBundledWorkflows();
  }

  private void loadBundledWorkflows() {
    try (var stream = getClass().getClassLoader().getResourceAsStream(WORKFLOW_INDEX)) {
      if (stream == null) return;
      var mapper = new ObjectMapper(new YAMLFactory());
      JacksonConfiguration.configureObjectMapperForKlabTypes(mapper);
      try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        for (String line :
            reader
                .lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                .toList()) {
          try (var definition =
              getClass().getClassLoader().getResourceAsStream("workflows/" + line)) {
            if (definition == null)
              throw new KlabIllegalStateException("Missing bundled workflow " + line);
            Workflow workflow = mapper.readValue(definition, Workflow.class);
            var errors = workflow.validate();
            if (!errors.isEmpty())
              throw new KlabIllegalStateException(
                  "Invalid bundled workflow " + line + ": " + String.join("; ", errors));
            var existing = kbox.getWorkflow(workflow.getId());
            if (existing == null || !Objects.equals(existing.getVersion(), workflow.getVersion()))
              kbox.putWorkflow(workflow);
          }
        }
      }
    } catch (KlabIllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new KlabIllegalStateException(e);
    }
  }

  public Workflow getWorkflow(String id) {
    var workflow = kbox.getWorkflow(id);
    if (workflow == null) throw new KlabIllegalArgumentException("Unknown workflow " + id);
    workflow.validate();
    return workflow;
  }

  public Workflow getWorkflow(String id, String version) {
    var workflow = kbox.getWorkflow(id, version);
    if (workflow == null)
      throw new KlabIllegalArgumentException("Unknown workflow " + id + "@" + version);
    workflow.validate();
    return workflow;
  }

  /** Retrieve any persistent workflow artifact through its generic Resources CRUD URN. */
  public KlabAsset retrieve(String urn, KlabAsset.KnowledgeClass type, UserScope scope) {
    var coordinates = WorkflowUrns.parse(urn, type);
    return switch (type) {
      case WORKFLOW -> getWorkflow(coordinates.ownerId(), coordinates.version());
      case WORKFLOW_STATE -> {
        var workflow = getWorkflow(coordinates.ownerId(), coordinates.version());
        var state = workflow.getStates().get(coordinates.artifactId());
        if (state == null) throw new KlabIllegalArgumentException("Unknown workflow state " + urn);
        yield state;
      }
      case WORKFLOW_TRANSITION -> {
        var workflow = getWorkflow(coordinates.ownerId(), coordinates.version());
        var transition = workflow.getTransitions().get(coordinates.artifactId());
        if (transition == null)
          throw new KlabIllegalArgumentException("Unknown workflow transition " + urn);
        yield transition;
      }
      case FLOW -> getFlow(coordinates.ownerId(), scope);
      case FLOW_STATE -> {
        var flow = getFlow(coordinates.ownerId(), scope);
        var state = flow.getStates().get(coordinates.artifactId());
        if (state == null)
          throw new KlabResourceAccessException("Unknown or inaccessible flow state " + urn);
        yield state;
      }
      case FLOW_TRANSITION ->
          getFlow(coordinates.ownerId(), scope).getHistory().stream()
              .filter(transaction -> coordinates.artifactId().equals(transaction.getId()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new KlabResourceAccessException(
                          "Unknown or inaccessible flow transition " + urn));
      case FLOW_ATTACHMENT ->
          getFlow(coordinates.ownerId(), scope).getStates().values().stream()
              .flatMap(state -> state.getAttachments().stream())
              .filter(attachment -> coordinates.artifactId().equals(attachment.getId()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new KlabResourceAccessException(
                          "Unknown or inaccessible flow attachment " + urn));
      default -> throw new KlabIllegalArgumentException(type + " is not a workflow asset class");
    };
  }

  public List<? extends KlabAsset> list(KlabAsset.KnowledgeClass type, UserScope scope) {
    return switch (type) {
      case WORKFLOW -> kbox.listWorkflows().stream().peek(Workflow::validate).toList();
      case WORKFLOW_STATE ->
          kbox.listWorkflows().stream()
              .peek(Workflow::validate)
              .flatMap(workflow -> workflow.getStates().values().stream())
              .toList();
      case WORKFLOW_TRANSITION ->
          kbox.listWorkflows().stream()
              .peek(Workflow::validate)
              .flatMap(workflow -> workflow.getTransitions().values().stream())
              .toList();
      case FLOW -> listFlows(true, scope);
      case FLOW_STATE ->
          listFlows(true, scope).stream()
              .flatMap(flow -> flow.getStates().values().stream())
              .toList();
      case FLOW_TRANSITION ->
          listFlows(true, scope).stream().flatMap(flow -> flow.getHistory().stream()).toList();
      case FLOW_ATTACHMENT ->
          listFlows(true, scope).stream()
              .flatMap(flow -> flow.getStates().values().stream())
              .flatMap(state -> state.getAttachments().stream())
              .toList();
      default -> throw new KlabIllegalArgumentException(type + " is not a workflow asset class");
    };
  }

  public synchronized Workflow submitWorkflow(
      Workflow workflow, SubmissionMode mode, UserScope scope) {
    requireAdmin(WorkflowParticipant.from(scope));
    if (workflow == null)
      throw new KlabIllegalArgumentException("A workflow definition is required");
    var errors = workflow.validate();
    if (!errors.isEmpty()) throw new KlabIllegalArgumentException(String.join("; ", errors));
    var existing = kbox.getWorkflow(workflow.getId(), workflow.getVersion());
    if (existing != null) {
      if (mode == SubmissionMode.ADD || mode == SubmissionMode.CREATE_OR_UPDATE) return existing;
      throw new KlabIllegalStateException(
          "Workflow versions are immutable; submit a new version instead of " + mode);
    }
    if (mode == SubmissionMode.UPDATE
        || mode == SubmissionMode.REPLACE
        || mode == SubmissionMode.MERGE)
      throw new KlabIllegalStateException(
          mode + " requires an existing workflow, whose version is immutable");
    if (!kbox.putWorkflow(workflow))
      throw new KlabIllegalStateException("Cannot persist " + workflow.getUrn());
    return workflow;
  }

  public Flow submitFlow(Flow submitted, SubmissionMode mode, UserScope scope) {
    if (submitted == null || submitted.getWorkflowId() == null)
      throw new KlabIllegalArgumentException("A submitted flow needs a workflowId");
    if (mode == SubmissionMode.UPDATE
        || mode == SubmissionMode.REPLACE
        || mode == SubmissionMode.MERGE)
      throw new KlabIllegalArgumentException(
          "Flows are changed by submitting states, transitions and attachments");
    if (submitted.getStates().size() != 1)
      throw new KlabIllegalArgumentException("A new flow must contain exactly one initial state");
    var initial = submitted.getStates().values().iterator().next();
    if (initial.getAssetUrn() != null
        && submitted.getAssetUrn() != null
        && !initial.getAssetUrn().equals(submitted.getAssetUrn()))
      throw new KlabIllegalArgumentException(
          "Flow and initial state identify different target assets");
    if (initial.getAssetType() != null
        && submitted.getAssetType() != null
        && initial.getAssetType() != submitted.getAssetType())
      throw new KlabIllegalArgumentException(
          "Flow and initial state identify different target asset types");
    if (initial.getPermissionsOwnerUrn() != null
        && submitted.getPermissionsOwnerUrn() != null
        && !initial.getPermissionsOwnerUrn().equals(submitted.getPermissionsOwnerUrn()))
      throw new KlabIllegalArgumentException(
          "Flow and initial state identify different permissions owners");
    initial.setAssetUrn(
        submitted.getAssetUrn() == null ? initial.getAssetUrn() : submitted.getAssetUrn());
    initial.setAssetType(
        submitted.getAssetType() == null ? initial.getAssetType() : submitted.getAssetType());
    initial.setPermissionsOwnerUrn(
        submitted.getPermissionsOwnerUrn() == null
            ? initial.getPermissionsOwnerUrn()
            : submitted.getPermissionsOwnerUrn());
    return createFlow(submitted.getWorkflowId(), initial, submitted.isPublicRead(), scope);
  }

  public Flow.State submitState(Flow.State state, SubmissionMode mode, UserScope scope) {
    if (state == null || state.getFlowId() == null)
      throw new KlabIllegalArgumentException("A submitted state needs a flowId");
    var flow = requiredFlow(state.getFlowId());
    boolean exists = state.getId() != null && flow.getStates().containsKey(state.getId());
    if (mode == SubmissionMode.ADD) {
      return exists
          ? getFlow(state.getFlowId(), scope).getStates().get(state.getId())
          : createState(state.getFlowId(), state, scope);
    }
    if (mode == SubmissionMode.UPDATE && !exists)
      throw new KlabIllegalStateException("Flow state does not exist: " + state.getUrn());
    if (mode == SubmissionMode.MERGE)
      throw new KlabIllegalArgumentException("Flow state merge is not supported");
    return exists
        ? updateState(state.getFlowId(), state.getId(), state, scope)
        : createState(state.getFlowId(), state, scope);
  }

  public synchronized Flow createFlow(String workflowId, Flow.State initial, UserScope scope) {
    return createFlow(workflowId, initial, false, scope);
  }

  public synchronized Flow createFlow(
      String workflowId, Flow.State initial, boolean publicRead, UserScope scope) {
    var participant = WorkflowParticipant.from(scope);
    var workflow = getWorkflow(workflowId);
    if (initial == null || initial.getSchemaId() == null)
      throw new KlabIllegalArgumentException("An initial state schema is required");
    var init =
        workflow.getTransitions().values().stream()
            .filter(
                t ->
                    t.getSourceStates().contains(Workflow.INIT)
                        && initial.getSchemaId().equals(t.getTargetState()))
            .filter(
                t ->
                    participant.hasAnyRole(t.getRoles())
                        && !participant.getDisallowedTransitions().contains(t.getId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new KlabResourceAccessException(
                        "No authorized INIT transition enters " + initial.getSchemaId()));
    var schema = requiredStateSchema(workflow, initial.getSchemaId());
    if (!workflow.canAccess(schema, participant)) throw access(initial.getSchemaId());
    if (initial.getAssetUrn() == null
        || initial.getAssetUrn().isBlank()
        || initial.getAssetType() == null)
      throw new KlabIllegalArgumentException(
          "A flow must identify its target asset URN and knowledge class");
    if (!schema.getAssetTypes().isEmpty()
        && !schema.getAssetTypes().contains(initial.getAssetType()))
      throw new KlabIllegalArgumentException(
          "State " + schema.getId() + " does not admit " + initial.getAssetType() + " assets");
    var now = Instant.now();
    var flow = new Flow();
    flow.setId(UUID.randomUUID().toString());
    if (initial.getOwner() == null || initial.getOwner().isBlank()) {
      initial.setOwner(participant.getIdentity());
    }
    normalizeNewState(initial, flow.getId(), init.getTargetState(), now);
    flow.setWorkflowId(workflow.getId());
    flow.setWorkflowVersion(workflow.getVersion());
    flow.setAssetUrn(initial.getAssetUrn());
    flow.setAssetType(initial.getAssetType());
    flow.setPermissionsOwnerUrn(initial.getPermissionsOwnerUrn());
    flow.setOwner(participant.getIdentity());
    flow.setPublicRead(publicRead);
    flow.setCreatedAt(now);
    flow.setUpdatedAt(now);
    flow.setRevision(1);
    flow.getStates().put(initial.getId(), initial);
    if (schema.isOpen()) flow.getCurrentStateIds().add(initial.getId());
    else flow.setStatus(Flow.Status.CLOSED);
    flow.getHistory()
        .add(
            transaction(
                flow.getId(),
                null,
                init.getId(),
                null,
                initial.getId(),
                participant,
                now,
                MapCopy.empty()));
    if (!kbox.putFlow(flow))
      throw new KlabIllegalStateException("Cannot persist flow " + flow.getId());
    synchronizeResourceInfo(flow, workflow);
    return project(flow, workflow, participant);
  }

  /** Reopen the latest actionable stage. Only workflow administrators may do this. */
  public synchronized Flow reopenFlow(String flowId, UserScope scope) {
    var participant = WorkflowParticipant.from(scope);
    requireAdmin(participant);
    var flow = requiredFlow(flowId);
    if (flow.getStatus() == Flow.Status.ACTIVE) {
      return project(flow, requiredWorkflowVersion(flow), participant);
    }
    var workflow = requiredWorkflowVersion(flow);
    var latest =
        flow.getHistory().stream()
            .max(
                java.util.Comparator.comparing(
                    Flow.Transaction::getTimestamp,
                    java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
            .orElseThrow(() -> new KlabIllegalStateException("Flow has no stage to reopen"));
    var target = requiredState(flow, latest.getTargetStateId());
    var targetSchema = requiredStateSchema(workflow, target.getSchemaId());
    boolean targetActionable =
        targetSchema.isOpen()
            && workflow.getTransitions().values().stream()
                .anyMatch(
                    transition -> transition.getSourceStates().contains(target.getSchemaId()));
    var stateId =
        targetActionable || latest.getSourceStateId() == null
            ? target.getId()
            : latest.getSourceStateId();
    var state = requiredState(flow, stateId);
    state.setStatus(Flow.StateStatus.OPEN);
    state.setUpdatedAt(Instant.now());
    flow.getCurrentStateIds().add(stateId);
    flow.setStatus(Flow.Status.ACTIVE);
    persistMutation(flow, workflow);
    return project(flow, workflow, participant);
  }

  public Flow getFlow(String id, UserScope scope) {
    var participant = WorkflowParticipant.from(scope);
    var flow = requiredFlow(id);
    var workflow = requiredWorkflowVersion(flow);
    if (!canSee(flow, workflow, participant)) throw access(id);
    return project(flow, workflow, participant);
  }

  public List<Flow> listFlows(boolean includeClosed, UserScope scope) {
    var participant = WorkflowParticipant.from(scope);
    var ret = new ArrayList<Flow>();
    for (var flow : kbox.listFlows()) {
      hydrateCoordinates(flow);
      if ((!includeClosed && flow.getStatus() == Flow.Status.CLOSED)) continue;
      var workflow = requiredWorkflowVersion(flow);
      if (canSee(flow, workflow, participant)) ret.add(project(flow, workflow, participant));
    }
    return ret;
  }

  public synchronized Flow.State createState(String flowId, Flow.State state, UserScope scope) {
    var participant = WorkflowParticipant.from(scope);
    var flow = requiredActiveFlow(flowId);
    var workflow = requiredWorkflowVersion(flow);
    var schema = requiredStateSchema(workflow, state == null ? null : state.getSchemaId());
    requireManager(workflow, schema, participant);
    if (state.getOwner() == null || state.getOwner().isBlank()) {
      state.setOwner(participant.getIdentity());
    }
    normalizeNewState(state, flowId, schema.getId(), Instant.now());
    if (flow.getStates().containsKey(state.getId()))
      throw new KlabIllegalArgumentException("Duplicate state id " + state.getId());
    flow.getStates().put(state.getId(), state);
    if (schema.isOpen()) flow.getCurrentStateIds().add(state.getId());
    persistMutation(flow, workflow);
    return state;
  }

  public synchronized Flow.State updateState(
      String flowId, String stateId, Flow.State update, UserScope scope) {
    var participant = WorkflowParticipant.from(scope);
    var flow = requiredActiveFlow(flowId);
    var current = requiredState(flow, stateId);
    var workflow = requiredWorkflowVersion(flow);
    var schema = requiredStateSchema(workflow, current.getSchemaId());
    requireContributor(workflow, schema, participant);
    requireStageEditor(current, participant);
    if (current.getStatus() == Flow.StateStatus.CLOSED)
      throw new KlabIllegalStateException("Closed states cannot be updated");
    if (update == null
        || (update.getSchemaId() != null && !current.getSchemaId().equals(update.getSchemaId())))
      throw new KlabIllegalArgumentException("A state's schema cannot be changed");
    current.setTitle(update.getTitle());
    current.setStatus(update.getStatus() == null ? current.getStatus() : update.getStatus());
    current.setAssignees(update.getAssignees());
    if (update.getOwner() != null
        && (participant.getRoles().contains(WorkflowRole.ADMIN)
            || participant.getRoles().contains(WorkflowRole.EDITOR))) {
      current.setOwner(update.getOwner());
    }
    current.setMetadata(update.getMetadata());
    current.setUpdatedAt(Instant.now());
    if (current.getStatus() == Flow.StateStatus.CLOSED)
      flow.getCurrentStateIds().remove(current.getId());
    else flow.getCurrentStateIds().add(current.getId());
    if (flow.getCurrentStateIds().isEmpty()) flow.setStatus(Flow.Status.CLOSED);
    persistMutation(flow, workflow);
    return current;
  }

  public synchronized boolean deleteState(String flowId, String stateId, UserScope scope) {
    var participant = WorkflowParticipant.from(scope);
    var flow = requiredActiveFlow(flowId);
    var state = requiredState(flow, stateId);
    var workflow = requiredWorkflowVersion(flow);
    requireManager(workflow, requiredStateSchema(workflow, state.getSchemaId()), participant);
    requireStageEditor(state, participant);
    if (flow.getCurrentStateIds().contains(stateId)
        || flow.getHistory().stream()
            .anyMatch(
                t -> stateId.equals(t.getSourceStateId()) || stateId.equals(t.getTargetStateId())))
      throw new KlabIllegalStateException(
          "Current or historically referenced states cannot be deleted");
    for (var attachment : state.getAttachments()) kbox.deleteWorkflowAttachment(attachment.getId());
    flow.getStates().remove(stateId);
    persistMutation(flow, workflow);
    return true;
  }

  public synchronized Flow transition(
      String flowId, Flow.TransitionRequest request, UserScope scope) {
    var participant = WorkflowParticipant.from(scope);
    var flow = requiredActiveFlow(flowId);
    if (request == null) throw new KlabIllegalArgumentException("A transition request is required");
    if (request.getExpectedRevision() >= 0 && request.getExpectedRevision() != flow.getRevision())
      throw new KlabIllegalStateException(
          "Flow revision conflict: expected "
              + request.getExpectedRevision()
              + " but found "
              + flow.getRevision());
    var source = requiredState(flow, request.getSourceStateId());
    if (!flow.getCurrentStateIds().contains(source.getId()))
      throw new KlabIllegalStateException("Transitions must start at a current state");
    var workflow = requiredWorkflowVersion(flow);
    var transition = workflow.getTransitions().get(request.getTransitionId());
    if (transition == null || !transition.getSourceStates().contains(source.getSchemaId()))
      throw new KlabIllegalArgumentException(
          "Transition "
              + request.getTransitionId()
              + " is not admitted from "
              + source.getSchemaId());
    if (!participant.hasAnyRole(transition.getRoles())
        || participant.getDisallowedTransitions().contains(transition.getId()))
      throw access(transition.getId());
    if (!participant.canRespondTo(source))
      throw new KlabResourceAccessException("The group response deadline has elapsed");
    requireContributor(workflow, requiredStateSchema(workflow, source.getSchemaId()), participant);
    requireStageEditor(source, participant);
    validateTransitionInputs(transition, source);
    var now = Instant.now();
    var transactionId =
        request.getTransactionId() == null || request.getTransactionId().isBlank()
            ? UUID.randomUUID().toString()
            : request.getTransactionId();
    WorkflowUrns.flowTransition(flowId, transactionId);
    if (flow.getHistory().stream()
        .anyMatch(transaction -> transactionId.equals(transaction.getId())))
      throw new KlabIllegalArgumentException("Duplicate transaction id " + transactionId);
    var target = request.getTargetState() == null ? new Flow.State() : request.getTargetState();
    if (target.getOwner() == null || target.getOwner().isBlank()) {
      target.setOwner(participant.getIdentity());
    }
    normalizeNewState(target, flowId, transition.getTargetState(), now);
    if (flow.getStates().containsKey(target.getId()))
      throw new KlabIllegalArgumentException("Duplicate target state id " + target.getId());
    source.setStatus(Flow.StateStatus.CLOSED);
    source.setUpdatedAt(now);
    flow.getCurrentStateIds().remove(source.getId());
    flow.getStates().put(target.getId(), target);
    var targetSchema = requiredStateSchema(workflow, transition.getTargetState());
    if (targetSchema.isOpen()) flow.getCurrentStateIds().add(target.getId());
    flow.getHistory()
        .add(
            transaction(
                flowId,
                transactionId,
                transition.getId(),
                source.getId(),
                target.getId(),
                participant,
                now,
                request.getMetadata()));
    if (flow.getCurrentStateIds().isEmpty()) flow.setStatus(Flow.Status.CLOSED);
    persistMutation(flow, workflow);
    return project(flow, workflow, participant);
  }

  public synchronized Flow.Attachment addAttachment(
      String flowId, String stateId, Flow.AttachmentUpload upload, UserScope scope) {
    var participant = WorkflowParticipant.from(scope);
    var flow = requiredActiveFlow(flowId);
    var state = requiredState(flow, stateId);
    var workflow = requiredWorkflowVersion(flow);
    var schema = requiredStateSchema(workflow, state.getSchemaId());
    requireContributor(workflow, schema, participant);
    requireStageEditor(state, participant);
    if (state.getStatus() == Flow.StateStatus.CLOSED)
      throw new KlabIllegalStateException("Attachments cannot be added to a closed state");
    if (upload == null || upload.getContent() == null)
      throw new KlabIllegalArgumentException("Attachment content is required");
    var rule =
        schema.getAttachments().stream()
            .filter(r -> Objects.equals(r.getType(), upload.getType()))
            .findFirst()
            .orElseThrow(
                () ->
                    new KlabIllegalArgumentException(
                        "Attachment type " + upload.getType() + " is not admitted"));
    validateAttachmentRule(rule, upload, state);
    var descriptor = new Flow.Attachment();
    descriptor.setId(UUID.randomUUID().toString());
    descriptor.setFlowId(flowId);
    descriptor.setStateId(stateId);
    descriptor.setType(upload.getType());
    descriptor.setFileName(upload.getFileName());
    descriptor.setMediaType(upload.getMediaType());
    descriptor.setAssetType(upload.getAssetType());
    descriptor.setSize(upload.getContent().length);
    descriptor.setCreatedAt(Instant.now());
    descriptor.setCreatedBy(participant.getIdentity());
    try {
      descriptor.setChecksum(
          HexFormat.of()
              .formatHex(MessageDigest.getInstance("SHA-256").digest(upload.getContent())));
    } catch (Exception e) {
      throw new KlabIllegalStateException(e);
    }
    if (!kbox.putWorkflowAttachment(descriptor.getId(), flowId, stateId, upload.getContent()))
      throw new KlabIllegalStateException("Cannot persist attachment");
    state.getAttachments().add(descriptor);
    state.setUpdatedAt(Instant.now());
    persistMutation(flow, workflow);
    return descriptor;
  }

  public byte[] getAttachment(String flowId, String attachmentId, UserScope scope) {
    var participant = WorkflowParticipant.from(scope);
    var flow = requiredFlow(flowId);
    var workflow = requiredWorkflowVersion(flow);
    var state =
        flow.getStates().values().stream()
            .filter(s -> s.getAttachments().stream().anyMatch(a -> attachmentId.equals(a.getId())))
            .findFirst()
            .orElseThrow(
                () -> new KlabIllegalArgumentException("Unknown attachment " + attachmentId));
    if (!flow.isPublicRead()) {
      requireContributor(workflow, requiredStateSchema(workflow, state.getSchemaId()), participant);
    }
    var bytes = kbox.getWorkflowAttachment(attachmentId);
    if (bytes == null) throw new KlabIllegalStateException("Attachment payload is missing");
    return bytes;
  }

  public synchronized boolean deleteAttachment(
      String flowId, String attachmentId, UserScope scope) {
    var participant = WorkflowParticipant.from(scope);
    var flow = requiredActiveFlow(flowId);
    var workflow = requiredWorkflowVersion(flow);
    for (var state : flow.getStates().values()) {
      var attachment =
          state.getAttachments().stream()
              .filter(candidate -> attachmentId.equals(candidate.getId()))
              .findFirst()
              .orElse(null);
      if (attachment == null) continue;
      requireContributor(workflow, requiredStateSchema(workflow, state.getSchemaId()), participant);
      requireStageEditor(state, participant);
      if (state.getStatus() == Flow.StateStatus.CLOSED)
        throw new KlabIllegalStateException("Attachments cannot be deleted from a closed state");
      if (!kbox.deleteWorkflowAttachment(attachmentId))
        throw new KlabIllegalStateException("Attachment payload is missing");
      state.getAttachments().remove(attachment);
      state.setUpdatedAt(Instant.now());
      persistMutation(flow, workflow);
      return true;
    }
    throw new KlabIllegalArgumentException("Unknown attachment " + attachmentId);
  }

  private void validateTransitionInputs(Workflow.TransitionSchema transition, Flow.State source) {
    if (!transition.getSourceAssetTypes().isEmpty()
        && source.getAttachments().stream()
            .noneMatch(a -> transition.getSourceAssetTypes().contains(a.getAssetType())))
      throw new KlabIllegalStateException("Transition requires an admitted k.LAB asset attachment");
    if (!transition.getSourceMediaTypes().isEmpty()
        && source.getAttachments().stream()
            .noneMatch(a -> mediaMatches(transition.getSourceMediaTypes(), a.getMediaType())))
      throw new KlabIllegalStateException("Transition requires an admitted media attachment");
  }

  private void validateAttachmentRule(
      Workflow.AttachmentRule rule, Flow.AttachmentUpload upload, Flow.State state) {
    if (rule.getAssetType() != null && rule.getAssetType() != upload.getAssetType())
      throw new KlabIllegalArgumentException("Wrong k.LAB asset type for " + rule.getType());
    if (rule.getMediaType() != null
        && !mediaMatches(java.util.Set.of(rule.getMediaType()), upload.getMediaType()))
      throw new KlabIllegalArgumentException("Wrong media type for " + rule.getType());
    long count =
        state.getAttachments().stream()
            .filter(a -> Objects.equals(a.getType(), rule.getType()))
            .count();
    if (rule.getArity() >= 0 && count >= rule.getArity())
      throw new KlabIllegalStateException("Attachment arity exceeded for " + rule.getType());
  }

  private boolean mediaMatches(java.util.Set<String> admitted, String actual) {
    if (actual == null) return false;
    return admitted.stream()
        .anyMatch(
            m ->
                "*/*".equals(m)
                    || m.equals(actual)
                    || (m.endsWith("/*") && actual.startsWith(m.substring(0, m.length() - 1))));
  }

  private Flow project(Flow stored, Workflow workflow, WorkflowParticipant participant) {
    Flow copy = Utils.Json.parseObject(Utils.Json.asString(stored), Flow.class);
    if (stored.isPublicRead()) return copy;
    boolean adminOrOwner =
        participant.getRoles().contains(WorkflowRole.ADMIN)
            || Objects.equals(stored.getOwner(), participant.getIdentity());
    if (!adminOrOwner)
      copy.getStates()
          .entrySet()
          .removeIf(
              e ->
                  !workflow.canAccess(
                      workflow.getStates().get(e.getValue().getSchemaId()), participant));
    copy.getCurrentStateIds()
        .removeIf(
            id -> {
              var state = copy.getStates().get(id);
              boolean admin = participant.getRoles().contains(WorkflowRole.ADMIN);
              return state == null
                  || (!admin
                      && !workflow.canAccess(
                          workflow.getStates().get(state.getSchemaId()), participant))
                  || (!state.getAssignees().isEmpty()
                      && !state.getAssignees().contains(participant.getIdentity())
                      && !admin);
            });
    copy.getHistory()
        .removeIf(
            t ->
                (t.getSourceStateId() != null
                        && !copy.getStates().containsKey(t.getSourceStateId()))
                    || !copy.getStates().containsKey(t.getTargetStateId()));
    return copy;
  }

  private boolean canSee(Flow flow, Workflow workflow, WorkflowParticipant participant) {
    return flow.isPublicRead()
        || participant.getRoles().contains(WorkflowRole.ADMIN)
        || Objects.equals(flow.getOwner(), participant.getIdentity())
        || flow.getStates().values().stream()
            .anyMatch(
                s -> workflow.canAccess(workflow.getStates().get(s.getSchemaId()), participant));
  }

  private void requireManager(
      Workflow workflow, Workflow.StateSchema schema, WorkflowParticipant participant) {
    if (!workflow.canAccess(schema, participant)
        || !participant.hasAnyRole(schema.getManagerRoles())) throw access(schema.getId());
  }

  private void requireContributor(
      Workflow workflow, Workflow.StateSchema schema, WorkflowParticipant participant) {
    if (!workflow.canAccess(schema, participant)) throw access(schema.getId());
  }

  private void requireStageEditor(Flow.State state, WorkflowParticipant participant) {
    boolean admin = participant.getRoles().contains(WorkflowRole.ADMIN);
    boolean owner = Objects.equals(state.getOwner(), participant.getIdentity());
    boolean assignedEditor =
        participant.getRoles().contains(WorkflowRole.EDITOR)
            && state.getAssignees().contains(participant.getIdentity());
    if (!admin && !owner && !assignedEditor) throw access(state.getId());
  }

  private void normalizeNewState(Flow.State state, String flowId, String schemaId, Instant now) {
    state.setId(
        state.getId() == null || state.getId().isBlank()
            ? UUID.randomUUID().toString()
            : state.getId());
    state.setFlowId(flowId);
    state.setSchemaId(schemaId);
    state.setStatus(Flow.StateStatus.OPEN);
    state.setAttachments(new ArrayList<>());
    state.setCreatedAt(now);
    state.setUpdatedAt(now);
  }

  private Flow.Transaction transaction(
      String flowId,
      String transactionId,
      String transition,
      String source,
      String target,
      WorkflowParticipant participant,
      Instant now,
      java.util.Map<String, Object> metadata) {
    var ret = new Flow.Transaction();
    ret.setId(transactionId == null ? UUID.randomUUID().toString() : transactionId);
    ret.setFlowId(flowId);
    ret.setTransitionId(transition);
    ret.setSourceStateId(source);
    ret.setTargetStateId(target);
    ret.setActor(participant.getIdentity());
    ret.setTimestamp(now);
    ret.setMetadata(metadata == null ? Metadata.create() : Metadata.create(metadata));
    return ret;
  }

  private void persistMutation(Flow flow, Workflow workflow) {
    flow.setRevision(flow.getRevision() + 1);
    flow.setUpdatedAt(Instant.now());
    if (!kbox.putFlow(flow))
      throw new KlabIllegalStateException("Cannot persist flow " + flow.getId());
    synchronizeResourceInfo(flow, workflow);
  }

  private void synchronizeResourceInfo(Flow flow, Workflow workflow) {
    var summary = reviewSummary(flow, workflow);
    if (!kbox.updateResourceInfoForFlow(flow, summary.stage(), summary.reviewStatus()))
      throw new KlabIllegalStateException(
          "Cannot update workflow catalog for target asset " + flow.getAssetUrn());
  }

  /**
   * Read an optional review result from state-schema metadata; active flows default to REVIEWING.
   */
  private ReviewSummary reviewSummary(Flow flow, Workflow workflow) {
    var summary = new ReviewSummary(ResourceInfo.Stage.REVIEWING, 1);
    Flow.State relevant = null;
    if (!flow.getCurrentStateIds().isEmpty()) {
      relevant =
          flow.getCurrentStateIds().stream()
              .map(flow.getStates()::get)
              .filter(Objects::nonNull)
              .max(
                  java.util.Comparator.comparing(
                      Flow.State::getUpdatedAt,
                      java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
              .orElse(null);
    } else if (!flow.getHistory().isEmpty()) {
      relevant =
          flow.getStates()
              .get(flow.getHistory().get(flow.getHistory().size() - 1).getTargetStateId());
    }
    if (relevant == null) return summary;
    var schema = workflow.getStates().get(relevant.getSchemaId());
    if (schema == null) return summary;
    var stageValue = schema.getMetadata().get("resourceStage");
    var statusValue = schema.getMetadata().get("reviewStatus");
    ResourceInfo.Stage stage = summary.stage();
    int status = summary.reviewStatus();
    if (stageValue != null) {
      try {
        stage = ResourceInfo.Stage.valueOf(stageValue.toString().trim().toUpperCase());
      } catch (IllegalArgumentException ignored) {
        throw new KlabIllegalStateException(
            "Invalid resourceStage metadata in workflow state " + schema.getId());
      }
    }
    if (statusValue instanceof Number number) status = number.intValue();
    else if (statusValue != null) {
      try {
        status = Integer.parseInt(statusValue.toString());
      } catch (NumberFormatException ignored) {
        throw new KlabIllegalStateException(
            "Invalid reviewStatus metadata in workflow state " + schema.getId());
      }
    } else if (stageValue != null) status = stage.status;
    return new ReviewSummary(stage, status);
  }

  private record ReviewSummary(ResourceInfo.Stage stage, int reviewStatus) {}

  private Flow requiredFlow(String id) {
    var ret = kbox.getFlow(id);
    if (ret == null) throw new KlabIllegalArgumentException("Unknown flow " + id);
    hydrateCoordinates(ret);
    return ret;
  }

  private void hydrateCoordinates(Flow flow) {
    flow.getStates()
        .values()
        .forEach(
            state -> {
              state.setFlowId(flow.getId());
              state.getAttachments().forEach(attachment -> attachment.setFlowId(flow.getId()));
            });
    flow.getHistory().forEach(transaction -> transaction.setFlowId(flow.getId()));
  }

  private void requireAdmin(WorkflowParticipant participant) {
    if (!participant.getRoles().contains(WorkflowRole.ADMIN))
      throw access("workflow administration");
  }

  private Flow requiredActiveFlow(String id) {
    var ret = requiredFlow(id);
    if (ret.getStatus() != Flow.Status.ACTIVE)
      throw new KlabIllegalStateException("Flow " + id + " is closed");
    return ret;
  }

  private Flow.State requiredState(Flow flow, String id) {
    var ret = flow.getStates().get(id);
    if (ret == null) throw new KlabIllegalArgumentException("Unknown state " + id);
    return ret;
  }

  private Workflow.StateSchema requiredStateSchema(Workflow workflow, String id) {
    var ret = workflow.getStates().get(id);
    if (ret == null) throw new KlabIllegalArgumentException("Unknown state schema " + id);
    return ret;
  }

  private Workflow requiredWorkflowVersion(Flow flow) {
    var ret = kbox.getWorkflow(flow.getWorkflowId(), flow.getWorkflowVersion());
    if (ret == null)
      throw new KlabIllegalStateException(
          "Missing workflow schema " + flow.getWorkflowId() + "@" + flow.getWorkflowVersion());
    return ret;
  }

  private KlabResourceAccessException access(String object) {
    return new KlabResourceAccessException("Workflow access denied for " + object);
  }

  /** Avoid allocating a mutable map for the common INIT transaction. */
  private static final class MapCopy {
    private static java.util.Map<String, Object> empty() {
      return java.util.Map.of();
    }
  }
}
