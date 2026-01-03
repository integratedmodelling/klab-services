package org.integratedmodelling.klab.api.view.modeler.views;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.View;
import org.integratedmodelling.klab.api.view.annotations.UIEventHandler;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;

import java.util.Collection;
import java.util.List;

public interface ResourcesNavigator extends View {

  /**
   * Notify the view that a workspace has been modified, with a list of changed assets.
   *
   * @param changedContainer the workspace that was modified
   * @param changedAssets the list of assets that were changed
   */
  void workspaceModified(
      NavigableContainer changedContainer, Collection<NavigableAsset> changedAssets);

  /**
   * Monitor the engine status so we can choose to disable when the service(s) we need are
   * unavailable.
   *
   * @param status
   */
  void engineStatusChanged(Engine.Status status);

  /**
   * Return the workspace with the given URN if it is being visualized, or null otherwise. Called
   * before merging incoming changes which will trigger a call to {@link
   * #workspaceModified(NavigableContainer, Collection<NavigableAsset>)}
   *
   * @param workspace
   * @return
   */
  NavigableContainer getVisualizedWorkspace(String workspace);
}
