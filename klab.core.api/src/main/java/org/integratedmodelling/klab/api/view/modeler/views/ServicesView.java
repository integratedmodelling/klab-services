package org.integratedmodelling.klab.api.view.modeler.views;

import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.view.View;

public interface ServicesView extends View {

    /**
     * Called after a change in service configuration has been made, passing the service that has changed
     * status and the new running status for it. The UI should list all the services available in the engine
     * and show the status of the current one(s) per each monitored category. If there wasn't a current
     * service at the time of the call, the view should call
     * {@link
     * org.integratedmodelling.klab.api.view.modeler.views.controllers.ServicesViewController#focusService(KlabService.ServiceCapabilities)}
     * as if the user had selected the service, so that the current service in the engine is guaranteed in
     * sync with what is shown in the view.
     */
    void servicesConfigurationChanged(KlabService.ServiceCapabilities service, RunningInstance.Status running);

    /**
     * Called when any of the services in the engine scope reports its status. The view should react by
     * updating the monitored status, if any. This may come at frequent, regular intervals.
     *
     * @param status
     */
    void notifyServiceStatus(KlabService.ServiceStatus status);

}
