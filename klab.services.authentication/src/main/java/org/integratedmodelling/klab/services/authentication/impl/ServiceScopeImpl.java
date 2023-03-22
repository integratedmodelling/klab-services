package org.integratedmodelling.klab.services.authentication.impl;

import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.UserIdentity;

public class ServiceScopeImpl extends Monitor implements ServiceScope {

    private static final long serialVersionUID = 605310381727313326L;

    private Parameters<String> data = Parameters.create();
    private UserIdentity user;

    public ServiceScopeImpl(UserIdentity user) {
        super(user);
        this.user = user;
    }

    protected ServiceScopeImpl(ServiceScopeImpl parent) {
        super(parent.user);
        this.user = parent.user;
    }

    @Override
    public Parameters<String> getData() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isLocal() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isExclusive() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isDedicated() {
        // TODO Auto-generated method stub
        return false;
    }
   

}
