package org.integratedmodelling.klab.services.actors.messages.user;

import java.io.Serializable;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.geometry.Geometry;

public class CreateContext implements Serializable {

    private static final long serialVersionUID = -4721979530562111456L;

    private String contextId;
    private Geometry geometry;
    private ContextScope scope;

    public CreateContext() {
    }
    
    public CreateContext(ContextScope scope, String contextId, Geometry geometry) {
        this.setScope(scope);
        this.contextId = contextId;
        this.geometry = geometry;
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

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

}
