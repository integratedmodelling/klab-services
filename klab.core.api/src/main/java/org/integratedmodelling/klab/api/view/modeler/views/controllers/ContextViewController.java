package org.integratedmodelling.klab.api.view.modeler.views.controllers;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.view.ViewController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIEventHandler;
import org.integratedmodelling.klab.api.view.annotations.UIViewController;
import org.integratedmodelling.klab.api.view.modeler.views.ContextView;

@UIViewController(value = UIReactor.Type.ContextView, viewType = ContextView.class)
public interface ContextViewController extends ViewController<ContextView> {

//    @UIEventHandler(UIEvent.ServiceSelected)
//    void setServiceCapabilities(RuntimeService.Capabilities capabilities);

    /**
     * Called when other views put semantics or a model into focus. May pre-select it for observation or do
     * nothing.
     *
     * @param observable can only be {@link Semantics},
     *                   {@link org.integratedmodelling.klab.api.knowledge.Model} or
     *                   {@link org.integratedmodelling.klab.api.knowledge.Resource}.
     */
    void knowledgeFocused(Knowledge observable);

    /**
     * Called when knowledge is selected for observation, through other views, API action or drag/drop. Should
     * trigger observation of the knowledge.
     *
     * @param observable can only be {@link Semantics},
     *                   {@link org.integratedmodelling.klab.api.knowledge.Model} or
     *                   {@link org.integratedmodelling.klab.api.knowledge.Resource}.
     */
    void knowledgeSelected(Knowledge observable);

    @UIEventHandler(UIEvent.EngineStatusChanged)
    void engineStatusChanged(Engine.Status status);
}
