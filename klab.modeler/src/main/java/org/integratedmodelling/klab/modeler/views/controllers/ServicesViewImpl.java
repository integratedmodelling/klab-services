package org.integratedmodelling.klab.modeler.views.controllers;

import org.integratedmodelling.common.view.AbstractUIViewController;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.modeler.views.ServicesView;
import org.integratedmodelling.klab.api.view.modeler.views.ServicesViewController;

public class ServicesViewImpl extends AbstractUIViewController<ServicesView> implements ServicesViewController {

    public ServicesViewImpl(UIController controller) {
        super(controller);
    }

    @Override
    public void serviceAvailable(KlabService service) {

    }

    @Override
    public void focusService(KlabService service) {

    }

    @Override
    public void statusAvailable(KlabService service, KlabService.ServiceStatus status) {

    }
}
