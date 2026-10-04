package org.integratedmodelling.klab.services.resources.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.junit.jupiter.api.Test;

class PredicateModelDiscoveryTest {
  @Test void discoveryFailureCannotMasqueradeAsNoModel() throws Exception {
    var box = mock(ModelKbox.class, CALLS_REAL_METHODS);
    var database = mock(org.integratedmodelling.klab.persistence.h2.H2Database.class);
    var context = mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
    var request = mock(Observable.class);
    var geometry = mock(org.integratedmodelling.klab.api.geometry.Geometry.class);
    var failure = new IllegalStateException("Reasoner unavailable");
    doNothing().when(box).initialize(context);
    var databaseField = org.integratedmodelling.klab.persistence.h2.H2Kbox.class.getDeclaredField("database");
    databaseField.setAccessible(true);
    databaseField.set(box, database);
    when(database.hasTable("model")).thenReturn(true);
    when(request.getUrn()).thenReturn("earth:PhysicalEnvironment of each earth:Region");
    doThrow(failure).when(box).queryModels(request, geometry, null, List.of(), context);
    var thrown = assertThrows(org.integratedmodelling.klab.api.exceptions.KlabStorageException.class,
        () -> box.query(request, geometry, null, List.of(), context));
    assertSame(failure, thrown.getCause());
  }

  @Test void persistedConceptsRemainDiscoverableAfterRestart() throws Exception {
    var box = mock(ModelKbox.class, CALLS_REAL_METHODS);
    var scope = mock(Scope.class);
    var reasoner = mock(Reasoner.class);
    var request = mock(Observable.class);
    var head = concept("earth:PhysicalEnvironment");
    var classifier = concept("earth:PhysicalEnvironment of each earth:Region");
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(request.getContextualization()).thenReturn(Contextualization.CLASSIFICATION);
    when(reasoner.coreObservable(request)).thenReturn(head);
    when(reasoner.coreObservable(classifier)).thenReturn(head);
    when(reasoner.resolveConcept(classifier.getUrn())).thenReturn(classifier);
    when(reasoner.resolving(head)).thenReturn(List.of(head));
    when(reasoner.semanticDistance(classifier, request, null)).thenReturn(0);
    field(box, "scope", scope);
    field(box, "definitionHash", Map.of(classifier.getUrn(), 42L));
    field(box, "coreTypeHash", new HashMap<>());
    field(box, "conceptHash", new HashMap<>());
    assertEquals(Set.of(42L), box.getCompatibleTypeIds(request, null));
    assertEquals(Set.of(42L), box.getCompatibleTypeIds(request, null));
    verify(reasoner, times(1)).resolveConcept(classifier.getUrn());
  }

  @Test void reRegisteringPersistedConceptRestoresCandidatesWithoutWritingDatabase() throws Exception {
    var box = mock(ModelKbox.class, CALLS_REAL_METHODS);
    var scope = mock(Scope.class);
    var reasoner = mock(Reasoner.class);
    var request = mock(Observable.class);
    var head = concept("earth:Region");
    var candidate = concept("each earth:Region");
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(request.getContextualization()).thenReturn(Contextualization.INSTANTIATION);
    when(reasoner.coreObservable(request)).thenReturn(head);
    when(reasoner.coreObservable(candidate)).thenReturn(head);
    when(reasoner.resolving(head)).thenReturn(List.of(head));
    when(reasoner.semanticDistance(candidate, request, null)).thenReturn(0);
    field(box, "scope", scope);
    field(box, "definitionHash", Map.of(candidate.getUrn(), 7L));
    field(box, "coreTypeHash", new HashMap<>());
    field(box, "conceptHash", new HashMap<>());
    assertEquals(7L, box.requireConceptId(candidate, scope));
    assertEquals(Set.of(7L), box.getCompatibleTypeIds(request, null));
    verify(reasoner, never()).resolveConcept(anyString());
  }

  @Test void temporarilyUnavailablePersistedConceptIsRetried() throws Exception {
    var box = mock(ModelKbox.class, CALLS_REAL_METHODS);
    var scope = mock(Scope.class);
    var reasoner = mock(Reasoner.class);
    var request = mock(Observable.class);
    var head = concept("earth:PhysicalEnvironment");
    var candidate = concept("earth:PhysicalEnvironment of each earth:Region");
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(request.getContextualization()).thenReturn(Contextualization.CLASSIFICATION);
    when(reasoner.coreObservable(request)).thenReturn(head);
    when(reasoner.coreObservable(candidate)).thenReturn(head);
    when(reasoner.resolving(head)).thenReturn(List.of(head));
    when(reasoner.resolveConcept(candidate.getUrn())).thenReturn(null, candidate);
    when(reasoner.semanticDistance(candidate, request, null)).thenReturn(0);
    field(box, "scope", scope);
    field(box, "definitionHash", Map.of(candidate.getUrn(), 42L));
    field(box, "coreTypeHash", new HashMap<>());
    field(box, "conceptHash", new HashMap<>());
    assertTrue(box.getCompatibleTypeIds(request, null).isEmpty());
    assertEquals(Set.of(42L), box.getCompatibleTypeIds(request, null));
  }

  @Test void modelIndexIncludesExactAndSubsumingHeadsThenChecksFullSemantics() throws Exception {
    var box = mock(ModelKbox.class, CALLS_REAL_METHODS);
    var scope = mock(Scope.class); var reasoner = mock(Reasoner.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    var request = mock(Observable.class);
    when(request.getContextualization()).thenReturn(Contextualization.CHARACTERIZATION);
    var child = concept("P2"); var parent = concept("P1");
    var exact = concept("P2 of S"); var broad = concept("P1 of S"); var wrongBearer = concept("P1 of T");
    when(reasoner.coreObservable(request)).thenReturn(child);
    when(reasoner.resolving(child)).thenReturn(List.of(child,parent));
    when(reasoner.semanticDistance(exact,request,null)).thenReturn(0);
    when(reasoner.semanticDistance(broad,request,null)).thenReturn(50);
    when(reasoner.semanticDistance(wrongBearer,request,null)).thenReturn(-50);
    field(box,"scope",scope);
    field(box,"coreTypeHash",Map.of("P2",Set.of("P2 of S"),"P1",Set.of("P1 of S","P1 of T")));
    field(box,"conceptHash",Map.of("P2 of S",exact,"P1 of S",broad,"P1 of T",wrongBearer));
    field(box,"definitionHash",Map.of("P2 of S",1L,"P1 of S",2L,"P1 of T",3L));
    assertEquals(Set.of(1L,2L),box.getCompatibleTypeIds(request,null));
  }
  private Concept concept(String urn) { var c=mock(Concept.class); when(c.getUrn()).thenReturn(urn); return c; }
  private void field(Object target,String name,Object value) throws Exception {
    var field=ObservableKbox.class.getDeclaredField(name);field.setAccessible(true);field.set(target,value);
  }
}
