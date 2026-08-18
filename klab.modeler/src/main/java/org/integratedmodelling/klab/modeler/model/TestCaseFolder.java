package org.integratedmodelling.klab.modeler.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;

public class TestCaseFolder extends NavigableFolderImpl<NavigableDocument> {

  public static final String TITLE = "Test cases";

  TestCaseFolder(NavigableProject project) {
    super(TITLE, project);
  }

  @Override
  protected List<NavigableAsset> createChildren() {
    return parent(NavigableProject.class).getTestCases().stream()
        .map(
            s ->
                (NavigableAsset)
                    (s instanceof NavigableKActorsBehavior navigableObservationStrategies
                        ? s
                        : new NavigableKActorsBehavior(s, this)))
        .toList();
  }
}
