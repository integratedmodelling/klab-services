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
  @Test void connectionSeesPointMembersInParentTransaction() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var producer = observation(10, SemanticType.SUBJECT, true, false);
    var point = observation(11, SemanticType.SUBJECT, false, false);
    producer.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.create(
        org.integratedmodelling.klab.runtime.scale.space.ShapeImpl.create(
            "EPSG:4326 POLYGON ((0 0, 2 0, 2 2, 0 2, 0 0))").encode()));
    point.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.create(
        org.integratedmodelling.klab.runtime.scale.space.ShapeImpl.create("EPSG:4326 POINT (1 1)").encode()));
    var scope = mock(org.integratedmodelling.klab.services.scopes.ServiceContextScope.class);
    var twin = mock(DigitalTwin.class);
    when(scope.getDigitalTwin()).thenReturn(twin);
    var parent = mock(DigitalTwin.Transaction.class);
    var child = mock(DigitalTwin.Transaction.class);
    when(scope.getCurrentTransaction()).thenReturn(child);
    when(child.getParent()).thenReturn(parent);
    var cohort = mock(org.integratedmodelling.klab.api.knowledge.Cohort.class);
    when(parent.outgoing(cohort)).thenReturn(List.of(
        new org.integratedmodelling.klab.api.data.impl.LinkImpl(cohort, point, GraphModel.Relationship.HAS_MEMBER)));
    var runtime = mock(RuntimeService.class, CALLS_REAL_METHODS);
    doReturn(cohort).when(runtime).getCohortFor(any(), eq(scope), eq(false));
    assertEquals(List.of(point), runtime.getMembers(producer, scope));
  }

  @Test void namedReferenceUsesTheProducerInstanceRegardlessOfPortOrder() throws Exception {
    for (boolean referenceFirst : List.of(false, true)) {
      var root = observation(-1, SemanticType.RELATIONSHIP, true, false);
      var producer = observation(-2, SemanticType.SUBJECT, true, false);
      var scope = mock(org.integratedmodelling.klab.services.scopes.ServiceContextScope.class);
      var compiled = new CompiledDataflow(mock(RuntimeService.class), root, scope);
      var rootActuator = new org.integratedmodelling.common.runtime.ActuatorImpl();
      rootActuator.setObservation(root); rootActuator.setId(-1);
      rootActuator.setActuatorType(org.integratedmodelling.klab.api.services.runtime.Actuator.Type.OBSERVE);
      var source = new org.integratedmodelling.common.runtime.ActuatorImpl();
      source.setObservation(producer); source.setId(-2); source.setName("source");
      source.setActuatorType(org.integratedmodelling.klab.api.services.runtime.Actuator.Type.OBSERVE);
      var target = new org.integratedmodelling.common.runtime.ActuatorImpl();
      target.setObservation(Observation.forTransport(producer)); target.setId(-2); target.setName("target");
      target.setActuatorType(org.integratedmodelling.klab.api.services.runtime.Actuator.Type.REFERENCE);
      rootActuator.getChildren().addAll(referenceFirst ? List.of(target, source) : List.of(source, target));
      var field = CompiledDataflow.class.getDeclaredField("rootActuator"); field.setAccessible(true); field.set(compiled, rootActuator);
      var method = CompiledDataflow.class.getDeclaredMethod("requireObservations", org.integratedmodelling.klab.api.services.runtime.Actuator.class);
      method.setAccessible(true); method.invoke(compiled, rootActuator);
      field = CompiledDataflow.class.getDeclaredField("actuatorObservations"); field.setAccessible(true);
      var bindings = (Map<?, ?>) field.get(compiled);
      assertSame(bindings.get(source), bindings.get(target));
    }
  }

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

  @Test void collectiveRelationshipsUseCohortsAndTopLevelIndividualsNeedNoContext() throws Exception {
    for (boolean bond : List.of(false, true)) {
      var root = observation(-1, SemanticType.SUBJECT, false, false);
      var producer = observation(-2, SemanticType.RELATIONSHIP, true, bond);
      var individual = observation(-3, SemanticType.RELATIONSHIP, false, bond);
      assertEquals(Observation.Role.COLLECTIVE_SUBSTANTIAL, Observation.classifyRole(producer));
      assertEquals(Observation.Role.RELATIONAL, Observation.classifyRole(individual));
      var scope = mock(org.integratedmodelling.klab.services.scopes.ServiceContextScope.class, RETURNS_DEEP_STUBS);
      when(scope.getContextObservation()).thenReturn(null);
      var runtime = mock(RuntimeService.class);
      var cohort = mock(org.integratedmodelling.klab.api.knowledge.Cohort.class);
      when(runtime.getCohortFor(any(), eq(scope), eq(true))).thenReturn(cohort);
      var compiled = new CompiledDataflow(runtime, root, scope);
      var dependencies = CompiledDataflow.class.getDeclaredField("dependentObservations");
      dependencies.setAccessible(true);
      @SuppressWarnings("unchecked")
      var observations = (Map<Long, Observation>) dependencies.get(compiled);
      observations.put(producer.getId(), producer);
      observations.put(individual.getId(), individual);
      var graphField = CompiledDataflow.class.getDeclaredField("dependencyGraph");
      graphField.setAccessible(true);
      graphField.set(compiled, mock(org.jgrapht.Graph.class));
      var transaction = mock(org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.TransactionImpl.class);
      assertTrue(compiled.store(transaction));
      verify(transaction).link(producer, cohort, GraphModel.Relationship.CONTRIBUTED_TO);
      verify(transaction).link(scope.getDigitalTwin().getKnowledgeGraph().scope(), individual,
          GraphModel.Relationship.HAS_CHILD);
    }
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
