package org.integratedmodelling.klab.services.actors.messages.context;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.runtime.kactors.messages.AgentMessage;

public class Observe extends AgentMessage {

    private static final long serialVersionUID = 1563875963809256131L;

    private String urn;
    private Geometry geometry;
    private ContextScope scope;
    public String getUrn() {
        return urn;
    }
    public void setUrn(String urn) {
        this.urn = urn;
    }
    public Geometry getGeometry() {
        return geometry;
    }
    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }
    public ContextScope getScope() {
        return scope;
    }
    public void setScope(ContextScope scope) {
        this.scope = scope;
    }


}