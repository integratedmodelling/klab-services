package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.ProcessPlan;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.Test;

class ProcessOwnershipTest {
  static Object field(CompiledDataflow compiled, String name) throws Exception {
    var field = CompiledDataflow.class.getDeclaredField(name); field.setAccessible(true);
    return field.get(compiled);
  }
  @Test void qualityOwnershipUsesItsProcessBearerInsteadOfTheRootOrProcess() throws Exception {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var root = ConnectionExecutionTest.observation(-10, SemanticType.SUBJECT, false, false);
    var bearer = ConnectionExecutionTest.observation(-20, SemanticType.EVENT, false, false);
    var process = ConnectionExecutionTest.observation(-30, SemanticType.PROCESS, false, false);
    var quality = ConnectionExecutionTest.observation(-40, SemanticType.QUALITY, false, false);
    quality.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.create(
        "T0(1){tstart=1388534400000,tend=1420070400000,ttype=PHYSICAL}"));
    var scope = mock(ServiceContextScope.class, RETURNS_DEEP_STUBS);
    when(scope.getContextObservation()).thenReturn(root);
    when(scope.getObservation(-20)).thenReturn(bearer);
    var compiled = new CompiledDataflow(mock(RuntimeService.class), root, scope);
    var plan = new ActuatorImpl(); plan.setId(-30); plan.setObservation(process);
    plan.setExecutionRole(Actuator.ExecutionRole.PROCESS);
    var input = new ActuatorImpl(); input.setId(-40); input.setName("elevation"); input.setObservation(quality);
    plan.getChildren().add(input);
    var parentQuality = new org.integratedmodelling.common.knowledge.ConceptImpl();
    parentQuality.setUrn("test:ParentQuality"); parentQuality.setName("ParentQuality"); parentQuality.setNamespace("test");
    parentQuality.getType().add(SemanticType.QUALITY);
    var reasoner = mock(org.integratedmodelling.klab.api.services.Reasoner.class);
    doReturn(reasoner).when(scope).getService(org.integratedmodelling.klab.api.services.Reasoner.class);
    when(reasoner.is(quality.getObservable(), parentQuality)).thenReturn(true);
    var relation = new org.integratedmodelling.klab.api.knowledge.SemanticInfluence(parentQuality,
        quality.getObservable().getSemantics(), org.integratedmodelling.klab.api.knowledge.SemanticInfluence.Kind.INCREASES_WITH,
        "test:inherited restriction");
    var bindings = new ProcessPlan(2, -20, "test:model", List.of(
        new ProcessPlan.Binding("elevation", quality.getObservable(), ProcessPlan.Effect.AFFECTED, true, true)), List.of(), List.of(relation));
    plan.getData().put(ProcessPlan.DATA_KEY, Utils.Json.asString(bindings));
    ((Map<Actuator, Observation>) field(compiled, "actuatorObservations")).putAll(Map.of(plan, process, input, quality));
    ((Map<Long, Observation>) field(compiled, "dependentObservations")).put(quality.getId(), quality);
    var graphField = CompiledDataflow.class.getDeclaredField("dependencyGraph"); graphField.setAccessible(true);
    graphField.set(compiled, mock(org.jgrapht.Graph.class));
    var transaction = mock(DigitalTwinImpl.TransactionImpl.class);
    assertTrue(compiled.store(transaction));
    verify(transaction).link(bearer, quality, GraphModel.Relationship.HAS_CHILD);
    verify(transaction, never()).link(process, quality, GraphModel.Relationship.HAS_CHILD);
    verify(transaction, never()).link(root, quality, GraphModel.Relationship.HAS_CHILD);
    verify(transaction).linkProcessInfluence(process, quality, "test:model", "elevation", List.of());
    verify(transaction).link(quality, quality, GraphModel.Relationship.AFFECTS,
        ProcessPlan.EDGE_ROLE, ProcessPlan.DESCRIPTIVE, "property", "odo:increasesWith",
        "semanticRelation", "INCREASES_WITH", "provenance", "test:inherited restriction",
        "declaredSource", "test:ParentQuality", "declaredTarget", quality.getObservable().getSemantics().getUrn(),
        "model", "test:model", "bearerId", bearer);
    assertTrue(quality.isSubstantialQuality());
    var callback = org.mockito.ArgumentCaptor.forClass(Runnable.class);
    verify(transaction).beforeCommit(callback.capture());
    bearer.setId(200);
    callback.getValue().run();
    assertEquals(200, CompiledDataflow.processPlan(plan).bearerId());
    verify(transaction).update(plan);
  }
}
