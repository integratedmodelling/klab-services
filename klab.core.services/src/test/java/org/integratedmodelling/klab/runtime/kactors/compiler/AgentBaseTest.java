package org.integratedmodelling.klab.runtime.kactors.compiler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
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
}
