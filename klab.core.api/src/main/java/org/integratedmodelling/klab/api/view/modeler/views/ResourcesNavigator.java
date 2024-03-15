package org.integratedmodelling.klab.api.view.modeler.views;

import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.view.View;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIActionHandler;
import org.integratedmodelling.klab.api.view.annotations.UIEventHandler;
import org.integratedmodelling.klab.api.view.annotations.UIView;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.view.modeler.panels.DocumentEditor;
import org.integratedmodelling.klab.api.view.modeler.panels.KnowledgeEditor;

/**
 * The resource navigator is tuned to the current resources service in the current user scope. It should give
 * access to the workspaces published by the service in that scope, including the worldview if one is provided
 * by the service. The documents should be presented in a suitable organization and it should be possible to
 * select them for editing or visualization through a suitable action. If the service is owned by the user and
 * suitably insulated (local), the UI should invoke a
 * {@link DocumentEditor} with write permission upon
 * selection. Otherwise, it may choose a read-only editor or a specialized viewer/explorer (such as
 * {@link KnowledgeEditor}) according to implementation and
 * type of document.
 * <p>
 * The modeler should remember the configuration of documents and editors in the workbench for each workspace
 * and service, and reconstruct the latest configuration at each workspace switch.
 */
@UIView(value = UIReactor.Type.ResourceNavigator, label = "k.LAB Resource Navigator")
public interface ResourcesNavigator extends View {

    @UIActionHandler(value = UIAction.ImportProject, label = "New project", tooltip = "Create a new k" +
            ".LAB project")
    default void importProject(String projectUrl) {
        getModeler().dispatch(this, UIEvent.ImportProjectRequest, projectUrl);
    }

    @UIActionHandler(value = UIAction.NewProject, label = "New project", tooltip = "Create a new k" +
            ".LAB project")
    default void createProject(String projectUrn) {
        getModeler().dispatch(this, UIEvent.NewProjectRequest, projectUrn);
    }

    @UIActionHandler(value = UIAction.DocumentSelect, label = "", tooltip = "")
    default void selectDocument(NavigableDocument document) {
        getModeler().dispatch(this, UIEvent.DocumentSelected, document);
    }

    /**
     * Switch the workspace to one known to the default resources service in the current user scope. If the
     * worldview should come into view (with access to the knowledge editor instead of the document editor),
     * pass {@link Worldview#WORLDVIEW_WORKSPACE_IDENTIFIER} as the workspace URN.
     *
     * @param workspaceUrn
     */
    @UIActionHandler(value = UIAction.SwitchWorkspace, label = "Switch workspace", tooltip = "Switch the " +
            "workspace in use for the current service")
    default void switchWorkspace(String workspaceUrn) {
        var service = getModeler().user().getService(ResourcesService.class);
        if (service != null) {
            if (Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER.equals(workspaceUrn)) {
                var worldview = service.getWorldview();
                if (worldview != null) {
                    getModeler().dispatch(this, UIEvent.WorldviewSelected, worldview);
                }
            } else {
                var workspace = service.resolveWorkspace(workspaceUrn, getModeler().user());
                if (workspace != null) {
                    getModeler().dispatch(this, UIEvent.WorkspaceSelected, workspace);
                }
            }
        }
    }


    /**
     * Receive the "default" service capabilities. After calling this, the modeler will select the current
     * workspace and send UIEvent#WorkspaceSelected.
     *
     * @param capabilities
     */
    @UIEventHandler(UIEvent.ServiceAvailable)
    void handleServiceAvailable(ResourcesService.Capabilities capabilities);

    @UIEventHandler(UIEvent.WorkspaceSelected)
    void handleWorkspaceSelection(Workspace workspace);

    @UIEventHandler(UIEvent.WorldviewSelected)
    void handleWorldviewSelection(Worldview workspace);

    @UIEventHandler(UIEvent.WorkspaceChanged)
    void handleWorkspaceChange(ResourceSet resourceSet);

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
