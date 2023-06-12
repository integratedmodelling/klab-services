package org.integratedmodelling.klab.services.actors.messages.user;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.services.actors.messages.AgentMessage;

public class CreateContext extends AgentMessage {

    private static final long serialVersionUID = -4721979530562111456L;

    private String contextId;
    private ContextScope scope;

    public CreateContext() {
    }
    
    public CreateContext(ContextScope scope, String contextId) {
        this.setScope(scope);
        this.contextId = contextId;
    }

    public String getContextId() {
        return contextId;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
    }

    public ContextScope getScope() {
        return scope;
    }

    public void setScope(ContextScope scope) {
        this.scope = scope;
    }

}