package org.integratedmodelling.klab.runtime.kactors.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase;
import org.integratedmodelling.klab.runtime.kactors.AgentScope;
import org.junit.jupiter.api.Test;

class RuntimeAgentBaseTest {

  @Test
  void nonFunctionAgentWaitsForRootScopeDoneAfterMainReturns() throws Exception {
    var agent = new BackgroundRuntimeAgent();
    Thread thread = null;

    try {
      assertSame(RuntimeAgentBase.TASK_RUNNING, agent.run());
      assertTrue(agent.mainReturned.await(1, TimeUnit.SECONDS));

      thread = agent.agentThread.get();
      assertNotNull(thread);
      assertTrue(thread.isVirtual());
      assertTrue(thread.isAlive());

      agent.rootScope().done();
      thread.join(1000);

      assertFalse(thread.isAlive());
    } finally {
      agent.rootScope().done();
      if (thread != null) {
        thread.join(1000);
      }
    }
  }

  @Test
  void scopedSubscriptionsIgnoreTerminationAndDisposeOnDone() {
    var agent = new ReactiveRuntimeAgent();
    var parentScope = (AgentScope) agent.rootScope();
    var received = new AtomicInteger();

    var eventScope =
        agent.listen(
                parentScope, (event, scope) -> received.incrementAndGet(), RuntimeAgentBase.EventType.FIRE);

    eventScope.doFire("first");
    eventScope.done();
    eventScope.doFire("second");

    assertEquals(1, received.get());
  }

  @Test
  void returnEmitsOnePayloadEventBeforeTerminatingScope() {
    var agent = new ReactiveRuntimeAgent();
    var parentScope = (AgentScope) agent.rootScope();
    var received = new CopyOnWriteArrayList<RuntimeAgentBase.EventType>();

    var eventScope =
        agent.listen(
                parentScope,
                (event, scope) -> received.add(event.type()),
                RuntimeAgentBase.EventType.RETURN);

    eventScope.doReturn("done");

    assertEquals(List.of(RuntimeAgentBase.EventType.RETURN), received);
    assertTrue(eventScope.isDone());
  }

  @Test
  void supplierCompletionEmitsReturnPayloadAndTerminatesScope() {
    var agent = new ReactiveRuntimeAgent();
    var parentScope = (AgentScope) agent.rootScope();
    var future = new CompletableFuture<String>();
    var received = new CopyOnWriteArrayList<Object>();

    var eventScope =
        agent.listen(
                parentScope,
                (event, scope) -> received.add(event.payload()),
                RuntimeAgentBase.EventType.RETURN);
    assertSame(RuntimeAgentBase.TASK_RUNNING, agent.supply(eventScope, scope -> future));

    future.complete("done");

    assertEquals(List.of("done"), received);
    assertTrue(eventScope.isDone());
  }

  @Test
  void exceptionalScopeCompletionEmitsExceptionPayloadBeforeTermination() {
    var agent = new ReactiveRuntimeAgent();
    var parentScope = (AgentScope) agent.rootScope();
    var received = new CopyOnWriteArrayList<RuntimeAgentBase.EventType>();

    var eventScope =
        agent.listen(
                parentScope,
                (event, scope) -> received.add(event.type()),
                RuntimeAgentBase.EventType.EXCEPTION);

    eventScope.done(new IllegalStateException("boom"));

    assertEquals(List.of(RuntimeAgentBase.EventType.EXCEPTION), received);
    assertTrue(eventScope.isDone());
  }

  @Test
  void nestedEmitterRelaysFireThroughEnclosingActionScope() {
    var agent = new ReactiveRuntimeAgent();
    var rootScope = (AgentScope) agent.rootScope();
    var received = new CopyOnWriteArrayList<Object>();

    var enclosingActionScope =
        agent.listen(
                rootScope,
                (event, scope) -> received.add(event.payload()),
                RuntimeAgentBase.EventType.FIRE);
    var nestedVerbScope =
        agent.listen(
                enclosingActionScope,
                (event, scope) -> scope.doFire("relayed"),
                RuntimeAgentBase.EventType.FIRE);

    nestedVerbScope.doFire("source");

    assertEquals(List.of("relayed"), received);
  }

  private static class BackgroundRuntimeAgent extends RuntimeAgentBase {

    private final CountDownLatch mainReturned = new CountDownLatch(1);
    private final AtomicReference<Thread> agentThread = new AtomicReference<>();

    private BackgroundRuntimeAgent() {
      super(null, null);
    }

    @Override
    protected ExitValue main(AgentScope rootScope) {
      agentThread.set(Thread.currentThread());
      mainReturned.countDown();
      return NORMAL_EXIT;
    }

    @Override
    public Verb.Type getAgentExecutionMode() {
      return Verb.Type.EMITTER;
    }
  }

  private static class ReactiveRuntimeAgent extends RuntimeAgentBase {

    private ReactiveRuntimeAgent() {
      super(null, null);
    }

    private AgentScope listen(
        AgentScope scope,
        BiConsumer<Event, AgentScope> consumer,
        RuntimeAgentBase.EventType... eventTypes) {
      return onEvent(scope, consumer, eventTypes);
    }

    private <T> ExitValue supply(
        AgentScope scope, Function<AgentScope, CompletableFuture<T>> supplier) {
      return runSupplier(scope, supplier);
    }

    @Override
    protected ExitValue main(AgentScope rootScope) {
      return NORMAL_EXIT;
    }

    @Override
    public Verb.Type getAgentExecutionMode() {
      return Verb.Type.EMITTER;
    }
  }
}
