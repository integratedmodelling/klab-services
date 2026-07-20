package org.integratedmodelling.klab.runtime.kactors;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.SessionScope;

public abstract class ApplicationBase extends RuntimeAgentBase {
  public ApplicationBase(KActorsBehavior behavior, SessionScope scope) {
    super(behavior, scope);
  }
}
