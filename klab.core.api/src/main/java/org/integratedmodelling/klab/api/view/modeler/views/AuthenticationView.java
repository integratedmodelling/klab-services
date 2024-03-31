package org.integratedmodelling.klab.api.view.modeler.views;

import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.view.View;

public interface AuthenticationView extends View {

    /**
     * Send the user. The user identity may be null, which means we have lost authentication and there is
     * no provision for anonymity.
     *
     * @param identity the current user or null.
     */
    void notifyUser(UserIdentity identity);

}
