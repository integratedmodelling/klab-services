package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Exact current configuration ingredients and their observed substantial participants. */
final class ConfigurationRoleInputs {
  private ConfigurationRoleInputs() {}

  static ConsequenceNativeInputs.Snapshot capture(
      long source, ConsequenceNativeInputs.Snapshot base, ServiceContextScope scope) {
    var graph = scope.getDigitalTwin().getKnowledgeGraph();
    var selections = new ArrayList<>(base.reads().selections());
    selections.add(
        new KnowledgeGraph.ConsequenceEdgeSelection(
            source,
            GraphModel.Relationship.EMERGED_FROM,
            GraphModel.Relationship.Direction.OUTGOING));
    KnowledgeGraph.ConsequenceReadSet complete;
    {
      var first = provisional(graph, scope, base.reads().assetIds(), selections);
      exactSubset(base.reads(), first);
      var ids = new TreeSet<Long>(base.reads().assetIds());
      var ingredients = new TreeSet<Long>();
      for (var edge : first.edges())
        if (edge.relationship() == GraphModel.Relationship.EMERGED_FROM
            && edge.source().id() instanceof Number from
            && from.longValue() == source
            && edge.target().id() instanceof Number target) ingredients.add(target.longValue());
      ids.addAll(ingredients);
      for (var id : ingredients) {
        selections.add(
            new KnowledgeGraph.ConsequenceEdgeSelection(
                id, GraphModel.Relationship.HAS_CHILD, GraphModel.Relationship.Direction.INCOMING));
        for (var relationship :
            List.of(
                GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE,
                GraphModel.Relationship.HAS_RELATIONSHIP_TARGET,
                GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT))
          selections.add(
              new KnowledgeGraph.ConsequenceEdgeSelection(
                  id, relationship, GraphModel.Relationship.Direction.OUTGOING));
      }
      var neighbors = provisional(graph, scope, ids, selections);
      exactSubset(first, neighbors);
      for (var edge : neighbors.edges())
        for (var endpoint : List.of(edge.source(), edge.target()))
          if (endpoint.id() instanceof Number id
              && endpoint.labels().contains(GraphModel.Labels.OBSERVATION)) ids.add(id.longValue());
      try (var transaction = graph.createTransaction(scope)) {
        complete = transaction.captureConsequenceInputs(ids, selections);
        transaction.validateConsequenceInputs(base.reads());
        transaction.validateConsequenceInputs(first);
        transaction.validateConsequenceInputs(neighbors);
      } catch (java.io.IOException failure) {
        throw new IllegalStateException("Configuration participant capture failed", failure);
      }
      exactSubset(neighbors, complete);
      if (!neighbors.edges().equals(complete.edges()))
        throw new IllegalStateException("Configuration participants changed during capture");
    }
    var observations = new LinkedHashMap<Long, Observation>();
    for (var asset : complete.assets())
      if (asset.labels().contains(GraphModel.Labels.OBSERVATION)) {
        var value = graph.materializeConsequenceObservation(asset, scope);
        if (value == null || value.getId() != asset.id())
          throw new IllegalStateException("Configuration ingredient payload is unavailable");
        observations.put(asset.id(), value);
      }
    return new ConsequenceNativeInputs.Snapshot(
        complete, observations, base.observerId(), base.focusId());
  }

  private static KnowledgeGraph.ConsequenceReadSet provisional(
      KnowledgeGraph graph,
      ServiceContextScope scope,
      Collection<Long> ids,
      Collection<KnowledgeGraph.ConsequenceEdgeSelection> selections) {
    try (var transaction = graph.createTransaction(scope)) {
      return transaction.captureConsequenceInputs(ids, selections);
    } catch (java.io.IOException failure) {
      throw new IllegalStateException(
          "Provisional configuration participant capture failed", failure);
    }
  }

  private static void exactSubset(
      KnowledgeGraph.ConsequenceReadSet before, KnowledgeGraph.ConsequenceReadSet after) {
    if (!Objects.equals(before.contextId(), after.contextId())
        || !after.assets().containsAll(before.assets()))
      throw new IllegalStateException("Configuration native inputs changed during capture");
    var selected =
        after.edges().stream()
            .filter(
                edge ->
                    before.selections().stream()
                        .anyMatch(
                            selection ->
                                selection.relationship() == edge.relationship()
                                    && Objects.equals(
                                        selection.assetId(),
                                        selection.direction()
                                                == GraphModel.Relationship.Direction.INCOMING
                                            ? edge.target().id()
                                            : edge.source().id())))
            .toList();
    if (!new HashSet<>(selected).equals(new HashSet<>(before.edges())))
      throw new IllegalStateException("Configuration native structure changed during capture");
  }

  private static Set<Long> parents(long source, ConsequenceNativeInputs.Snapshot inputs) {
    var result = new TreeSet<Long>();
    for (var edge : inputs.reads().edges())
      if (edge.relationship() == GraphModel.Relationship.HAS_CHILD
          && edge.target().id() instanceof Number target
          && target.longValue() == source
          && edge.source().id() instanceof Number id
          && substantial(inputs.observations().get(id.longValue()))) result.add(id.longValue());
    return Set.copyOf(result);
  }

  private static Set<Long> ingredients(long source, ConsequenceNativeInputs.Snapshot inputs) {
    var result = new TreeSet<Long>();
    for (var edge : inputs.reads().edges())
      if (edge.relationship() == GraphModel.Relationship.EMERGED_FROM
          && edge.source().id() instanceof Number from
          && from.longValue() == source
          && edge.target().id() instanceof Number id) result.add(id.longValue());
    return Set.copyOf(result);
  }

  static Set<Long> bearers(long source, ConsequenceNativeInputs.Snapshot inputs) {
    var ingredients = ingredients(source, inputs);
    if (ingredients.isEmpty()) return Set.of();
    var common = parents(source, inputs);
    if (common.size() != 1) return Set.of();
    for (var id : ingredients) {
      var ingredient = inputs.observation(id);
      if (!SemanticType.isDependent(ingredient.getObservable().getSemantics().getType())
          || !parents(id, inputs).equals(common)) return Set.of();
    }
    return common;
  }

  static Set<Long> participants(long source, ConsequenceNativeInputs.Snapshot inputs) {
    var ingredients = new TreeSet<Long>();
    var result = new TreeSet<Long>();
    for (var edge : inputs.reads().edges())
      if (edge.relationship() == GraphModel.Relationship.EMERGED_FROM
          && edge.source().id() instanceof Number from
          && from.longValue() == source
          && edge.target().id() instanceof Number id) ingredients.add(id.longValue());
    for (var id : ingredients) if (substantial(inputs.observations().get(id))) result.add(id);
    for (var edge : inputs.reads().edges()) {
      if (!(edge.source().id() instanceof Number from)
          || !ingredients.contains(from.longValue())
          || !(edge.target().id() instanceof Number to)) continue;
      if (Set.of(
                  GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE,
                  GraphModel.Relationship.HAS_RELATIONSHIP_TARGET,
                  GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT)
              .contains(edge.relationship())
          && substantial(inputs.observations().get(to.longValue()))) result.add(to.longValue());
    }
    result.addAll(bearers(source, inputs));
    return Set.copyOf(result);
  }

  private static boolean substantial(Observation value) {
    return value != null
        && !value.isEmpty()
        && value.getObservable() != null
        && !value.getObservable().getSemantics().isCollective()
        && SemanticType.isEnumerableSubstantial(value.getObservable().getSemantics().getType());
  }
}
