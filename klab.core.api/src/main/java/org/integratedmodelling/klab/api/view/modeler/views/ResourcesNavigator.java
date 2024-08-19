package org.integratedmodelling.klab.api.view.modeler.views;

import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.View;
import org.integratedmodelling.klab.api.view.annotations.UIEventHandler;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;

import java.util.List;

public interface ResourcesNavigator extends View {

    void showWorkspaces(List<NavigableContainer> workspaces);

    void showResources(NavigableContainer workspace);

    void workspaceModified(NavigableContainer changedContainer);

    void showAssetInfo(NavigableAsset asset);

    void highlightAssetPath(List<NavigableAsset> path);

    void setServiceCapabilities(ResourcesService.Capabilities capabilities);

    void workspaceCreated(NavigableContainer workspace);

    void resetValidationNotifications(NavigableContainer notifications);
}
