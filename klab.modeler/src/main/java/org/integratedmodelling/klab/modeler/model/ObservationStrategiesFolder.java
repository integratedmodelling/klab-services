package org.integratedmodelling.klab.modeler.model;

import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ObservationStrategiesFolder extends NavigableFolderImpl<NavigableDocument> {

  public static final String TITLE = "Observation strategies";

  ObservationStrategiesFolder(NavigableProject project) {
    super(TITLE, project);
  }

  @Override
  protected List<NavigableAsset> createChildren() {
    return parent(NavigableProject.class).getObservationStrategies().stream()
        .map(
            s ->
                (NavigableAsset)
                    (s instanceof NavigableObservationStrategies navigableObservationStrategies
                        ? s
                        : new NavigableObservationStrategies(s, this)))
        .toList();
  }
}
