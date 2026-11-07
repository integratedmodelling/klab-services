package org.integratedmodelling.common.documentation;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.documentation.FlowFlowChartAdapter;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles;
import org.integratedmodelling.klab.api.services.resources.workflow.impl.FlowImpl;
import org.junit.jupiter.api.Test;

class FlowFlowChartAdapterTest {
  @Test void preservesDistinctInstancesAndRecordedBranchesLoopsWithoutProjectingPayloads() throws Exception {
    var flow = Flow.create(); flow.setId("instance"); flow.setWorkflowId("generic");
    flow.setMetadata(Map.of("secret", "PRIVATE-FLOW"));
    for (String id : List.of("step", "step:in", "step:out")) {
      var stage = Flow.State.create(); stage.setId(id); stage.setFlowId(flow.getId()); stage.setSchemaId("editing");
      stage.setStatus(Flow.StateStatus.OPEN); stage.setTitle("PRIVATE-TITLE");
      stage.setDescription("PRIVATE-DESCRIPTION"); stage.setMetadata(Map.of("secret", "PRIVATE-STAGE"));
      flow.getStates().put(id, stage); flow.getCurrentStateIds().add(id);
    }
    for (var pair : List.of(List.of("step", "step:in"), List.of("step", "step:out"), List.of("step:out", "step"))) {
      var event = new FlowImpl.TransactionImpl(); event.setId("event" + flow.getHistory().size());
      event.setFlowId(flow.getId());
      event.setSourceStateId(pair.getFirst()); event.setTargetStateId(pair.getLast());
      event.setTransitionId("recorded"); event.setMetadata(Map.of("secret", "PRIVATE-EVENT"));
      flow.getHistory().add(event);
    }
    var before = org.integratedmodelling.common.utils.Utils.Json.asString(flow);
    var graph = new FlowFlowChartAdapter().adapt(flow); graph.validate();
    assertEquals(3, graph.getRoot().getChildren().size());
    assertEquals(3, graph.getRoot().getEdges().size());
    assertEquals(3, graph.getRoot().getChildren().stream().filter(n -> Boolean.TRUE.equals(n.getMetadata().get("current"))).count());
    assertFalse(new ObjectMapper().writeValueAsString(graph).contains("PRIVATE"));
    assertEquals(before, org.integratedmodelling.common.utils.Utils.Json.asString(flow));
  }

  @Test void neverRecreatesHiddenStagesOrUninstantiatedStagesForLegacyCycles() throws Exception {
    var flow = Flow.create(); flow.setId("projected"); flow.setWorkflowId("review");
    var stage = Flow.State.create(); stage.setId("public-round"); stage.setSchemaId("community-review");
    flow.getStates().put(stage.getId(), stage);
    var hidden = new FlowImpl.TransactionImpl(); hidden.setId("hidden-history");
    hidden.setSourceStateId("SECRET-SOURCE"); hidden.setTargetStateId(stage.getId());
    flow.getHistory().add(hidden);
    var publicRound = new ReviewCycles.Round("visible", ReviewCycles.Audience.COMMUNITY, "owner",
        Instant.EPOCH, null, List.of(new ReviewCycles.Contribution("private", "someone", "SECRET-DRAFT", false, Instant.EPOCH)), stage.getId());
    var legacyRound = new ReviewCycles.Round("legacy", ReviewCycles.Audience.REVIEWER, "owner", Instant.EPOCH, null, List.of());
    flow.setReviewCycles(List.of(new ReviewCycles.Cycle("cycle", "SECRET-SOURCE", 1, null, "owner", Instant.EPOCH, null,
        List.of(publicRound, legacyRound))));
    var graph = new FlowFlowChartAdapter().adapt(flow); graph.validate();
    assertEquals(2, graph.getRoot().getChildren().size());
    assertEquals(1, graph.getRoot().getEdges().size());
    assertEquals("cycleRound", graph.getRoot().getEdges().getFirst().getMetadata().get("kind"));
    var json = new ObjectMapper().writeValueAsString(graph);
    assertFalse(json.contains("SECRET")); assertFalse(json.contains("legacy"));
    assertFalse(json.contains("hidden-history")); assertFalse(json.contains("someone"));
  }
}
