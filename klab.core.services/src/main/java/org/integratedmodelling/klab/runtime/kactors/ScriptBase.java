package org.integratedmodelling.klab.runtime.kactors;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.SessionScope;

public abstract class ScriptBase extends AgentBase {
  public ScriptBase(KActorsBehavior behavior, SessionScope scope) {
    super(behavior, scope);
  }
}
