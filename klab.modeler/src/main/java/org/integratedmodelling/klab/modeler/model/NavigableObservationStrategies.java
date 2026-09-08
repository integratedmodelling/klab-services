package org.integratedmodelling.klab.modeler.model;

import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.KlabLanguage;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kim.KimObservationPlan;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategyDocument;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NavigableObservationStrategies
    extends NavigableKlabDocument<KimObservationStrategy, KimObservationStrategyDocument>
    implements KimObservationStrategyDocument {

  @Serial private static final long serialVersionUID = 3213955882357790089L;

  public NavigableObservationStrategies(
      KimObservationStrategyDocument document, NavigableKlabAsset<?> parent) {
    super(document, parent);
  }

  @Override public int getModelVersion() { return delegate.getModelVersion(); }
  @Override public KimObservationPlan.Source getSource() { return delegate.getSource(); }
  @Override public List<String> getImports() { return delegate.getImports(); }
  @Override public Map<String, Object> getCoverage() { return delegate.getCoverage(); }

  @Override
  protected List<NavigableAsset> createChildren() {
    return getStatements().stream()
        .map(s -> (NavigableAsset) new NavigableObservationStrategy(s, this))
        .toList();
  }

  @Override
  public Collection<Annotation> getAnnotations() {
    return delegate.getAnnotations();
  }

  @Override
  public Set<String> importedNamespaces(boolean withinType) {
    return delegate.importedNamespaces(withinType);
  }

  @Override
  public KlabLanguage getLanguage() {
    return delegate.getLanguage();
  }
}
