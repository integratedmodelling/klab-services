package org.integratedmodelling.klab.api.identities;

import java.util.Set;

/**
 * Institutional user. Certificates (for now) may belong to either institutions or individuals.
 */
public interface PartnerIdentity extends Identity {

    public String getName();

    public String getEmailAddress();

    Set<Group> getGroups();

    public String getAddress();

    public UserIdentity getContactPerson();

    String getAuthenticatingHub();

    String getPublicKey();

    String getToken();

    String getUrl();

    // TODO institutional stuff

}
