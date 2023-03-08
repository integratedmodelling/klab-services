package org.integratedmodelling.klab.services.actors.messages.user;

import java.io.Serializable;

import org.integratedmodelling.klab.api.knowledge.observation.scope.Scope;

public class CreateApplication implements Serializable {

    private String applicationId;
    private Scope scope;

    public CreateApplication() {
    }

    public CreateApplication(Scope scope, String sessionId) {
        this.scope = scope;
        this.applicationId = sessionId;
    }

    private static final long serialVersionUID = -6105256439472164152L;

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
