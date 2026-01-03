package org.integratedmodelling.klab.modeler.model;

import java.util.Collection;
import java.util.Set;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.KlabLanguage;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;

public class NavigableKActorsBehavior
    extends NavigableKlabDocument<KActorsAction, KActorsBehavior> {

  public NavigableKActorsBehavior(KActorsBehavior n, NavigableKlabAsset navigableProject) {
    super(n, navigableProject);
  }

  // TODO
  

  @Override
  public Set<String> importedNamespaces(boolean withinType) {
    return delegate.importedNamespaces(withinType);
  }

  @Override
  public KlabLanguage getLanguage() {
    return delegate.getLanguage();
  }

  @Override
  public Collection<Annotation> getAnnotations() {
    return delegate.getAnnotations();
  }
}
