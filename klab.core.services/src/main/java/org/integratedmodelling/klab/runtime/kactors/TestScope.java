package org.integratedmodelling.klab.runtime.kactors;

public abstract class TestScope extends AgentScope {

  public TestScope(RuntimeAgentBase actor) {
    super(actor);
  }

  public TestScope(TestScope parent, long actionId) {
    super(parent, actionId);
  }
}
