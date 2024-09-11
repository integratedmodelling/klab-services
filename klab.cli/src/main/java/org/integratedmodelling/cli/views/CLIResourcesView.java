package org.integratedmodelling.cli.views;

import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
import org.integratedmodelling.klab.api.view.modeler.views.ResourcesNavigator;

import java.util.List;

public class CLIResourcesView extends CLIView implements ResourcesNavigator {

    @Override
    public void showWorkspaces(List<NavigableContainer> workspaces) {

    }

    @Override
    public void showResources(NavigableContainer workspace) {

    }

    @Override
    public void workspaceModified(NavigableContainer changedContainer) {

    }

    @Override
    public void showAssetInfo(NavigableAsset asset) {

    }

    @Override
    public void highlightAssetPath(List<NavigableAsset> path) {

    }

    @Override
    public void setServiceCapabilities(ResourcesService.Capabilities capabilities) {

    }

    @Override
    public void workspaceCreated(NavigableContainer workspace) {

    }

    @Override
    public void resetValidationNotifications(NavigableContainer notifications) {

    }
}
