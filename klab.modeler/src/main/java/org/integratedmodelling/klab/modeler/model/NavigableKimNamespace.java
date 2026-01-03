package org.integratedmodelling.klab.modeler.model;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.KlabLanguage;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;

public class NavigableKimNamespace extends NavigableKlabDocument<KlabStatement, KimNamespace>
    implements KimNamespace {

  @Serial private static final long serialVersionUID = 3213955882357790089L;

  public NavigableKimNamespace(KimNamespace document, NavigableKlabAsset<?> parent) {
    super(document, parent);
  }

  @Override
  protected List<NavigableAsset> createChildren() {
    return getStatements().stream()
        .map(
            s ->
                (NavigableAsset)
                    switch (s) {
                      case KimModel kimModel -> new NavigableKimModel(kimModel, this);
                      case KimSymbolDefinition kimSymbolDefinition ->
                          new NavigableKimSymbolDefinition(kimSymbolDefinition, this);
                      default ->
                          throw new KlabInternalErrorException(
                              "Unrecognized statement in namespace when wrapping for navigation");
                    })
        .toList();
  }

  @Override
  public KlabStatement.Scope getScope() {
    return delegate.getScope();
  }

  @Override
  public Collection<String> getDisjointNamespaces() {
    return delegate.getDisjointNamespaces();
  }

  @Override
  public Geometry getCoverage() {
    return delegate.getCoverage();
  }

  @Override
  public Map<String, List<String>> getImports() {
    return delegate.getImports();
  }

  @Override
  public String getScriptId() {
    return delegate.getScriptId();
  }

  @Override
  public String getTestCaseId() {
    return delegate.getTestCaseId();
  }

  @Override
  public boolean isScenario() {
    return delegate.isScenario();
  }

  @Override
  public boolean isWorldviewBound() {
    return delegate.isWorldviewBound();
  }

  @Override
  public Set<String> importedNamespaces(boolean withinType) {
    return delegate.importedNamespaces(withinType);
  }

  @Override
  public Collection<Annotation> getAnnotations() {
    return delegate.getAnnotations();
  }

  @Override
  public KlabLanguage getLanguage() {
    return delegate.getLanguage();
  }
}
