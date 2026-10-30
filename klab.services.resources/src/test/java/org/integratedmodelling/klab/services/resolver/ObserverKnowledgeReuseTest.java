package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.EnumSet;
import java.util.List;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.services.scopes.*;
import org.junit.jupiter.api.Test;

class ObserverKnowledgeReuseTest {
  @org.junit.jupiter.api.BeforeAll static void configure() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
  }
  private ObservableImpl observable(String name, SemanticType type) {
    var concept = new ConceptImpl(); concept.setUrn(name); concept.setType(EnumSet.of(type));
    var result = new ObservableImpl(); result.setSemantics(concept); result.setUrn(name); return result;
  }
  private ObservationImpl observation(long id, ObservableImpl meaning) {
    var result = new ObservationImpl(); result.setId(id); result.setUrn("urn:observation:"+id); result.setObservable(meaning); result.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.create("1")); return result;
  }
  private ObservableImpl perspective(ObservableImpl base, ObservableImpl agent) {
    var result = new ObservableImpl(base); result.setObserverSemantics(agent.asConcept());
    result.setUrn(base.getUrn()+" observed as "+agent.getUrn()); return result;
  }
  private ServiceContextScope scope(KnowledgeGraph graph, Reasoner reasoner) {
    // This fixture exercises candidate lookup, not a graph semantic-revision transition.
    when(graph.getSemanticRevision()).thenReturn(-1L);
    var user = mock(UserIdentity.class); when(user.getId()).thenReturn("alice");
    var result = new ServiceContextScope(new ServiceSessionScope(new ServiceUserScope(user, mock(RuntimeService.class))),
        DigitalTwin.Configuration.builder().id("observer-reuse").name("test").build(), user);
    var twin = mock(DigitalTwin.class); when(twin.getKnowledgeGraph()).thenReturn(graph); result.setDigitalTwin(twin);
    var receipt = mock(Reasoner.Capabilities.class); when(receipt.getKnowledgeRevision()).thenReturn(7L);
    when(reasoner.capabilities(any())).thenReturn(receipt);
    var status = mock(org.integratedmodelling.klab.api.services.KlabService.ServiceStatus.class);
    when(status.isOperational()).thenReturn(true); when(reasoner.status()).thenReturn(status);
    result.addService(reasoner); return result;
  }
  @SuppressWarnings("unchecked")
  @Test void implicitLookupPrefersCompatibleSpecificAcrossAllVisibleMatchesWithoutInstanceEquality() {
    var graph = mock(KnowledgeGraph.class); var reasoner = mock(Reasoner.class);
    var query = (KnowledgeGraph.Query<Observation>) mock(KnowledgeGraph.Query.class, RETURNS_SELF);
    when(graph.query(eq(Observation.class), any())).thenReturn(query);
    var quality = observable("test:Quality", SemanticType.QUALITY); var observerClass = observable("test:GeneralObserver", SemanticType.AGENT);
    var selectedClass = observable("test:SelectedObserver", SemanticType.AGENT); var incompatible = observable("test:Incompatible", SemanticType.AGENT);
    var selected = observation(100, selectedClass); when(graph.getAsset(eq(100L), any(), eq(Observation.class))).thenReturn(selected);
    var absolute = observation(41, quality); var specific = observation(42, perspective(quality, observerClass));
    var wrong = observation(43, perspective(quality, incompatible));
    when(query.run(any())).thenReturn(List.of(absolute, wrong, specific));
    when(reasoner.semanticDistance(eq(observerClass.asConcept()), eq(selectedClass.asConcept()), isNull(), eq(7L))).thenReturn(1);
    when(reasoner.semanticDistance(eq(incompatible.asConcept()), eq(selectedClass.asConcept()), isNull(), eq(7L))).thenReturn(-1);
    when(reasoner.semanticDistance(eq(specific.getObservable()), eq(quality), isNull(), eq(7L))).thenReturn(0);
    var scope = scope(graph, reasoner).withObserver(selected);
    assertSame(specific, scope.getObservation(observation(-2, quality)));
    assertNotEquals(selected.getId(), specific.getId());
    assertNull(quality.getObserverSemantics());
  }
  @SuppressWarnings("unchecked")
  @Test void explicitLookupRejectsAbsoluteAndOtherPerspectiveDespiteDifferentSessionObserver() {
    var graph = mock(KnowledgeGraph.class); var reasoner = mock(Reasoner.class);
    var query = (KnowledgeGraph.Query<Observation>) mock(KnowledgeGraph.Query.class, RETURNS_SELF);
    when(graph.query(eq(Observation.class), any())).thenReturn(query);
    var quality = observable("test:Quality", SemanticType.QUALITY); var first = observable("test:First", SemanticType.AGENT);
    var second = observable("test:Second", SemanticType.AGENT);
    var requested = perspective(quality, second); var absolute = observation(41, quality);
    when(query.run(any())).thenReturn(List.of(absolute));
    var scope = scope(graph, reasoner);
    assertNull(scope.getObservation(observation(-2, requested)));
    var compatible = observation(42, perspective(quality, second)); var wrong = observation(43, perspective(quality, first));
    when(query.run(any())).thenReturn(List.of(absolute, wrong, compatible));
    when(reasoner.semanticDistance(eq(wrong.getObservable()), eq(requested), isNull(), eq(7L))).thenReturn(-1);
    when(reasoner.semanticDistance(eq(compatible.getObservable()), eq(requested), isNull(), eq(7L))).thenReturn(0);
    assertSame(compatible, scope.getObservation(observation(-2, requested)));
  }
  @SuppressWarnings("unchecked")
  @Test void narrowSpecificCannotHideCompleteAbsoluteAndUnknownSupportDoesNotCount() {
    var graph = mock(KnowledgeGraph.class); var reasoner = mock(Reasoner.class);
    var query = (KnowledgeGraph.Query<Observation>) mock(KnowledgeGraph.Query.class, RETURNS_SELF);
    when(graph.query(eq(Observation.class), any())).thenReturn(query);
    var quality = observable("test:Quality", SemanticType.QUALITY); var observer = observable("test:Observer", SemanticType.AGENT);
    var selected = observation(100, observer); when(graph.getAsset(eq(100L), any(), eq(Observation.class))).thenReturn(selected);
    var absolute = observation(41, quality); var specific = observation(42, perspective(quality, observer));
    var support = org.integratedmodelling.klab.api.geometry.Geometry.create("T0(1){tstart=0,tend=10,ttype=PHYSICAL}");
    absolute.setGeometry(support); specific.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.create("T0(1){tstart=0,tend=5,ttype=PHYSICAL}"));
    var requested = observation(-2, quality); requested.setGeometry(support);
    when(query.run(any())).thenReturn(List.of(specific, absolute));
    when(reasoner.semanticDistance(any(), any(), isNull(), eq(7L))).thenReturn(0);
    var scope = scope(graph, reasoner).withObserver(selected);
    assertSame(absolute, scope.getObservation(requested));
    specific.setGeometry(support); assertSame(specific, scope.getObservation(requested));
    specific.setGeometry(null); assertSame(absolute, scope.getObservation(requested));
    absolute.setGeometry(null); assertNull(scope.getObservation(requested));
  }
  @SuppressWarnings("unchecked")
  @Test void unboundedIdZeroLookupUsesKnownSourceSupportWithoutInstantiation() {
    var graph = mock(KnowledgeGraph.class); var reasoner = mock(Reasoner.class);
    var query = (KnowledgeGraph.Query<Observation>) mock(KnowledgeGraph.Query.class, RETURNS_SELF);
    when(graph.query(eq(Observation.class), any())).thenReturn(query);
    var quality = observable("test:Quality", SemanticType.QUALITY);
    var existing = observation(42, quality); when(query.run(any())).thenReturn(List.of(existing));
    var request = observation(Observation.QUERY_ID, quality); request.setGeometry(null);
    var scope = scope(graph, reasoner);
    assertSame(existing, scope.getObservation(request));
    assertEquals(Observation.QUERY_ID, request.getId()); assertNull(request.getGeometry());
    verify(graph, never()).createTransaction(any());
  }
  @Test void identicalScalarSupportIsKnownCompleteWhileMissingOrEmptySupportIsNot() {
    var scalar = org.integratedmodelling.klab.api.geometry.Geometry.create("1");
    assertTrue(org.integratedmodelling.klab.runtime.ObserverReuseSupport.coverage(scalar, scalar).isComplete());
    assertNull(org.integratedmodelling.klab.runtime.ObserverReuseSupport.coverage(
        (org.integratedmodelling.klab.api.geometry.Geometry)null, scalar));
    assertFalse(org.integratedmodelling.klab.runtime.ObserverReuseSupport.coverage(
        org.integratedmodelling.klab.api.geometry.Geometry.EMPTY, scalar).isComplete());
  }
}
