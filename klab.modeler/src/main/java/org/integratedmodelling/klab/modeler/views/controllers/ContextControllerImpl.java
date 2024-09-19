package org.integratedmodelling.klab.modeler.views.controllers;

import org.integratedmodelling.common.view.AbstractUIViewController;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.modeler.views.ContextInspector;
import org.integratedmodelling.klab.api.view.modeler.views.ContextView;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.ContextInspectorController;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.ContextViewController;

public class ContextControllerImpl extends AbstractUIViewController<ContextView> implements ContextViewController {

    public ContextControllerImpl(UIController controller) {
        super(controller);
    }

//    @Override
//    public void setServiceCapabilities(RuntimeService.Capabilities capabilities) {
//        view().setServiceCapabilities(capabilities);
//    }

    @Override
    public void knowledgeFocused(Knowledge observable) {

    }

    @Override
    public void knowledgeSelected(Knowledge observable) {
    }

    @Override
    public void engineStatusChanged(Engine.Status status) {
        view().engineStatusChanged(status);
    }
}
