package org.integratedmodelling.klab.api.documentation;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Objects;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles;

/**
 * Diagram of an already authorized, projected flow. This is persisted history, not a schema or a
 * prediction. Never resolve missing stages: a missing endpoint may have been removed for privacy.
 * Only structural fields are copied; attachments, candidate payloads, feedback and extension
 * metadata do not belong in a graph response.
 */
public class FlowFlowChartAdapter implements FlowChart.Adapter<Flow> {
  @Override
  public FlowChart adapt(Flow flow) {
    Objects.requireNonNull(flow, "flow");
    return FlowChart.builder("flow").root(root -> {
      root.label(Objects.toString(flow.getWorkflowId(), "Workflow") + " (saved, visible history)")
          .layout("elk.algorithm", "layered").layout("elk.direction", "RIGHT")
          .metadata("projection", "persisted-visible-history")
          .metadata("flowId", flow.getId()).metadata("workflowId", flow.getWorkflowId())
          .metadata("workflowVersion", flow.getWorkflowVersion()).metadata("revision", flow.getRevision())
          .metadata("status", name(flow.getStatus()));
      var roundLabels = new HashMap<String, String>();
      int cycleNumber = 0;
      for (var cycle : flow.getReviewCycles()) {
        int number = ++cycleNumber;
        int roundNumber = 0;
        for (var round : cycle.rounds()) {
          ++roundNumber;
          if (round.stageId() != null && flow.getStates().containsKey(round.stageId()))
            roundLabels.put(round.stageId(), "Cycle " + number + ", "
                + (round.audience() == ReviewCycles.Audience.REVIEWER ? "peer" : "community")
                + " round " + roundNumber);
        }
        String id = cycleId(cycle.id());
        root.node(id, node -> node.label("Review cycle " + number + " ["
                + (cycle.closedAt() == null ? "open" : "closed") + "]")
            .port(id + ":in", FlowChart.Role.INPUT).port(id + ":out", FlowChart.Role.OUTPUT)
            .metadata("kind", "reviewCycle").metadata("cycleId", cycle.id())
            .metadata("sourceFlowRevision", cycle.sourceFlowRevision())
            .metadata("openedAt", time(cycle.openedAt())).metadata("closedAt", time(cycle.closedAt())));
      }
      var counts = new HashMap<String, Integer>();
      flow.getStates().values().stream()
          .sorted(Comparator.comparing(Flow.State::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
              .thenComparing(Flow.State::getId))
          .forEach(state -> {
            String id = stateId(state.getId());
            int number = counts.merge(Objects.toString(state.getSchemaId(), "Stage"), 1, Integer::sum);
            String label = roundLabels.getOrDefault(state.getId(),
                Objects.toString(state.getSchemaId(), "Stage") + " " + number);
            root.node(id, node -> node.label(label + " [" + name(state.getStatus()).toLowerCase(java.util.Locale.ROOT) + "]")
                .port(id + ":in", FlowChart.Role.INPUT).port(id + ":out", FlowChart.Role.OUTPUT)
                .metadata("kind", "persistedStage").metadata("stateId", state.getId())
                .metadata("schemaId", state.getSchemaId()).metadata("status", name(state.getStatus()))
                .metadata("current", flow.getCurrentStateIds().contains(state.getId()))
                .metadata("createdAt", time(state.getCreatedAt())).metadata("updatedAt", time(state.getUpdatedAt())));
          });
      int eventNumber = 0;
      for (var event : flow.getHistory()) {
        int number = eventNumber++;
        if (!flow.getStates().containsKey(event.getTargetStateId())) continue;
        String source;
        if (event.getSourceStateId() == null) {
          source = "history-input:" + number;
          root.port(source, FlowChart.Role.INPUT);
        } else {
          if (!flow.getStates().containsKey(event.getSourceStateId())) continue;
          source = stateId(event.getSourceStateId()) + ":out";
        }
        root.link("history:" + number, source, stateId(event.getTargetStateId()) + ":in", link -> {
          link.getLabels().add(FlowChart.labelOf(Objects.toString(event.getTransitionId(), "recorded transition")));
          link.getMetadata().put("kind", "persistedTransaction");
          link.getMetadata().put("transactionId", event.getId());
          link.getMetadata().put("transitionId", event.getTransitionId());
          link.getMetadata().put("timestamp", time(event.getTimestamp()));
        });
      }
      int relation = 0;
      for (var cycle : flow.getReviewCycles()) {
        String id = cycleId(cycle.id());
        if (cycle.sourceStateId() != null && flow.getStates().containsKey(cycle.sourceStateId()))
          relation(root, relation++, stateId(cycle.sourceStateId()), id, "frozen candidate", "cycleSource");
        for (var round : cycle.rounds()) {
          if (round.stageId() != null && flow.getStates().containsKey(round.stageId()))
            relation(root, relation++, id, stateId(round.stageId()), "round in cycle", "cycleRound");
        }
        // The runtime records enter-coalescing against the frozen source, not against each round.
        // Link its cycle only when the persisted relation is unambiguous. Do not infer from dates.
        if (cycle.closedAt() != null && flow.getReviewCycles().stream()
            .filter(c -> Objects.equals(c.sourceStateId(), cycle.sourceStateId())).count() == 1) {
          for (var event : flow.getHistory()) {
            var target = flow.getStates().get(event.getTargetStateId());
            if ("enter-coalescing".equals(event.getTransitionId()) && target != null
                && "coalescing".equals(target.getSchemaId())
                && Objects.equals(event.getSourceStateId(), cycle.sourceStateId()))
              relation(root, relation++, id, stateId(target.getId()), "closed cycle to coalescing", "cycleCoalescing");
          }
        }
      }
    }).build();
  }

  private static void relation(FlowChart.NodeBuilder root, int number, String source, String target,
      String label, String kind) {
    root.link("relation:" + number, source + ":out", target + ":in", link -> {
      link.getLabels().add(FlowChart.labelOf(label));
      link.getMetadata().put("kind", kind);
    });
  }

  private static String stateId(String id) { return "stage:" + id.length() + ":" + id; }
  private static String cycleId(String id) { return "cycle:" + id.length() + ":" + id; }
  private static String name(Enum<?> value) { return value == null ? "unknown" : value.name(); }
  private static String time(Instant value) { return value == null ? null : value.toString(); }
}
