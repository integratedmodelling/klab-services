package org.integratedmodelling.klab.runtime.kactors;

import java.util.function.Consumer;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;

public abstract class TestCaseBase extends RuntimeAgentBase {

  /** Execution scope reserved for test-case agents. */
  public static class TestCaseScope extends AgentScope {

    private final SessionScope session;
    private final ContextScope context;

    public TestCaseScope(RuntimeAgentBase actor, SessionScope session, ContextScope context) {
      super(actor);
      this.session = session;
      this.context = context;
    }

    protected TestCaseScope(TestCaseScope parent, long actionId) {
      super(parent, actionId);
      this.session = parent.session;
      this.context = parent.context;
    }

    @Override
    public void setup() {
      // TODO setup test sequence and counters; instrument action scopes for communication
      super.setup();
    }

    @Override
    public void dispose() {
      // TODO finish testing and compute statistics
      super.dispose();
    }

    @Override
    public SessionScope getSession() {
      return session;
    }

    @Override
    public ContextScope getContext() {
      return context;
    }

    @Override
    public TestCaseScope withId(long actionId) {
      return new TestCaseScope(this, actionId);
    }
  }

  protected SessionScope scope;

  protected void runTests() {
    // Specialized test discovery/reporting will be implemented here.
  }

  public TestCaseBase(KActorsBehavior behavior, SessionScope scope) {
    super(behavior, scope);
    this.scope = scope;
  }

  protected TestCaseBase(
      KActorsBehavior behavior,
      SessionScope scope,
      Observation observation,
      org.integratedmodelling.klab.api.scope.Scope creationScope) {
    super(behavior, scope, observation, creationScope);
    this.scope = scope;
  }

  @Override
  protected AgentScope initializeScope() {
    return new TestCaseScope(this, sessionScope(), contextScope());
  }

  public void runTest(Consumer<TestCaseScope> test) {
    // TODO
  }
}
