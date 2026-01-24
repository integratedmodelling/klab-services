package org.integratedmodelling.klab.modeler.views.controllers;

import java.util.HashMap;
import java.util.Map;
import org.integratedmodelling.common.view.AbstractUIViewController;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.modeler.views.ResourcesNavigator;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.ResourcesNavigatorController;
import org.integratedmodelling.klab.modeler.model.*;

public class ResourcesNavigatorControllerImpl extends AbstractUIViewController<ResourcesNavigator>
    implements ResourcesNavigatorController {

  Map<String, Workspace> lockedWorkspaces = new HashMap<>();
  ResourcesService currentService;

  //  NavigableWorkspace
  //      currentWorkspace; // we keep it so we can unlock it when switching to another or

  public ResourcesNavigatorControllerImpl(UIController controller) {
    super(controller);
  }

  @Override
  public void workspaceModified(ResourceSet changes) {

    if (changes.getWorkspace() != null
        && changes.getWorkspace().equals(Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER)) {
      // TODO there should be a worldview controller and it should also intercept this message,
      //  reacting only to this
      return;
    }

    //    NavigableContainer container;
    //
    //    if (!changes.isEmpty()) {
    //      container = assetMap.get(changes.getWorkspace());
    //      if (container != null) {
    //        if (!container.mergeChanges(changes, getController().engine().getOwner()).isEmpty()) {
    //          if (!changes.getObservationStrategies().isEmpty() ||
    // !changes.getOntologies().isEmpty()) {
    //            // send resource set to reasoner to update the knowledge if there are relevant
    // changes
    //            var reasoner = getController().user().getService(Reasoner.class);
    //            // do not send logical changes if the workspace is the worldview, which is
    // read-only
    //            if (reasoner instanceof Reasoner.Admin adminReasoner
    //                && !Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER.equals(changes.getWorkspace())) {
    //              var logicalChanges = adminReasoner.updateKnowledge(changes,
    // getController().user());
    //              if (!logicalChanges.isEmpty()) {
    //                getController().dispatch(this, UIEvent.LogicalValidation, logicalChanges);
    //              }
    //            }
    //          }
    //

    var workspace = view().getVisualizedWorkspace(changes.getWorkspace());
    if (workspace != null) {
      var changedAssets = workspace.mergeChanges(changes, getController().engine().getOwner());
      if (!changedAssets.isEmpty()) {
        // FIXME this should be done directly in the worldview controller, which should react
        //        if (!changes.getObservationStrategies().isEmpty() ||
        // !changes.getOntologies().isEmpty()) {
        //          // send resource set to reasoner to update the knowledge if there are relevant
        // changes
        //          var reasoner = getController().user().getService(Reasoner.class);
        //          // do not send logical changes if the workspace is the worldview, which is
        // read-only
        //          if (reasoner instanceof Reasoner.Admin adminReasoner
        //              && !Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER.equals(changes.getWorkspace()))
        // {
        //            var logicalChanges = adminReasoner.updateKnowledge(changes,
        // getController().user());
        //            if (!logicalChanges.isEmpty()) {
        //              getController().dispatch(this, UIEvent.LogicalValidation, logicalChanges);
        //            }
        //          }
        //        }
        view().workspaceModified(workspace, changes, changedAssets);
      }
    }
    //          if (Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER.equals(container.getUrn())) {
    //            getController()
    //                .engine()
    //                .getOwner()
    //                .send(
    //                    Message.MessageClass.KnowledgeLifecycle,
    //                    Message.MessageType.WorkspaceChanged,
    //                    changes);
    //          }
    //        }
    //
    //      } else {
    //
    //        // new workspace!
    //        var service = getController().engine().getOwner().getService(ResourcesService.class);
    //        if (Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER.equals(changes.getWorkspace())) {
    //          var worldview = service.retrieveWorldview();
    //          if (worldview != null) {
    //            container = new NavigableWorldview(worldview);
    //          }
    //        } else {
    //          var workspace = service.retrieveWorkspace(changes.getWorkspace(),
    // getController().user());
    //          if (workspace != null) {
    //            container = new NavigableWorkspace(workspace);
    //          }
    //        }
    //
    //        if (container != null) {
    //          assetMap.put(container.getUrn(), container);
    //          view().workspaceCreated(container);
    //        }
    //      }

    //      if (container != null) {
    //        // reopen any editors currently open on documents contained in here.
    //        for (var editorController :
    // getController().getOpenPanels(DocumentEditorController.class)) {
    //
    //          var document =
    //              container.findAsset(
    //                  editorController.getPayload().getUrn(),
    //                  NavigableDocument.class,
    //                  // TODO add all other documents
    //                  KlabAsset.KnowledgeClass.ONTOLOGY,
    //                  KlabAsset.KnowledgeClass.NAMESPACE,
    //                  KlabAsset.KnowledgeClass.OBSERVATION_STRATEGY_DOCUMENT,
    //                  KlabAsset.KnowledgeClass.BEHAVIOR,
    //                  KlabAsset.KnowledgeClass.APPLICATION,
    //                  KlabAsset.KnowledgeClass.COMPONENT,
    //                  KlabAsset.KnowledgeClass.TESTCASE,
    //                  KlabAsset.KnowledgeClass.RESOURCE);
    //
    //          if (document != null
    //              && editorController.getPayload().root().getUrn().equals(container.getUrn())) {
    //            editorController.reload(document);
    //          }
    //        }
    //      }
    //    }
  }

  @Override
  public void engineStatusChanged(Engine.Status status) {
    // TODO on startup should check all available workspaces. On change to services that own
    // workspaces, should disable
    //  panels or view if any.
    // Could maintain locked status and handle locking/unlocking of workspaces as well as read-only
    // status based on locking, permissions and errors
    view().engineStatusChanged(status);
  }

  //  @Override
  //  public void selectAsset(NavigableAsset asset) {
  //
  //    if (asset instanceof NavigableDocument document) {
  //      openPanel(DocumentEditor.class, document);
  //      // TODO we may want to handle cursor position here on the return value
  //      getController().configureWorkbench(this, document, true);
  //    } else if (asset instanceof NavigableWorldview worldview) {
  //      getController().switchWorkbench(this, worldview);
  //      view().showResources(worldview);
  //    } else if (asset instanceof NavigableWorkspace workspace) {
  //      negotiateLocking(workspace);
  //      getController().switchWorkbench(this, workspace);
  //      view().showResources(workspace);
  //    } else if (asset instanceof NavigableKlabStatement<?> navigableStatement) {
  //      // double click on statement: if the containing document is not in view, show it; move to
  // the
  //      // statement
  //      var document = asset.parent(NavigableDocument.class);
  //      if (document != null) {
  //        selectAsset(document);
  //        var panel = getController().getPanelController(document,
  // DocumentEditorController.class);
  //        if (panel != null) {
  //          panel.moveCaretTo(navigableStatement.getOffsetInDocument());
  //        }
  //      }
  //    }
  //  }

  @Override
  public boolean negotiateLocking(Workspace workspace) {
    releaseLock(workspace);
    var service = getController().engine().getOwner().getService(ResourcesService.class);
    var anythingLocked = false;
    if (workspace instanceof NavigableWorkspace navigableWorkspace) {
      for (var asset : navigableWorkspace.children()) {
        if (asset instanceof NavigableProject project && !project.isLocked()) {
          // attempt locking
          anythingLocked = service.lockProject(project.getUrn(), getController().user());
          //          if (url != null) {
          //            if (url.getProtocol().equals("file")) {
          //              var file = new File(url.getFile());
          //              if (file.isDirectory()) {
          //                project.setLocked(true);
          //                project.setRootDirectory(file);
          //                anythingLocked = true;
          //              }
          //            } else {
          //              // TODO download contents from zip
          //              throw new KlabUnimplementedException(
          //                  "locked project synchronization from " + "services");
          //            }
          //          }
        }
      }
    }
    if (anythingLocked) {
      lockedWorkspaces.put(workspace.getUrn(), workspace);
    }

    return anythingLocked;
  }

  @Override
  public void releaseLock(Workspace workspace) {
    if (workspace != null) {
      var service = getController().engine().getOwner().getService(ResourcesService.class);
      if (workspace instanceof NavigableWorkspace navigableWorkspace) {
        for (var asset : navigableWorkspace.children()) {
          if (asset instanceof NavigableProject project && project.isLocked()) {
            service.unlockProject(project.getUrn(), getController().user());
            project.setLocked(false);
            project.setRootDirectory(null);
          }
        }
        lockedWorkspaces.remove(workspace.getUrn());
      }
    }
  }

  //  @Override
  //  public void focusAsset(NavigableAsset asset) {
  //
  //    // any info panel should be updated
  //    view().showAssetInfo(asset);
  //    if (asset instanceof NavigableDocument document) {
  //
  //      var panel = getController().getPanelController(document, DocumentEditorController.class);
  //      if (panel != null) {
  //        panel.bringForward();
  //      }
  //
  //    } else if (asset instanceof NavigableKlabStatement<?> navigableStatement) {
  //      var document = navigableStatement.parent(NavigableDocument.class);
  //      if (document != null) {
  //        var panel = getController().getPanelController(document,
  // DocumentEditorController.class);
  //        if (panel != null) {
  //          panel.bringForward();
  //          panel.moveCaretTo(navigableStatement.getOffsetInDocument());
  //        }
  //      }
  //    }
  //  }

  //  @Override
  //  public void removeAsset(NavigableAsset asset) {
  //    System.out.println("IMPLEMENT ME: remove asset " + asset);
  //  }
  //
  //  @Override
  //  public void resourcesValidated(ResourceSet notifications) {
  //    /*
  //     * The asset map doesn't change
  //     * TODO we could ingest the notifications into the assets
  //     */
  //    for (var asset : assetMap.values()) {
  //      if (!asset.mergeChanges(notifications, getController().engine().getOwner()).isEmpty()) {
  //        view().resetValidationNotifications(asset);
  //      }
  //    }
  //  }

  //  @Override
  //  public void handleDocumentPositionChange(NavigableDocument document, Integer position) {
  //    if (document instanceof NavigableKlabDocument<?, ?> doc) {
  //      var path = doc.getClosestAsset(position);
  //      if (path != null && !path.isEmpty()) {
  //        view().highlightAssetPath(path);
  //      }
  //    }
  //  }

  //  private void createNavigableAssets(ResourcesService service) {
  //    assetMap.clear();
  //    var capabilities = service.capabilities(getController().engine().getOwner());
  //    if (capabilities.isWorldviewProvider()) {
  //      assetMap.put(
  //          Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER,
  //          new NavigableWorldview(service.retrieveWorldview()));
  //    }
  //    for (var workspaceId : capabilities.getWorkspaceNames()) {
  //      var workspace = service.retrieveWorkspace(workspaceId, getController().user());
  //      if (workspace != null) {
  //        assetMap.put(workspaceId, new NavigableWorkspace(workspace));
  //      }
  //    }
  //
  //  }

  @Override
  public void shutdown() {
    try {
      lockedWorkspaces.forEach((k, v) -> releaseLock(v));
      lockedWorkspaces.clear();
    } catch (Throwable t) {
      // ignore. May happen during remote service shutdown
    }
    super.shutdown();
  }
}
