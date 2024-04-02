package org.integratedmodelling.klab.modeler.views;

import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.view.modeler.views.AuthenticationView;

public class AuthenticationViewAdvisor extends BaseViewAdvisor implements AuthenticationView {
    @Override
    public void notifyUser(UserIdentity identity) {
    }
}
