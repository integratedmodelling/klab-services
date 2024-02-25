package org.integratedmodelling.klab.rest;

import org.integratedmodelling.klab.api.identities.Group;

import java.util.List;

public interface AuthenticatedIdentity {

    IdentityReference getIdentity();

    List<Group> getGroups();

    String getExpiry();

    String getToken();
}
