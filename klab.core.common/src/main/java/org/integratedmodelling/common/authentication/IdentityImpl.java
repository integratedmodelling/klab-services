package org.integratedmodelling.common.authentication;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.Identity;

public class IdentityImpl implements Identity {
    private Parameters<String> data = Parameters.create();
    private Type identityType;
    private String id;
//    private Identity parentIdentity;
    private boolean authenticated;

    @Override
    public Type getIdentityType() {
        return this.identityType;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public boolean is(Type type) {
        return this.identityType == type;
    }

    @Override
    public Parameters<String> getData() {
        return this.data;
    }

    public void setData(Parameters<String> data) {
        this.data = data;
    }

    public void setIdentityType(Type identityType) {
        this.identityType = identityType;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }
}
