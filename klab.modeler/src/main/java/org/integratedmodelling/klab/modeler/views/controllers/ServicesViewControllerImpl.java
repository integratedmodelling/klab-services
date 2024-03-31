package org.integratedmodelling.klab.modeler.views.controllers;

import org.integratedmodelling.common.view.AbstractUIViewController;
import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.modeler.views.ServicesView;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.ServicesViewController;

public class ServicesViewControllerImpl extends AbstractUIViewController<ServicesView> implements ServicesViewController {

    public ServicesViewControllerImpl(UIController controller) {
        super(controller);
    }

    @Override
    public void serviceAvailable(KlabService.ServiceCapabilities service) {
        view().notifyService(service, RunningInstance.Status.RUNNING);
    }

    @Override
    public void serviceUnavailable(KlabService.ServiceCapabilities service) {
        view().notifyService(service, RunningInstance.Status.STOPPED);
    }

    @Override
    public void serviceStarting(KlabService.ServiceCapabilities service) {
        view().notifyService(service, RunningInstance.Status.WAITING);
    }

    @Override
    public void serviceStatus(KlabService.ServiceStatus status) {
//        System.out.println(status);
        view().notifyServiceStatus(status);
    }

    @Override
    public void focusService(KlabService service) {
//        dispatch()
    }

}
