package org.integratedmodelling.klab.runtime.kactors;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.runtime.kactors.view.ApplicationUiModel;

public abstract class ApplicationBase extends RuntimeAgentBase {

  /** Execution scope reserved for application agents. */
  public static class ApplicationScope extends AgentScope {

    private final SessionScope session;
    private final ContextScope context;
    private final ApplicationUiModel model;

    public ApplicationScope(RuntimeAgentBase actor, SessionScope session, ContextScope context) {
      super(actor);
      this.session = session;
      this.context = context;
      this.model = new ApplicationUiModel();
      ui(model.root());
    }

    protected ApplicationScope(ApplicationScope parent, long actionId) {
      super(parent, actionId);
      this.session = parent.session;
      this.context = parent.context;
      this.model = parent.model;
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

    @Override
    public void setup() {
      ((ApplicationBase) getAgent()).initializeUi();
    }

    @Override
    public void dispose() {
      model.close();
      if (session != null) {
        session.close();
      }
      super.dispose();
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

  /** The live server-side application interface, independent of any renderer connection. */
  public final ApplicationUiModel ui() {
    return ((ApplicationScope) rootScope()).model;
  }

  private void initializeUi() {
    if (sourceBehavior() == null) return;
    for (var action : sourceBehavior().getStatements()) {
      boolean initial =
          action.getAnnotations().stream()
              .anyMatch(
                  annotation ->
                      ApplicationUiModel.isSurfaceAnnotation(annotation.getName())
                          && (java.util.Set.of("header", "footer", "drawer")
                                  .contains(annotation.getName())
                              ? !Boolean.FALSE.equals(annotation.get("initial"))
                              : Boolean.TRUE.equals(annotation.get("initial"))));
      if (initial) {
        if (!action.getArguments().isEmpty())
          throw new IllegalArgumentException("Initial UI actions cannot require arguments");
        // A class-cache hit may be instantiated with a fresh, unanalyzed semantic bean. The
        // generated method's return shape is authoritative; this helper also joins suppliers.
        invokeSelfSupplier(action.getUrn(), (AgentScope) rootScope()).join();
      }
    }
  }
}
