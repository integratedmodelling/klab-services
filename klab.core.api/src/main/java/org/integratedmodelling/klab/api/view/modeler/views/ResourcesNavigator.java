package org.integratedmodelling.klab.api.view.modeler.views;

import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.view.ViewController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIEventHandler;
import org.integratedmodelling.klab.api.view.annotations.UIView;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;

/**
 * The resource navigator is tuned to the current resources service in the current user scope. It should give
 * access to the workspaces published by the service in that scope, including the worldview if one is provided
 * by the service. The documents should be presented in a suitable organization and it should be possible to
 * select them for editing or visualization through a suitable action. If the service is owned by the user and
 * suitably insulated (local), the UI should invoke a {@link org.integratedmodelling.klab.api.view.modeler.panels.DocumentEditor} with write permission upon
 * selection. Otherwise, it may choose a read-only editor or a specialized viewer/explorer (such as
 * {@link org.integratedmodelling.klab.api.view.modeler.panels.KnowledgeEditor}) according to implementation and type of document.
 * <p>
 * The modeler should remember the configuration of documents and editors in the workbench for each workspace
 * and service, and reconstruct the latest configuration at each workspace switch.
 */
@UIView(value = UIReactor.Type.ResourceNavigator, label = "k.LAB Resource Navigator", target =
        ResourcesService.class)
public interface ResourcesNavigator extends ViewController {


    /**
     * Load the passed service in the UI. If the service is null, disable the UI. For changes relative to the
     * current service, do not call this one but call {@link #assetChanged(NavigableKlabAsset, ResourceSet)}.
     *
     * @param service
     */
    @UIEventHandler(UIEvent.ServiceAvailable)
    void loadService(ResourcesService service);

    /**
     * Adapt the UI to any changes in the workspace coming from the service handled. If the workspace is the
     * currently focal one, the changeset should produce UI changes. The asset could be the entire workspace,
     * a project, a document, a resource, a  or an internal element within a document.
     *
     * @param asset     the asset that has changed.
     * @param changeset the detail of the changes (will trigger reload)
     */
    @UIEventHandler(UIEvent.AssetChanged)
    void assetChanged(NavigableAsset asset, ResourceSet changeset);

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

    /**
     * The user has changed its current editing position within a document. The navigator should ensure that
     * the document is selected and adjust its UI to show the element under the caret.
     *
     * @param document
     * @param position
     */
    @UIEventHandler(UIEvent.DocumentPositionChanged)
    void handleDocumentPositionChange(NavigableDocument document, int position);
}
