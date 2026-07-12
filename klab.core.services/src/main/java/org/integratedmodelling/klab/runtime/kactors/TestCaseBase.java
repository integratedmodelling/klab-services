package org.integratedmodelling.klab.runtime.kactors;

import java.util.function.Consumer;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.runtime.kactors.actors.runtime.TestScope;

public abstract class TestCaseBase extends AgentBase {

  protected SessionScope scope;

  protected abstract void runTests();

  public TestCaseBase(KActorsBehavior behavior, SessionScope scope) {
    super(behavior, scope);
  }

  public void runTest(Consumer<TestScope> test) {
    // TODO
  }
}
