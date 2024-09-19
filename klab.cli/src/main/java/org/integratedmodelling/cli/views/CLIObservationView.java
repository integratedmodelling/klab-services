package org.integratedmodelling.cli.views;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.view.modeler.views.ContextView;

public class CLIObservationView extends CLIView implements ContextView {

//    @Override
//    public void setServiceCapabilities(RuntimeService.Capabilities capabilities) {
//
//    }

    @Override
    public void engineStatusChanged(Engine.Status status) {

    }
}
