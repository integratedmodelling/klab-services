package org.integratedmodelling.klab.api.view.modeler.views;

import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.view.View;

public interface ServicesView extends View {

    void notifyService(KlabService.ServiceCapabilities service, RunningInstance.Status status);

    void notifyServiceStatus(KlabService.ServiceStatus status);
}
