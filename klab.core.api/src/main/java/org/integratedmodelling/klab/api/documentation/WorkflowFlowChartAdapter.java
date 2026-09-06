package org.integratedmodelling.klab.api.documentation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;

/**
 * Structural workflow projection for visualization and reporting, without evaluating authorization.
 */
public class WorkflowFlowChartAdapter implements FlowChart.Adapter<Workflow> {

  @Override
  public FlowChart adapt(Workflow workflow) {
    Objects.requireNonNull(workflow, "workflow");
    var builder = FlowChart.builder("workflow");
    builder.root(
        root -> {
          root.label(workflow.getName() == null ? workflow.getId() : workflow.getName())
              .layout("elk.algorithm", "layered")
              .layout("elk.direction", "RIGHT")
              .metadata("workflowId", workflow.getId())
              .metadata("version", workflow.getVersion())
              .metadata("description", workflow.getDescription())
              .metadata("assetTypes", json(workflow.getAssetTypes()))
              .metadata("properties", json(workflow.getMetadata()));
          for (var entry : new TreeMap<>(workflow.getStates()).entrySet()) {
            String id = stateId(entry.getKey());
            var state = Objects.requireNonNull(entry.getValue(), "state");
            root.node(
                id,
                node ->
                    node.label(entry.getKey())
                        .size(160, 60)
                        .port(id + ":in", FlowChart.Role.INPUT)
                        .port(id + ":out", FlowChart.Role.OUTPUT)
                        .metadata("stateId", entry.getKey())
                        .metadata("description", state.getDescription())
                        .metadata("instructions", state.getInstructions())
                        .metadata("completionCriteria", state.getCompletionCriteria())
                        .metadata("open", state.isOpen())
                        .metadata("managerRoles", json(state.getManagerRoles()))
                        .metadata("contributorRoles", json(state.getContributorRoles()))
                        .metadata("admittedGroups", json(state.getAdmittedGroups()))
                        .metadata("assetTypes", json(state.getAssetTypes()))
                        .metadata("attachments", attachments(state))
                        .metadata("properties", json(state.getMetadata())));
          }
          int index = 0;
          for (var entry : new TreeMap<>(workflow.getTransitions()).entrySet()) {
            var transition = Objects.requireNonNull(entry.getValue(), "transition");
            if (transition.getSourceStates().isEmpty())
              throw new IllegalArgumentException("Transition has no sources: " + entry.getKey());
            requireState(workflow, transition.getTargetState());
            for (String source : new TreeSet<>(transition.getSourceStates())) {
              if (!Workflow.INIT.equals(source)) requireState(workflow, source);
              String from;
              if (Workflow.INIT.equals(source)) {
                from = inputPortId(entry.getKey());
                root.port(
                    from,
                    FlowChart.Role.INPUT,
                    port -> {
                      port.getMetadata().put("kind", "workflowInput");
                      port.getMetadata().put("transitionId", entry.getKey());
                      port.getMetadata().put("targetState", transition.getTargetState());
                    });
              } else {
                from = stateId(source) + ":out";
              }
              root.link(
                  "transition:" + index++,
                  from,
                  stateId(transition.getTargetState()) + ":in",
                  link -> {
                    link.getLabels().add(FlowChart.labelOf(entry.getKey()));
                    var metadata = link.getMetadata();
                    metadata.put("transitionId", entry.getKey());
                    metadata.put("sourceState", source);
                    metadata.put("targetState", transition.getTargetState());
                    metadata.put("description", transition.getDescription());
                    metadata.put("roles", json(transition.getRoles()));
                    metadata.put("sourceAssetTypes", json(transition.getSourceAssetTypes()));
                    metadata.put("sourceMediaTypes", json(transition.getSourceMediaTypes()));
                    metadata.put("properties", json(transition.getMetadata()));
                  });
            }
          }
          var transitionSources = new TreeSet<String>();
          for (var transition : workflow.getTransitions().values()) {
            transitionSources.addAll(transition.getSourceStates());
          }
          for (String terminal : new TreeSet<>(workflow.getStates().keySet())) {
            if (transitionSources.contains(terminal)) continue;
            String portId = outputPortId(terminal);
            root.port(
                portId,
                FlowChart.Role.OUTPUT,
                port -> {
                  port.getMetadata().put("kind", "workflowOutput");
                  port.getMetadata().put("stateId", terminal);
                });
            root.link(
                "terminal:" + stateId(terminal),
                stateId(terminal) + ":out",
                portId,
                link -> {
                  link.getMetadata().put("kind", "workflowOutput");
                  link.getMetadata().put("sourceState", terminal);
                });
          }
        });
    return builder.build();
  }

  // Length-prefixing keeps node IDs distinct from port IDs even for arbitrary schema keys.
  private static String stateId(String key) {
    if (key == null || key.isBlank()) throw new IllegalArgumentException("Missing state key");
    return "state:" + key.length() + ":" + key;
  }

  private static String inputPortId(String transitionId) {
    if (transitionId == null || transitionId.isBlank())
      throw new IllegalArgumentException("Missing transition key");
    return "input:" + transitionId.length() + ":" + transitionId;
  }

  private static String outputPortId(String stateId) {
    return "output:" + stateId.length() + ":" + stateId;
  }

  private static void requireState(Workflow workflow, String key) {
    if (key == null || !workflow.getStates().containsKey(key))
      throw new IllegalArgumentException("Unknown workflow state: " + key);
  }

  private static List<Object> attachments(Workflow.StateSchema state) {
    List<Object> result = new ArrayList<>();
    for (var rule : state.getAttachments()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("type", rule.getType());
      item.put("mediaType", rule.getMediaType());
      item.put("assetType", json(rule.getAssetType()));
      item.put("arity", rule.getArity());
      item.put("required", rule.isRequired());
      result.add(item);
    }
    return result;
  }

  /** Copy extension values, converting enum/set values to ordinary JSON scalars/arrays. */
  private static Object json(Object value) {
    return json(value, Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  private static Object json(Object value, Set<Object> active) {
    if (value == null || value instanceof String || value instanceof Boolean) return value;
    if (value instanceof Number number) {
      if (!(number instanceof Byte
              || number instanceof Short
              || number instanceof Integer
              || number instanceof Long
              || number instanceof java.math.BigInteger
              || number instanceof java.math.BigDecimal
              || number instanceof Float
              || number instanceof Double)
          || (number instanceof Double && !Double.isFinite(number.doubleValue()))
          || (number instanceof Float && !Float.isFinite(number.floatValue())))
        throw new IllegalArgumentException("Unsupported JSON number: " + number);
      return number;
    }
    if (value instanceof Enum<?> enumeration) return enumeration.name();
    if (!active.add(value)) throw new IllegalArgumentException("Cyclic workflow metadata");
    try {
      if (value instanceof Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
          if (!(entry.getKey() instanceof String key))
            throw new IllegalArgumentException("Metadata keys must be strings");
          copy.put(key, json(entry.getValue(), active));
        }
        return copy;
      }
      if (value instanceof Collection<?> collection) {
        List<Object> copy = new ArrayList<>();
        for (Object item : collection) copy.add(json(item, active));
        return copy;
      }
      throw new IllegalArgumentException(
          "Non-JSON workflow metadata: " + value.getClass().getName());
    } finally {
      active.remove(value);
    }
  }
}
