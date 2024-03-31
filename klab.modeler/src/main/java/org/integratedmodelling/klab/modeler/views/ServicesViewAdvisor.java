package org.integratedmodelling.klab.modeler.views;

import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.view.modeler.views.ServicesView;

public class ServicesViewAdvisor extends BaseViewAdvisor implements ServicesView {

    @Override
    public void notifyService(KlabService.ServiceCapabilities service, RunningInstance.Status status) {

    }

    @Override
    public void notifyServiceStatus(KlabService.ServiceStatus status) {

    }
}
