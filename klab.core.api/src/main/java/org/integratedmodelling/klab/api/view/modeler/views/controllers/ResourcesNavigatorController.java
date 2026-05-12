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
 * The resource navigator is tuned to the current resources service in the current user scope. It
 * should give access to the workspaces published by the service in that scope, including the
 * worldview if one is provided by the service. The documents should be presented in a suitable
 * organization and it should be possible to select them for editing or visualization through a
 * suitable action. If the service is owned by the user and suitably insulated (local), the UI
 * should invoke a {@link DocumentEditorController} with write permission upon selection. Otherwise,
 * it may choose a read-only editor or a specialized viewer/explorer (such as {@link
 * KnowledgeEditorController}) according to implementation and type of document.
 *
 * <p>The modeler should remember the configuration of documents and editors in the workbench for
 * each workspace and service, and reconstruct the latest configuration at each workspace switch.
 */
@UIViewController(
    value = UIReactor.Type.ResourceNavigator,
    viewType = ResourcesNavigator.class,
    label = "k.LAB Resource Navigator",
    target = ResourcesService.class)
public interface ResourcesNavigatorController extends ViewController<ResourcesNavigator> {

  /**
   * Invoked when a workspace needs to be reloaded in the UI. The workspace may or may not be the
   * one currently shown.
   *
   * @param changes
   */
  @UIEventHandler(UIReactor.UIEvent.WorkspaceModified)
  void workspaceModified(ResourceSet changes);

  @UIEventHandler(UIEvent.EngineStatusChanged)
  void engineStatusChanged(Engine.Status status);

  boolean negotiateLocking(Workspace workspace);

  void releaseLock(Workspace currentWorkspace);

}
