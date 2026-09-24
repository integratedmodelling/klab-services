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
    assertTrue(failure.getMessage().contains("ACCEPT_LOSSY_MEDIATIONS=false"));
    assertTrue(failure.getMessage().contains("resolution differs"));
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
}
