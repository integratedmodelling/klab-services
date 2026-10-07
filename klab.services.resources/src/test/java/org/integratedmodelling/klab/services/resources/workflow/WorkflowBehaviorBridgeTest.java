package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kactors.*;
import org.integratedmodelling.klab.api.lang.kactors.impl.*;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.impl.*;
import org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase;
import org.integratedmodelling.klab.runtime.kactors.compiler.AgentCompiler;
import org.junit.jupiter.api.Test;

class WorkflowBehaviorBridgeTest {
  @Test void initBindsDocumentAndProjectAndRestoresTheirReferences() {
    var behavior = behavior();
    var init = assign("init", "target", "document", ValueType.IDENTIFIER);
    init.setArguments(List.of(new KActorsActionImpl.ArgumentImpl("document"), new KActorsActionImpl.ArgumentImpl("project")));
    var code = new ArrayList<>(init.getCode()); code.addAll(assign("unused", "targetProject", "project", ValueType.IDENTIFIER).getCode()); init.setCode(code);
    behavior.setStatements(List.of(init));
    var owner = owner(); var resources = mock(org.integratedmodelling.klab.api.services.ResourcesService.class);
    when(owner.getServices(org.integratedmodelling.klab.api.services.ResourcesService.class)).thenReturn(List.of(resources));
    when(resources.serviceId()).thenReturn("resources");
    var document = new org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl();
    document.setUrn("test.target"); document.setProjectName("project");
    when(resources.retrieve("test.target", org.integratedmodelling.klab.api.lang.kim.KimOntology.class, owner)).thenReturn(document);
    var workflow = new WorkflowImpl(); workflow.setBehavior(behavior.getUrn());
    var schema = new WorkflowImpl.StateSchemaImpl(); schema.setId("editing"); workflow.getStates().put("editing", schema);
    var flow = Flow.create(); flow.setOwner("owner"); flow.setAssetUrn("test.target");
    flow.setAssetType(org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.ONTOLOGY);
    var stage = Flow.State.create(); stage.setId("stage"); stage.setSchemaId("editing"); flow.getStates().put("stage", stage);
    try (var session = bridge(behavior).open(workflow, flow, stage, owner)) { session.invoke(List.of(), stage, null); session.checkpoint(stage); }
    var restored = Utils.Json.parseObject(Utils.Json.asString(flow), Flow.class);
    try (var session = bridge(behavior).open(workflow, restored, restored.getStates().get("stage"), owner)) {
      session.invoke(List.of(), restored.getStates().get("stage"), null); session.checkpoint(restored.getStates().get("stage"));
    }
    var globals = (Map<?, ?>) restored.getBehaviorCheckpoint().globals().get("state");
    assertEquals("test.target", ((Map<?, ?>) globals.get("target")).get("urn"));
    assertEquals("PROJECT", ((Map<?, ?>) globals.get("targetProject")).get("kind"));
    assertEquals(flow.getBehaviorCheckpoint(), restored.getBehaviorCheckpoint());
  }
  static KActorsActionImpl assign(String actionName, String variable, Object value, ValueType type) {
    var literal = new KActorsValueImpl();
    literal.setType(type); literal.setStatedValue(value);
    var assignment = new KActorsStatementImpl.AssignmentImpl();
    assignment.setVariable(variable);
    assignment.setAssignmentScope(KActorsStatement.Assignment.Scope.ACTOR);
    assignment.setValue(literal);
    var action = new KActorsActionImpl();
    action.setUrn(actionName); action.setCode(List.of(assignment));
    return action;
  }

  static KActorsBehaviorImpl behavior() {
    var behavior = new KActorsBehaviorImpl();
    behavior.setUrn("test.workflow.instrumentation");
    behavior.setDescription("Workflow checkpoint test");
    behavior.setBehaviorType(KActorsBehavior.Type.BEHAVIOR);
    var init = assign("init", "value", "initial", ValueType.STRING);
    var update = assign("update", "value", "text", ValueType.IDENTIFIER);
    update.setArguments(List.of(new KActorsActionImpl.ArgumentImpl("text", Annotation.of("type", "class", "String"))));
    var prepare = new KActorsActionImpl(); prepare.setUrn("prepare");
    prepare.setArguments(List.of(new KActorsActionImpl.ArgumentImpl("content")));
    var title = new KActorsStatementImpl.VerbImpl(); title.setRecipient("content"); title.setMessage("title");
    var arguments = new KActorsArgumentsImpl();
    var text = new KActorsValueImpl(); text.setType(ValueType.STRING); text.setStatedValue("Prepared");
    arguments.putUnnamed(text); title.setArguments(arguments); prepare.setCode(List.of(title));
    behavior.setStatements(List.of(init, update, prepare));
    return behavior;
  }

  static WorkflowBehaviorBridge bridge(KActorsBehavior behavior) {
    return new WorkflowBehaviorBridge(() -> new AgentCompiler.Resolver() {
      @Override public KActorsBehavior resolveBehavior(String urn, UserScope scope) { return behavior; }
    }, owner -> null);
  }

  static UserScope owner() {
    var scope = mock(UserScope.class);
    var user = mock(UserIdentity.class);
    when(user.getUsername()).thenReturn("owner");
    when(scope.getUser()).thenReturn(user);
    return scope;
  }

  @Test void checkpointRoundTripRestoresStateWithoutReplayingInitAndOffersMissingInputs() {
    var behavior = behavior();
    var workflow = new WorkflowImpl(); workflow.setBehavior(behavior.getUrn());
    var schema = new WorkflowImpl.StateSchemaImpl(); schema.setId("editing");
    var button = new WorkflowBehavior.Action("change", "Change", "update", Map.of());
    schema.setActions(List.of(button)); workflow.getStates().put("editing", schema);
    var flow = Flow.create(); flow.setOwner("owner"); flow.setId("flow");
    var stage = Flow.State.create(); stage.setSchemaId("editing"); stage.setId("stage");
    flow.getStates().put(stage.getId(), stage);
    var owner = owner();
    try (var session = bridge(behavior).open(workflow, flow, stage, owner)) {
      assertEquals("text", session.available(stage).getFirst().missing().getFirst().name());
      assertNull(flow.getBehaviorCheckpoint(), "Discovery must not execute init");
      session.invoke(List.of(), stage, null); session.checkpoint(stage);
    }
    assertEquals("initial", ((Map<?, ?>) flow.getBehaviorCheckpoint().globals().get("state")).get("value"));
    try (var session = bridge(behavior).open(workflow, flow, stage, owner)) {
      session.execute(button, stage, Map.of("text", "saved"));
    }
    var restored = Utils.Json.parseObject(Utils.Json.asString(flow), Flow.class);
    var restoredStage = restored.getStates().get("stage");
    try (var session = bridge(behavior).open(workflow, restored, restoredStage, owner)) {
      session.invoke(List.of(), restoredStage, null); session.checkpoint(restoredStage);
    }
    assertEquals("saved", ((Map<?, ?>) restored.getBehaviorCheckpoint().globals().get("state")).get("value"));
    assertEquals(restored.getBehaviorCheckpoint(), restoredStage.getBehaviorCheckpoint());
  }

  @Test void bindsNamesThenUniqueTypesAndRejectsAmbiguityAndMissingAutomaticInputs() {
    var action = new KActorsActionImpl();
    action.setArguments(List.of(new KActorsActionImpl.ArgumentImpl("schema", Annotation.of("type", "class", "Workflow"))));
    var workflow = new WorkflowImpl();
    assertSame(workflow, WorkflowBehaviorBridge.bind(action, Map.of(), Map.of("workflow", workflow), null)[0]);
    assertThrows(IllegalArgumentException.class, () -> WorkflowBehaviorBridge.bind(action, Map.of(),
        Map.of("workflow", workflow, "other", new WorkflowImpl()), null));
    assertThrows(IllegalArgumentException.class, () -> WorkflowBehaviorBridge.bind(action, Map.of(), Map.of(), null));
    action.setArguments(List.of(new KActorsActionImpl.ArgumentImpl("workflow")));
    assertSame(workflow, WorkflowBehaviorBridge.bind(action, Map.of("workflow", "forged"), Map.of("workflow", workflow), null)[0]);
  }

  @Test void checkpointRejectsLiveObjectsAndCyclicStateAndCopiesMutableValues() {
    assertThrows(IllegalArgumentException.class, () -> RuntimeAgentBase.portableState(Flow.create()));
    var cyclic = new LinkedHashMap<String, Object>(); cyclic.put("self", cyclic);
    assertThrows(IllegalArgumentException.class, () -> RuntimeAgentBase.portableState(cyclic));
    var values = new ArrayList<>(List.of("before"));
    var snapshot = (Map<?, ?>) RuntimeAgentBase.portableState(Map.of("values", values));
    values.set(0, "after"); assertEquals(List.of("before"), snapshot.get("values"));
  }

  public static final class SupplierAgent extends RuntimeAgentBase {
    public java.util.concurrent.CompletableFuture<Object> action_init(
        org.integratedmodelling.klab.runtime.kactors.AgentScope scope, Object... arguments) {
      return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
        setActorState("initialized", true); return "done";
      });
    }
    public java.util.concurrent.CompletableFuture<Object> action_fail(
        org.integratedmodelling.klab.runtime.kactors.AgentScope scope, Object... arguments) {
      return java.util.concurrent.CompletableFuture.failedFuture(new IllegalArgumentException("rejected"));
    }
    public Object action_dynamic(org.integratedmodelling.klab.runtime.kactors.AgentScope scope, Object... arguments) {
      runDynamicVerb(new Peer(), "record", scope.withId(100)); return null;
    }
    public Object action_emitter(org.integratedmodelling.klab.runtime.kactors.AgentScope scope, Object... arguments) {
      runDynamicVerb(new Peer(), "emit", scope.withId(101)); return null;
    }
    public final class Peer {
    public java.util.concurrent.CompletableFuture<Object> record() {
      return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
        setActorState("dynamic", "saved"); return "saved";
      }, java.util.concurrent.CompletableFuture.delayedExecutor(100, java.util.concurrent.TimeUnit.MILLISECONDS));
    }
    @org.integratedmodelling.klab.api.services.runtime.extension.Verb(name = "emit", fires = String.class)
    public void emit() { setActorState("emitted", true); }
    }
    @Override protected ExitValue main(org.integratedmodelling.klab.runtime.kactors.AgentScope scope) { return NORMAL_EXIT; }
    @Override public org.integratedmodelling.klab.api.services.runtime.extension.Verb.Type getAgentExecutionMode() {
      return org.integratedmodelling.klab.api.services.runtime.extension.Verb.Type.SUPPLIER;
    }
  }

  @Test void awaitsSupplierInitializationAndPropagatesSupplierFailure() {
    var agent = new SupplierAgent();
    try {
      agent.initializeCheckpoint();
      assertEquals(true, ((Map<?, ?>) agent.checkpointState().get("state")).get("initialized"));
      assertThrows(IllegalStateException.class, () -> agent.invokeCheckpointAction("fail"));
    } finally { agent.stop(); }
  }

  @Test void waitsForDynamicallyBoundSuppliersAndRejectsDynamicEmittersBeforeInvocation() {
    var agent = new SupplierAgent();
    try {
      agent.invokeCheckpointAction("dynamic");
      assertEquals("saved", ((Map<?, ?>) agent.checkpointState().get("state")).get("dynamic"));
      assertThrows(IllegalStateException.class, () -> agent.invokeCheckpointAction("emitter"));
      assertFalse(((Map<?, ?>) agent.checkpointState().get("state")).containsKey("emitted"));
    } finally { agent.stop(); }
  }

  @Test void rejectsEmitterBeforeInitializing() {
    var behavior = behavior();
    var fire = new KActorsStatementImpl.FireImpl();
    var value = new KActorsValueImpl(); value.setType(ValueType.STRING); value.setStatedValue("event");
    fire.setValue(value);
    var action = new KActorsActionImpl(); action.setUrn("events"); action.setCode(List.of(fire));
    var statements = new ArrayList<>(behavior.getStatements()); statements.add(action); behavior.setStatements(statements);
    assertThrows(IllegalArgumentException.class, () ->
        org.integratedmodelling.klab.runtime.kactors.compiler.runtime.AgentRegistry.INSTANCE.checkpointAgent(
            behavior, owner(), new AgentCompiler.Resolver() {}, null, new Object[0]));
  }

  @Test void yamlRoundTripsBindingsAndRejectsBindingsWithoutBehavior() {
    String yaml = """
        id: instrumented
        version: '1'
        behavior: examples.review
        states:
          editing:
            managerRoles: [EDITOR]
            onStart: [{action: prepare}]
            onCommit: [{action: validate}]
            actions:
              - id: email
                label: Send email
                action: email
                parameters: {subject: Review}
        transitions:
          initialize:
            sourceStates: [INIT]
            targetState: editing
            roles: [EDITOR]
            actions: [{action: created}]
        """;
    var workflow = Utils.YAML.load(new java.io.ByteArrayInputStream(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8)), Workflow.class);
    assertTrue(workflow.validate().isEmpty(), workflow.validate().toString());
    var copy = Utils.Json.parseObject(Utils.Json.asString(workflow), Workflow.class);
    assertEquals("email", copy.getStates().get("editing").getActions().getFirst().action());
    copy.setBehavior(null); assertFalse(copy.validate().isEmpty());
  }
}
