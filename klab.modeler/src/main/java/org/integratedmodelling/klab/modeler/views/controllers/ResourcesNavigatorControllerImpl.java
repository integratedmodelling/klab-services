package org.integratedmodelling.klab.modeler.views.controllers;

import org.integratedmodelling.common.view.AbstractUIViewController;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
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

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class ResourcesNavigatorControllerImpl extends AbstractUIViewController<ResourcesNavigator> implements ResourcesNavigatorController {

    Map<String, NavigableContainer> assetMap = new LinkedHashMap<>();
    ResourcesService currentService;
    NavigableWorkspace currentWorkspace; // we keep it so we can unlock it when switching to another or

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

        NavigableContainer container;

        if (!changes.isEmpty()) {
            container = assetMap.get(changes.getWorkspace());
            if (container != null) {
                if (container.mergeChanges(changes, getController().engine().serviceScope())) {
                    if (!changes.getObservationStrategies().isEmpty() || !changes.getOntologies().isEmpty()) {
                        // send resource set to reasoner to update the knowledge if there are relevant changes
                        var reasoner = getController().user().getService(Reasoner.class);
                        // do not send logical changes if the workspace is the worldview, which is read-only
                        if (reasoner.isExclusive() && reasoner instanceof Reasoner.Admin adminReasoner && !Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER.equals(changes.getWorkspace())) {
                            var logicalChanges = adminReasoner.updateKnowledge(changes,getController().user());
                            if (!logicalChanges.isEmpty()) {
                                getController().dispatch(this, UIEvent.LogicalValidation, logicalChanges);
                            }
                        }
                    }

                    view().workspaceModified(container);
                    if (Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER.equals(container.getUrn())) {
                        getController().engine().serviceScope().send(Message.MessageClass.KnowledgeLifecycle, Message.MessageType.WorkspaceChanged, changes);
                    }
                }

            } else {

                // new workspace!
                var service = getController().engine().serviceScope().getService(ResourcesService.class);
                if (Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER.equals(changes.getWorkspace())) {
                    var worldview = service.getWorldview();
                    if (worldview != null) {
                        container = new NavigableWorldview(worldview);
                    }
                } else {
                    var workspace = service.resolveWorkspace(changes.getWorkspace(), getController().user());
                    if (workspace != null) {
                        container = new NavigableWorkspace(workspace);
                    }
                }

                if (container != null) {
                    assetMap.put(container.getUrn(), container);
                    view().workspaceCreated(container);
                }
            }

            if (container != null) {
                // reopen any editors currently open on documents contained in here.
                for (var editorController : getController().getOpenPanels(DocumentEditorController.class)) {

                    var document = container.findAsset(editorController.getPayload().getUrn(),
                            NavigableDocument.class,
                            // TODO add all other documents
                            KlabAsset.KnowledgeClass.ONTOLOGY, KlabAsset.KnowledgeClass.NAMESPACE,
                            KlabAsset.KnowledgeClass.OBSERVATION_STRATEGY_DOCUMENT,
                            KlabAsset.KnowledgeClass.BEHAVIOR,
                            KlabAsset.KnowledgeClass.APPLICATION, KlabAsset.KnowledgeClass.COMPONENT,
                            KlabAsset.KnowledgeClass.TESTCASE, KlabAsset.KnowledgeClass.RESOURCE);

                    if (document != null && editorController.getPayload().root().getUrn().equals(container.getUrn())) {
                        editorController.reload(document);
                    }
                }
            }
        }

    }

    @Override
    public void engineStatusChanged(Engine.Status status) {
        view().engineStatusChanged(status);
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
        } else if (asset instanceof NavigableKlabStatement<?> navigableStatement) {
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
                        project.setLocked(false);
                        project.setRootDirectory(null);
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

        } else if (asset instanceof NavigableKlabStatement<?> navigableStatement) {
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
    public void resourcesValidated(ResourceSet notifications) {
        /*
         * The asset map doesn't change
         * TODO we could ingest the notifications into the assets
         */
        for (var asset : assetMap.values()) {
            if (asset.mergeChanges(notifications, getController().engine().serviceScope())) {
                view().resetValidationNotifications(asset);
            }
        }
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
