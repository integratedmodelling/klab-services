package org.integratedmodelling.klab.api.view.modeler.views.controllers;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.view.ViewController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIEventHandler;
import org.integratedmodelling.klab.api.view.annotations.UIViewController;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.view.modeler.panels.controllers.DocumentEditorController;
import org.integratedmodelling.klab.api.view.modeler.panels.controllers.KnowledgeEditorController;
import org.integratedmodelling.klab.api.view.modeler.views.ResourcesNavigator;

/**
 * The resource navigator is tuned to the current resources service in the current user scope. It should give
 * access to the workspaces published by the service in that scope, including the worldview if one is provided
 * by the service. The documents should be presented in a suitable organization and it should be possible to
 * select them for editing or visualization through a suitable action. If the service is owned by the user and
 * suitably insulated (local), the UI should invoke a {@link DocumentEditorController} with write permission
 * upon selection. Otherwise, it may choose a read-only editor or a specialized viewer/explorer (such as
 * {@link KnowledgeEditorController}) according to implementation and type of document.
 * <p>
 * The modeler should remember the configuration of documents and editors in the workbench for each workspace
 * and service, and reconstruct the latest configuration at each workspace switch.
 */
@UIViewController(value = UIReactor.Type.ResourceNavigator, viewType = ResourcesNavigator.class, label = "k" +
        ".LAB " +
        "Resource Navigator", target = ResourcesService.class)
public interface ResourcesNavigatorController extends ViewController<ResourcesNavigator> {

    /**
     * Invoked when a workspace needs to be reloaded in the UI. The workspace may or may not be the one
     * currently shown.
     *
     * @param changes
     */
    @UIEventHandler(UIReactor.UIEvent.WorkspaceModified)
    void workspaceModified(ResourceSet changes);

    @UIEventHandler(UIEvent.EngineStatusChanged)
    void engineStatusChanged(Engine.Status status);

    /**
     * "SELECT" class events are double-clicks - bring the target to the forefront.
     *
     * @param asset never null
     */
    @UIEventHandler(UIEvent.AssetSelected)
    void selectAsset(NavigableAsset asset);

    /**
     * "FOCUS" class events are just single click and may or may not produce UI changes but do identify an
     * element as the current focal one for its class. A focal element remains focal in the view until
     * changed. Focus on null means remove any current focus.
     * <p>
     * The focal object should be dispatched to any listeners so they can adapt.
     *
     * @param asset Can be a {@link Workspace}, a
     *              {@link org.integratedmodelling.klab.api.knowledge.Worldview}, a concrete subclass of
     *              {@link org.integratedmodelling.klab.api.lang.kim.KlabDocument} or one of the
     *              {@link org.integratedmodelling.klab.api.knowledge.KlabAsset}s included in a document.
     */
    @UIEventHandler(UIEvent.AssetFocused)
    void focusAsset(NavigableAsset asset);

    @UIEventHandler(UIEvent.AssetDeleteRequest)
    void removeAsset(NavigableAsset asset);

    @UIEventHandler(UIEvent.LogicalValidation)
    void resourcesValidated(ResourceSet notifications);

    /**
     * The user has changed its current editing position within a document. The navigator should ensure that
     * the document is selected and adjust its UI to show the element under the caret.
     *
     * FIXME change the Integer argument to int when the argument matcher becomes smarter.
     *
     * @param document
     * @param position
     */
    @UIEventHandler(UIEvent.DocumentPositionChanged)
    void handleDocumentPositionChange(NavigableDocument document, Integer position);
}
