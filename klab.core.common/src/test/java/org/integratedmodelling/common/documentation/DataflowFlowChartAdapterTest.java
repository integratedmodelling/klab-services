package org.integratedmodelling.common.documentation;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.common.runtime.*;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.documentation.*;
import org.integratedmodelling.klab.api.collections.Identifier;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.junit.jupiter.api.Test;

class DataflowFlowChartAdapterTest {
  @Test void preservesRootsSharedDependenciesCallOrderAndActiveOccurrence() throws Exception {
    var producer = node("producer", 42, Actuator.Type.OBSERVE);
    var reference = node("input", 42, Actuator.Type.REFERENCE);
    var consumer = node("consumer", -2, Actuator.Type.OBSERVE);
    consumer.getChildren().add(reference);
    consumer.getChildren().add(producer);
    var parameters = new ArrayList<>(List.of("before"));
    consumer.getComputation().add(new ServiceCallImpl("test.first", "input", Identifier.create("input"), "values", parameters));
    consumer.getComputation().add(new ServiceCallImpl("test.second"));
    var dataflow = new DataflowImpl(); dataflow.getComputation().add(producer); dataflow.getComputation().add(consumer);
    var chart = new DataflowFlowChartAdapter(consumer).adapt(dataflow);
    chart.validate();
    assertEquals(3, chart.getRoot().getChildren().size());
    var active = chart.getRoot().getChildren().stream().filter(n -> Boolean.TRUE.equals(n.getMetadata().get("active"))).findFirst().orElseThrow();
    assertEquals(active.getId(), chart.getMetadata().get("activeNode"));
    assertEquals(2, active.getChildren().size());
    assertEquals(active.getChildren().get(1).getId(), active.getEdges().getFirst().getTargets().getFirst());
    assertEquals(3, chart.getRoot().getEdges().size());
    assertTrue(chart.getRoot().getEdges().stream().anyMatch(e -> "reference".equals(e.getMetadata().get("kind"))));
    parameters.set(0, "after"); consumer.getComputation().clear();
    var mapper = JacksonConfiguration.newObjectMapper();
    String json = mapper.writeValueAsString(chart);
    var restored = mapper.readValue(json, FlowChart.class); restored.validate();
    assertTrue(json.contains("before")); assertFalse(json.contains("after"));
    assertFalse(json.contains("ActuatorImpl"));
  }
  @Test void updateAndUnsupportedArgumentsRemainDiagnosticJson() {
    var update = node("classify", 0, Actuator.Type.UPDATE);
    update.setContextualization(org.integratedmodelling.klab.api.knowledge.Contextualization.CLASSIFICATION);
    update.setEffect(Actuator.Effect.SEMANTIC_UPDATE);
    var binding = new ActuatorImpl.TargetBindingImpl(); binding.setKind(Actuator.TargetBinding.Kind.COHORT_MEMBERS);
    binding.setSources(List.of("members")); update.getTargetBindings().add(binding);
    update.getComputation().add(new ServiceCallImpl("test.classify", "unsupported", new Object()));
    var chart = new DataflowFlowChartAdapter().adapt(update); chart.validate();
    var node = chart.getRoot().getChildren().getFirst();
    assertEquals("CLASSIFICATION", node.getMetadata().get("contextualization"));
    assertEquals(1, ((List<?>) node.getMetadata().get("targetBindings")).size());
    assertThrows(IllegalArgumentException.class, () -> new DataflowFlowChartAdapter(new ActuatorImpl()).adapt(update));
  }
  private static ActuatorImpl node(String name, long id, Actuator.Type type) {
    var ret = new ActuatorImpl(); ret.setName(name); ret.setId(id); ret.setActuatorType(type); return ret;
  }
}
