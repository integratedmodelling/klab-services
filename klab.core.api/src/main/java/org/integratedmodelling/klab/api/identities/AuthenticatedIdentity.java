package org.integratedmodelling.klab.api.identities;

import java.util.Collection;
import java.util.Set;

/**
 * An authenticated identity may be anonymous or not and has a username and a set of groups.  This is the
 * least amount of information required to be assigned a scope in k.LAB.
 */
public interface AuthenticatedIdentity {

    boolean isAnonymous();

    String getUsername();

    Collection<Group> getGroups();

}
