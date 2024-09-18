package org.integratedmodelling.klab.modeler.views.controllers;

import org.integratedmodelling.common.view.AbstractUIViewController;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.modeler.views.ServicesView;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.ServicesViewController;

/**
 * We need no storage because the service status is the one in the engine.
 */
public class ServicesViewControllerImpl extends AbstractUIViewController<ServicesView> implements ServicesViewController {

    public ServicesViewControllerImpl(UIController controller) {
        super(controller);
    }

//    @Override
//    public void serviceAvailable(KlabService.ServiceCapabilities service) {
//        // TODO add to engine, select if first or configured default
//        view().servicesConfigurationChanged(service, RunningInstance.Status.RUNNING);
//    }
//
//    @Override
//    public void serviceUnavailable(KlabService.ServiceCapabilities service) {
//        // TODO add to engine, select if first or configured default
//        view().servicesConfigurationChanged(service, RunningInstance.Status.STOPPED);
//    }
//
//    @Override
//    public void serviceStarting(KlabService.ServiceCapabilities service) {
//        // TODO add to engine, select if first or configured default
//        view().servicesConfigurationChanged(service, RunningInstance.Status.WAITING);
//    }
//
//    @Override
//    public void serviceStatus(KlabService.ServiceStatus status) {
//        // TODO add to engine, select if first or configured default
//        view().notifyServiceStatus(status);
//    }
//
//    @Override
//    public void reasoningAvailable(Reasoner.Capabilities capabilities) {
//        view().reasoningAvailable(capabilities);
//    }

    @Override
    public void engineStatusChanged(Engine.Status status) {
        // TODO all the above as needed
    }

    @Override
    public void selectService(KlabService.ServiceCapabilities service) {
        // set the engine's current service. TODO call actions only if the service has changed (return
        //  a boolean). Currently not working with this logic as the current service may not have been
        /// notified.
        getController().setDefaultService(service);
        getController().dispatch(this, UIEvent.ServiceSelected, service);
    }

    @Override
    public void focusService(KlabService.ServiceCapabilities service) {
        // inform all other views
        getController().dispatch(this, UIEvent.ServiceFocused, service);
    }

}
