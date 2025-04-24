package org.integratedmodelling.common.authentication;

import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;

import java.util.Collections;
import java.util.Date;
import java.util.Set;

public class AnonymousUser implements UserIdentity {

	private Parameters<String> data = Parameters.create();
	
    @Override
    public Type getIdentityType() {
        return Type.ENGINE_USER;
    }

    @Override
    public String getId() {
        return ServicesAPI.ANONYMOUS_TOKEN;
    }

//    @Override
//    public Identity getParentIdentity() {
//        return null;
//    }

    @Override
    public boolean is(Type type) {
        return type == Type.ENGINE_USER;
    }

    @Override
    public boolean isAuthenticated() {
        return false;
    }

//    @Override
//    public <T extends Identity> T getParentIdentity(Class<T> type) {
//        return null;
//    }

    @Override
    public String getUsername() {
        return "anonymous";
    }

    @Override
    public Set<Group> getGroups() {
        return Collections.emptySet();
    }

    @Override
    public boolean isAnonymous() {
        return true;
    }

    @Override
    public boolean isOnline() {
        return false;
    }

    @Override
    public String getServerURL() {
        return null;
    }

    @Override
    public String getEmailAddress() {
        return "invalid.email@noservice.com";
    }

    @Override
    public String getFirstName() {
        return "Anonymous";
    }

    @Override
    public String getLastName() {
        return "User";
    }

    @Override
    public String getInitials() {
        return "";
    }

    @Override
    public String getAffiliation() {
        return "Non-existent affiliation";
    }

    @Override
    public String getComment() {
        return "";
    }

    @Override
    public Date getLastLogin() {
        return new Date();
    }

	@Override
	public Parameters<String> getData() {
		return data;
	}
	
	public String toString() {
		return "anonymous user (invalid.email@noservice.com)";
	}

}
