package org.integratedmodelling.klab.runtime.language;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    return load(statement, namespace, scope, null);
  }

  /** Optionally record the exact semantic dependencies of this projection. */
  public static Model load(KimModel statement, KimNamespace namespace, Scope scope,
      java.util.Set<String> semanticReads) {
    return load(statement, namespace, scope, semanticReads, false);
  }

  public static Model load(KimModel statement, KimNamespace namespace, Scope scope,
      Set<String> semanticReads, boolean batchPorts) {
    var reasoner = scope.getService(Reasoner.class);

    ModelImpl model = new ModelImpl();
    var resolutionInfo = new ModelImpl.ResolutionInfoImpl();

    resolutionInfo.setScope(statement.getScope());
    resolutionInfo.setInScenario(namespace.isScenario());
    resolutionInfo.setResolutionCriteria(namespace.getResolutionCriteria() == null
        ? java.util.Map.of() : new java.util.HashMap<>(namespace.getResolutionCriteria()));
    // TODO coverage!

    model.getAnnotations().addAll(AnnotationCollector.merge(statement.getAnnotations()));
    if (batchPorts) {
      var ports = new ArrayList<KimObservable>(statement.getObservables());
      int outputs = ports.size();
      ports.addAll(statement.getDependencies());
      if (!ports.isEmpty()) {
        var projection = reasoner.resolveModelPorts(ports, semanticReads != null);
        if (projection == null || projection.ports().size() != ports.size())
          throw new IllegalStateException("Model port projection has the wrong arity");
        model.getObservables().addAll(projection.ports().subList(0, outputs));
        model.getDependencies().addAll(projection.ports().subList(outputs, ports.size()));
        if (semanticReads != null) semanticReads.addAll(projection.semanticReads());
      }
    } else {
      for (KimObservable observable : statement.getObservables())
        model.getObservables().add(annotatedObservable(observable, reasoner, semanticReads));
      for (KimObservable observable : statement.getDependencies())
        model.getDependencies().add(annotatedObservable(observable, reasoner, semanticReads));
    }

    model.setResolutionInfo(resolutionInfo);

    model.setLearningModel(statement.isLearningModel());
    model.setLearnedResourceId(statement.getLearnedResourceId());
    // TODO geometry covered etc.
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

  public static Reasoner.ModelPorts resolvePorts(
      List<KimObservable> ports, Reasoner reasoner, boolean trackReads) {
    Set<String> reads = trackReads ? new HashSet<>() : null;
    var projected = ports.stream().map(port -> annotatedObservable(port, reasoner, reads)).toList();
    return new Reasoner.ModelPorts(projected, reads == null ? Set.of() : reads);
  }

  private static org.integratedmodelling.klab.api.knowledge.Observable annotatedObservable(
      KimObservable syntax, Reasoner reasoner, java.util.Set<String> semanticReads) {
    var resolved = reasoner.resolveObservable(syntax.getUrn());
    if (semanticReads != null) {
      semanticReads.add(syntax.getSemantics() == null ? null : syntax.getSemantics().getUrn());
      recordRead(resolved == null ? null : resolved.getSemantics(), semanticReads);
      if (resolved != null && resolved.getObserverSemantics() != null)
        recordRead(resolved.getObserverSemantics(), semanticReads);
    }
    if (resolved == null) return null;
    var result = new ObservableImpl((ObservableImpl) resolved);
    // Port aliases belong to this declaration, independently of the reasoner's semantic cache.
    if (syntax.getFormalName() != null) result.setStatedName(syntax.getFormalName());
    var semantics = new ConceptImpl((ConceptImpl) resolved.getSemantics());
    semantics.setAnnotations(AnnotationCollector.merge(
        semantics.getAnnotations(), AnnotationCollector.collect(
            syntax.getSemantics(), name -> {
              var concept = reasoner.resolveConcept(name);
              if (semanticReads != null) {
                semanticReads.add(name);
                recordRead(concept, semanticReads);
              }
              return concept;
            })));
    result.setSemantics(semantics);
    result.setAnnotations(AnnotationCollector.merge(
        resolved.getAnnotations(), syntax.getAnnotations()));
    return result;
  }

  private static void recordRead(org.integratedmodelling.klab.api.knowledge.Concept concept,
      java.util.Set<String> semanticReads) {
    semanticReads.add(concept == null
        || concept.is(org.integratedmodelling.klab.api.knowledge.SemanticType.NOTHING)
        ? null : concept.getUrn());
  }

}
