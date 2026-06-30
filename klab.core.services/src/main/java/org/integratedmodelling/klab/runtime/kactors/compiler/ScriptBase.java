package org.integratedmodelling.klab.runtime.kactors.compiler;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.SessionScope;

public abstract class ScriptBase extends ActorBase {
  public ScriptBase(KActorsBehavior behavior, SessionScope scope) {
    super(behavior, scope);
  }
}
