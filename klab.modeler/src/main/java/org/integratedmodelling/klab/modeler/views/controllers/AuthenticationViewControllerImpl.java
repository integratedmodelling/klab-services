package org.integratedmodelling.klab.modeler.views.controllers;

import org.integratedmodelling.common.view.AbstractUIViewController;
import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.modeler.views.AuthenticationView;
import org.integratedmodelling.klab.api.view.modeler.views.ServicesView;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.AuthenticationViewController;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.ServicesViewController;

import java.io.File;

public class AuthenticationViewControllerImpl extends AbstractUIViewController<AuthenticationView> implements AuthenticationViewController {

    public AuthenticationViewControllerImpl(UIController controller) {
        super(controller);
    }

    @Override
    public void logoutUser() {

    }

    @Override
    public void installCertificate(File certificateFile) {

    }

    @Override
    public void loginUser(String username, String password) {

    }

    @Override
    public void loginUser(KlabCertificate certificate) {

    }

    @Override
    public void authenticationResult(UserIdentity identity) {

    }
}
