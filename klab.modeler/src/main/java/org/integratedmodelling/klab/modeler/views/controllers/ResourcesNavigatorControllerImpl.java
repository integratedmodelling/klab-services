package org.integratedmodelling.klab.modeler.views.controllers;

import org.integratedmodelling.common.view.AbstractUIViewController;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.view.modeler.panels.DocumentEditor;
import org.integratedmodelling.klab.api.view.modeler.panels.controllers.DocumentEditorController;
import org.integratedmodelling.klab.api.view.modeler.views.ResourcesNavigator;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.ResourcesNavigatorController;
import org.integratedmodelling.klab.modeler.model.*;
import org.integratedmodelling.klab.rest.HubNotificationMessage;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class ResourcesNavigatorControllerImpl extends AbstractUIViewController<ResourcesNavigator> implements ResourcesNavigatorController {

    Map<String, NavigableContainer> assetMap = new LinkedHashMap<>();
    ResourcesService currentService;
    NavigableWorkspace currentWorkspace; // we keep it so we can unlock it when switching to another or
    private ArrayList<NavigableContainer> workspaces;
    // exiting

    public ResourcesNavigatorControllerImpl(UIController controller) {
        super(controller);
    }

    @Override
    public void serviceSelected(ResourcesService.Capabilities capabilities) {
        releaseLocks();
        var service = service(capabilities, ResourcesService.class);
        if (service == null) {
            view().disable();
        } else {
            view().enable();
            view().setServiceCapabilities(capabilities);
            getController().storeView(currentService);
            createNavigableAssets(service);
            view().showWorkspaces(new ArrayList<>(assetMap.values()));
        }
    }

    @Override
    public void workspaceModified(ResourceSet changes) {

        if (!changes.isEmpty()) {
            var container = assetMap.get(changes.getWorkspace());
            if (container != null) {
                if (container.mergeChanges(changes, getController().engine().serviceScope())) {
                    view().workspaceModified(container);
                    if (Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER.equals(container.getUrn())) {
                        getController().engine().serviceScope().send(Message.MessageClass.KnowledgeLifecycle, Message.MessageType.WorkspaceChanged, changes);
                    }
                }
            } else {

                // new workspace!
                var service = getController().engine().serviceScope().getService(ResourcesService.class);
                NavigableContainer newContainer = null;
                if (Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER.equals(changes.getWorkspace())) {
                    var worldview = service.getWorldview();
                    if (worldview != null) {
                        newContainer = new NavigableWorldview(worldview);
                    }
                } else {
                    var workspace = service.resolveWorkspace(changes.getWorkspace(), getController().user());
                    if (workspace != null) {
                        newContainer = new NavigableWorkspace(workspace);
                    }
                }

                if (newContainer != null) {
                    assetMap.put(newContainer.getUrn(), newContainer);
                    view().workspaceCreated(newContainer);
                }

            }
        }
    }

    @Override
    public void selectAsset(NavigableAsset asset) {

        if (asset instanceof NavigableDocument document) {
            openPanel(DocumentEditor.class, document);
            // TODO we may want to handle cursor position here on the return value
            getController().configureWorkbench(this, document, true);
        } else if (asset instanceof NavigableWorldview worldview) {
            getController().switchWorkbench(this, worldview);
            view().showResources(worldview);
        } else if (asset instanceof NavigableWorkspace workspace) {
            negotiateLocking(workspace);
            getController().switchWorkbench(this, workspace);
            view().showResources(workspace);
        } else if (asset instanceof NavigableKlabStatement navigableStatement) {
            // double click on statement: if the containing document is not in view, show it; move to the
            // statement
            var document = asset.parent(NavigableDocument.class);
            if (document != null) {
                selectAsset(document);
                var panel = getController().getPanelController(document, DocumentEditorController.class);
                if (panel != null) {
                    panel.moveCaretTo(navigableStatement.getOffsetInDocument());
                }
            }
        }
    }

    private void negotiateLocking(NavigableWorkspace workspace) {
        releaseLocks();
        var service = getController().engine().serviceScope().getService(ResourcesService.class);
        var anythingLocked = false;
        if (service instanceof ResourcesService.Admin admin) {
            for (var asset : workspace.children()) {
                if (asset instanceof NavigableProject project && !project.isLocked()) {
                    // attempt locking
                    var url = admin.lockProject(project.getUrn(), getController().user());
                    if (url != null) {
                        if (url.getProtocol().equals("file")) {
                            var file = new File(url.getFile());
                            if (file.isDirectory()) {
                                project.setLocked(true);
                                project.setRootDirectory(file);
                                anythingLocked = true;
                            }
                        } else {
                            // TODO download contents from zip
                            throw new KlabUnimplementedException("locked project synchronization from " +
                                    "services");
                        }
                    }
                }
            }
        }
        if (anythingLocked) {
            currentWorkspace = workspace;
        }
    }

    private void releaseLocks() {
        if (currentWorkspace != null) {
            var service = getController().engine().serviceScope().getService(ResourcesService.class);
            if (service instanceof ResourcesService.Admin admin) {
                for (var asset : currentWorkspace.children()) {
                    if (asset instanceof NavigableProject project && project.isLocked()) {
                        admin.unlockProject(project.getUrn(), getController().user());
                        ((NavigableProject) asset).setLocked(false);
                        ((NavigableProject) asset).setRootDirectory(null);
                    }
                }
            }
        }
    }

    @Override
    public void focusAsset(NavigableAsset asset) {

        // any info panel should be updated
        view().showAssetInfo(asset);
        if (asset instanceof NavigableDocument document) {

            var panel = getController().getPanelController(document, DocumentEditorController.class);
            if (panel != null) {
                panel.bringForward();
            }

        } else if (asset instanceof NavigableKlabStatement navigableStatement) {
            var document = navigableStatement.parent(NavigableDocument.class);
            if (document != null) {
                var panel = getController().getPanelController(document, DocumentEditorController.class);
                if (panel != null) {
                    panel.bringForward();
                    panel.moveCaretTo(navigableStatement.getOffsetInDocument());
                }
            }
        }
    }

    @Override
    public void removeAsset(NavigableAsset asset) {
        System.out.println("IMPLEMENT ME: remove asset " + asset);
    }

    @Override
    public void handleDocumentPositionChange(NavigableDocument document, Integer position) {
        if (document instanceof NavigableKlabDocument<?, ?> doc) {
            var path = doc.getClosestAsset(position);
            if (path != null && !path.isEmpty()) {
                view().highlightAssetPath(path);
            }
        }
    }

    private void createNavigableAssets(ResourcesService service) {
        assetMap.clear();
        var capabilities = service.capabilities(getController().engine().serviceScope());
        if (capabilities.isWorldviewProvider()) {
            assetMap.put(Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER,
                    new NavigableWorldview(service.getWorldview()));
        }
        for (var workspaceId : capabilities.getWorkspaceNames()) {
            var workspace = service.resolveWorkspace(workspaceId, getController().user());
            if (workspace != null) {
                assetMap.put(workspaceId, new NavigableWorkspace(workspace));
            }
        }

        // record service URL and ID in each root asset's metadata
        for (var asset : assetMap.values()) {
            asset.getMetadata().put(Metadata.KLAB_SERVICE_ID, service.serviceId());
            asset.getMetadata().put(Metadata.KLAB_SERVICE_URL, service.getUrl());
        }
    }

    @Override
    public void shutdown() {
        try {
            releaseLocks();
        } catch (Throwable t) {
            // ignore. May happen during remote service shutdown
        }
        super.shutdown();
    }
}
