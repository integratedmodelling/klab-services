package org.integratedmodelling.klab.api.documentation;

import java.util.*;
import org.integratedmodelling.klab.api.collections.Identifier;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

/** Detached execution-plan diagram. This is not a source serializer or a provenance replay plan. */
public final class DataflowFlowChartAdapter implements FlowChart.Adapter<Dataflow> {
  private final Actuator active;
  public DataflowFlowChartAdapter() { this(null); }
  public DataflowFlowChartAdapter(Actuator active) { this.active = active; }
  public FlowChart adapt(Dataflow dataflow) { return adapt(dataflow.getComputation()); }
  public FlowChart adapt(Actuator root) { return adapt(List.of(root)); }

  private FlowChart adapt(List<Actuator> roots) {
    var nodes = new ArrayList<Actuator>();
    Map<Actuator, String> ids = new IdentityHashMap<>();
    for (var root : roots) collect(root, nodes, ids);
    var builder = FlowChart.builder("dataflow").metadata("schema", "klab.dataflow-plan.v1")
        .metadata("edgeDirection", "prerequisite-to-consumer");
    if (active != null) {
      if (!ids.containsKey(active)) throw new IllegalArgumentException("Active actuator is outside the plan");
      builder.metadata("activeNode", ids.get(active));
    }
    return builder.root(root -> {
      root.label("Contextualization plan").layout("elk.algorithm", "layered").layout("elk.direction", "RIGHT");
      for (var actuator : nodes) {
        String id = ids.get(actuator);
        root.node(id, n -> {
          var observable = actuator.getOperationObservable() != null ? actuator.getOperationObservable()
              : actuator.getObservation() == null ? null : actuator.getObservation().getObservable();
          n.label(Objects.toString(actuator.getName(), id) + (observable == null ? "" : ": " + observable.getUrn()))
              .metadata("actuatorType", value(actuator.getActuatorType(), 0))
              .metadata("contextualization", value(actuator.getContextualization(), 0))
              .metadata("effect", value(actuator.getEffect(), 0))
              .metadata("observationId", actuator.getId()).metadata("transientId", actuator.getTransientId())
              .metadata("strategy", actuator.getStrategyUrn()).metadata("active", actuator == active);
          if (actuator.getCoverage() != null) n.metadata("coverage", actuator.getCoverage().encode());
          if (actuator.getRequestedSupport() != null) n.metadata("requestedSupport", actuator.getRequestedSupport().encode());
          var bindings = new ArrayList<Map<String, Object>>();
          for (var binding : actuator.getTargetBindings()) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("kind", value(binding.getKind(), 0)); b.put("sources", new ArrayList<>(binding.getSources()));
            if (binding.getTarget() != null) b.put("observationId", binding.getTarget().getId());
            bindings.add(b);
          }
          n.metadata("targetBindings", bindings);
          int i = 0;
          for (var call : actuator.getComputation()) {
            String callId = id + "-call-" + i;
            n.node(callId, c -> c.label(call.getUrn()).metadata("kind", "contextualizer")
                .metadata("parameters", value(call.getParameters(), 0))
                .metadata("requiredVersion", call.getRequiredVersion() == null ? null : call.getRequiredVersion().toString()));
            if (i > 0) n.link(id + "-sequence-" + i, id + "-call-" + (i - 1), callId);
            i++;
          }
        });
      }
      int edge = 0;
      for (var actuator : nodes) for (var child : actuator.getChildren()) {
        root.link("dependency-" + edge++, ids.get(child), ids.get(actuator), link -> {
          if (child.getName() != null) link.getLabels().add(FlowChart.labelOf(child.getName()));
          link.getMetadata().put("kind", "prerequisite");
        });
      }
      // Reference actuators are occurrences, not the producer itself. Preserve and link both.
      for (var reference : nodes) if (reference.getActuatorType() == Actuator.Type.REFERENCE) {
        for (var producer : nodes) if (producer.getActuatorType() != Actuator.Type.REFERENCE
            && producer.getId() != 0 && producer.getId() == reference.getId()) {
          root.link("reference-" + edge++, ids.get(producer), ids.get(reference), link -> link.getMetadata().put("kind", "reference"));
        }
      }
    }).build();
  }

  private static void collect(Actuator node, List<Actuator> nodes, Map<Actuator, String> ids) {
    if (ids.containsKey(node)) return;
    ids.put(node, "actuator-" + nodes.size()); nodes.add(node);
    for (var child : node.getChildren()) collect(child, nodes, ids);
  }

  /** Bound diagnostic projection; never retain arbitrary parameter objects or cyclic containers. */
  private static Object value(Object object, int depth) {
    if (object == null || object instanceof String || object instanceof Boolean) return object;
    if (object instanceof Number n) return Double.isFinite(n.doubleValue()) ? n : n.toString();
    if (object instanceof Enum<?> e) return e.name();
    if (depth > 16) return Map.of("truncated", true);
    if (object instanceof Identifier id) return Map.of("reference", id.getValue());
    if (object instanceof Geometry g) return g.encode();
    if (object instanceof KlabAsset a) return Map.of("urn", Objects.toString(a.getUrn(), ""));
    if (object instanceof ServiceCall call) return Map.of("function", call.getUrn(), "parameters", value(call.getParameters(), depth + 1));
    if (object instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>();
      map.forEach((k, v) -> copy.put(String.valueOf(k), value(v, depth + 1))); return copy;
    }
    if (object instanceof Collection<?> collection) return collection.stream().map(v -> value(v, depth + 1)).toList();
    if (object.getClass().isArray()) {
      var copy = new ArrayList<>();
      for (int i = 0; i < java.lang.reflect.Array.getLength(object); i++) copy.add(value(java.lang.reflect.Array.get(object, i), depth + 1));
      return copy;
    }
    return Map.of("omittedType", object.getClass().getName());
  }
}
