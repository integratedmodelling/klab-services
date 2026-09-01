package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.authentication.CustomProperty;
import org.integratedmodelling.klab.api.data.Metadata;
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
    assertEquals(1, visible.size());
    assertEquals(
        "asset-review", createFlow(manager, initial(), false, wildcard).getWorkflowId());
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
    var deletable = new Flow.State();
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
    var deletable = new Flow.State();
    deletable.setSchemaId("accepted");
    var created = manager.createState(flow.getId(), deletable, admin);
    var attachment = new Flow.Attachment();
    attachment.setId("retained-attachment");
    store.getFlow(flow.getId()).getStates().get(created.getId()).getAttachments().add(attachment);
    store.attachments.put(attachment.getId(), new byte[] {1});

    assertThrows(
        KlabIllegalStateException.class,
        () -> manager.deleteState(flow.getId(), created.getId(), admin));
    assertTrue(store.getFlow(flow.getId()).getStates().containsKey(created.getId()));
    assertTrue(store.attachments.containsKey(attachment.getId()));
  }

  private Flow.State initial() {
    var state = new Flow.State();
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
      flows.put(flow.getId(), flow);
      return true;
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
  }
}
