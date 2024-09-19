package org.integratedmodelling.klab.modeler.views;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
import org.integratedmodelling.klab.api.view.modeler.views.ResourcesNavigator;

import java.util.List;

/**
 * TODO implement data model to replicate the actions when the Eclipse modeler is done.
 */
public class ResourcesNavigatorAdvisor extends BaseViewAdvisor implements ResourcesNavigator {

    @Override
    public void showWorkspaces(List<NavigableContainer> workspaces) {

    }

    @Override
    public void showResources(NavigableContainer workspace) {

    }

    @Override
    public void workspaceModified(NavigableContainer container) {

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

    @Override
    public void engineStatusChanged(Engine.Status status) {

    }
}
