package org.integratedmodelling.klab.modeler.model;

import java.util.List;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;

public class BehaviorFolder extends NavigableFolderImpl<NavigableDocument> {

  public static final String TITLE = "Behaviors";

  BehaviorFolder(NavigableProject project) {
    super(TITLE, project);
  }

  @Override
  protected List<NavigableAsset> createChildren() {
    return parent(NavigableProject.class).getBehaviors().stream()
        .map(
            behavior ->
                (NavigableAsset)
                    (behavior instanceof NavigableKActorsBehavior navigable ?
                        navigable : new NavigableKActorsBehavior(behavior, this)))
        .toList();
  }
}
