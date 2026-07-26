package org.integratedmodelling.klab.runtime.kactors.compiler.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsVisitor;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsActionImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsStatementImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsValueImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.runtime.kactors.compiler.AgentCompiler;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.junit.jupiter.api.Test;

class AgentRegistryTest {

  @Test
  void compilesLoadsInstantiatesAndIndexesAgents() {
    var behavior = finiteBehavior("test.registry." + UUID.randomUUID().toString().replace("-", ""));
    var request = request(behavior.getUrn(), "registry test");

    var first = AgentRegistry.INSTANCE.getOrCreateAgent(request, behavior, null);

    assertTrue(first.isViable(), () -> first.getNotifications().toString());
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
            request(behavior.getUrn(), "ignored"),
            behavior,
            creationScope,
            observation,
            validator,
            resolver);

    assertTrue(handle.isViable(), () -> handle.getNotifications().toString());
    assertTrue(validations.get() > 0);
    var runtime = AgentRegistry.INSTANCE.getRuntimeAgent(handle.getUrn());
    assertNotNull(runtime);
    assertSame(observation, runtime.getObservation());
    assertSame(creationScope, runtime.getCreationScope());
    assertEquals(-1, runtime.getStartedAt());
    assertEquals(-1, runtime.getLastActivityAt());
    assertTrue(handle.stop());
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
}
