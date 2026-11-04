package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.impl.LinkImpl;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.services.runtime.CompiledDataflow;
import org.integratedmodelling.klab.services.runtime.RuntimeService;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;

class DependencyOnlyCollectiveConsumerTest {
  private static ObservationImpl observation(long id, boolean collective) {
    var concept = new ConceptImpl();
    concept.setUrn(collective ? "each test:Stream" : "test:Region");
    concept.setName(collective ? "Stream" : "Region");
    concept.setReferenceName(collective ? "each_stream" : "region");
    concept.setType(EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE));
    concept.setCollective(collective);
    var observable = new ObservableImpl();
    observable.setUrn(concept.getUrn());
    observable.setSemantics(concept);
    var result = new ObservationImpl();
    result.setId(id);
    result.setObservable(observable);
    result.setGeometry(Geometry.create("1"));
    return result;
  }

  private static ActuatorImpl plan(Observation observation) {
    var result = new ActuatorImpl();
    result.setId(observation.getId());
    result.setObservation(observation);
    result.setActuatorType(Actuator.Type.OBSERVE);
    result.setExecutionRole(Actuator.ExecutionRole.INITIALIZATION);
    return result;
  }

  private static void field(Object target, String name, Object value) throws Exception {
    var field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  @Test
  @SuppressWarnings("unchecked")
  void emptyExplainerRetainsActualInputsBeforeInitAndAfterDurableIdAssignment() throws Exception {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var root = observation(-2, false);
    var producer = observation(-3, true);
    var plan = plan(root);
    var input = plan(producer);
    input.setName("streams");
    plan.getChildren().add(input);
    var cohort = new CohortImpl();
    cohort.setId(70);
    cohort.setObservable(producer.getObservable());
    var scope = mock(ServiceContextScope.class, RETURNS_DEEP_STUBS);
    when(scope.getConsequenceCause()).thenReturn(null);
    when(scope.getResolutionCycle()).thenReturn(null);
    when(scope.getResolutionCycleBinding()).thenReturn(null);
    var twin = mock(DigitalTwinImpl.class);
    var graph = mock(KnowledgeGraph.class);
    when(scope.getDigitalTwin()).thenReturn(twin);
    when(twin.getKnowledgeGraph()).thenReturn(graph);
    when(graph.scope()).thenReturn(RuntimeAsset.CONTEXT_ASSET);
    when(graph.dataflow()).thenReturn(RuntimeAsset.DATAFLOW_ASSET);
    var runtime = mock(RuntimeService.class);
    when(runtime.getCohortFor(any(), same(scope), eq(true))).thenReturn(cohort);
    var transaction = spy(twin.new TransactionImpl(
        Activity.of(Activity.Type.SUBMISSION), scope, RuntimeAsset.PROVENANCE_ASSET, root));
    when(scope.getCurrentTransaction()).thenReturn(transaction);
    var compiled = new CompiledDataflow(runtime, root, scope);
    field(compiled, "rootActuator", plan);
    var bindings = CompiledDataflow.class.getDeclaredField("actuatorObservations");
    bindings.setAccessible(true);
    ((Map<Actuator, Observation>) bindings.get(compiled)).putAll(Map.of(plan, root, input, producer));
    var dependencyGraph = mock(org.jgrapht.Graph.class);
    when(dependencyGraph.vertexSet()).thenReturn(Set.of(plan, input));
    when(dependencyGraph.edgeSet()).thenReturn(Set.of());
    field(compiled, "dependencyGraph", dependencyGraph);
    var scheduler = new SchedulerImpl(scope, twin);
    try {
      assertTrue(compiled.store(transaction));
      assertNull(transaction.getExecutor(root));
      assertSame(plan, transaction.getInputPlan(root));
      assertNull(root.getMetadata().get(Scheduler.PLAN_METADATA_KEY),
          "Portable snapshots require durable IDs; INIT must use the transaction plan");

      // Read the actual staged cohort edge, including its pre-commit source identity.
      var graphField = DigitalTwinImpl.TransactionImpl.class.getDeclaredField("graph");
      graphField.setAccessible(true);
      var staged = (org.jgrapht.Graph) graphField.get(transaction);
      var edge = staged.getAllEdges(cohort, root).iterator().next();
      var propertiesField = edge.getClass().getDeclaredField("properties");
      propertiesField.setAccessible(true);
      var properties = (Map<String, Object>) propertiesField.get(edge);
      assertEquals(-3L, properties.get(ProcessPlan.COLLECTIVE_SOURCE_OBSERVATION));
      var link = new LinkImpl(cohort, root, GraphModel.Relationship.AFFECTS);
      link.properties().putAll(properties);
      when(graph.getLinks(root, GraphModel.Relationship.Direction.INCOMING, scope,
          GraphModel.Relationship.AFFECTS)).thenReturn(List.of(link));
      when(graph.isObservationCurrent(root, scope)).thenReturn(true);
      var reasoner = mock(Reasoner.class);
      doReturn(reasoner).when(scope).getService(Reasoner.class);
      when(reasoner.resolveObservable(anyString())).thenReturn(producer.getObservable());
      when(reasoner.baseSubstantialType(any(), same(scope)))
          .thenReturn(producer.getObservable().getSemantics());
      assertTrue(scheduler.executeDependency(root, root.getGeometry(), Scheduler.Event.initialization(), scope));
      link.properties().put(ProcessPlan.COLLECTIVE_SOURCE_OBSERVATION, -999L);
      assertThrows(IllegalStateException.class,
          () -> ComputationalClosure.requireCollectiveInput(cohort, link, plan, scope));
      link.properties().putAll(properties);

      root.setId(100);
      producer.setId(101);
      var actionsField = DigitalTwinImpl.TransactionImpl.class.getDeclaredField("beforeCommitActions");
      actionsField.setAccessible(true);
      var beforeCommit = (List<Runnable>) actionsField.get(transaction);
      beforeCommit.forEach(Runnable::run);
      var saved = org.integratedmodelling.klab.utilities.Utils.Json.parseObject(
          root.getMetadata().get(Scheduler.PLAN_METADATA_KEY).toString(), Actuator.class);
      assertTrue(saved.getComputation().isEmpty());
      assertEquals(101, saved.getChildren().getFirst().getObservation().getId());
      assertEquals(101L, properties.get(ProcessPlan.COLLECTIVE_SOURCE_OBSERVATION));
      link.properties().putAll(properties);
      assertDoesNotThrow(() -> ComputationalClosure.requireCollectiveInput(cohort, link, saved, scope));
      transaction.fail(new java.util.concurrent.CancellationException("fixture cleanup"));
      assertNull(transaction.getInputPlan(root));
      assertNull(root.getMetadata().get(Scheduler.PLAN_METADATA_KEY));
    } finally {
      scheduler.close();
      transaction.fail(new java.util.concurrent.CancellationException("fixture cleanup"));
    }
  }
}
