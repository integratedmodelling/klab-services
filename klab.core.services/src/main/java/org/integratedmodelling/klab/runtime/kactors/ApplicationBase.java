package org.integratedmodelling.klab.runtime.kactors;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;

public abstract class ApplicationBase extends RuntimeAgentBase {

  /** Execution scope reserved for application agents. */
  public static class ApplicationScope extends AgentScope {

    private final SessionScope session;
    private final ContextScope context;

    public ApplicationScope(
        RuntimeAgentBase actor, SessionScope session, ContextScope context) {
      super(actor);
      this.session = session;
      this.context = context;
    }

    protected ApplicationScope(ApplicationScope parent, long actionId) {
      super(parent, actionId);
      this.session = parent.session;
      this.context = parent.context;
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
    public ApplicationScope withId(long actionId) {
      return new ApplicationScope(this, actionId);
    }
  }

  public ApplicationBase(KActorsBehavior behavior, SessionScope scope) {
    super(behavior, scope);
  }

  protected ApplicationBase(
      KActorsBehavior behavior,
      SessionScope scope,
      Observation observation,
      org.integratedmodelling.klab.api.scope.Scope creationScope) {
    super(behavior, scope, observation, creationScope);
  }

  @Override
  protected AgentScope initializeScope() {
    return new ApplicationScope(this, sessionScope(), contextScope());
  }
}
