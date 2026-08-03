package org.integratedmodelling.klab.runtime.kactors;

import java.util.*;
import java.util.function.Consumer;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.scope.SessionScope;

public abstract class TestCaseBase extends RuntimeAgentBase {

  /** Execution scope reserved for test-case agents. */
  public static class TestCaseScope extends AgentScope {

    private final SessionScope session;
    private final ContextScope context;
    // container to register contexts created during a test. Managed by the context agent.
    private Map<String, Set<ContextScope>> contexts = new HashMap<>();

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

    public void registerContext(ContextScope context) {
      contexts.computeIfAbsent(getCurrentAction(), k -> new HashSet<>()).add(context);
    }

    @Override
    public void beforeAction(String actionName, List<Annotation> annotations) {
      super.beforeAction(actionName, annotations);
    }

    @Override
    public void afterAction(String actionName, List<Annotation> annotations) {
      var contexts = this.contexts.get(getCurrentAction());
      if (contexts != null) {
        contexts.stream()
            .filter(c -> c.getConfiguration().getPersistence() == Persistence.ONE_OFF)
            .forEach(ContextScope::close);
      }
      super.afterAction(actionName, annotations);
    }

    @Override
    public void dispose() {
      // TODO finish testing and compute statistics
      super.dispose();
    }

    /**
     * Called after every assertion evaluation, before a failed assertion is propagated.
     *
     * @param assertion the complete semantic assertion bean
     * @param success whether evaluation and comparison succeeded
     * @param exception the evaluation/comparison failure, or {@code null} on success
     */
    public void assertionEvaluated(
        KActorsStatement.Assert.Assertion assertion, boolean success, Throwable exception) {}

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

  @Override
  protected void assertValue(Object actual, Object expected) {
    super.assertValue(actual, expected);
  }

  @Override
  protected void assertionEvaluated(
      AgentScope scope,
      KActorsStatement.Assert.Assertion assertion,
      boolean success,
      Throwable exception) {
    if (rootScope() instanceof TestCaseScope testCaseScope) {
      testCaseScope.assertionEvaluated(assertion, success, exception);
    }
  }

  public void runTest(Consumer<TestCaseScope> test) {
    // TODO
  }
}
