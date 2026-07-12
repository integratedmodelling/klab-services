package org.integratedmodelling.klab.runtime.kactors.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.runtime.kactors.AgentBase;
import org.integratedmodelling.klab.runtime.kactors.actors.runtime.AgentScope;
import org.junit.jupiter.api.Test;

class AgentBaseTest {

  @Test
  void nonFunctionAgentWaitsForRootScopeDoneAfterMainReturns() throws Exception {
    var agent = new BackgroundAgent();
    Thread thread = null;

    try {
      assertSame(AgentBase.TASK_RUNNING, agent.run());
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
    var agent = new ReactiveAgent();
    var childScope = ((AgentScope) agent.rootScope()).withId(1);
    var received = new AtomicInteger();

    agent.listen(childScope, event -> received.incrementAndGet(), AgentBase.EventType.FIRE);

    childScope.doFire("first");
    childScope.done();
    childScope.doFire("second");

    assertEquals(1, received.get());
  }

  @Test
  void returnEmitsOnePayloadEventBeforeTerminatingScope() {
    var agent = new ReactiveAgent();
    var childScope = ((AgentScope) agent.rootScope()).withId(1);
    var received = new CopyOnWriteArrayList<AgentBase.EventType>();

    agent.listen(childScope, event -> received.add(event.type()), AgentBase.EventType.RETURN);

    childScope.doReturn("done");

    assertEquals(List.of(AgentBase.EventType.RETURN), received);
    assertTrue(childScope.isDone());
  }

  @Test
  void exceptionalScopeCompletionEmitsExceptionPayloadBeforeTermination() {
    var agent = new ReactiveAgent();
    var childScope = ((AgentScope) agent.rootScope()).withId(1);
    var received = new CopyOnWriteArrayList<AgentBase.EventType>();

    agent.listen(childScope, event -> received.add(event.type()), AgentBase.EventType.EXCEPTION);

    childScope.done(new IllegalStateException("boom"));

    assertEquals(List.of(AgentBase.EventType.EXCEPTION), received);
    assertTrue(childScope.isDone());
  }

  private static class BackgroundAgent extends AgentBase {

    private final CountDownLatch mainReturned = new CountDownLatch(1);
    private final AtomicReference<Thread> agentThread = new AtomicReference<>();

    private BackgroundAgent() {
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

  private static class ReactiveAgent extends AgentBase {

    private ReactiveAgent() {
      super(null, null);
    }

    private void listen(
        AgentScope scope, Consumer<AgentBase.Event> consumer, AgentBase.EventType... eventTypes) {
      onEvent(scope, consumer, eventTypes);
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
