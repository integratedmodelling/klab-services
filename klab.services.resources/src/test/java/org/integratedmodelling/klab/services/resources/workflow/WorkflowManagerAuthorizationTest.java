package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
  void publicFlowIsFullyVisibleButCannotBeMutatedByItsReader() {
    var store = new MemoryStore();
    var manager = manager(store);
    var flow = createFlow(manager, initial(), true, scope("editor", "EDITOR"));

    Flow visible = invoke(manager, "getFlow", new Class<?>[] {String.class, UserScope.class}, flow.getId(), scope("reader", "REVIEWER"));
    assertTrue(visible.isPublicRead());
    assertEquals(flow.getStates().keySet(), visible.getStates().keySet());
    var failure = assertThrows(
        InvocationTargetException.class,
        () ->
            invokeRaw(manager, "updateState", new Class<?>[] {String.class, String.class, Flow.State.class, UserScope.class},
                flow.getId(),
                flow.getCurrentStateIds().iterator().next(),
                flow.getStates().values().iterator().next(),
                scope("reader", "REVIEWER")));
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
    var property = new CustomProperty();
    property.setKey(WorkflowParticipant.ROLES_PROPERTY);
    property.setValue(role);
    Group group =
        (Group)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {Group.class},
                (proxy, method, args) ->
                    switch (method.getName()) {
                      case "getId" -> "workflow-" + role.toLowerCase();
                      case "getCustomProperties" -> List.of(property);
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
