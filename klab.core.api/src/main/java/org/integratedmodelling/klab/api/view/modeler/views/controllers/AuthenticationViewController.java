package org.integratedmodelling.klab.api.view.modeler.views.controllers;

import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.ViewController;
import org.integratedmodelling.klab.api.view.annotations.UIActionHandler;
import org.integratedmodelling.klab.api.view.annotations.UIEventHandler;
import org.integratedmodelling.klab.api.view.annotations.UIView;
import org.integratedmodelling.klab.api.view.modeler.views.AuthenticationView;
import org.integratedmodelling.klab.api.view.modeler.views.ServicesView;

import java.io.File;

/**
 * Service chooser at a minimum should give access to every service available in the engine, reflect their
 * state (at least availability or not) and potentially give UI users options to interact more if the services
 * can be locally or remotely administrated. The main UI user action that must be supported is the choice of
 * one service per category as the "focal"/current service for the engine and for other views..
 */
@UIView(value = UIReactor.Type.AuthenticationView)
public interface AuthenticationViewController extends ViewController<AuthenticationView> {

    /**
     * User logs out and become anonymous.
     *
     */
//    @UIActionHandler
    void logoutUser();

    /**
     * User action chooses a new certificate file.
     *
     * @param certificateFile
     */
    //    @UIActionHandler
    void installCertificate(File certificateFile);

    /**
     * Login user through username/password. Does not allow using local services.
     *
     * @param username
     * @param password
     */
    // @UIActionHandler
    void loginUser(String username, String password);

    /**
     * Login user through k.LAB certificate. Gives access to local distribution.
     *
     * @param certificate
     */
    void loginUser(KlabCertificate certificate);

    @UIEventHandler(UIEvent.UserAuthenticated)
    void authenticationResult(UserIdentity identity);


}
