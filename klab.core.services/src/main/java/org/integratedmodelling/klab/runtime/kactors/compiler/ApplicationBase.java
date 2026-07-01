package org.integratedmodelling.klab.runtime.kactors.compiler;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.SessionScope;

public abstract class ApplicationBase extends ActorBase {
  public ApplicationBase(KActorsBehavior behavior, SessionScope scope) {
    super(behavior, scope);
  }
}
