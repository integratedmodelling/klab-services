package org.integratedmodelling.klab.services.actors.messages.user;

import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.runtime.kactors.messages.AgentMessage;

public class CreateApplication extends AgentMessage {

    private static final long serialVersionUID = -6105256439472164152L;

    private String applicationId;
    private Scope scope;

    public CreateApplication() {
    }

    public CreateApplication(Scope scope, String sessionId) {
        this.scope = scope;
        this.applicationId = sessionId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }

}
