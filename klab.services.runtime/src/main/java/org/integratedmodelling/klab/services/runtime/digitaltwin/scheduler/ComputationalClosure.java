package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.CollectiveReductionBinding;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.utilities.Utils;

/** Computational dependency closure; descriptive reachability never supplies execution ordering. */
public final class ComputationalClosure {
  private ComputationalClosure() {}

  /**
   * A cohort is a dependency catalogue, not an executable observation. This validates its
   * association only; the consumer must still obtain fresh issued query evidence.
   */
  static void requireCollectiveInput(
      Cohort cohort, KnowledgeGraph.Link edge, Actuator plan, ContextScope scope) {
    if (!ProcessPlan.PREREQUISITE.equals(edge.properties().get(ProcessPlan.EDGE_ROLE)))
      throw new IllegalStateException("Unbound collective computational prerequisite");
    boolean reduction = plan.getData().containsKey(CollectiveReductionBinding.DATA_KEY);
    var edgeBinding = edge.properties().get(CollectiveReductionBinding.DATA_KEY);
    if (reduction) {
      var binding =
          Utils.Json.parseObject(
              plan.getData().get(CollectiveReductionBinding.DATA_KEY).toString(),
              CollectiveReductionBinding.class);
      if (edgeBinding == null
          || !edgeBinding
              .toString()
              .equals(plan.getData().get(CollectiveReductionBinding.DATA_KEY).toString())
          || !java.util.Objects.equals(
              edge.properties().get(ProcessPlan.COLLECTIVE_COHORT_OBSERVABLE),
              cohort.getObservable().getUrn()))
        throw new IllegalStateException(
            "Collective edge disagrees with its certified reduction input");
      MembershipInputChange.requireCohortOperand(
          scope, binding.operand(), cohort.getObservable().getUrn());
    } else {
      var original = edge.properties().get(ProcessPlan.COLLECTIVE_SOURCE_OBSERVATION);
      if (!(original instanceof Number id)
          || plan.getChildren().stream()
              .noneMatch(
                  c ->
                      c.getObservation() != null
                          && c.getObservation().getId() == id.longValue()
                          && c.getObservation().getObservable().getSemantics().isCollective()))
        throw new IllegalStateException(
            "Collective edge disagrees with original model input bindings");
      if (!java.util.Objects.equals(
          edge.properties().get(ProcessPlan.COLLECTIVE_COHORT_OBSERVABLE),
          cohort.getObservable().getUrn()))
        throw new IllegalStateException("Collective cohort header changed");
      var child =
          plan.getChildren().stream()
              .filter(
                  c ->
                      c.getObservation() != null
                          && c.getObservation().getId() == ((Number) original).longValue())
              .findFirst()
              .orElseThrow();
      MembershipInputChange.requireCohortOperand(
          scope, child.getObservation().getObservable().getUrn(), cohort.getObservable().getUrn());
    }
  }

  public static List<Observation> ordered(
      Observation seed, long bearer, Geometry support, KnowledgeGraph graph, ContextScope scope) {
    return orderedSeed(seed, bearer, support, graph, scope);
  }

  public static List<Observation> ordered(
      Cohort seed, long bearer, Geometry support, KnowledgeGraph graph, ContextScope scope) {
    return orderedSeed(seed, bearer, support, graph, scope);
  }

  private static List<Observation> orderedSeed(
      org.integratedmodelling.klab.api.data.RuntimeAsset seed,
      long bearer,
      Geometry support,
      KnowledgeGraph graph,
      ContextScope scope) {
    var roots = new TreeMap<Long, org.integratedmodelling.klab.api.data.RuntimeAsset>();
    roots.put(seed.getId(), seed);
    for (var edge :
        graph.getLinks(
            seed,
            GraphModel.Relationship.Direction.OUTGOING,
            scope,
            GraphModel.Relationship.AFFECTS)) {
      if (seed instanceof Observation
          && ProcessPlan.INFLUENCE.equals(edge.properties().get(ProcessPlan.EDGE_ROLE))
          && edge.target() instanceof Observation quality
          && eligible(quality, bearer, seed.getId(), support)) roots.put(quality.getId(), quality);
    }
    var queue = new ArrayDeque<>(roots.values());
    var visited = new HashSet<Long>();
    var selected = new TreeMap<Long, Observation>();
    var dependencies = new HashMap<Long, Set<Long>>();
    while (!queue.isEmpty()) {
      var source = queue.removeFirst();
      if (!visited.add(source.getId())) continue;
      for (var edge :
          graph.getLinks(
              source,
              GraphModel.Relationship.Direction.OUTGOING,
              scope,
              GraphModel.Relationship.AFFECTS)) {
        if (!ProcessPlan.PREREQUISITE.equals(edge.properties().get(ProcessPlan.EDGE_ROLE))
            || !(edge.target() instanceof Observation target)
            || roots.containsKey(target.getId())
            || !eligible(target, bearer, seed.getId(), support, seed instanceof Cohort)
            || !graph.isObservationCurrent(target, scope)) continue;
        var encoded = target.getMetadata().get(Scheduler.PLAN_METADATA_KEY);
        if (encoded == null)
          throw new IllegalStateException(
              "Causal computation has no portable input bindings; re-resolve " + target.getUrn());
        var plan = Utils.Json.parseObject(encoded.toString(), Actuator.class);
        if (plan.getExecutionRole() != Actuator.ExecutionRole.INITIALIZATION
            || plan.getActuatorType() == Actuator.Type.REFERENCE) continue;
        boolean reduction = plan.getData().containsKey(CollectiveReductionBinding.DATA_KEY);
        if (plan.getComputation().isEmpty() && !reduction) continue;
        if (source instanceof Cohort cohort) {
          requireCollectiveInput(cohort, edge, plan, scope);
        } else if (plan.getChildren().stream()
            .noneMatch(
                c -> c.getObservation() != null && c.getObservation().getId() == source.getId()))
          throw new IllegalStateException(
              "Causal edge disagrees with original model input bindings");
        selected.put(target.getId(), target);
        dependencies
            .computeIfAbsent(target.getId(), ignored -> new HashSet<>())
            .add(source.getId());
        queue.add(target);
      }
    }
    var result = new ArrayList<Observation>();
    var completed = new HashSet<>(roots.keySet());
    while (!selected.isEmpty()) {
      var ready =
          selected.keySet().stream()
              .filter(id -> completed.containsAll(dependencies.getOrDefault(id, Set.of())))
              .toList();
      if (ready.isEmpty()) throw new IllegalStateException("Cycle in causal model computations");
      for (var id : ready) {
        result.add(selected.remove(id));
        completed.add(id);
      }
    }
    return List.copyOf(result);
  }

  private static boolean eligible(
      Observation quality, long bearer, long occurrence, Geometry support) {
    return eligible(quality, bearer, occurrence, support, false);
  }

  private static boolean eligible(
      Observation quality, long bearer, long occurrence, Geometry support, boolean cohortInput) {
    var repository = org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE;
    var boundary = repository.scale(support);
    boolean instant =
        boundary.getTime() != null
            && boundary.getTime().getStart() != null
            && boundary.getTime().getEnd() != null
            && boundary.getTime().getStart().getMilliseconds()
                == boundary.getTime().getEnd().getMilliseconds();
    // Quality temporal geometry records computed support, not the lifetime of its bearer.
    // Event boundaries may extend that support, including beyond the original simulation horizon.
    var space = instant ? repository.scale(quality.getGeometry()).getSpace() : null;
    return quality.getId() > 0
        && quality.getObservable().is(SemanticType.QUALITY)
        && (cohortInput
            || quality.getParentId() <= 0
            || quality.getParentId() == bearer
            || quality.getParentId() == occurrence)
        && (instant
            ? space == null || boundary.getSpace() == null || space.intersects(boundary.getSpace())
            : ConsequenceClosure.overlaps(quality.getGeometry(), support));
  }
}
