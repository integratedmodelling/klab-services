package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import java.util.List;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ObserverDeferredResolutionTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  private ObservableImpl observable(String urn, SemanticType type) {
    var concept = new ConceptImpl(); concept.setUrn(urn); concept.setType(EnumSet.of(type));
    var result = new ObservableImpl(); result.setSemantics(concept); result.setUrn(urn); return result;
  }
  private ObservationImpl observation(long id, Observable observable, Geometry support) {
    var result = new ObservationImpl(); result.setId(id); result.setObservable(observable); result.setGeometry(support); return result;
  }
  private class Fixture {
    final ContextScope scope = mock(ContextScope.class);
    final Reasoner reasoner = mock(Reasoner.class);
    final RuntimeService runtime = mock(RuntimeService.class);
    final org.integratedmodelling.klab.api.knowledge.observation.scale.Scale scale = GeometryRepository.INSTANCE.scale(Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}"));
    final ObservableImpl requested = observable("test:Quality", SemanticType.QUALITY);
    final ObservableImpl observer = observable("test:Observer", SemanticType.AGENT);
    final ObservationImpl request = observation(-2, requested, scale);
    final ObservationImpl absolute = observation(42, requested, scale);
    final ObservationStrategy recursive = mock(ObservationStrategy.class);
    final ObservationStrategy direct = mock(ObservationStrategy.class);
    final ResolutionCompiler compiler = spy(new ResolutionCompiler(mock(ResolverService.class)));
    Fixture() {
      when(scope.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(scope);
      when(scope.getContextObservation()).thenReturn(observation(20, observable("test:Bearer", SemanticType.SUBJECT), scale));
      when(scope.getObserver()).thenReturn(observation(30, observer, scale));
      when(scope.getService(Reasoner.class)).thenReturn(reasoner);
      when(scope.getService(RuntimeService.class)).thenReturn(runtime);
      var receipt = mock(Reasoner.Capabilities.class); when(receipt.getKnowledgeRevision()).thenReturn(7L);
      when(reasoner.capabilities(scope)).thenReturn(receipt);
      when(reasoner.semanticDistance(any(), any(), isNull(), eq(7L))).thenReturn(0);
      var state = org.integratedmodelling.klab.api.collections.Parameters.<String>create();
      state.put("__RESOLUTION_GRAPH__", ResolutionGraph.create(scope)); when(scope.getData()).thenReturn(state);
      when(recursive.getRank()).thenReturn(1); when(direct.getRank()).thenReturn(0);
      var operation = mock(ObservationStrategy.Operation.class);
      when(operation.getType()).thenReturn(ObservationStrategy.Operation.Type.OBSERVE);
      when(operation.getObservable()).thenReturn(requested); when(direct.getOperations()).thenReturn(List.of(operation));
      when(reasoner.computeObservationStrategies(request, scope)).thenReturn(List.of(recursive, direct));
      doReturn(new ResolutionCompiler.QueryMatch(absolute, absolute, scale, scale, Coverage.create(scale, 1), true))
          .when(compiler).query(requested, scale, scope);
    }
    Model model(String name, Geometry support, boolean specific) {
      var output = new ObservableImpl(requested);
      if (specific) { output.setObserverSemantics(observer.asConcept()); output.setUrn(requested.getUrn()+" observed as "+observer.getUrn()); }
      var model = mock(Model.class); when(model.getUrn()).thenReturn("test:"+name); when(model.getNamespace()).thenReturn("test");
      when(model.getCoverage()).thenReturn(support); when(model.getObservables()).thenReturn(List.of(output));
      var computation = mock(org.integratedmodelling.klab.api.lang.Contextualizable.class);
      when(model.getComputation()).thenReturn(List.of(computation));
      when(runtime.resolveContextualizables(model.getComputation(), scope)).thenReturn(new ResourceSet());
      return model;
    }
    void models(Model... models) {
      var context = scope.getContextObservation().getObservable().asConcept();
      doReturn(List.of(models)).when(compiler).queryModels(requested, context, scope, scale);
    }
  }

  @Test void specificDependencyFailureFallsBackToExistingAbsoluteBeforeRecursiveOrNewAbsoluteModel() {
    var f = new Fixture(); var specific = f.model("specific", f.scale, true); var absoluteModel = f.model("absolute", f.scale, false);
    var missing = observable("test:Missing", SemanticType.QUALITY); when(specific.getDependencies()).thenReturn(List.of(missing));
    doReturn(new ResolutionCompiler.QueryMatch(null, null, f.scale, null, Coverage.create(f.scale, 0)))
        .when(f.compiler).query(missing, f.scale, f.scope);
    var builder = mock(Observation.Builder.class, RETURNS_SELF); when(builder.register()).thenReturn(Observation.empty());
    when(f.scope.observation(missing)).thenReturn(builder);
    f.models(specific, absoluteModel);
    var result = f.compiler.resolve(f.request, f.scope);
    assertTrue(result.getCoverage().isComplete());
    assertTrue(result.graph().vertexSet().contains(f.absolute.getObservable()));
    verify(f.recursive, never()).getOperations();
    verify(f.runtime, never()).resolveContextualizables(absoluteModel.getComputation(), f.scope);
  }

  @Test void partialSpecificWithoutCompositionFallsBackToCompleteAbsoluteReference() {
    var f = new Fixture();
    var firstHalf = GeometryRepository.INSTANCE.scale(Geometry.create("T0(1){tend=5,tstart=0,ttype=PHYSICAL}"));
    var specific = f.model("specific", firstHalf, true); f.models(specific);
    var result = f.compiler.resolve(f.request, f.scope);
    assertTrue(result.getCoverage().isComplete());
    assertFalse(result.graph().vertexSet().contains(specific));
    var fallbackEdges = result.graph().edgeSet().stream().filter(edge -> edge.observationId == 42).toList();
    assertEquals(1, fallbackEdges.size());
    assertNotNull(fallbackEdges.getFirst().referenceSupport);
    // No value-copy composition operator exists: preserve the complete original asset rather
    // than pretending the partial specific computation plus a reference filled its output.
    assertEquals(0, GeometryRepository.INSTANCE.scale(fallbackEdges.getFirst().referenceSupport).getTime().getStart().getMilliseconds());
    assertEquals(10, GeometryRepository.INSTANCE.scale(fallbackEdges.getFirst().referenceSupport).getTime().getEnd().getMilliseconds());
    verify(f.recursive, never()).getOperations();
  }

  @Test void completeSpecificOutputOverridesExistingAbsoluteWithoutFallbackReference() {
    var f = new Fixture(); var specific = f.model("specific", f.scale, true); f.models(specific);
    var result = f.compiler.resolve(f.request, f.scope);
    assertTrue(result.getCoverage().isComplete());
    assertTrue(result.graph().vertexSet().contains(specific));
    assertTrue(result.graph().edgeSet().stream().noneMatch(edge -> edge.observationId == 42));
    verify(f.recursive, never()).getOperations();
  }
  @Test void existingProcessExtentReportsItsActualPartialCoverage() {
    var scope = mock(ContextScope.class);
    var process = observable("test:Process", SemanticType.PROCESS);
    var requested = GeometryRepository.INSTANCE.scale(Geometry.create("T0(1){tstart=0,tend=10,ttype=PHYSICAL}"));
    var partial = observation(42, process, Geometry.create("T0(1){tstart=0,tend=5,ttype=PHYSICAL}"));
    when(scope.getObservation(any(Observation.class))).thenReturn(partial);
    var match = new ResolutionCompiler(mock(ResolverService.class)).query(process, requested, scope);
    assertTrue(match.hasCoverage()); assertFalse(match.coverage().isComplete());
    assertEquals(0.5, match.coverage().getCoverage(), 0.0001);
    assertEquals(5, match.coveredScale().getTime().getEnd().getMilliseconds());
  }
}
