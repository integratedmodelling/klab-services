package org.integratedmodelling.klab.runtime.kactors.actors.runtime;

import org.integratedmodelling.klab.runtime.kactors.compiler.ActorBase;

public abstract class TestScope extends AgentScope {

  public TestScope(ActorBase actor) {
    super(actor);
  }

  public TestScope(TestScope parent, long actionId) {
    super(parent, actionId);
  }
}
