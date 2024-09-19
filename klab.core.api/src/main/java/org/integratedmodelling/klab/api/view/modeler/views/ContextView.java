package org.integratedmodelling.klab.api.view.modeler.views;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.view.View;

public interface ContextView extends View {

//    void setServiceCapabilities(RuntimeService.Capabilities capabilities);

    void engineStatusChanged(Engine.Status status);
}
