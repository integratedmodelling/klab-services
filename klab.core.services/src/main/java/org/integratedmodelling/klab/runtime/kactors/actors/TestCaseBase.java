package org.integratedmodelling.klab.runtime.kactors.actors;

import org.integratedmodelling.klab.api.scope.SessionScope;

public class TestCaseBase extends ActorBase {

    protected SessionScope scope;

    public TestCaseBase(SessionScope scope) {
        super();
        this.scope = scope;
    }
}
