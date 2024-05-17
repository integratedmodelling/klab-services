package org.integratedmodelling.klab.runtime.kactors.messages;

import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.kactors.AgentMessage;

public class CreateSession extends AgentMessage {

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
