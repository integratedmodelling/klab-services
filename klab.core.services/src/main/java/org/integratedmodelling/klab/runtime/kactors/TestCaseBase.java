package org.integratedmodelling.klab.runtime.kactors;

import java.util.function.Consumer;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.SessionScope;

public abstract class TestCaseBase extends RuntimeAgentBase {

  protected SessionScope scope;

  protected abstract void runTests();

  public TestCaseBase(KActorsBehavior behavior, SessionScope scope) {
    super(behavior, scope);
  }

  public void runTest(Consumer<TestScope> test) {
    // TODO
  }
}
