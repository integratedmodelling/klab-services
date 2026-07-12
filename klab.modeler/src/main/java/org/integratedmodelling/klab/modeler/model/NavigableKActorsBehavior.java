package org.integratedmodelling.klab.modeler.model;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.KlabLanguage;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;
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

  @Override
  public Platform getPlatform() {
    return delegate.getPlatform();
  }

  @Override
  public String getOutput() {
    return delegate.getOutput();
  }

  @Override
  public List<String> getImports() {
    return delegate.getImports();
  }

  @Override
  public String getStyle() {
    return delegate.getStyle();
  }

  @Override
  public List<String> getLocales() {
    return delegate.getLocales();
  }

  @Override
  public String getLabel() {
    return delegate.getLabel();
  }

  @Override
  public String getDescription() {
    return delegate.getDescription();
  }

  @Override
  public String getLogo() {
    return delegate.getLogo();
  }

  @Override
  public String getProjectId() {
    return delegate.getProjectId();
  }

  @Override
  public Map<String, String> getStyleSpecs() {
    return delegate.getStyleSpecs();
  }

  @Override
  public boolean isPublic() {
    return delegate.isPublic();
  }

  @Override
  public KActorsBehavior.Type getBehaviorType() {
    return delegate.getBehaviorType();
  }
}
