package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.ProcessPlan;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;

/** Pure per-transition reachability, never an execution order or an INIT/creation operation. */
public final class ConsequenceClosure {
  private ConsequenceClosure() {}

  public static List<Observation> affected(Observation seed, long bearerId, KnowledgeGraph graph, ContextScope scope) {
    return affected(seed, bearerId, seed.getGeometry(), graph, scope);
  }

  public static List<Observation> affected(Observation seed, long bearerId,
      org.integratedmodelling.klab.api.geometry.Geometry support, KnowledgeGraph graph, ContextScope scope) {
    if (bearerId <= 0 || support == null) throw new IllegalArgumentException("Closure requires bearer and transition support");
    var reached = new TreeMap<Long, Observation>();
    var queue = new ArrayDeque<Observation>();
    var visited = new HashSet<Long>();
    queue.add(seed);
    while (!queue.isEmpty()) {
      var current = queue.removeFirst();
      if (current.getId() <= 0) throw new IllegalArgumentException("Consequence closure requires durable observations");
      if (!visited.add(current.getId())) continue;
      if (!overlaps(current.getGeometry(), support)) continue;
      boolean quality = current.getObservable().is(SemanticType.QUALITY);
      if (quality && current.getParentId() > 0 && current.getParentId() != bearerId) continue;
      if (quality) reached.put(current.getId(), current);
      for (var direction : GraphModel.Relationship.Direction.values()) {
        for (var edge : graph.getLinks(current, direction, scope, GraphModel.Relationship.AFFECTS)) {
          var role = edge.properties().get(ProcessPlan.EDGE_ROLE);
          boolean descriptive = ProcessPlan.DESCRIPTIVE.equals(role);
          boolean direct = !quality && direction == GraphModel.Relationship.Direction.OUTGOING
              && ProcessPlan.INFLUENCE.equals(role)
              && (current.getObservable().is(SemanticType.PROCESS) || current.getObservable().is(SemanticType.EVENT));
          if (!direct && !(quality && descriptive)) continue;
          if (descriptive && (!(edge.properties().get("bearerId") instanceof Number owner)
              || owner.longValue() != bearerId)) continue;
          var next = direction == GraphModel.Relationship.Direction.INCOMING ? edge.source() : edge.target();
          if (next instanceof Observation observation && observation.getObservable().is(SemanticType.QUALITY))
            queue.add(observation);
        }
      }
    }
    return List.copyOf(reached.values());
  }

  public static boolean overlaps(org.integratedmodelling.klab.api.geometry.Geometry a,
      org.integratedmodelling.klab.api.geometry.Geometry b) {
    if (a == null || b == null) return false;
    var repository = org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE;
    var first = repository.scale(a); var second = repository.scale(b);
    // Scale.isEmpty() is not sufficient for disjoint temporal supports; compare each extent.
    if (first.getTime() != null && second.getTime() != null
        && !first.getTime().intersects(second.getTime())) return false;
    return first.getSpace() == null || second.getSpace() == null
        || first.getSpace().intersects(second.getSpace());
  }
}
