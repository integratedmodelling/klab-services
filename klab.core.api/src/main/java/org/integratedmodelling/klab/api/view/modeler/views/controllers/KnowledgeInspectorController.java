package org.integratedmodelling.klab.api.view.modeler.views.controllers;

import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.ViewController;
import org.integratedmodelling.klab.api.view.annotations.UIEventHandler;
import org.integratedmodelling.klab.api.view.annotations.UIViewController;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.view.modeler.views.KnowledgeInspector;

@UIViewController(value = UIReactor.Type.KnowledgeInspector, viewType = KnowledgeInspector.class)
public interface KnowledgeInspectorController extends ViewController<KnowledgeInspector> {
    /**
     * View selected a resource for display.
     *
     * @param resource
     */
    @UIEventHandler(UIReactor.UIEvent.ResourceFocused)
    void focusResource(Resource resource);

    /**
     * User clicked on a document, possibly highlighting a specific statement for display. The selected one is
     * the last in the path.
     */
    @UIEventHandler(UIReactor.UIEvent.DocumentPositionChanged)
    void focusDocument(NavigableDocument document, Integer position);

    /**
     * View selected a service for display
     *
     * @param capabilities
     */
    @UIEventHandler(UIReactor.UIEvent.ServiceFocused)
    void focusService(KlabService.ServiceCapabilities capabilities);

}
