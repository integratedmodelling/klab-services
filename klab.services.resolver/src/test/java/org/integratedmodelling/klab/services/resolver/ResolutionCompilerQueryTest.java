package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ResolutionCompilerQueryTest {

  @Test
  void completeTierZeroPrecomputedDataSkipsEarlierRecursiveCandidate() {
    tierZeroSkipsRecursiveCandidate(false);
  }

  @Test
  void completeTierZeroModelSkipsEarlierRecursiveCandidate() {
    tierZeroSkipsRecursiveCandidate(true);
  }

  private void tierZeroSkipsRecursiveCandidate(boolean useModel) {
    var scope=mock(ContextScope.class);
    when(scope.withResolutionConstraints(any(org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint[].class)))
        .thenReturn(scope);
    var scale=GeometryRepository.INSTANCE.scale(Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}"));
    var requested=mock(Observable.class); var data=mock(Observable.class); var domains=mock(Observable.class);
    for (var observable : java.util.List.of(requested,data,domains)) {
      var semantics=mock(Concept.class); when(semantics.getType()).thenReturn(EnumSet.of(SemanticType.QUALITY));
      when(observable.getSemantics()).thenReturn(semantics);
      when(observable.is(SemanticType.QUALITY)).thenReturn(true);
    }
    when(requested.getUrn()).thenReturn("test:FlowDirection"); when(data.getUrn()).thenReturn("test:FlowDirection");
    // A precomputed model supplies the same requested native semantics, not a cross-bearer cast.
    var nativeSemantics=requested.getSemantics();
    when(data.getSemantics()).thenReturn(nativeSemantics);
    when(domains.is(SemanticType.QUALITY)).thenReturn(false);
    when(domains.getSemantics().getType()).thenReturn(EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE));
    when(domains.getSemantics().isCollective()).thenReturn(true);
    when(domains.getUrn()).thenReturn("each test:RiverBasin");
    var request=mock(Observation.class); when(request.getObservable()).thenReturn(requested); when(request.getGeometry()).thenReturn(scale);
    var context=mock(Observation.class); when(context.getGeometry()).thenReturn(scale); when(scope.getContextObservation()).thenReturn(context);
    var contextObservable=mock(Observable.class); when(context.getObservable()).thenReturn(contextObservable);
    var contextSemantics=mock(Concept.class); when(contextObservable.getSemantics()).thenReturn(contextSemantics);
    var existing=mock(Observation.class); when(existing.getObservable()).thenReturn(data);
    when(existing.getGeometry()).thenReturn(scale); when(existing.getId()).thenReturn(42L);
    when(existing.getMetadata()).thenReturn(org.integratedmodelling.klab.api.data.Metadata.create());
    var direct=mock(org.integratedmodelling.klab.api.knowledge.ObservationStrategy.class);
    var recursive=mock(org.integratedmodelling.klab.api.knowledge.ObservationStrategy.class);
    when(direct.getRank()).thenReturn(0); when(recursive.getRank()).thenReturn(1);
    for (var strategy : java.util.List.of(direct,recursive)) {
      var operation=mock(org.integratedmodelling.klab.api.knowledge.ObservationStrategy.Operation.class);
      when(operation.getType()).thenReturn(strategy==direct && useModel
          ? org.integratedmodelling.klab.api.knowledge.ObservationStrategy.Operation.Type.OBSERVE
          : org.integratedmodelling.klab.api.knowledge.ObservationStrategy.Operation.Type.RESOLVE);
      when(operation.getObservable()).thenReturn(strategy==direct?data:domains);
      when(strategy.getOperations()).thenReturn(java.util.List.of(operation));
    }
    var reasoner=mock(org.integratedmodelling.klab.api.services.Reasoner.class);
    when(scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(reasoner);
    when(reasoner.computeObservationStrategies(request,scope)).thenReturn(java.util.List.of(recursive,direct));
    var state=org.integratedmodelling.klab.api.collections.Parameters.<String>create();
    state.put("__RESOLUTION_GRAPH__",ResolutionGraph.create(scope)); when(scope.getData()).thenReturn(state);
    var compiler=org.mockito.Mockito.spy(new ResolutionCompiler(mock(ResolverService.class)));
    org.mockito.Mockito.doReturn(new ResolutionCompiler.QueryMatch(null,null,scale,null,
        org.integratedmodelling.klab.api.services.resolver.Coverage.create(scale,0))).when(compiler).query(requested,scale,scope);
    org.mockito.Mockito.doReturn(new ResolutionCompiler.QueryMatch(existing,existing,scale,scale,
        org.integratedmodelling.klab.api.services.resolver.Coverage.create(scale,1))).when(compiler).query(data,scale,scope);
    if (useModel) {
      var model=mock(org.integratedmodelling.klab.api.knowledge.Model.class);
      when(model.getUrn()).thenReturn("test:precomputed"); when(model.getNamespace()).thenReturn("test");
      when(model.getCoverage()).thenReturn(scale); when(model.getObservables()).thenReturn(java.util.List.of(data));
      var computation=mock(org.integratedmodelling.klab.api.lang.Contextualizable.class);
      when(model.getComputation()).thenReturn(java.util.List.of(computation));
      org.mockito.Mockito.doReturn(java.util.List.of(model)).when(compiler).queryModels(data,contextSemantics,scope,scale);
      var runtime=mock(org.integratedmodelling.klab.api.services.RuntimeService.class);
      when(scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(runtime);
      when(runtime.resolveContextualizables(model.getComputation(),scope))
          .thenReturn(new org.integratedmodelling.klab.api.services.resources.ResourceSet());
    }
    var result=compiler.resolve(request,scope);
    assertTrue(result.getCoverage().isComplete());
    verify(compiler,never()).query(domains,scale,scope);
    verify(scope,never()).observation(any());
    verify(recursive,never()).getOperations();
    verify(recursive,never()).getContextBinding();
    verify(scope,never()).getObservation(org.mockito.ArgumentMatchers.anyLong());
  }

  @Test
  void identicalCollectivePortsResolveOnlyOnceWithinAStrategy() {
    var scope = mock(ContextScope.class);
    when(scope.withResolutionConstraints(any())).thenReturn(scope);
    var scale = GeometryRepository.INSTANCE.scale(Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}"));
    var semantics = mock(Concept.class);
    when(semantics.isCollective()).thenReturn(true);
    when(semantics.getType()).thenReturn(EnumSet.of(SemanticType.SUBJECT));
    var observable = mock(Observable.class);
    when(observable.getSemantics()).thenReturn(semantics);
    when(observable.getUrn()).thenReturn("each test:Junction");
    var existing = mock(Observation.class);
    when(existing.getMetadata()).thenReturn(org.integratedmodelling.klab.api.data.Metadata.create());
    when(existing.getId()).thenReturn(42L);
    when(existing.getGeometry()).thenReturn(scale);
    var compiler = org.mockito.Mockito.spy(new ResolutionCompiler(mock(ResolverService.class)));
    org.mockito.Mockito.doReturn(new ResolutionCompiler.QueryMatch(existing, existing, scale, scale,
        org.integratedmodelling.klab.api.services.resolver.Coverage.create(scale, 1)))
        .when(compiler).query(observable, scale, scope);
    var strategy = mock(org.integratedmodelling.klab.api.knowledge.ObservationStrategy.class);
    var source = mock(org.integratedmodelling.klab.api.knowledge.ObservationStrategy.Operation.class);
    var target = mock(org.integratedmodelling.klab.api.knowledge.ObservationStrategy.Operation.class);
    when(source.getId()).thenReturn("sources"); when(target.getId()).thenReturn("targets");
    for (var operation : java.util.List.of(source, target)) {
      when(operation.getObservable()).thenReturn(observable);
      when(operation.getType()).thenReturn(org.integratedmodelling.klab.api.knowledge.ObservationStrategy.Operation.Type.RESOLVE);
    }
    when(strategy.getOperations()).thenReturn(java.util.List.of(source, target));
    compiler.resolve(strategy, scale, ResolutionGraph.create(scope), scope);
    verify(compiler, org.mockito.Mockito.times(1)).query(observable, scale, scope);
  }

  @BeforeAll
  static void configureKlab() {
    ServiceConfiguration.injectInstantiators();
  }

  @Test
  void existingContextualQualityIsACompleteReferenceWithoutAnIdZeroQuery() {
    var observable = mock(Observable.class);
    var semantics = mock(Concept.class);
    var scope = mock(ContextScope.class);
    var existing = mock(Observation.class);
    var requested =
        GeometryRepository.INSTANCE.scale(
            Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}"));

    when(observable.is(SemanticType.QUALITY)).thenReturn(true);
    when(observable.getSemantics()).thenReturn(semantics);
    when(semantics.getType()).thenReturn(EnumSet.of(SemanticType.QUALITY));
    when(scope.getObservation(any(Observation.class))).thenReturn(existing);
    when(existing.getMetadata()).thenReturn(org.integratedmodelling.klab.api.data.Metadata.create());
    when(existing.getId()).thenReturn(42L);

    var result = new ResolutionCompiler(mock(ResolverService.class)).query(observable, requested, scope);

    assertSame(existing, result.result());
    assertSame(existing, result.reference());
    assertSame(requested, result.coveredScale());
    assertTrue(result.coverage().isComplete());
    verify(scope, never()).observation(any());
  }

  @Test
  void qualityExtentMismatchHonorsRuntimeGateDuringResolution() {
    var scope=mock(ContextScope.class,org.mockito.Mockito.RETURNS_DEEP_STUBS);
    var observable=mock(Observable.class);
    var semantics=mock(Concept.class);
    when(observable.is(SemanticType.QUALITY)).thenReturn(true);
    when(observable.getSemantics()).thenReturn(semantics);
    when(semantics.getType()).thenReturn(EnumSet.of(SemanticType.QUALITY));
    var existing=mock(Observation.class);
    when(existing.getMetadata()).thenReturn(org.integratedmodelling.klab.api.data.Metadata.create());
    when(existing.getId()).thenReturn(42L);when(existing.getObservable()).thenReturn(observable);
    when(existing.getGeometry()).thenReturn(Geometry.create("S2(4,4){proj=EPSG:4326,shape=EPSG:4326 POLYGON ((0 0&comma;0 4&comma;4 4&comma;4 0&comma;0 0))}"));
    when(scope.getObservation(any(Observation.class))).thenReturn(existing);
    var requested=GeometryRepository.INSTANCE.scale(Geometry.create("S2(2,2){proj=EPSG:4326,shape=EPSG:4326 POLYGON ((0 0&comma;0 4&comma;4 4&comma;4 0&comma;0 0))}"));
    org.mockito.Mockito.doReturn(mock(org.integratedmodelling.klab.api.services.RuntimeService.class,org.mockito.Mockito.RETURNS_DEEP_STUBS)).when(scope).getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
    var settings=scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class).settings();
    when(settings.get(org.integratedmodelling.klab.api.configuration.Setting.ACCEPT_LOSSY_MEDIATIONS,Boolean.class)).thenReturn(false);
    var compiler=new ResolutionCompiler(mock(ResolverService.class));
    var failure=org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
        ()->compiler.query(observable,requested,scope));
    assertTrue(failure.getMessage().startsWith(
        "ACCEPT_LOSSY_MEDIATIONS is false: spatial extents do not match: "));
    assertTrue(failure.getMessage().contains("cell resolution differs"));
    when(settings.get(org.integratedmodelling.klab.api.configuration.Setting.ACCEPT_LOSSY_MEDIATIONS,Boolean.class)).thenReturn(true);
    assertSame(existing,compiler.query(observable,requested,scope).reference());
    org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE,
        org.integratedmodelling.klab.api.configuration.Setting.ACCEPT_LOSSY_MEDIATIONS.defaultValue);
  }

  @Test
  void provisionalQualityIsNotTreatedAsPersistedKnowledge() {
    var observable = mock(Observable.class);
    var semantics = mock(Concept.class);
    var scope = mock(ContextScope.class);
    var provisional = mock(Observation.class);
    var requested =
        GeometryRepository.INSTANCE.scale(
            Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}"));

    when(observable.is(SemanticType.QUALITY)).thenReturn(true);
    when(observable.getSemantics()).thenReturn(semantics);
    when(semantics.getType()).thenReturn(EnumSet.of(SemanticType.QUALITY));
    when(scope.getObservation(any(Observation.class))).thenReturn(provisional);
    when(provisional.getId()).thenReturn(-2L);

    var result =
        new ResolutionCompiler(mock(ResolverService.class)).query(observable, requested, scope);

    assertSame(provisional, result.result());
    assertNull(result.reference());
    assertTrue(result.coverage().isEmpty());
    verify(scope, never()).observation(any());
  }
  @Test void loweringRetainsOrderedModelIdentityEvenWithoutComputations() {
    var scope=mock(ContextScope.class);var graph=ResolutionGraph.create(scope);var actuator=new org.integratedmodelling.common.runtime.ActuatorImpl();
    var observable=mock(Observable.class);var first=mock(org.integratedmodelling.klab.api.knowledge.Model.class);var second=mock(org.integratedmodelling.klab.api.knowledge.Model.class);
    when(first.getUrn()).thenReturn("same.namespace.producerA");when(second.getUrn()).thenReturn("same.namespace.producerB");
    for(var model:java.util.List.of(first,second)){when(model.getObservables()).thenReturn(java.util.List.of(observable));when(model.getComputation()).thenReturn(java.util.List.of());graph.graph().addVertex(model);}
    var compiler=new DataflowCompiler(null,graph,scope);
    for(var model:java.util.List.of(first,first,second))compiler.compileModel(actuator,null,Geometry.create("*"),mock(org.integratedmodelling.klab.api.knowledge.ObservationStrategy.class),model,null);
    org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("same.namespace.producerA","same.namespace.producerA","same.namespace.producerB"),actuator.getData().get(org.integratedmodelling.klab.api.services.runtime.Actuator.PRODUCING_MODELS_KEY));assertTrue(actuator.getComputation().isEmpty());
    var unnamed=mock(org.integratedmodelling.klab.api.knowledge.Model.class);when(unnamed.getObservables()).thenReturn(java.util.List.of(observable));when(unnamed.getComputation()).thenReturn(java.util.List.of());graph.graph().addVertex(unnamed);
    compiler.compileModel(actuator,null,Geometry.create("*"),mock(org.integratedmodelling.klab.api.knowledge.ObservationStrategy.class),unnamed,null);
    org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("same.namespace.producerA","same.namespace.producerA","same.namespace.producerB",""),actuator.getData().get(org.integratedmodelling.klab.api.services.runtime.Actuator.PRODUCING_MODELS_KEY));

  }

}
