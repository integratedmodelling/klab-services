package org.integratedmodelling.klab.runtime.kactors.compiler;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.runtime.kactors.actors.runtime.TestScope;

import java.util.function.Consumer;

public abstract class TestCaseBase extends ActorBase {

    protected SessionScope scope;

    protected abstract void runTests();

    public TestCaseBase(KActorsBehavior behavior, SessionScope scope) {
        super(behavior);
        this.scope = scope;
    }

    public void runTest(Consumer<TestScope> test) {
        // TODO
    }

    public void run() {
        runTests();
    }
}
