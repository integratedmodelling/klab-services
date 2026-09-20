package org.integratedmodelling.klab.api.digitaltwin;

import java.io.Serializable;
import java.util.List;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;

/** Durable S7 envelope carried in committed Activity metadata, including on history replay. */
public record TransitionCommit(
    int version,
    String contextId,
    KnowledgeGraph.Commit commit,
    List<SchedulerJournal> journals,
    List<TemporalStateDelta> qualities)
    implements Serializable {
  public static final String METADATA_KEY = "klab.transition.commit";

  public TransitionCommit {
    if (version != 1
        || contextId == null
        || contextId.isBlank()
        || commit == null
        || commit.getId() <= 0)
      throw new IllegalArgumentException("Unsupported or invalid transition commit");
    journals = List.copyOf(journals);
    qualities = List.copyOf(qualities);
    if (journals.isEmpty() || journals.stream().anyMatch(j -> j.commitId() != commit.getId()))
      throw new IllegalArgumentException("Transition journals must belong to their commit");
    for (var quality : qualities) {
      if (journals.stream()
          .noneMatch(
              j ->
                  j.eventId().equals(quality.eventId())
                      && j.changedAssets().contains(quality.observationId())))
        throw new IllegalArgumentException("Quality delta has no matching consequence journal");
    }
  }

  public boolean consequential() {
    return journals.stream()
        .anyMatch(j -> j.publicationPending() || j.kind() == Scheduler.Event.Type.EVENT);
  }
}
