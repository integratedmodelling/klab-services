package org.integratedmodelling.klab.runtime.kactors.tests;

import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.extension.Actor;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;

@Actor(name="context")
public class ContextActor extends SessionActor {

    public ContextActor(SessionScope sessionScope) {
        super(sessionScope);
    }

    @Verb(name="new")
    public ContextScope newContext() {
        return null;
    }
}
