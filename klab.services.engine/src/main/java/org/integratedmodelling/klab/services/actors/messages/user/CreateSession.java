package org.integratedmodelling.klab.services.actors.messages.user;

import java.io.Serializable;

import org.integratedmodelling.klab.api.knowledge.observation.scope.Scope;

public class CreateSession implements Serializable {

    private static final long serialVersionUID = -4721979530562111456L;

    private String sessionId;
    private Scope scope;

    public CreateSession() {
    }
    
    public CreateSession(Scope scope, String sessionId) {
        this.setScope(scope);
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }

}
