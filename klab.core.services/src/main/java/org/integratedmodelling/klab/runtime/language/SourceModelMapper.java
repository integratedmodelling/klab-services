package org.integratedmodelling.klab.runtime.language;

import org.integratedmodelling.common.knowledge.ModelImpl;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.lang.ContextualizableImpl;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.lang.AnnotationCollector;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.resolver.Coverage;

/** Identical source projection for namespace publication and resolver model construction. */
public final class SourceModelMapper {
  private SourceModelMapper() {}

  public static Model load(KimModel statement, KimNamespace namespace, Scope scope) {

    var reasoner = scope.getService(Reasoner.class);

    ModelImpl model = new ModelImpl();
    var resolutionInfo = new ModelImpl.ResolutionInfoImpl();

    resolutionInfo.setScope(statement.getScope());
    resolutionInfo.setInScenario(namespace.isScenario());
    resolutionInfo.setResolutionCriteria(namespace.getResolutionCriteria() == null
        ? java.util.Map.of() : new java.util.HashMap<>(namespace.getResolutionCriteria()));
    // TODO coverage!

    model.getAnnotations().addAll(AnnotationCollector.merge(statement.getAnnotations()));
    for (KimObservable observable : statement.getObservables()) {
      model.getObservables().add(annotatedObservable(observable, reasoner));
    }
    for (KimObservable observable : statement.getDependencies()) {
      model.getDependencies().add(annotatedObservable(observable, reasoner));
    }

    model.setResolutionInfo(resolutionInfo);

    // TODO learners, geometry covered etc.
    model.setUrn(statement.getUrn());
    model.setMetadata(
        statement.getMetadata()); // FIXME add processed metadata with the existing symbol table
    model.setNamespace(statement.getNamespace());
    model.setProjectName(statement.getProjectName());

    // TODO any literal value must be added first
    // TODO use static builders for Contextualizable instead of polymorphic constructors

    if (!statement.getResourceUrns().isEmpty()) {
      model.getComputation().add(new ContextualizableImpl(statement.getResourceUrns()));
    }

    model.getComputation().addAll(statement.getContextualization());

    // FIXME use coverage from NS or model if any
    model.setCoverage(Coverage.universal());

    return model;
  }

  private static org.integratedmodelling.klab.api.knowledge.Observable annotatedObservable(
      KimObservable syntax, Reasoner reasoner) {
    var resolved = reasoner.resolveObservable(syntax.getUrn());
    if (resolved == null) return null;
    var result = new ObservableImpl((ObservableImpl) resolved);
    var semantics = new ConceptImpl((ConceptImpl) resolved.getSemantics());
    semantics.setAnnotations(AnnotationCollector.merge(
        semantics.getAnnotations(), AnnotationCollector.collect(
            syntax.getSemantics(), reasoner::resolveConcept)));
    result.setSemantics(semantics);
    result.setAnnotations(AnnotationCollector.merge(
        resolved.getAnnotations(), syntax.getAnnotations()));
    return result;
  }

}
