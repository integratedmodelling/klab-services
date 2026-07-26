package org.integratedmodelling.klab.runtime.kactors;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;

public abstract class ScriptBase extends RuntimeAgentBase {

  /**
   * Execution scope reserved for script agents.
   *
   * <p>TODO setup I/O, current directories, result persistence contract and runtime environment
   */
  public static class ScriptScope extends AgentScope {

    private final SessionScope session;
    private final ContextScope context;

    public ScriptScope(RuntimeAgentBase actor, SessionScope session, ContextScope context) {
      super(actor);
      this.session = session;
      this.context = context;
    }

    protected ScriptScope(ScriptScope parent, long actionId) {
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
    public ScriptScope withId(long actionId) {
      return new ScriptScope(this, actionId);
    }
  }

  public ScriptBase(KActorsBehavior behavior, SessionScope scope) {
    super(behavior, scope);
  }

  protected ScriptBase(
      KActorsBehavior behavior,
      SessionScope scope,
      Observation observation,
      org.integratedmodelling.klab.api.scope.Scope creationScope) {
    super(behavior, scope, observation, creationScope);
  }

  @Override
  protected AgentScope initializeScope() {
    return new ScriptScope(this, sessionScope(), contextScope());
  }
}