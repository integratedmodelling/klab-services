package org.integratedmodelling.common.services.client.digitaltwin;

import java.util.*;
import org.integratedmodelling.klab.api.digitaltwin.*;

/** Context-local history. Logical event identity is independent of timestamp and arrival order. */
public final class TransitionHistory {
  private final String contextId;
  private final Map<Long, TransitionCommit> commits = new HashMap<>();

  public TransitionHistory(String contextId) {
    this.contextId = Objects.requireNonNull(contextId);
  }

  public synchronized boolean accept(TransitionCommit transition) {
    if (!contextId.equals(transition.contextId()))
      throw new IllegalArgumentException("Foreign transition");
    var previous = commits.get(transition.commit().getId());
    if (previous != null) return false;
    commits.put(transition.commit().getId(), transition);
    return true;
  }

  public synchronized boolean containsCommit(long id) {
    return commits.containsKey(id);
  }

  public synchronized List<TransitionCommit> commits() {
    return commits.values().stream()
        .sorted(Comparator.comparingLong(t -> t.commit().getId()))
        .toList();
  }

  public synchronized List<SchedulerJournal> timeline() {
    return commits.values().stream()
        .flatMap(t -> t.journals().stream())
        .filter(j -> j.publicationPending() || j.kind() == Scheduler.Event.Type.EVENT)
        .sorted(
            Comparator.comparingLong(SchedulerJournal::end)
                .thenComparing(SchedulerJournal::eventId))
        .toList();
  }

  public synchronized List<TemporalStateDelta> qualityStates(long observationId) {
    return commits().stream()
        .flatMap(t -> t.qualities().stream())
        .filter(q -> q.observationId() == observationId)
        .toList();
  }

  public synchronized boolean modifies(long observationId) {
    return commits.values().stream()
        .anyMatch(t -> t.consequential() && t.commit().getModifiedAssets().contains(observationId));
  }

  /** One duration bar per started event, grouped by canonical semantic type for IDE lanes. */
  public synchronized Map<String, List<ObservedEvent>> eventsByType() {
    var started = new TreeMap<Long, ObservedEvent>();
    commits.values().stream().flatMap(c -> c.journals().stream())
        .map(SchedulerJournal::observedEvent).filter(Objects::nonNull)
        .filter(e -> e.boundary() == Scheduler.Event.Boundary.START)
        .forEach(e -> started.putIfAbsent(e.observationId(), e));
    var grouped = new TreeMap<String, List<ObservedEvent>>();
    started.values().stream().sorted(Comparator.comparingLong(ObservedEvent::start)
        .thenComparingLong(ObservedEvent::observationId))
        .forEach(e -> grouped.computeIfAbsent(e.type(), key -> new ArrayList<>()).add(e));
    grouped.replaceAll((key, value) -> List.copyOf(value));
    return Collections.unmodifiableMap(grouped);
  }
}
