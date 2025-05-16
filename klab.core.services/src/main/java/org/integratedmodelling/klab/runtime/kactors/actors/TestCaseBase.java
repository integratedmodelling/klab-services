package org.integratedmodelling.klab.runtime.kactors.actors;

import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.runtime.kactors.tests.TestScope;

import java.util.function.Consumer;

public abstract class TestCaseBase extends ActorBase {

    protected SessionScope scope;

    protected abstract void runTests();

    public TestCaseBase(SessionScope scope) {
        super();
        this.scope = scope;
    }

    public void runTest(Consumer<TestScope> test) {
        // TODO
    }

    public void run() {
        runTests();
    }
}
