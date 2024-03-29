package org.integratedmodelling.klab.api.view.modeler.views.controllers;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.ViewController;
import org.integratedmodelling.klab.api.view.annotations.UIActionHandler;
import org.integratedmodelling.klab.api.view.annotations.UIEventHandler;
import org.integratedmodelling.klab.api.view.annotations.UIView;
import org.integratedmodelling.klab.api.view.modeler.views.ServicesView;

/**
 * Service chooser at a minimum should give access to every service available in the engine, reflect their
 * state (at least availability or not) and potentially give UI users options to interact more if the services
 * can be locally or remotely administrated. The main UI user action that must be supported is the choice of
 * one service per category as the "focal"/current service for the engine and for other views..
 */
@UIView(value = UIReactor.Type.ServiceChooser, target = Engine.class)
public interface ServicesViewController extends ViewController<ServicesView> {

    /**
     * Service exists - may be on, off or in error. If no other services are available for its type, the view
     * should focus it unless there are configuration options to that extent.
     *
     * @param service
     */
    @UIEventHandler
    void serviceAvailable(KlabService service);

    /**
     * User action choosing a service to focus on. React and dispatch.
     *
     * @param service
     */
    @UIActionHandler(UIReactor.UIAction.SelectService)
    void focusService(KlabService service);

    /**
     * Will be called at least once after {@link #serviceAvailable(KlabService)} to report on service status,
     * and possibly at regular intervals depending on the engine implementation. This may make the service
     * available, unavailable, busy, and report on current load factor and state.
     *
     * @param service
     * @param status
     */
    @UIEventHandler
    void statusAvailable(KlabService service, KlabService.ServiceStatus status);


}
