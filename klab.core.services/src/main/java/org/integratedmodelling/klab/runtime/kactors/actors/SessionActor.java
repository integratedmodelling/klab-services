package org.integratedmodelling.klab.runtime.kactors.actors;

import org.integratedmodelling.klab.api.scope.SessionScope;

public class SessionActor {

    private final SessionScope scope;

    public SessionActor(SessionScope sessionScope) {
        this.scope = sessionScope;
    }
}
