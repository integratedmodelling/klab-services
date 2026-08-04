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
