package org.integratedmodelling.klab.modeler.views.controllers;

import org.integratedmodelling.common.view.AbstractUIViewController;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.view.modeler.views.ResourcesNavigator;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.ResourcesNavigatorController;
import org.integratedmodelling.klab.modeler.model.NavigableWorkspace;
import org.integratedmodelling.klab.modeler.model.NavigableWorldview;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class ResourcesNavigatorControllerImpl extends AbstractUIViewController<ResourcesNavigator> implements ResourcesNavigatorController {

    Map<String, NavigableContainer> assetMap = new LinkedHashMap<>();
    ResourcesService currentService;

    public ResourcesNavigatorControllerImpl(UIController controller) {
        super(controller);
    }

    @Override
    public void loadService(ResourcesService.Capabilities capabilities) {
        var service = service(capabilities, ResourcesService.class);
        if (service == null) {
            view().disable();
        } else {
            view().enable();
            getController().storeView(currentService);
            createNavigableAssets(service);
            view().showWorkspaces(new ArrayList<>(assetMap.values()));
        }
    }

    @Override
    public void assetChanged(NavigableAsset asset, ResourceSet changeset) {

    }

    @Override
    public void selectAsset(NavigableAsset asset) {
    }

    @Override
    public void focusAsset(NavigableAsset asset) {

    }

    @Override
    public void removeAsset(NavigableAsset asset) {

    }

    @Override
    public void handleDocumentPositionChange(NavigableDocument document, int position) {

    }

    private void createNavigableAssets(ResourcesService service) {
        assetMap.clear();
        var capabilities = service.capabilities();
        if (capabilities.isWorldviewProvider()) {
            assetMap.put("Worldview", new NavigableWorldview(service.getWorldview()));
        }
        for (var workspaceId : capabilities.getWorkspaceNames()) {
            var workspace = service.resolveWorkspace(workspaceId, getController().user());
            if (workspace != null) {
                assetMap.put(workspaceId, new NavigableWorkspace(workspace));
            }
        }
    }


}
