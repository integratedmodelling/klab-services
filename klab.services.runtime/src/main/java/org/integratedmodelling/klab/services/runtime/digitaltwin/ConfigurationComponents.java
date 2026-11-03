package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher;

/**
 * Component-local Network ingredients, proven from a complete captured local relationship domain.
 * Connectivity uses actual endpoints, never semantic labels or a representative relationship.
 * Split/merge and member-replacement identity policy are deliberately not chosen here.
 */
public final class ConfigurationComponents {
  private ConfigurationComponents() {}

  private static final Set<GraphModel.Relationship> ENDPOINTS =
      EnumSet.of(
          GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE,
          GraphModel.Relationship.HAS_RELATIONSHIP_TARGET,
          GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT);

  public enum EndpointKind {
    DIRECTED,
    BOND
  }

  public static final class UnsupportedGrouping extends RuntimeException {
    public UnsupportedGrouping(String reason) {
      super(reason);
    }
  }

  public static List<ConsequencePatternMatcher.Snapshot> partition(
      ConsequencePatternMatcher.Snapshot snapshot, Map<Long, EndpointKind> relationshipKinds) {
    var relationshipIds = relationshipKinds.keySet();
    if (!snapshot.complete())
      throw new UnsupportedGrouping("Complete local relationship domain required");
    var facts = new TreeMap<Long, ConsequencePatternMatcher.Fact>();
    for (var fact : snapshot.facts()) {
      if (fact.dependent() || fact.cohortId() <= 0)
        throw new UnsupportedGrouping(
            "Mixed or unplaced configuration ingredients require a separate grouping contract");
      facts.put(fact.id(), fact);
    }
    if (!facts.keySet().containsAll(relationshipIds))
      throw new UnsupportedGrouping("Relationship identity set differs from captured facts");
    var assets = new HashMap<Long, KnowledgeGraph.ConsequenceAssetSnapshot>();
    for (var asset : snapshot.reads().assets()) assets.put(asset.id(), asset);
    var members = new TreeMap<Long, Set<Long>>();
    for (var id : relationshipIds) {
      for (var type : ENDPOINTS)
        if (!snapshot
            .reads()
            .selections()
            .contains(
                new KnowledgeGraph.ConsequenceEdgeSelection(
                    id, type, GraphModel.Relationship.Direction.OUTGOING)))
          throw new UnsupportedGrouping("Complete selected endpoint sets are required");
      if (!assets.get(id).labels().contains("Observation"))
        throw new UnsupportedGrouping("Relationship observation payload required");
      var endpoints = new TreeSet<Long>();
      var sources = new TreeSet<Long>();
      var targets = new TreeSet<Long>();
      var participants = new TreeSet<Long>();
      for (var edge : snapshot.reads().edges()) {
        if (!ENDPOINTS.contains(edge.relationship()) || !Objects.equals(edge.source().id(), id))
          continue;
        if (!(edge.target().id() instanceof Long endpoint)
            || !assets.containsKey(endpoint)
            || !assets.get(endpoint).elementId().equals(edge.target().elementId())
            || !assets.get(endpoint).labels().contains("Observation"))
          throw new UnsupportedGrouping(
              "Endpoint payload and structural native identity must be captured together");
        if (!assets.get(id).elementId().equals(edge.source().elementId()))
          throw new UnsupportedGrouping("Relationship native identity changed");
        endpoints.add(endpoint);
        switch (edge.relationship()) {
          case HAS_RELATIONSHIP_SOURCE -> sources.add(endpoint);
          case HAS_RELATIONSHIP_TARGET -> targets.add(endpoint);
          case HAS_RELATIONSHIP_PARTICIPANT -> participants.add(endpoint);
          default -> throw new UnsupportedGrouping("Unsupported endpoint kind");
        }
      }
      var kind = relationshipKinds.get(id);
      if (kind == null)
        throw new UnsupportedGrouping("Canonical relationship orientation is unavailable");
      if (kind == EndpointKind.BOND) {
        if (!sources.isEmpty() || !targets.isEmpty() || participants.size() != 2)
          throw new UnsupportedGrouping(
              "Canonical unoriented bond requires exactly its two captured participants");
      } else if (sources.size() != 1 || targets.size() != 1 || !participants.isEmpty())
        throw new UnsupportedGrouping(
            "Canonical directed relationship requires one captured source and target");
      members.put(id, endpoints);
    }
    for (var id : facts.keySet()) if (!relationshipIds.contains(id)) members.put(id, Set.of(id));
    var remaining = new TreeSet<>(facts.keySet());
    var result = new ArrayList<ConsequencePatternMatcher.Snapshot>();
    while (!remaining.isEmpty()) {
      long seed = remaining.pollFirst();
      var component = new TreeSet<Long>();
      component.add(seed);
      var endpoints = new HashSet<>(members.get(seed));
      boolean added;
      do {
        added = false;
        for (var id : new ArrayList<>(remaining)) {
          if ((snapshot.catalogue() != null
                  || facts.get(id).cohortId() == facts.get(seed).cohortId())
              && !Collections.disjoint(endpoints, members.get(id))) {
            component.add(id);
            remaining.remove(id);
            endpoints.addAll(members.get(id));
            added = true;
          }
        }
      } while (added);
      if (Collections.disjoint(component, relationshipIds))
        continue; // Co-occurring substantials alone cannot structure a configuration.
      result.add(
          new ConsequencePatternMatcher.Snapshot(
              snapshot.contextId(),
              snapshot.observerId(),
              snapshot.epoch(),
              component.stream().map(facts::get).toList(),
              snapshot.contexts(),
              true,
              snapshot.reads(),
              snapshot.catalogue()));
    }
    return List.copyOf(result);
  }

  public enum IdentityKind {
    EXISTING,
    NEW,
    DEFERRED
  }

  public record Identity(IdentityKind kind, Long observationId, String reason) {}

  /**
   * Deterministic economic continuity: process components by smallest member ID and retain the
   * lowest available prior identity. Additional split components are born; merged-away identities
   * remain persistent and are reported separately for an empty current membership.
   */
  public record Assignment(Map<Integer, Identity> components, Set<Long> emptyIdentities) {
    public Assignment {
      components = Map.copyOf(components);
      emptyIdentities = Set.copyOf(emptyIdentities);
    }
  }

  public static Map<Integer, Identity> identify(
      List<Set<Long>> components, Map<Long, Set<Long>> previous, Set<Integer> provenBirths) {
    return assign(components, previous, provenBirths).components();
  }

  public static Assignment assign(
      List<Set<Long>> components, Map<Long, Set<Long>> previous, Set<Integer> provenBirths) {
    var partition = new HashSet<Long>();
    for (var component : components)
      for (var id : component)
        if (id == null || id <= 0 || !partition.add(id))
          throw new IllegalArgumentException("Disjoint durable component partition required");
    for (var entry : previous.entrySet())
      if (entry.getKey() == null
          || entry.getKey() <= 0
          || entry.getValue().stream().anyMatch(id -> id == null || id <= 0))
        throw new IllegalArgumentException("Durable previous identity required");
    if (provenBirths.stream()
        .anyMatch(index -> index == null || index < 0 || index >= components.size()))
      throw new IllegalArgumentException("Birth evidence names an absent component");
    var order = new ArrayList<Integer>();
    for (int i = 0; i < components.size(); i++) {
      if (components.get(i).isEmpty())
        throw new IllegalArgumentException(
            "Empty network is an existing identity update, not a new component");
      order.add(i);
    }
    order.sort(Comparator.comparingLong(index -> Collections.min(components.get(index))));
    var used = new HashSet<Long>();
    var result = new LinkedHashMap<Integer, Identity>();
    for (var index : order) {
      var overlap = new TreeSet<Long>();
      for (var entry : previous.entrySet())
        if (!Collections.disjoint(components.get(index), entry.getValue()))
          overlap.add(entry.getKey());
      var available = overlap.stream().filter(id -> !used.contains(id)).findFirst();
      if (available.isPresent()) {
        long id = available.get();
        used.add(id);
        result.put(
            index,
            new Identity(
                IdentityKind.EXISTING,
                id,
                overlap.size() > 1
                    ? "Deterministic merge continuity; other identities persist empty"
                    : "Deterministic component continuity"));
      } else if (!overlap.isEmpty() || provenBirths.contains(index))
        result.put(
            index,
            new Identity(
                IdentityKind.NEW,
                null,
                overlap.isEmpty()
                    ? "Complete captured discovery establishes a new component"
                    : "Additional split component"));
      else
        result.put(
            index,
            new Identity(
                IdentityKind.DEFERRED,
                null,
                "No captured identity association or complete birth evidence"));
    }
    var empty = new TreeSet<>(previous.keySet());
    empty.removeAll(used);
    return new Assignment(result, empty);
  }
}
