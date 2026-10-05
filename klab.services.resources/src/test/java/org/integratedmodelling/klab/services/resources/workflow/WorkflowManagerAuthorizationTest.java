package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.services.resources.workflow.impl.FlowImpl;
import org.integratedmodelling.klab.api.authentication.CustomProperty;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowParticipant;
import org.integratedmodelling.klab.resources.WorkflowStore;
import org.junit.jupiter.api.Test;

class WorkflowManagerAuthorizationTest {

  @Test
  void serviceAdministrationAllowsWorkflowInspectionWithoutFabricatedGroupMembership() {
    var store = new MemoryStore();
    var manager = manager(store);
    var ordinary = scope("owner", "REVIEWER", "another-workflow");
    var administrator = (UserScope) Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[] {UserScope.class, org.integratedmodelling.klab.api.scope.ServiceSideScope.class},
        (proxy, method, arguments) -> {
          if (method.getName().equals("isAuthorized")) {
            return arguments[0] == org.integratedmodelling.klab.api.authentication.CRUDOperation.ADMINISTER;
          }
          if (method.getName().equals("getId")) return "webui:owner";
          return method.invoke(ordinary, arguments);
        });
    List<?> hidden = invoke(manager, "list",
        new Class<?>[] {KlabAsset.KnowledgeClass.class, UserScope.class},
        KlabAsset.KnowledgeClass.WORKFLOW, ordinary);
    assertTrue(hidden.isEmpty());
    List<?> visible = invoke(manager, "list",
        new Class<?>[] {KlabAsset.KnowledgeClass.class, UserScope.class},
        KlabAsset.KnowledgeClass.WORKFLOW, administrator);
    assertFalse(visible.isEmpty());
    assertEquals(store.listWorkflows().size(), visible.size());
    assertTrue(WorkflowParticipant.from(administrator).isWorkflowPermitted("asset-review"));
    assertFalse(WorkflowParticipant.from(ordinary).isWorkflowPermitted("asset-review"));
  }

  @Test
  void workflowPermissionAllowListIsEnforced() {
    var store = new MemoryStore();
    var manager = manager(store);

    var denied = scope("editor", "EDITOR", "another-workflow");
    var failure =
        assertThrows(
            InvocationTargetException.class,
            () ->
                invokeRaw(
                    manager,
                    "createFlow",
                    new Class<?>[] {
                      String.class, Flow.State.class, boolean.class, UserScope.class
                    },
                    "asset-review",
                    initial(),
                    false,
                    denied));
    assertTrue(failure.getCause() instanceof KlabResourceAccessException);
    assertFalse(WorkflowParticipant.from(denied).isWorkflowPermitted("asset-review"));
    List<?> hidden =
        invoke(
            manager,
            "list",
            new Class<?>[] {KlabAsset.KnowledgeClass.class, UserScope.class},
            KlabAsset.KnowledgeClass.WORKFLOW,
            denied);
    assertTrue(hidden.isEmpty());

    var wildcard = scope("editor", "EDITOR", "*");
    assertTrue(WorkflowParticipant.from(wildcard).isWorkflowPermitted("asset-review"));
    List<?> visible =
        invoke(
            manager,
            "list",
            new Class<?>[] {KlabAsset.KnowledgeClass.class, UserScope.class},
            KlabAsset.KnowledgeClass.WORKFLOW,
            wildcard);
    assertEquals(store.listWorkflows().size(), visible.size());
    assertEquals(
        "asset-review", createFlow(manager, initial(), false, wildcard).getWorkflowId());
  }

  @Test
  void workflowAssetTypeIsEnforcedBeforeCreatingAFlow() {
    var manager = manager(new MemoryStore());
    var inadmissible = initial();
    inadmissible.setAssetType(KlabAsset.KnowledgeClass.NAMESPACE);

    var failure =
        assertThrows(
            InvocationTargetException.class,
            () ->
                invokeRaw(
                    manager,
                    "createFlow",
                    new Class<?>[] {
                      String.class, Flow.State.class, boolean.class, UserScope.class
                    },
                    "asset-review",
                    inadmissible,
                    false,
                    scope("editor", "EDITOR")));

    assertTrue(failure.getCause() instanceof KlabIllegalArgumentException);
    assertTrue(failure.getCause().getMessage().contains("Workflow asset-review does not admit"));
  }

  @Test
  void publicFlowIsFullyVisibleButCannotBeMutatedByItsReader() {
    var store = new MemoryStore();
    var manager = manager(store);
    var flow = createFlow(manager, initial(), true, scope("editor", "EDITOR"));
    var reader = scope("reader", "REVIEWER", "another-workflow");

    Flow visible =
        invoke(
            manager,
            "getFlow",
            new Class<?>[] {String.class, UserScope.class},
            flow.getId(),
            reader);
    assertTrue(visible.isPublicRead());
    assertEquals(flow.getStates().keySet(), visible.getStates().keySet());
    Workflow visibleSchema =
        invoke(
            manager,
            "getWorkflow",
            new Class<?>[] {String.class, UserScope.class},
            flow.getWorkflowId(),
            reader);
    assertEquals(flow.getWorkflowId(), visibleSchema.getId());
    var failure = assertThrows(
        InvocationTargetException.class,
        () ->
            invokeRaw(manager, "updateState", new Class<?>[] {String.class, String.class, Flow.State.class, UserScope.class},
                flow.getId(),
                flow.getCurrentStateIds().iterator().next(),
                flow.getStates().values().iterator().next(),
                reader));
    assertTrue(failure.getCause() instanceof KlabResourceAccessException);
  }

  @Test
  void onlyAdministratorCanReopenClosedFlow() {
    var store = new MemoryStore();
    var manager = manager(store);
    var flow = createFlow(manager, initial(), false, scope("editor", "EDITOR"));
    var stored = store.getFlow(flow.getId());
    var stateId = stored.getCurrentStateIds().iterator().next();
    stored.getStates().get(stateId).setStatus(Flow.StateStatus.CLOSED);
    stored.getCurrentStateIds().clear();
    stored.setStatus(Flow.Status.CLOSED);

    var failure = assertThrows(
        InvocationTargetException.class,
        () -> invokeRaw(manager, "reopenFlow", new Class<?>[] {String.class, UserScope.class}, flow.getId(), scope("editor", "EDITOR")));
    assertTrue(failure.getCause() instanceof KlabResourceAccessException);
    Flow reopened = invoke(manager, "reopenFlow", new Class<?>[] {String.class, UserScope.class}, flow.getId(), scope("admin", "ADMIN"));
    assertEquals(Flow.Status.ACTIVE, reopened.getStatus());
    assertTrue(reopened.getCurrentStateIds().contains(stateId));
    assertEquals(Flow.StateStatus.OPEN, reopened.getStates().get(stateId).getStatus());
  }

  @Test
  void stageLifecycleCallbacksObserveCommittedCreationAndPreDeletionState() {
    var store = new MemoryStore();
    var events = new ArrayList<String>();
    var manager =
        new WorkflowManager(
            store,
            new WorkflowStageLifecycleHandler() {
              @Override
              public void afterStageCreated(Context context) {
                events.add(
                    "created:"
                        + context.stageSchema().getId()
                        + ":"
                        + store
                            .getFlow(context.flow().getId())
                            .getStates()
                            .containsKey(context.stage().getId()));
              }

              @Override
              public void beforeStageDeleted(Context context) {
                events.add(
                    "deleting:"
                        + context.stageSchema().getId()
                        + ":"
                        + store
                            .getFlow(context.flow().getId())
                            .getStates()
                            .containsKey(context.stage().getId()));
              }
            });
    var admin = scope("admin", "ADMIN");
    var flow = manager.createFlow("asset-review", initial(), false, admin);
    var deletable = new FlowImpl.StateImpl();
    deletable.setSchemaId("accepted");

    var created = manager.createState(flow.getId(), deletable, admin);
    assertTrue(manager.deleteState(flow.getId(), created.getId(), admin));

    assertEquals(
        List.of("created:editing:true", "created:accepted:true", "deleting:accepted:true"),
        events);
    assertFalse(store.getFlow(flow.getId()).getStates().containsKey(created.getId()));
  }

  @Test
  void callbackFailuresRespectLifecycleCommitBoundaries() {
    var store = new MemoryStore();
    var manager =
        new WorkflowManager(
            store,
            new WorkflowStageLifecycleHandler() {
              @Override
              public void afterStageCreated(Context context) throws Exception {
                throw new Exception("post-create failure");
              }

              @Override
              public void beforeStageDeleted(Context context) throws Exception {
                throw new Exception("pre-delete failure");
              }
            });
    var admin = scope("admin", "ADMIN");

    var flow = manager.createFlow("asset-review", initial(), false, admin);
    assertTrue(
        store
            .getFlow(flow.getId())
            .getStates()
            .containsKey(flow.getStates().keySet().iterator().next()));
    var deletable = new FlowImpl.StateImpl();
    deletable.setSchemaId("accepted");
    var created = manager.createState(flow.getId(), deletable, admin);
    var attachment = new FlowImpl.AttachmentImpl();
    attachment.setId("retained-attachment");
    store.getFlow(flow.getId()).getStates().get(created.getId()).getAttachments().add(attachment);
    store.attachments.put(attachment.getId(), new byte[] {1});

    assertThrows(
        KlabIllegalStateException.class,
        () -> manager.deleteState(flow.getId(), created.getId(), admin));
    assertTrue(store.getFlow(flow.getId()).getStates().containsKey(created.getId()));
    assertTrue(store.attachments.containsKey(attachment.getId()));
  }

  @Test
  void firstStageIsPersistedOnlyAfterAValidAtomicSubmission() {
    var store = new MemoryStore();
    var manager = new WorkflowManager(store);
    // asset-review 1.1 intentionally made candidate optional. Exercise the atomic gate with an
    // explicitly required fixture rule, rather than relying on the obsolete bundled default.
    store.getWorkflow("asset-review").getStates().get("editing").getAttachments().getFirst().setRequired(true);
    var editor = scope("editor", "EDITOR");
    var request = Flow.InitializationRequest.create();
    request.setInitialState(initial());
    var transition = Flow.TransitionRequest.create();
    transition.setSourceStateId("temporary");
    transition.setTransitionId("submit");
    transition.setTargetState(Flow.State.create());
    request.setTransition(transition);

    // The server replaces no caller IDs: point the request at the initial state's generated ID by
    // assigning one before validation, just as the IDE's provisional flow does.
    request.getInitialState().setId("temporary");
    assertThrows(
        KlabIllegalStateException.class,
        () -> manager.initializeFlow("asset-review", request, editor));
    assertTrue(store.listFlows().isEmpty());
    assertTrue(store.attachments.isEmpty());

    var upload = Flow.AttachmentUpload.create();
    upload.setType("candidate");
    upload.setFileName("candidate.json");
    upload.setMediaType("application/json");
    upload.setAssetType(KlabAsset.KnowledgeClass.RESOURCE);
    upload.setContent(new byte[] {1, 2, 3});
    request.setAttachments(List.of(upload));
    var flow = manager.initializeFlow("asset-review", request, editor);

    assertEquals(1, store.listFlows().size());
    assertEquals(1, store.attachments.size());
    assertEquals(2, flow.getHistory().size());
  }

  @Test
  void editorCreatorCanDeleteTheCompleteFlowButAnotherEditorCannot() {
    var store = new MemoryStore();
    var manager = new WorkflowManager(store);
    var creator = scope("creator", "EDITOR");
    var flow = manager.createFlow("asset-review", initial(), false, creator);
    var stored = store.getFlow(flow.getId());
    var state = stored.getStates().values().iterator().next();
    var attachment = new FlowImpl.AttachmentImpl();
    attachment.setId("delete-with-flow");
    state.getAttachments().add(attachment);
    store.attachments.put(attachment.getId(), new byte[] {1});

    assertThrows(
        KlabResourceAccessException.class,
        () -> manager.deleteFlow(flow.getId(), scope("another-editor", "EDITOR")));
    assertTrue(manager.deleteFlow(flow.getId(), creator));
    assertTrue(store.listFlows().isEmpty());
    assertTrue(store.attachments.isEmpty());
  }

  @Test
  void instrumentedButtonsPersistAcrossManagersAndEnforceRevisionAndEditorIdentity() {
    var store = new MemoryStore();
    var manager = new WorkflowManager(store);
    var behavior = WorkflowBehaviorBridgeTest.behavior();
    manager.setBehaviorBridge(WorkflowBehaviorBridgeTest.bridge(behavior));
    var workflow = store.getWorkflow("asset-review");
    workflow.setBehavior(behavior.getUrn());
    workflow.getStates().get("editing").setActions(List.of(
        new org.integratedmodelling.klab.api.services.resources.workflow.WorkflowBehavior.Action("change", "Change", "update", Map.of())));
    var owner = scope("owner", "EDITOR");
    var flow = manager.createFlow("asset-review", initial(), owner);
    var stageId = flow.getCurrentStateIds().iterator().next();
    assertNull(flow.getBehaviorCheckpoint());
    assertNull(flow.getStates().get(stageId).getBehaviorCheckpoint());
    assertNotNull(store.getFlow(flow.getId()).getBehaviorCheckpoint());
    assertEquals(1, manager.getActions(flow.getId(), stageId, owner).getFirst().missing().size());
    var request = new org.integratedmodelling.klab.api.services.resources.workflow.WorkflowBehavior.ActionRequest(
        flow.getRevision(), Map.of("text", "saved"));
    assertThrows(KlabResourceAccessException.class, () -> manager.executeAction(flow.getId(), stageId,
        "change", request, scope("other", "EDITOR")));
    var updated = manager.executeAction(flow.getId(), stageId, "change", request, owner);
    assertEquals(flow.getRevision() + 1, updated.getRevision());
    assertThrows(KlabIllegalStateException.class, () -> manager.executeAction(flow.getId(), stageId,
        "change", request, owner));
    var restarted = new WorkflowManager(store);
    restarted.setBehaviorBridge(WorkflowBehaviorBridgeTest.bridge(behavior));
    restarted.executeAction(flow.getId(), stageId, "change",
        new org.integratedmodelling.klab.api.services.resources.workflow.WorkflowBehavior.ActionRequest(
            updated.getRevision(), Map.of("text", "resumed")), owner);
    assertEquals("resumed", ((Map<?, ?>) store.getFlow(flow.getId()).getBehaviorCheckpoint().globals().get("state")).get("value"));
  }

  @Test
  void missingAutomaticParameterPreventsFlowPersistence() {
    var store = new MemoryStore();
    var manager = new WorkflowManager(store);
    var behavior = WorkflowBehaviorBridgeTest.behavior();
    manager.setBehaviorBridge(WorkflowBehaviorBridgeTest.bridge(behavior));
    var workflow = store.getWorkflow("asset-review");
    workflow.setBehavior(behavior.getUrn());
    workflow.getStates().get("editing").setOnStart(List.of(
        new org.integratedmodelling.klab.api.services.resources.workflow.WorkflowBehavior.Action(null, null, "update", Map.of())));
    assertThrows(IllegalArgumentException.class, () -> manager.createFlow("asset-review", initial(), scope("owner", "EDITOR")));
    assertTrue(store.listFlows().isEmpty());
  }

  @Test
  void transitionCheckpointsSourceAndTargetAndRejectsNonportableActionStateAtomically() {
    var store = new MemoryStore();
    var manager = new WorkflowManager(store);
    var behavior = WorkflowBehaviorBridgeTest.behavior();
    var actions = new ArrayList<org.integratedmodelling.klab.api.lang.kactors.KActorsAction>(behavior.getStatements());
    for (String name : List.of("entered", "committed", "moved", "arrived"))
      actions.add(WorkflowBehaviorBridgeTest.assign(name, "value", name, org.integratedmodelling.klab.api.data.ValueType.STRING));
    var capture = WorkflowBehaviorBridgeTest.assign("capture", "value", "stage", org.integratedmodelling.klab.api.data.ValueType.IDENTIFIER);
    capture.setArguments(List.of(new org.integratedmodelling.klab.api.lang.kactors.impl.KActorsActionImpl.ArgumentImpl("stage")));
    actions.add(capture); behavior.setStatements(actions);
    manager.setBehaviorBridge(WorkflowBehaviorBridgeTest.bridge(behavior));
    var workflow = store.getWorkflow("asset-review"); workflow.setBehavior(behavior.getUrn());
    var sourceSchema = workflow.getStates().get("editing");
    sourceSchema.setOnStart(List.of(binding("prepare"), binding("entered")));
    sourceSchema.setOnCommit(List.of(binding("committed")));
    workflow.getTransitions().get("submit").setActions(List.of(binding("moved")));
    workflow.getStates().get("peer-review").setOnStart(List.of(binding("arrived")));
    workflow.getTransitions().get("submit").getSourceMediaTypes().clear();
    var owner = scope("owner", "EDITOR");
    var flow = manager.createFlow("asset-review", initial(), owner);
    String stateId = flow.getCurrentStateIds().iterator().next();
    var request = Flow.TransitionRequest.create();
    request.setSourceStateId(stateId); request.setTransitionId("submit");
    request.setExpectedRevision(flow.getRevision());
    var transitioned = manager.transition(flow.getId(), request, owner);
    var stored = store.getFlow(flow.getId());
    assertEquals("Prepared", stored.getStates().get(stateId).getTitle());
    assertEquals("moved", ((Map<?, ?>) stored.getStates().get(stateId).getBehaviorCheckpoint().globals().get("state")).get("value"));
    assertEquals("arrived", ((Map<?, ?>) stored.getBehaviorCheckpoint().globals().get("state")).get("value"));
    assertNull(transitioned.getBehaviorCheckpoint());

    // A live stage reference is rejected at the checkpoint boundary without replacing stored state.
    sourceSchema.setActions(List.of(new org.integratedmodelling.klab.api.services.resources.workflow.WorkflowBehavior.Action("capture", "Capture", "capture", Map.of())));
    var another = manager.createFlow("asset-review", initial(), owner);
    var stageId = another.getCurrentStateIds().iterator().next();
    assertThrows(IllegalArgumentException.class, () -> manager.executeAction(another.getId(), stageId, "capture",
        new org.integratedmodelling.klab.api.services.resources.workflow.WorkflowBehavior.ActionRequest(another.getRevision(), Map.of()), owner));
    assertEquals(another.getRevision(), store.getFlow(another.getId()).getRevision());
    assertEquals("entered", ((Map<?, ?>) store.getFlow(another.getId()).getBehaviorCheckpoint().globals().get("state")).get("value"));
  }

  @Test
  void behaviorAttachmentsSatisfyRequiredInputsAndFailedSavesCleanUpPayloads() {
    var store = new MemoryStore();
    var manager = new WorkflowManager(store);
    var behavior = WorkflowBehaviorBridgeTest.behavior();
    var action = new org.integratedmodelling.klab.api.lang.kactors.impl.KActorsActionImpl();
    action.setUrn("attach");
    action.setArguments(List.of(new org.integratedmodelling.klab.api.lang.kactors.impl.KActorsActionImpl.ArgumentImpl("content")));
    var verb = new org.integratedmodelling.klab.api.lang.kactors.impl.KActorsStatementImpl.VerbImpl();
    verb.setRecipient("content"); verb.setMessage("attach_text");
    var arguments = new org.integratedmodelling.klab.api.lang.kactors.impl.KActorsArgumentsImpl();
    for (String text : List.of("candidate", "candidate.json", "application/json", "{\"generated\":true}")) {
      var value = new org.integratedmodelling.klab.api.lang.kactors.impl.KActorsValueImpl();
      value.setType(org.integratedmodelling.klab.api.data.ValueType.STRING); value.setStatedValue(text);
      arguments.putUnnamed(value);
    }
    verb.setArguments(arguments); action.setCode(List.of(verb));
    var actions = new ArrayList<>(behavior.getStatements()); actions.add(action); behavior.setStatements(actions);
    manager.setBehaviorBridge(WorkflowBehaviorBridgeTest.bridge(behavior));
    var workflow = store.getWorkflow("asset-review"); workflow.setBehavior(behavior.getUrn());
    var schema = workflow.getStates().get("editing");
    schema.getAttachments().getFirst().setRequired(true);
    schema.getAttachments().getFirst().setAssetType(KlabAsset.KnowledgeClass.RESOURCE);
    schema.setOnStart(List.of(binding("attach")));
    var owner = scope("owner", "EDITOR");
    var request = Flow.InitializationRequest.create();
    request.setInitialState(initial()); request.getInitialState().setId("initial");
    var transition = Flow.TransitionRequest.create(); transition.setSourceStateId("initial");
    transition.setTransitionId("submit"); transition.setTargetState(Flow.State.create());
    request.setTransition(transition);
    var flow = manager.initializeFlow("asset-review", request, owner);
    var attachment = store.getFlow(flow.getId()).getStates().get("initial").getAttachments().getFirst();
    assertEquals("candidate.json", attachment.getFileName());
    assertEquals("{\"generated\":true}", new String(store.attachments.get(attachment.getId()), java.nio.charset.StandardCharsets.UTF_8));
    assertEquals(2, flow.getHistory().size());
    int count = store.attachments.size();
    store.failFlowWrites = true;
    assertThrows(KlabIllegalStateException.class, () -> manager.createFlow("asset-review", initial(), owner));
    assertEquals(count, store.attachments.size(), "Failed persistence must remove newly written payloads");
    assertEquals(1, store.flows.size());
  }

  private org.integratedmodelling.klab.api.services.resources.workflow.WorkflowBehavior.Action binding(String name) {
    return new org.integratedmodelling.klab.api.services.resources.workflow.WorkflowBehavior.Action(null, null, name, Map.of());
  }

  private Flow.State initial() {
    var state = new FlowImpl.StateImpl();
    state.setSchemaId("editing");
    state.setAssetUrn("urn:test:resource");
    state.setAssetType(KlabAsset.KnowledgeClass.RESOURCE);
    state.setPermissionsOwnerUrn("urn:test:resource");
    return state;
  }

  private Object manager(WorkflowStore store) {
    try {
      var type =
          Class.forName(
              "org.integratedmodelling.klab.services.resources.workflow.WorkflowManager");
      return type.getConstructor(WorkflowStore.class).newInstance(store);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private Flow createFlow(Object manager, Flow.State state, boolean publicRead, UserScope scope) {
    return invoke(
        manager,
        "createFlow",
        new Class<?>[] {String.class, Flow.State.class, boolean.class, UserScope.class},
        "asset-review",
        state,
        publicRead,
        scope);
  }

  @SuppressWarnings("unchecked")
  private <T> T invoke(Object target, String method, Class<?>[] types, Object... arguments) {
    try {
      return (T) invokeRaw(target, method, types, arguments);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e.getCause() == null ? e : e.getCause());
    }
  }

  private Object invokeRaw(Object target, String method, Class<?>[] types, Object... arguments)
      throws ReflectiveOperationException {
    return target.getClass().getMethod(method, types).invoke(target, arguments);
  }

  private UserScope scope(String username, String role) {
    return scope(username, role, "asset-review");
  }

  private UserScope scope(String username, String role, String permitted) {
    var roleProperty = new CustomProperty();
    roleProperty.setKey(WorkflowParticipant.ROLES_PROPERTY);
    roleProperty.setValue(role);
    var permittedProperty = new CustomProperty();
    permittedProperty.setKey(WorkflowParticipant.PERMITTED_WORKFLOWS_PROPERTY);
    permittedProperty.setValue(permitted);
    Group group =
        (Group)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {Group.class},
                (proxy, method, args) ->
                    switch (method.getName()) {
                      case "getId" -> "workflow-" + role.toLowerCase();
                      case "getCustomProperties" -> List.of(roleProperty, permittedProperty);
                      default -> defaultValue(method.getReturnType());
                    });
    UserIdentity user =
        (UserIdentity)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {UserIdentity.class},
                (proxy, method, args) ->
                    switch (method.getName()) {
                      case "getUsername" -> username;
                      case "getEmailAddress" -> username + "@example.org";
                      case "getGroups" -> List.of(group);
                      case "getData" -> Metadata.create();
                      case "isAuthenticated" -> true;
                      case "isAnonymous" -> false;
                      default -> defaultValue(method.getReturnType());
                    });
    return (UserScope)
        Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {UserScope.class},
            (proxy, method, args) ->
                "getUser".equals(method.getName())
                    ? user
                    : defaultValue(method.getReturnType()));
  }

  private Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) return null;
    if (type == boolean.class) return false;
    if (type == char.class) return '\0';
    if (type == byte.class) return (byte) 0;
    if (type == short.class) return (short) 0;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == float.class) return 0F;
    return 0D;
  }

  private static final class MemoryStore implements WorkflowStore {
    private boolean failFlowWrites;
    private final Map<String, Workflow> workflows = new LinkedHashMap<>();
    private final Map<String, Flow> flows = new LinkedHashMap<>();
    private final Map<String, byte[]> attachments = new LinkedHashMap<>();

    @Override
    public boolean putWorkflow(Workflow workflow) {
      workflows.put(workflow.getId() + "@" + workflow.getVersion(), workflow);
      return true;
    }

    @Override
    public Workflow getWorkflow(String id) {
      return workflows.values().stream()
          .filter(workflow -> workflow.getId().equals(id))
          .findFirst()
          .orElse(null);
    }

    @Override
    public Workflow getWorkflow(String id, String version) {
      return workflows.get(id + "@" + version);
    }

    @Override
    public List<Workflow> listWorkflows() {
      return new ArrayList<>(workflows.values());
    }

    @Override
    public boolean putFlow(Flow flow) {
      if (failFlowWrites) return false;
      flows.put(flow.getId(), flow);
      return true;
    }

    @Override
    public boolean deleteFlow(String id) {
      return flows.remove(id) != null;
    }

    @Override
    public Flow getFlow(String id) {
      return flows.get(id);
    }

    @Override
    public List<Flow> listFlows() {
      return new ArrayList<>(flows.values());
    }

    @Override
    public boolean putWorkflowAttachment(
        String id, String flowId, String stateId, byte[] content) {
      attachments.put(id, content);
      return true;
    }

    @Override
    public byte[] getWorkflowAttachment(String id) {
      return attachments.get(id);
    }

    @Override
    public boolean deleteWorkflowAttachment(String id) {
      return attachments.remove(id) != null;
    }

    @Override
    public boolean updateResourceInfoForFlow(
        Flow flow, ResourceInfo.Stage stage, int reviewStatus) {
      return true;
    }

    @Override
    public boolean removeResourceInfoForFlow(Flow flow) {
      return true;
    }
  }
}
