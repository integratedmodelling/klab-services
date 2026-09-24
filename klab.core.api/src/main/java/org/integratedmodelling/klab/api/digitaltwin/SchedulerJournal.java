package org.integratedmodelling.klab.api.digitaltwin;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Atomic event/completion/publication envelope on the root activity. Dispatch/delivery follow S2. */
public record SchedulerJournal(int version, String eventId, String causalParentId,
    Scheduler.Event.Type kind, long start, long end, String registrationId,
    String planRevision, String support, long commitId, List<Long> changedAssets,
    boolean publicationPending, ObservedEvent observedEvent) implements Serializable {
  public SchedulerJournal(int version, String eventId, String causalParentId,
      Scheduler.Event.Type kind, long start, long end, String registrationId,
      String planRevision, String support, long commitId, List<Long> changedAssets,
      boolean publicationPending) {
    this(version, eventId, causalParentId, kind, start, end, registrationId, planRevision,
        support, commitId, changedAssets, publicationPending, null);
  }
  public static final String METADATA_KEY = "klab.scheduler.journal";
  public SchedulerJournal {
    if ((version != 1 && version != 2) || version == 1 && observedEvent != null
        || version == 2 && (observedEvent == null || kind != Scheduler.Event.Type.EVENT)
        || eventId == null || eventId.isBlank() || registrationId == null
        || registrationId.isBlank() || planRevision == null || planRevision.isBlank()
        || support == null || end < start || commitId < 0) {
      throw new IllegalArgumentException("Invalid scheduler journal envelope");
    }
    Objects.requireNonNull(kind);
    changedAssets = List.copyOf(changedAssets);
  }
  public SchedulerJournal committed(long id) {
    if (id <= 0) throw new IllegalArgumentException("A durable commit ID is required");
    return new SchedulerJournal(version, eventId, causalParentId, kind, start, end,
        registrationId, planRevision, support, id, changedAssets, publicationPending, observedEvent);
  }
}
