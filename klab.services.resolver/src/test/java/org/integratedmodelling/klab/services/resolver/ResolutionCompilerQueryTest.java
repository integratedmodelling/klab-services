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
    when(existing.getId()).thenReturn(42L);

    var result = new ResolutionCompiler(mock(ResolverService.class)).query(observable, requested, scope);

    assertSame(existing, result.result());
    assertSame(existing, result.reference());
    assertSame(requested, result.coveredScale());
    assertTrue(result.coverage().isComplete());
    verify(scope, never()).observation(any());
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
