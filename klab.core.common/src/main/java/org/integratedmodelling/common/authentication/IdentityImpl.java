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

//    @Override
//    public Identity getParentIdentity() {
//        return this.parentIdentity;
//    }

    @Override
    public boolean is(Type type) {
        return this.identityType == type;
    }

    @Override
    public Parameters<String> getData() {
        return this.data;
    }

//    @Override
//    public <T extends Identity> T getParentIdentity(Class<T> type) {
//        return null;
//    }

    public void setData(Parameters<String> data) {
        this.data = data;
    }

//    public void setIdentityType(Type identityType) {
//        this.identityType = identityType;
//    }

    public void setId(String id) {
        this.id = id;
    }

//    public void setParentIdentity(Identity parentIdentity) {
//        this.parentIdentity = parentIdentity;
//    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }
}
