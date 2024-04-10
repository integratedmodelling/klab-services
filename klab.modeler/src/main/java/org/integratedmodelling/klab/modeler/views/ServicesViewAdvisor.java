package org.integratedmodelling.klab.modeler.views;

import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.view.modeler.views.ServicesView;

/**
 * TODO implement data model to replicate the actions when the Eclipse modeler is done.
 */
public class ServicesViewAdvisor extends BaseViewAdvisor implements ServicesView {

    @Override
    public void servicesConfigurationChanged(KlabService.ServiceCapabilities service,
                                             RunningInstance.Status running) {

    }

    @Override
    public void notifyServiceStatus(KlabService.ServiceStatus status) {

    }

    @Override
    public void reasoningAvailable(Reasoner.Capabilities reasonerCapabilities) {

    }

}
