package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.junit.jupiter.api.Test;

class ConnectionExecutionTest {
  static ObservationImpl observation(long id, SemanticType type, boolean collective, boolean bond) {
    var concept = new ConceptImpl();
    concept.setUrn("test:" + type + (bond ? "Bond" : ""));
    concept.setName(type.name()); concept.setReferenceName(type.name().toLowerCase());
    var types = EnumSet.of(type, SemanticType.COUNTABLE);
    if (bond) types.add(SemanticType.BIDIRECTIONAL);
    concept.setType(types); concept.setCollective(collective);
    var observable = new ObservableImpl(); observable.setSemantics(concept);
    observable.setUrn(concept.getUrn());
    observable.setDescriptionType(collective ? Contextualization.CONNECTION : Contextualization.ACKNOWLEDGEMENT);
    var result = new ObservationImpl(); result.setId(id); result.setObservable(observable);
    result.setUrn("test:instance" + id);
    return result;
  }

  @Test void everyConnectionIsAcknowledgedBetweenItsOwnParticipantsWithProducerConstraints() {
    for (boolean bond : List.of(false, true)) {
      var collective = observation(-10, SemanticType.RELATIONSHIP, true, bond);
      var source = observation(11, SemanticType.SUBJECT, false, false);
      var target = observation(12, SemanticType.SUBJECT, false, false);
      var child = observation(-20, SemanticType.RELATIONSHIP, false, bond);
      child.setParticipants(List.of(source, target));
      var scope = scope(source, target);
      var within = mock(ContextScope.class); var between = mock(ContextScope.class);
      var member = mock(ContextScope.class);
      when(scope.within(collective)).thenReturn(within);
      when(within.between(source, target)).thenReturn(between);
      var constraints = List.of(ResolutionConstraint.of(ResolutionConstraint.Type.ResolutionNamespace, "connector.model"));
      when(between.withResolutionConstraints(constraints.toArray(ResolutionConstraint[]::new))).thenReturn(member);
      var client = mock(org.integratedmodelling.klab.api.services.RuntimeService.class);
      when(member.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(client);
      when(client.submit(child, member)).thenReturn(CompletableFuture.completedFuture(child));
      var outcomes = new ContextualizationScopeImpl(collective, null);
      outcomes.getOutcomes().add(child); outcomes.bindOutcomes(0, constraints);
      var runtime = mock(RuntimeService.class, CALLS_REAL_METHODS);
      runtime.submitContextualizationResult(outcomes, scope, Activity.Outcome.SUCCESS);
      verify(within).between(source, target);
      verify(client).submit(child, member);
      when(client.submit(child, member)).thenReturn(CompletableFuture.completedFuture(Observation.empty()));
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException.class,
          () -> runtime.submitContextualizationResult(outcomes, scope, Activity.Outcome.SUCCESS));
    }
  }

  @Test void directedRelationshipsAndBondsUseDifferentEdgesFromTheObservation() {
    var source = observation(11, SemanticType.SUBJECT, false, false);
    var target = observation(12, SemanticType.SUBJECT, false, false);
    for (boolean bond : List.of(false, true)) {
      var scope = scope(source, target);
      var transaction = mock(DigitalTwin.Transaction.class);
      when(scope.getCurrentTransaction()).thenReturn(transaction);
      var relationship = observation(-20, SemanticType.RELATIONSHIP, false, bond);
      relationship.setParticipants(List.of(source, target));
      ConnectionSupport.link(relationship, scope);
      verify(transaction).link(relationship, source, bond
          ? GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT : GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE);
      verify(transaction).link(relationship, target, bond
          ? GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT : GraphModel.Relationship.HAS_RELATIONSHIP_TARGET);
      verifyNoMoreInteractions(transaction);
    }
  }

  @Test void invalidParticipantsFailBeforeAnyMemberIsSubmitted() {
    var source = observation(11, SemanticType.SUBJECT, false, false);
    var target = observation(12, SemanticType.SUBJECT, false, false);
    var scope = scope(source, target);
    var child = observation(-20, SemanticType.RELATIONSHIP, false, false);
    assertThrows(IllegalArgumentException.class, () -> ConnectionSupport.participants(child, scope));
    child.setParticipants(List.of(source, source));
    assertThrows(IllegalArgumentException.class, () -> ConnectionSupport.participants(child, scope));
    child.setParticipants(List.of(source, observation(99, SemanticType.SUBJECT, false, false)));
    assertThrows(IllegalArgumentException.class, () -> ConnectionSupport.participants(child, scope));
    var collective = observation(-10, SemanticType.RELATIONSHIP, true, false);
    var outcomes = new ContextualizationScopeImpl(collective, null);
    outcomes.getOutcomes().add(child);
    var runtime = mock(RuntimeService.class, CALLS_REAL_METHODS);
    assertThrows(IllegalArgumentException.class,
        () -> runtime.submitContextualizationResult(outcomes, scope, Activity.Outcome.SUCCESS));
    verify(scope, never()).within(any());
  }

  @Test void emptyConnectionResultSucceedsWithoutAcknowledgements() {
    var collective = observation(-10, SemanticType.RELATIONSHIP, true, false);
    var scope = mock(ContextScope.class);
    var runtime = mock(RuntimeService.class, CALLS_REAL_METHODS);
    assertDoesNotThrow(() -> runtime.submitContextualizationResult(
        new ContextualizationScopeImpl(collective, null), scope, Activity.Outcome.SUCCESS));
    verify(scope, never()).getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
  }

  private ContextScope scope(Observation source, Observation target) {
    var scope = mock(ContextScope.class);
    when(scope.getObservation(source.getId())).thenReturn(source);
    when(scope.getObservation(target.getId())).thenReturn(target);
    when(scope.getService(Reasoner.class)).thenReturn(mock(Reasoner.class));
    return scope;
  }

  @Test void scopeQueriesReturnRelationshipObservationsAndBothBondDirections() throws Exception {
    var scope = mock(org.integratedmodelling.klab.services.scopes.ServiceContextScope.class, CALLS_REAL_METHODS);
    var transaction = mock(DigitalTwin.Transaction.class);
    var txField = org.integratedmodelling.klab.services.scopes.ServiceContextScope.class.getDeclaredField("currentTransaction");
    txField.setAccessible(true); txField.set(scope, transaction);
    var source = observation(-11, SemanticType.SUBJECT, false, false);
    var target = observation(-12, SemanticType.SUBJECT, false, false);
    var directed = observation(-20, SemanticType.RELATIONSHIP, false, false);
    var bond = observation(-21, SemanticType.RELATIONSHIP, false, true);
    var sourceEdge = edge(directed, source, GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE);
    var targetEdge = edge(directed, target, GraphModel.Relationship.HAS_RELATIONSHIP_TARGET);
    var bondSource = edge(bond, source, GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT);
    var bondTarget = edge(bond, target, GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT);
    when(transaction.incoming(source)).thenReturn(List.of(sourceEdge, bondSource));
    when(transaction.incoming(target)).thenReturn(List.of(targetEdge, bondTarget));
    when(transaction.outgoing(directed)).thenReturn(List.of(targetEdge, sourceEdge));
    when(transaction.outgoing(bond)).thenReturn(List.of(bondSource, bondTarget));
    assertEquals(Set.of(directed, bond), new HashSet<>(scope.getOutgoingRelationshipsOf(source)));
    assertEquals(List.of(bond), new ArrayList<>(scope.getIncomingRelationshipsOf(source)));
    assertEquals(Set.of(directed, bond), new HashSet<>(scope.getIncomingRelationshipsOf(target)));
    assertEquals(List.of(source, target), scope.getRelationshipParticipants(directed));
    assertEquals(Set.of(source, target), new HashSet<>(scope.getRelationshipParticipants(bond)));
  }

  private org.integratedmodelling.klab.api.data.KnowledgeGraph.Link edge(
      Observation source, Observation target, GraphModel.Relationship type) {
    var edge = mock(org.integratedmodelling.klab.api.data.KnowledgeGraph.Link.class);
    when(edge.source()).thenReturn(source); when(edge.target()).thenReturn(target); when(edge.type()).thenReturn(type);
    return edge;
  }
}
