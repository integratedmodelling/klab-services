package org.integratedmodelling.common.documentation;

import org.integratedmodelling.klab.api.documentation.FlowChart;
import org.integratedmodelling.klab.api.documentation.WorkflowFlowChartAdapter;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.services.resources.workflow.impl.WorkflowImpl;
import org.junit.jupiter.api.Test;

class FlowChartTest {
  @Test
  void plainJsonRoundTripPreservesHierarchyPortsAndExtensions() throws Exception {
    var chart = FlowChart.builder("root").metadata("report", "example")
        .root(root -> root.port("in", FlowChart.Role.INPUT)
            .port("out", FlowChart.Role.OUTPUT)
            .node("child", child -> child.size(120, 60)
                .metadata("nested", Map.of("values", List.of(1, true)))
                .port("child-in", FlowChart.Role.INPUT)
                .port("child-out", FlowChart.Role.OUTPUT))
            .link("incoming", "in", "child-in", link -> link.getMetadata().put("kind", "data"))
            .link("outgoing", "child-out", "out"))
        .build();
    var mapper = new ObjectMapper();
    String json = mapper.writeValueAsString(chart);
    assertFalse(json.contains("@class"));
    var restored = mapper.readValue(json, FlowChart.class);
    restored.validate();
    assertEquals(mapper.readTree(json), mapper.valueToTree(restored));
    assertEquals(FlowChart.Role.INPUT, restored.getRoot().getPorts().getFirst().getRole());
    var elk = mapper.valueToTree(chart.getRoot());
    assertEquals("child-in", elk.path("edges").get(0).path("targets").get(0).asText());
    assertTrue(elk.has("children"));
  }

  @Test
  void retainsHyperedgesAndLayoutResultsAndSupportsCustomAdapters() throws Exception {
    FlowChart.Adapter<String> adapter = id -> FlowChart.builder(id)
        .root(root -> root.node("a", n -> {}).node("b", n -> {}).node("c", n -> {})
            .link("hyperedge", "a", "c", edge -> {
              edge.getSources().add("b");
              var section = new FlowChart.Section();
              section.setId("section");
              var start = new FlowChart.Point(); start.setX(10); start.setY(20);
              var end = new FlowChart.Point(); end.setX(30); end.setY(40);
              section.setStartPoint(start); section.setEndPoint(end);
              section.getMetadata().put("routed", true);
              edge.getSections().add(section);
            })).build();
    var chart = FlowChart.adapt("root", adapter);
    var mapper = new ObjectMapper();
    var restored = mapper.readValue(mapper.writeValueAsString(chart), FlowChart.class);
    restored.validate();
    assertEquals(mapper.valueToTree(chart), mapper.valueToTree(restored));
    assertEquals(List.of("a", "b"), restored.getRoot().getEdges().getFirst().getSources());
  }

  @Test
  void validatesIdentityAndReferencesButAllowsLoopsAndIsolatedComponents() {
    assertThrows(IllegalArgumentException.class, () -> FlowChart.builder("root")
        .root(root -> root.node("root", n -> {})).build());
    assertThrows(IllegalArgumentException.class, () -> FlowChart.builder("root")
        .root(root -> root.link("edge", "root", "missing")).build());
    assertThrows(IllegalArgumentException.class, () -> FlowChart.builder("root")
        .root(root -> root.port("port", null)).build());
    var isolated = FlowChart.builder("root")
        .root(root -> root.port("in", FlowChart.Role.INPUT).port("out", FlowChart.Role.OUTPUT))
        .build();
    isolated.getRoot().getChildren().add(isolated.getRoot());
    assertThrows(IllegalArgumentException.class, isolated::validate);
    FlowChart.builder("root").root(root -> root.node("n", n -> {})
        .link("loop", "n", "n")).build();
  }

  private WorkflowImpl workflow() {
    var workflow = new WorkflowImpl(); workflow.setId("review"); workflow.setName("Review");
    for (String id : List.of("draft", "done", "isolated")) {
      var state = new WorkflowImpl.StateSchemaImpl(); state.setId(id);
      workflow.getStates().put(id, state);
    }
    var transition = new WorkflowImpl.TransitionSchemaImpl(); transition.setId("submit");
    transition.setSourceStates(new LinkedHashSet<>(List.of("INIT", "draft", "done")));
    transition.setTargetState("done"); workflow.getTransitions().put("submit", transition);
    return workflow;
  }

  @Test
  void workflowProjectionPreservesAlternativesInitAndDisconnectedStates() throws Exception {
    var workflow = workflow();
    workflow.getMetadata().put("extension", new ArrayList<>(List.of("original")));
    var chart = FlowChart.adapt(workflow, new WorkflowFlowChartAdapter());
    assertEquals(4, chart.getRoot().getChildren().size());
    assertEquals(3, chart.getRoot().getEdges().size());
    assertTrue(chart.getRoot().getEdges().stream().allMatch(e -> e.getSources().size() == 1));
    assertEquals("submit", chart.getRoot().getEdges().getFirst().getMetadata().get("transitionId"));
    var mapper = new ObjectMapper();
    var restored = mapper.readValue(mapper.writeValueAsString(chart), FlowChart.class);
    restored.validate();
    workflow.getMetadata().clear();
    assertTrue(((Map<?, ?>) chart.getRoot().getMetadata().get("properties")).containsKey("extension"));
    assertEquals(mapper.valueToTree(chart), mapper.valueToTree(restored));
  }

  @Test
  void workflowRejectsDanglingStatesAndNonJsonMetadata() {
    var workflow = workflow(); workflow.getTransitions().get("submit").setTargetState("missing");
    assertThrows(IllegalArgumentException.class, () -> new WorkflowFlowChartAdapter().adapt(workflow));
    workflow.getTransitions().get("submit").setTargetState("done");
    workflow.getMetadata().put("object", new Object());
    assertThrows(IllegalArgumentException.class, () -> new WorkflowFlowChartAdapter().adapt(workflow));
  }
}
