package org.integratedmodelling.klab.runtime.kactors;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.SessionScope;

public abstract class ScriptBase extends RuntimeAgentBase {
  public ScriptBase(KActorsBehavior behavior, SessionScope scope) {
    super(behavior, scope);
  }
}
