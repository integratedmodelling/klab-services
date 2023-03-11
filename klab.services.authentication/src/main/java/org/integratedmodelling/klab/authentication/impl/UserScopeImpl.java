package org.integratedmodelling.klab.authentication.impl;

import org.integratedmodelling.klab.api.authentication.scope.SessionScope;
import org.integratedmodelling.klab.api.authentication.scope.UserScope;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.UserIdentity;

public class UserScopeImpl extends Monitor implements UserScope {

    private static final long serialVersionUID = 605310381727313326L;

    private Parameters<String> data = Parameters.create();
    private UserIdentity user;

    public UserScopeImpl(UserIdentity user) {
        super(user);
        this.user = user;
    }

    protected UserScopeImpl(UserScopeImpl parent) {
        super(parent.user);
        this.user = parent.user;
    }
    
    @Override
    public SessionScope runSession(String sessionName) {
        SessionScopeImpl ret = new SessionScopeImpl(this);
        ret.setName(sessionName);
        return ret;
    }

    @Override
    public SessionScope runApplication(String behaviorName) {
        SessionScopeImpl ret = new SessionScopeImpl(this);
        ret.setName(behaviorName);
        return ret;
    }

    @Override
    public UserIdentity getUser() {
        return this.user;
    }

    @Override
    public Parameters<String> getData() {
        return this.data;
    }

}
