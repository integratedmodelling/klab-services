package org.integratedmodelling.klab.runtime.kactors.compiler.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.integratedmodelling.common.runtime.actors.AgentImpl;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsActionImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsStatementImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsValueImpl;
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
            AgentRegistry.Options.DO_NOT_COMPILE_JAVA,
            AgentRegistry.Options.INCLUDE_JAVA_CODE);

    assertTrue(translated.isViable(), () -> translated.getNotifications().toString());
    assertNull(translated.getUrn());
    assertNotNull(((AgentImpl) translated).getJavaCode());
    assertEquals(classesBefore, AgentRegistry.INSTANCE.getCompiledBehaviorCount());
    assertEquals(agentsBefore, AgentRegistry.INSTANCE.getRegisteredAgentCount());
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
    behavior.setBehaviorType(KActorsBehavior.Type.BEHAVIOR);
    behavior.setStatements(List.of(main));
    return behavior;
  }
}
