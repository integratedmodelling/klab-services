package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Detached native source, selected structural neighborhoods and recipient payloads. */
public final class ConsequenceNativeInputs {
  private ConsequenceNativeInputs() {}

  public record Snapshot(
      KnowledgeGraph.ConsequenceReadSet reads,
      Map<Long, Observation> observations,
      Long observerId,
      long focusId) {
    public Snapshot {
      observations = Map.copyOf(observations);
    }

    public Observation observation(long id) {
      var result = observations.get(id);
      if (result == null)
        throw new IllegalStateException("Consequence observation is not captured");
      return result;
    }
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
          "Provisional consequence capture could not close safely", failure);
    }
  }

  public static Snapshot capture(
      long source, Collection<Long> recipients, ServiceContextScope scope) {
    if (source <= 0 || recipients.stream().anyMatch(id -> id == null || id <= 0))
      throw new IllegalArgumentException("Consequence capture requires committed identities");
    var selections =
        List.of(
            new KnowledgeGraph.ConsequenceEdgeSelection(
                source,
                GraphModel.Relationship.HAS_CHILD,
                GraphModel.Relationship.Direction.INCOMING),
            new KnowledgeGraph.ConsequenceEdgeSelection(
                source,
                GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE,
                GraphModel.Relationship.Direction.OUTGOING),
            new KnowledgeGraph.ConsequenceEdgeSelection(
                source,
                GraphModel.Relationship.HAS_RELATIONSHIP_TARGET,
                GraphModel.Relationship.Direction.OUTGOING),
            new KnowledgeGraph.ConsequenceEdgeSelection(
                source,
                GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT,
                GraphModel.Relationship.Direction.OUTGOING));
    var observer = scope.getObserverId();
    var focus = scope.getContextObservation();
    long focusId = focus == null ? 0 : focus.getId();
    if (observer != null && observer <= 0 || focusId < 0)
      throw new IllegalStateException("Consequence perspective is not a durable accessible asset");
    var graph = scope.getDigitalTwin().getKnowledgeGraph();
    KnowledgeGraph.ConsequenceReadSet captured;
    KnowledgeGraph.ConsequenceReadSet first;
    {
      first = provisional(graph, scope, List.of(source), selections);
      var ids = new TreeSet<Long>();
      ids.add(source);
      ids.addAll(recipients);
      if (observer != null) ids.add(observer);
      if (focusId > 0) ids.add(focusId);
      for (var edge : first.edges())
        for (var endpoint : List.of(edge.source(), edge.target()))
          if (endpoint.id() instanceof Number id
              && (endpoint.labels().contains(GraphModel.Labels.OBSERVATION)
                  || endpoint.labels().contains(GraphModel.Labels.COHORT))) ids.add(id.longValue());
      captured = provisional(graph, scope, ids, selections);
      var original =
          first.assets().stream().filter(asset -> asset.id() == source).findFirst().orElseThrow();
      if (!captured.assets().contains(original)
          || !first.edges().equals(captured.edges())
          || !Objects.equals(first.contextId(), captured.contextId()))
        throw new IllegalStateException("Consequence source changed during native capture");
    }
    // Original source/model and attribution proofs are part of the scientific dependency
    // closure. Capture the exact Activities and their incident effect sets, never live metadata
    // after the commit baseline has already been decided.
    var evidenceSelections = new ArrayList<>(captured.selections());
    for (var asset : captured.assets())
      if (asset.labels().contains(GraphModel.Labels.OBSERVATION)) {
        var observation = graph.materializeConsequenceObservation(asset, scope);
        if (observation == null || observation.getObservable() == null)
          throw new IllegalStateException("Missing consequence input meaning");
        var effects = new LinkedHashSet<GraphModel.Relationship>();
        effects.add(
            GraphModel.Relationship.forContextualization(
                observation.getObservable().getContextualization()));
        effects.add(GraphModel.Relationship.CLASSIFIED);
        effects.add(GraphModel.Relationship.CHARACTERIZED);
        if (observation
            .getObservable()
            .is(org.integratedmodelling.klab.api.knowledge.SemanticType.PROCESS))
          effects.add(GraphModel.Relationship.CREATED);
        for (var effect : effects)
          evidenceSelections.add(
              new KnowledgeGraph.ConsequenceEdgeSelection(
                  asset.id(), effect, GraphModel.Relationship.Direction.INCOMING));
      }
    {
      var withEdges = provisional(graph, scope, captured.assetIds(), evidenceSelections);
      if (!withEdges.assets().containsAll(captured.assets())
          || !withEdges.edges().containsAll(captured.edges()))
        throw new IllegalStateException(
            "Consequence inputs changed before execution evidence capture");
      var ids = new TreeSet<Long>(captured.assetIds());
      for (var edge : withEdges.edges())
        for (var endpoint : List.of(edge.source(), edge.target()))
          if (endpoint.id() instanceof Number id
              && endpoint.labels().contains(GraphModel.Labels.ACTIVITY)) ids.add(id.longValue());
      final KnowledgeGraph.ConsequenceReadSet complete;
      try (var transaction = graph.createTransaction(scope)) {
        // Acquire the final sorted union once. Provisional passes own no surviving locks;
        // validating them now cannot introduce any additional lower-key endpoint lock.
        complete = transaction.captureConsequenceInputs(ids, evidenceSelections);
        transaction.validateConsequenceInputs(first);
        transaction.validateConsequenceInputs(captured);
        transaction.validateConsequenceInputs(withEdges);
      } catch (java.io.IOException failure) {
        throw new IllegalStateException(
            "Consequence evidence capture could not close safely", failure);
      }
      if (!complete.assets().containsAll(withEdges.assets())
          || !complete.edges().equals(withEdges.edges()))
        throw new IllegalStateException("Consequence execution evidence changed during capture");
      captured = complete;
    }
    if (!Objects.equals(captured.contextId(), scope.getId()))
      throw new IllegalStateException("Consequence native context changed");
    var observations = new LinkedHashMap<Long, Observation>();
    for (var asset : captured.assets())
      if (asset.labels().contains(GraphModel.Labels.OBSERVATION)) {
        var observation = graph.materializeConsequenceObservation(asset, scope);
        if (observation == null
            || observation.getId() != asset.id()
            || observations.putIfAbsent(asset.id(), observation) != null)
          throw new IllegalStateException("Consequence payload materialization is incomplete");
      }
    if (!Objects.equals(observer, scope.getObserverId())
        || focusId
            != (scope.getContextObservation() == null ? 0 : scope.getContextObservation().getId()))
      throw new IllegalStateException("Consequence perspective changed during native capture");
    var result = new Snapshot(captured, observations, observer, focusId);
    result.observation(source);
    recipients.forEach(result::observation);
    if (observer != null) {
      var agent = result.observation(observer);
      if (agent.isEmpty()
          || agent.getObservable() == null
          || !agent
              .getObservable()
              .is(org.integratedmodelling.klab.api.knowledge.SemanticType.AGENT)
          || agent.getObservable().getSemantics().isCollective())
        throw new IllegalStateException("Captured consequence observer is not an individual Agent");
    }
    if (focusId > 0) result.observation(focusId);
    return result;
  }
}
