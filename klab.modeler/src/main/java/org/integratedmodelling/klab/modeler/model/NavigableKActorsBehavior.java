package org.integratedmodelling.klab.modeler.model;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.KlabLanguage;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;

public class NavigableKActorsBehavior extends NavigableKlabDocument<KActorsAction, KActorsBehavior>
    implements KActorsBehavior {

  public NavigableKActorsBehavior(KActorsBehavior n, NavigableKlabAsset<?> navigableProject) {
    super(n, navigableProject);
  }

  @Override
  protected List<NavigableAsset> createChildren() {
    return getStatements().stream()
        .map(s -> (NavigableAsset) new NavigableKActorsAction(s, this))
        .toList();
  }

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

  @Override
  public Platform getPlatform() {
    return delegate.getPlatform();
  }

  @Override
  public List<Import> getImports() {
    return delegate.getImports();
  }

  @Override
  public List<Import> getInheritedBehaviors() {
    return delegate.getInheritedBehaviors();
  }

  @Override
  public String getDescription() {
    return delegate.getDescription();
  }

  @Override
  public KlabStatement.Scope getScope() {
    return delegate.getScope();
  }

  @Override
  public KActorsBehavior.Type getBehaviorType() {
    return delegate.getBehaviorType();
  }
}
