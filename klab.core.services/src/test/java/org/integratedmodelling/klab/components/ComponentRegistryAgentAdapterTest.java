package org.integratedmodelling.klab.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.services.runtime.extension.Actor;
import org.integratedmodelling.klab.api.services.runtime.extension.AgentAdapter;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.junit.jupiter.api.Test;

class ComponentRegistryAgentAdapterTest {

  @Test
  void discoversAndInvokesAnAsynchronousInstanceAgentAdapter() throws Exception {
    var registry = mock(ComponentRegistry.class, CALLS_REAL_METHODS);
    Method createDescriptor =
        ComponentRegistry.class.getDeclaredMethod(
            "createActorDescriptor", Actor.class, String.class, Class.class);
    createDescriptor.setAccessible(true);
    var descriptor =
        (Extensions.ActorDescriptor)
            createDescriptor.invoke(
                registry,
                AdaptableActor.class.getAnnotation(Actor.class),
                "test.",
                AdaptableActor.class);

    assertNotNull(descriptor.adapter);
    assertTrue(!descriptor.adapter.error);
    assertEquals("test.adaptable", descriptor.urn);
    assertEquals(
        "test.produced",
        descriptor.verbs.stream()
            .filter(verb -> verb.behaviorUrn != null)
            .findFirst()
            .orElseThrow()
            .behaviorUrn);
    var scope = mock(RuntimeAgent.Scope.class);
    var result = (Adapted) registry.invokeAgentAdapter(descriptor, "source", scope);

    assertEquals("source", result.source());
    assertSame(scope, result.scope());
  }

  @Actor(name = "adaptable", description = "Adapter discovery test")
  public static class AdaptableActor {

    @AgentAdapter
    public CompletableFuture<Object> adapt(RuntimeAgent.Scope scope, CharSequence source) {
      return CompletableFuture.completedFuture(new Adapted(source.toString(), scope));
    }

    @Verb(name = "make", producesAgent = "test.produced")
    public Object make() {
      return new Object();
    }
  }

  private record Adapted(String source, RuntimeAgent.Scope scope) {}
}
