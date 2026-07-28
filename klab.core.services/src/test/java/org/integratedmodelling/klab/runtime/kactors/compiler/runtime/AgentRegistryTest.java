package org.integratedmodelling.klab.runtime.kactors.compiler.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.runtime.actors.AgentImpl;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.collections.Constant;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsVisitor;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsActionImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsArgumentsImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsStatementImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsValueImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase;
import org.integratedmodelling.klab.runtime.kactors.compiler.AgentCompiler;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.junit.jupiter.api.Test;

class AgentRegistryTest {

  @Test
  void executedReturnInPersistentMainTerminatesTheAgentConditionally() throws Exception {
    var terminating =
        persistentConditionalReturnBehavior(
            "test.registry.persistent.return."
                + UUID.randomUUID().toString().replace("-", ""),
            true);
    var continuing =
        persistentConditionalReturnBehavior(
            "test.registry.persistent.continue."
                + UUID.randomUUID().toString().replace("-", ""),
            false);

    var terminatingHandle =
        AgentRegistry.INSTANCE.getOrCreateAgent(
            request(terminating.getUrn(), "terminating"), terminating, null);
    var continuingHandle =
        AgentRegistry.INSTANCE.getOrCreateAgent(
            request(continuing.getUrn(), "continuing"), continuing, null);

    assertTrue(terminatingHandle.start(), () -> terminatingHandle.getNotifications().toString());
    assertTrue(continuingHandle.start(), () -> continuingHandle.getNotifications().toString());
    var terminatingRuntime =
        (RuntimeAgentBase) AgentRegistry.INSTANCE.getRuntimeAgent(terminatingHandle.getUrn());
    var continuingRuntime =
        (RuntimeAgentBase) AgentRegistry.INSTANCE.getRuntimeAgent(continuingHandle.getUrn());
    for (int i = 0; i < 100 && terminatingHandle.isAlive(); i++) {
      Thread.sleep(10);
    }

    assertEquals("stopped", terminatingRuntime.status());
    assertFalse(terminatingHandle.isAlive());
    assertEquals("running", continuingRuntime.status());
    assertTrue(continuingHandle.isAlive());

    assertTrue(terminatingHandle.stop());
    assertTrue(continuingHandle.stop());
  }

  @Test
  void managedHandleExposesItsResolvedCustomMessageApi() {
    var behavior =
        finiteBehavior("test.registry.api." + UUID.randomUUID().toString().replace("-", ""));
    var annotation = Annotation.of("handle");
    annotation.putUnnamed(Constant.create("PING"));
    ((KActorsActionImpl) behavior.getStatements().getFirst())
        .setAnnotations(List.of(annotation));

    var agent =
        AgentRegistry.INSTANCE.getOrCreateAgent(
            request(behavior.getUrn(), "message api"), behavior, null);

    assertTrue(agent.isViable(), () -> agent.getNotifications().toString());
    assertEquals(List.of("PING"), agent.getHandledMessageClasses());
    assertTrue(agent.stop());
  }

  @Test
  void compilesLoadsInstantiatesAndIndexesAgents() {
    var behavior = finiteBehavior("test.registry." + UUID.randomUUID().toString().replace("-", ""));
    var request = request(behavior.getUrn(), "registry test");

    var first = AgentRegistry.INSTANCE.getOrCreateAgent(request, behavior, null);

    assertTrue(first.isViable(), () -> first.getNotifications().toString());
    assertEquals("registry test", first.getName());
    assertNotNull(first.getUrn());
    assertTrue(first.getUrn().startsWith("runtime:agent:"));
    var compiledClass = AgentRegistry.INSTANCE.getCompiledClass(behavior);
    assertNotNull(compiledClass);
    assertSame(first, AgentRegistry.INSTANCE.getAgent(first.getUrn()));

    var lookup = request(behavior.getUrn(), null);
    lookup.setUrn(first.getUrn());
    assertSame(first, AgentRegistry.INSTANCE.getOrCreateAgent(lookup, behavior, null));

    var second =
        AgentRegistry.INSTANCE.getOrCreateAgent(request(behavior.getUrn(), null), behavior, null);
    assertTrue(second.isViable(), () -> second.getNotifications().toString());
    assertTrue(!first.getUrn().equals(second.getUrn()));
    assertSame(compiledClass, AgentRegistry.INSTANCE.getCompiledClass(behavior));

    assertTrue(first.stop());
    assertNull(AgentRegistry.INSTANCE.getAgent(first.getUrn()));
    assertTrue(second.stop());
  }

  @Test
  void compiledSwitchYieldsAValueAtRuntime() {
    String urn = "test.registry.switch." + UUID.randomUUID().toString().replace("-", "");
    var selector = value(2);
    var first = new KActorsStatementImpl.VerbImpl.MatchActionImpl();
    first.setMatchCriterion(value(1));
    first.setActionOnMatch(yielded(10));
    var second = new KActorsStatementImpl.VerbImpl.MatchActionImpl();
    second.setMatchCriterion(value(2));
    second.setActionOnMatch(yielded(20));
    var switched = new KActorsStatementImpl.SwitchImpl();
    switched.setValue(selector);
    switched.setCases(List.of(first, second));
    var assignment = new KActorsStatementImpl.AssignmentImpl();
    assignment.setVariable("result");
    assignment.setAssignmentScope(
        org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Assignment.Scope.FRAME);
    assignment.setSwitch(switched);
    var returned = new KActorsStatementImpl.ReturnImpl();
    var result = new KActorsValueImpl();
    result.setType(ValueType.IDENTIFIER);
    result.setStatedValue("result");
    returned.setValue(result);
    var main = new KActorsActionImpl();
    main.setUrn("main");
    main.setCode(List.of(assignment, returned));
    var behavior = finiteBehavior(urn);
    behavior.setStatements(List.of(main));

    var handle =
        AgentRegistry.INSTANCE.getOrCreateAgent(request(urn, "switch execution"), behavior, null);
    assertTrue(handle.isViable(), () -> handle.getNotifications().toString());
    var runtime = AgentRegistry.INSTANCE.getRuntimeAgent(handle.getUrn());
    assertNotNull(runtime);
    assertEquals(20, ((RuntimeAgentBase) runtime).run().getReturnValue());
    assertTrue(handle.stop());
  }

  @Test
  void sourceOnlyTranslationDoesNotCreateAnInstanceOrClassEntry() {
    var behavior =
        finiteBehavior("test.registry.source." + UUID.randomUUID().toString().replace("-", ""));
    int classesBefore = AgentRegistry.INSTANCE.getCompiledBehaviorCount();
    int agentsBefore = AgentRegistry.INSTANCE.getRegisteredAgentCount();

    var translated =
        AgentRegistry.INSTANCE.getOrCreateAgent(
            request(behavior.getUrn(), null),
            behavior,
            null,
            RuntimeAgent.CompilationOptions.DO_NOT_COMPILE_JAVA,
            RuntimeAgent.CompilationOptions.INCLUDE_JAVA_CODE);

    assertTrue(translated.isViable(), () -> translated.getNotifications().toString());
    assertNull(translated.getUrn());
    assertNotNull(((AgentImpl) translated).getJavaCode());
    assertEquals(classesBefore, AgentRegistry.INSTANCE.getCompiledBehaviorCount());
    assertEquals(agentsBefore, AgentRegistry.INSTANCE.getRegisteredAgentCount());
  }

  @Test
  void changedSourceInvalidatesTheCompiledBehaviorCacheEvenAtTheSameTimestamp() {
    String urn = "test.registry.revision." + UUID.randomUUID().toString().replace("-", "");
    var original = finiteBehavior(urn);
    original.setLastUpdateTimestamp(100L);
    original.setSourceCode("script " + urn + " action main: return 0");
    var originalHandle =
        AgentRegistry.INSTANCE.getOrCreateAgent(
            request(urn, "original"), original, null);
    var originalClass = AgentRegistry.INSTANCE.getCompiledClass(original);

    var revised = finiteBehavior(urn);
    revised.setLastUpdateTimestamp(100L);
    revised.setSourceCode("script " + urn + " action main: return 1");
    var returned = (KActorsStatementImpl.ReturnImpl) revised.getStatements().getFirst().getCode().getFirst();
    ((KActorsValueImpl) returned.getValue()).setStatedValue(1);
    var revisedHandle =
        AgentRegistry.INSTANCE.getOrCreateAgent(
            request(urn, "revised"), revised, null);
    var revisedClass = AgentRegistry.INSTANCE.getCompiledClass(revised);

    assertNotNull(originalClass);
    assertNotNull(revisedClass);
    assertTrue(originalClass != revisedClass, "changed source must produce a newly loaded class");
    assertTrue(originalHandle.stop());
    assertTrue(revisedHandle.stop());
  }

  @Test
  void managedAgentsAreSingleUseAndExplicitStopIsTerminal() {
    var behavior =
        finiteBehavior("test.registry.singleuse." + UUID.randomUUID().toString().replace("-", ""));
    var agent =
        AgentRegistry.INSTANCE.getOrCreateAgent(
            request(behavior.getUrn(), "single use"), behavior, null);

    assertTrue(agent.start(), () -> agent.getNotifications().toString());
    assertTrue(!agent.start(), "a completed agent must not restart");
    assertTrue(agent.stop());
    assertTrue(!agent.stop(), "stop must be accepted only once");
    assertTrue(!agent.start(), "an explicitly stopped agent is terminal");
    assertNull(AgentRegistry.INSTANCE.getAgent(agent.getUrn()));
  }

  @Test
  void userBehaviorHasAtMostOneRegisteredInstancePerUserScope() {
    var behavior =
        finiteBehavior("test.registry.user." + UUID.randomUUID().toString().replace("-", ""));
    behavior.setBehaviorType(KActorsBehavior.Type.USER);
    var firstUser = mock(ServiceUserScope.class);
    var sameLogicalUser = mock(ServiceUserScope.class);
    var secondUser = mock(ServiceUserScope.class);
    when(firstUser.getId()).thenReturn("first-user");
    when(sameLogicalUser.getId()).thenReturn("first-user");
    when(secondUser.getId()).thenReturn("second-user");

    var first =
        AgentRegistry.INSTANCE.getOrCreateAgent(
            request(behavior.getUrn(), "first user"), behavior, firstUser);
    var sameUser =
        AgentRegistry.INSTANCE.getOrCreateAgent(
            request(behavior.getUrn(), "same user"), behavior, sameLogicalUser);
    var otherUser =
        AgentRegistry.INSTANCE.getOrCreateAgent(
            request(behavior.getUrn(), "other user"), behavior, secondUser);

    assertTrue(first.isViable(), () -> first.getNotifications().toString());
    assertSame(first, sameUser);
    assertTrue(otherUser.isViable(), () -> otherUser.getNotifications().toString());
    assertTrue(!first.getUrn().equals(otherUser.getUrn()));
    assertTrue(first.stop());

    var replacement =
        AgentRegistry.INSTANCE.getOrCreateAgent(
            request(behavior.getUrn(), "replacement user"), behavior, firstUser);
    assertTrue(replacement.isViable(), () -> replacement.getNotifications().toString());
    assertTrue(!first.getUrn().equals(replacement.getUrn()));
    assertTrue(replacement.stop());
    assertTrue(otherUser.stop());
  }

  @Test
  void customCompilerEnvironmentAndObservationReachTheRuntimeInstance() {
    var behavior =
        finiteBehavior("test.registry.environment." + UUID.randomUUID().toString().replace("-", ""));
    var validations = new AtomicInteger();
    var validator =
        new KActorsVisitor.LenientValidator() {
          @Override
          public List<org.integratedmodelling.klab.api.services.runtime.Notification>
              validateBehavior(
                  KActorsBehavior source, KActorsVisitor.KActorsContext context) {
            validations.incrementAndGet();
            return List.of();
          }
        };
    var resolver = new AgentCompiler.Resolver() {};
    var observation = mock(Observation.class);
    var creationScope = mock(UserScope.class);
    when(observation.getId()).thenReturn(73L);
    when(observation.getName()).thenReturn("represented agent");

    var handle =
        AgentRegistry.INSTANCE.getOrCreateAgent(
            request(behavior.getUrn(), "requested name"),
            behavior,
            creationScope,
            observation,
            validator,
            resolver);

    assertTrue(handle.isViable(), () -> handle.getNotifications().toString());
    assertEquals("requested name", handle.getName());
    assertTrue(validations.get() > 0);
    var runtime = AgentRegistry.INSTANCE.getRuntimeAgent(handle.getUrn());
    assertNotNull(runtime);
    assertSame(observation, runtime.getObservation());
    assertSame(creationScope, runtime.getCreationScope());
    assertEquals(-1, runtime.getStartedAt());
    assertEquals(-1, runtime.getLastActivityAt());
    assertTrue(handle.stop());
  }

  @Test
  void observationNameIsUsedOnlyWhenNoAgentNameIsRequested() {
    var behavior =
        finiteBehavior(
            "test.registry.observation.name."
                + UUID.randomUUID().toString().replace("-", ""));
    var observation = mock(Observation.class);
    var creationScope = mock(UserScope.class);
    when(observation.getName()).thenReturn("represented agent");

    var handle =
        AgentRegistry.INSTANCE.getOrCreateAgent(
            request(behavior.getUrn(), null),
            behavior,
            creationScope,
            observation,
            new KActorsVisitor.LenientValidator(),
            new AgentCompiler.Resolver() {});

    assertTrue(handle.isViable(), () -> handle.getNotifications().toString());
    assertEquals("represented agent", handle.getName());
    assertTrue(handle.stop());
  }

  @Test
  void compiledKActorsImportsSupportStaticAliasesAndConstructedInstances() {
    String suffix = UUID.randomUUID().toString().replace("-", "");
    var importedBehavior = finiteBehavior("test.registry.imported." + suffix);
    var work = new KActorsActionImpl();
    work.setUrn("work");
    work.setCode(
        List.of(
            ((KActorsStatementImpl.ReturnImpl)
                importedBehavior.getStatements().getFirst().getCode().getFirst())));
    importedBehavior.setStatements(List.of(work));

    var constructorCall = verb("tools", "new");
    var assignment = new KActorsStatementImpl.AssignmentImpl();
    assignment.setVariable("worker");
    assignment.setAssignmentScope(
        org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Assignment.Scope.FRAME);
    assignment.setFunction(constructorCall);
    var returned = new KActorsStatementImpl.ReturnImpl();
    returned.setFunction(verb("worker", "work"));
    var main = new KActorsActionImpl();
    main.setUrn("main");
    main.setCode(List.of(assignment, returned));
    var behavior = finiteBehavior("test.registry.importer." + suffix);
    behavior.setStatements(List.of(main));
    var imported = new KActorsBehaviorImpl.ImportImpl();
    imported.setImportedBehavior(importedBehavior.getUrn());
    imported.setImportedAlias("tools");
    behavior.setImports(List.of(imported));

    var resolver =
        new AgentCompiler.Resolver() {
          @Override
          public KActorsBehavior resolveBehavior(String urn, UserScope scope) {
            return importedBehavior.getUrn().equals(urn) ? importedBehavior : null;
          }
        };
    var environment = AgentCompiler.runtimeEnvironment(resolver, null);
    var handle =
        AgentRegistry.INSTANCE.getOrCreateAgent(
            request(behavior.getUrn(), "import execution"),
            behavior,
            null,
            environment.validator(),
            resolver);

    assertTrue(handle.isViable(), () -> handle.getNotifications().toString());
    assertTrue(handle.start(), () -> handle.getNotifications().toString());
    assertTrue(handle.stop());
  }

  @Test
  void compiledKActorsAdapterExecutesForAnAsClause() {
    String suffix = UUID.randomUUID().toString().replace("-", "");
    var adapterBehavior = finiteBehavior("test.registry.adapter." + suffix);
    adapterBehavior.setBehaviorType(KActorsBehavior.Type.COMPONENT);
    var adapt = new KActorsActionImpl();
    adapt.setUrn("adapt");
    adapt.setArgumentNames(List.of("source"));
    adapt.setAnnotations(List.of(Annotation.of("adapt")));
    var adaptedReturn = new KActorsStatementImpl.ReturnImpl();
    var source = new KActorsValueImpl();
    source.setType(ValueType.IDENTIFIER);
    source.setStatedValue("source");
    adaptedReturn.setValue(source);
    adapt.setCode(List.of(adaptedReturn));
    adapterBehavior.setStatements(List.of(adapt));

    var assignment = new KActorsStatementImpl.AssignmentImpl();
    assignment.setVariable("adapted");
    assignment.setAssignmentScope(
        org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Assignment.Scope.FRAME);
    assignment.setValue(value(42));
    assignment.setAdaptedBehaviorUrn(adapterBehavior.getUrn());
    var returned = new KActorsStatementImpl.ReturnImpl();
    var adapted = new KActorsValueImpl();
    adapted.setType(ValueType.IDENTIFIER);
    adapted.setStatedValue("adapted");
    returned.setValue(adapted);
    var main = new KActorsActionImpl();
    main.setUrn("main");
    main.setCode(List.of(assignment, returned));
    var behavior = finiteBehavior("test.registry.adapter.caller." + suffix);
    behavior.setStatements(List.of(main));

    var resolver =
        new AgentCompiler.Resolver() {
          @Override
          public KActorsBehavior resolveBehavior(String urn, UserScope scope) {
            return adapterBehavior.getUrn().equals(urn) ? adapterBehavior : null;
          }
        };
    var environment = AgentCompiler.runtimeEnvironment(resolver, null);
    var handle =
        AgentRegistry.INSTANCE.getOrCreateAgent(
            request(behavior.getUrn(), "adaptation execution"),
            behavior,
            null,
            environment.validator(),
            resolver);

    assertTrue(handle.isViable(), () -> handle.getNotifications().toString());
    var runtime = (RuntimeAgentBase) AgentRegistry.INSTANCE.getRuntimeAgent(handle.getUrn());
    assertNotNull(runtime);
    assertEquals(42, runtime.run().getReturnValue());
    assertTrue(handle.stop());
  }

  @Test
  void componentAdapterIsAvailableWhileInitIsRunning() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "");
    String targetUrn = "test.registry.java.adapter." + suffix;
    var descriptor = new Extensions.ActorDescriptor();
    descriptor.urn = targetUrn;
    descriptor.adapter = new Extensions.FunctionDescriptor();
    var implementation = new ComponentRegistry.ServiceImplementation();
    implementation.implementation = JavaAdapter.class;
    implementation.method = JavaAdapter.class.getMethod("adapt", Integer.class);
    var resolved = new AgentCompiler.ResolvedActor(descriptor, java.util.Map.of(), implementation);

    var adapted = new KActorsStatementImpl.AssignmentImpl();
    adapted.setVariable("adapted");
    adapted.setAssignmentScope(
        org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Assignment.Scope.FRAME);
    adapted.setValue(value(41));
    adapted.setAdaptedBehaviorUrn(targetUrn);
    var state = new KActorsStatementImpl.AssignmentImpl();
    state.setVariable("result");
    state.setAssignmentScope(
        org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Assignment.Scope.ACTOR);
    var adaptedIdentifier = new KActorsValueImpl();
    adaptedIdentifier.setType(ValueType.IDENTIFIER);
    adaptedIdentifier.setStatedValue("adapted");
    state.setValue(adaptedIdentifier);
    var init = new KActorsActionImpl();
    init.setUrn("init");
    init.setCode(List.of(adapted, state));
    var returned = new KActorsStatementImpl.ReturnImpl();
    var result = new KActorsValueImpl();
    result.setType(ValueType.IDENTIFIER);
    result.setStatedValue("result");
    returned.setValue(result);
    var main = new KActorsActionImpl();
    main.setUrn("main");
    main.setCode(List.of(returned));
    var behavior = finiteBehavior("test.registry.java.adapter.caller." + suffix);
    behavior.setStatements(List.of(init, main));

    var resolver =
        new AgentCompiler.Resolver() {
          @Override
          public AgentCompiler.ResolvedActor resolveActor(String urn, UserScope scope) {
            return targetUrn.equals(urn) ? resolved : null;
          }

          @Override
          public Object adaptToBehavior(
              String behaviorUrn, Object source, RuntimeAgent.Scope runtimeScope) {
            return JavaAdapter.adapt((Integer) source);
          }
        };
    var environment = AgentCompiler.runtimeEnvironment(resolver, null);
    var handle =
        AgentRegistry.INSTANCE.getOrCreateAgent(
            request(behavior.getUrn(), "init adaptation"),
            behavior,
            null,
            environment.validator(),
            resolver);

    assertTrue(handle.isViable(), () -> handle.getNotifications().toString());
    var runtime = (RuntimeAgentBase) AgentRegistry.INSTANCE.getRuntimeAgent(handle.getUrn());
    assertNotNull(runtime);
    assertEquals(42, runtime.run().getReturnValue());
    assertTrue(handle.stop());
  }

  public static class JavaAdapter {
    public static Object adapt(Integer source) {
      return source + 1;
    }
  }

  private static AgentImpl request(String behaviorUrn, String name) {
    var ret = new AgentImpl();
    ret.setBehaviorUrn(behaviorUrn);
    ret.setName(name);
    return ret;
  }

  private static KActorsBehaviorImpl finiteBehavior(String urn) {
    var value = new KActorsValueImpl();
    value.setType(ValueType.INTEGER);
    value.setStatedValue(0);
    var returned = new KActorsStatementImpl.ReturnImpl();
    returned.setValue(value);
    var main = new KActorsActionImpl();
    main.setUrn("main");
    main.setCode(List.of(returned));
    var behavior = new KActorsBehaviorImpl();
    behavior.setUrn(urn);
    behavior.setDescription("Registry test behavior");
    behavior.setBehaviorType(KActorsBehavior.Type.SCRIPT);
    behavior.setStatements(List.of(main));
    return behavior;
  }

  private static KActorsBehaviorImpl persistentConditionalReturnBehavior(
      String urn, boolean shouldReturn) {
    var condition = new KActorsValueImpl();
    condition.setType(ValueType.BOOLEAN);
    condition.setStatedValue(shouldReturn);
    var returned = new KActorsStatementImpl.ReturnImpl();
    returned.setValue(value(7));
    var conditional = new KActorsStatementImpl.IfImpl();
    conditional.setCondition(condition);
    conditional.setThenBody(returned);
    var main = new KActorsActionImpl();
    main.setUrn("main");
    main.setCode(List.of(conditional));
    var behavior = new KActorsBehaviorImpl();
    behavior.setUrn(urn);
    behavior.setDescription("Persistent conditional return test behavior");
    behavior.setBehaviorType(KActorsBehavior.Type.BEHAVIOR);
    behavior.setStatements(List.of(main));
    return behavior;
  }

  private static KActorsValueImpl value(int number) {
    var value = new KActorsValueImpl();
    value.setType(ValueType.INTEGER);
    value.setStatedValue(number);
    return value;
  }

  private static KActorsStatementImpl.YieldImpl yielded(int number) {
    var yielded = new KActorsStatementImpl.YieldImpl();
    yielded.setValue(value(number));
    return yielded;
  }

  private static KActorsStatementImpl.VerbImpl verb(String recipient, String message) {
    var verb = new KActorsStatementImpl.VerbImpl();
    verb.setRecipient(recipient);
    verb.setMessage(message);
    verb.setArguments(new KActorsArgumentsImpl());
    return verb;
  }
}
