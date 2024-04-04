package org.integratedmodelling.klab.api.view.modeler.views;

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

    void highlightAsset(NavigableAsset asset);

    void workspaceModified(ResourceSet changes);
}
