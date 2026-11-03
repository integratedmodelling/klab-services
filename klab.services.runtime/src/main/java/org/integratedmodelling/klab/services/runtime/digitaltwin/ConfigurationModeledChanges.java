package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.utilities.Utils;

/** Event inputs, not every endpoint of a bookkeeping edge. Native payloads remain required. */
final class ConfigurationModeledChanges {
  private ConfigurationModeledChanges() {}

  static final Set<GraphModel.Relationship> STRUCTURE =
      EnumSet.of(
          GraphModel.Relationship.HAS_MEMBER,
          GraphModel.Relationship.HAS_CHILD,
          GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE,
          GraphModel.Relationship.HAS_RELATIONSHIP_TARGET,
          GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT);

  static Set<Long> candidates(KnowledgeGraph.Commit commit) {
    var ids = new TreeSet<Long>();
    ids.addAll(commit.getAddedObservations());
    ids.addAll(commit.getModifiedAssets());
    ids.addAll(commit.getDeletedAssets());
    for (var links : List.of(commit.getAddedLinks(), commit.getDeletedLinks()))
      for (var link : links) {
        GraphModel.Relationship type;
        try {
          type = GraphModel.Relationship.valueOf(link.getThird());
        } catch (IllegalArgumentException unknown) {
          continue;
        }
        if (!STRUCTURE.contains(type)) continue;
        // Placement changes name their member. Parent catalogue/context is not an observed input
        // change.
        if (type == GraphModel.Relationship.HAS_CHILD || type == GraphModel.Relationship.HAS_MEMBER)
          ids.add(link.getSecond());
        else {
          ids.add(link.getFirst());
          ids.add(link.getSecond());
        }
      }
    ids.removeIf(id -> id == null || id <= 0);
    return Collections.unmodifiableSet(ids);
  }

  static Set<Long> observations(Set<Long> candidates, Map<Long, Observation> captured) {
    var ids = new TreeSet<Long>();
    for (var id : candidates) {
      var observation = captured.get(id);
      if (observation != null
          && observation.getMetadata().get("klab.configuration.current") == null) ids.add(id);
    }
    return Collections.unmodifiableSet(ids);
  }

  static Set<Long> journalChanges(
      KnowledgeGraph.ConsequenceAssetSnapshot activity,
      KnowledgeGraph.Commit commit,
      String context) {
    Object encoded = activity.nativeProperties().get(TransitionCommit.METADATA_KEY);
    if (encoded == null) return Set.of();
    if (!(encoded instanceof String text))
      throw new IllegalStateException("Native transition receipt is malformed");
    try {
      var tree =
          org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper()
              .readTree(text);
      if (tree.path("version").asInt() != 1
          || !context.equals(tree.path("contextId").asText())
          || tree.path("commit").path("id").asLong() != commit.getId()
          || !tree.path("journals").isArray())
        throw new SecurityException("Transition receipt belongs to another commit/context");
      var changed = new TreeSet<Long>();
      for (var row : tree.get("journals")) {
        var journal = Utils.Json.parseObject(row.toString(), SchedulerJournal.class);
        if (journal.commitId() != commit.getId())
          throw new SecurityException("Scheduler journal belongs to another commit");
        for (var id : journal.changedAssets())
          if (id == null || id <= 0)
            throw new IllegalStateException("Scheduler journal lacks durable changed observation");
          else changed.add(id);
      }
      return Set.copyOf(changed);
    } catch (java.io.IOException malformed) {
      throw new IllegalStateException("Native transition receipt cannot be decoded", malformed);
    }
  }
}
