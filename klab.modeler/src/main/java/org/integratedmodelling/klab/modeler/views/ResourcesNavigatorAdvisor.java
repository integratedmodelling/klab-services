package org.integratedmodelling.klab.modeler.views;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
import org.integratedmodelling.klab.api.view.modeler.views.ResourcesNavigator;

import java.util.Collection;
import java.util.List;

/** TODO implement data model to replicate the actions when the Eclipse modeler is done. */
public class ResourcesNavigatorAdvisor extends BaseViewAdvisor implements ResourcesNavigator {

  @Override
  public void workspaceModified(NavigableContainer container, Collection<NavigableAsset> changes) {}

  @Override
  public void engineStatusChanged(Engine.Status status) {}

  @Override
  public NavigableContainer getVisualizedWorkspace(String workspace) {
    return null;
  }
}
