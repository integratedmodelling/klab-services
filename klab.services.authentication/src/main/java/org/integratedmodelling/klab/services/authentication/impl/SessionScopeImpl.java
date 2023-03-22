package org.integratedmodelling.klab.services.authentication.impl;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.authentication.scope.SessionScope;
import org.integratedmodelling.klab.api.geometry.Geometry;

public class SessionScopeImpl extends UserScopeImpl implements SessionScope {

    private static final long serialVersionUID = -5840277560139759406L;

    private Status status = Status.STARTED;
    private String name;

    public void setName(String name) {
        this.name = name;
    }

    SessionScopeImpl(UserScopeImpl parent) {
        super(parent);
    }

    @Override
    public Geometry getGeometry() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ContextScope createContext(String id) {

        final ContextScopeImpl ret = new ContextScopeImpl(this);
        ret.setName(id);
        ret.setStatus(Status.WAITING);
        return ret;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Status getStatus() {
        return this.status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

}
