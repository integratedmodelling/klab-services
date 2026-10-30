package org.integratedmodelling.klab.runtime.storage;

import java.time.Instant;
import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.utilities.Utils;

/** Read-only assertions over committed process history, independent of initial numeric values. */
public final class TemporalInspector {
  private TemporalInspector() {}

  private record Transition(Activity activity, SchedulerJournal journal, TransitionCommit commit) {}

  /** Private projection of the durable service wire format; never an execution grant. */
  private record RegisteredSchedule(int computation, OccurrenceSchedule declaration,
      OccurrenceSchedule.Bound bound) {}
  private record Registration(int version, String id, String planRevision,
      Actuator.ExecutionRole role, long bearerId, List<RegisteredSchedule> schedules, String plan) {}

  private static Registration registration(Observation process) {
    Object encoded = process.getMetadata().get("klab.scheduler.occurrence");
    if (!(encoded instanceof String text))
      throw new IllegalStateException("Process has no persisted occurrence registration");
    var registration = Utils.Json.parseObject(text, Registration.class);
    if (process.getId() <= 0 || registration.version() != 1
        || registration.id() == null || registration.id().isBlank()
        || registration.planRevision() == null || registration.planRevision().isBlank()
        || registration.role() != Actuator.ExecutionRole.PROCESS
        || registration.schedules() == null || registration.schedules().size() != 1
        || registration.plan() == null || registration.plan().isBlank())
      throw new IllegalStateException("Invalid process occurrence registration");
    var plan = plan(registration);
    if (plan.getExecutionRole() != registration.role() || plan.getObservation() == null
        || plan.getObservation().getId() != process.getId()
        || !Objects.equals(plan.getObservation().getObservable().getSemantics(),
            process.getObservable().getSemantics())
        || plan.getOccurrenceSchedules().size() != 1)
      throw new IllegalStateException("Occurrence plan belongs to a different process");
    var scheduled = registration.schedules().getFirst();
    if (!Objects.equals(scheduled.declaration(), plan.getOccurrenceSchedules().get(scheduled.computation()))
        || !Objects.equals(scheduled.bound(), scheduled.declaration()
            .bind(Scale.create(process.getGeometry()).getTime())))
      throw new IllegalStateException("Occurrence plan and registered schedule disagree");
    return registration;
  }

  private static Actuator plan(Registration registration) {
    // The occurrence registration is authoritative; ordinary prerequisite-plan metadata is unrelated.
    return Utils.Json.parseObject(registration.plan(), Actuator.class);
  }

  private static List<Activity> creators(ContextScope scope, Observation observation) {
    return scope.getDigitalTwin().getKnowledgeGraph().query(Activity.class, scope)
        .target(observation).along(GraphModel.Relationship.CREATED).run(scope);
  }

  private static List<Transition> history(ContextScope scope, Observation process) {
    var registration = registration(process);
    requireObserver(scope, process);
    var result = new ArrayList<Transition>();
    var events = new HashSet<String>();
    var commits = new HashSet<Long>();
    for (var activity : creators(scope, process)) {
      if (activity.getType() != Activity.Type.SIMULATION) continue;
      if (activity.getOutcome() != Activity.Outcome.SUCCESS)
        throw new IllegalStateException("Unsuccessful committed simulation");
      var encoded = activity.getMetadata().get(TransitionCommit.METADATA_KEY);
      if (!(encoded instanceof String text))
        throw new IllegalStateException("Simulation has no committed transition envelope");
      var envelope = Utils.Json.parseObject(text, TransitionCommit.class);
      if (!scope.getId().equals(envelope.contextId()) || envelope.journals().size() != 1)
        throw new IllegalStateException("Expected one transition journal in the owning context");
      var journal = envelope.journals().getFirst();
      if (journal.kind() != Scheduler.Event.Type.TEMPORAL_TRANSITION
          || !registration.id().equals(journal.registrationId())
          || !registration.planRevision().equals(journal.planRevision())
          || !events.add(journal.eventId()) || !commits.add(journal.commitId()))
        throw new IllegalStateException("Duplicate, foreign or non-temporal process commit");
      var support = Geometry.create(journal.support());
      var time = Scale.create(support).getTime();
      if (time == null || time.getStart() == null || time.getEnd() == null
          || time.getStart().getMilliseconds() != journal.start()
          || time.getEnd().getMilliseconds() != journal.end()
          || !nonTemporalSupport(support).equals(nonTemporalSupport(process.getGeometry())))
        throw new IllegalStateException("Process journal support disagrees with its event");
      result.add(new Transition(activity, journal, envelope));
    }
    result.sort(Comparator.comparingLong(t -> t.journal().start()));
    return List.copyOf(result);
  }

  private static List<String> nonTemporalSupport(Geometry geometry) {
    // Localization promotes the original descriptor to native extents. Compare both in that
    // canonical representation, rather than rejecting equivalent descriptor spellings.
    return Scale.create(geometry).getDimensions().stream().filter(d -> d.getType() != Geometry.Dimension.Type.TIME)
        .map(d -> d.encode()).sorted().toList();
  }

  private static long requireObserver(ContextScope scope, Observation process) {
    var selected = org.integratedmodelling.common.runtime.ObserverProvenance.recover(scope, process);
    var root = org.integratedmodelling.common.runtime.ObserverProvenance.recover(
        scope, scope.getContextObservation());
    if (!selected.known() || !root.known())
      throw new IllegalStateException("Process and focused root require recorded observers");
    long observer = selected.observer() == null ? 0 : selected.observer().getId();
    if (observer != (root.observer() == null ? 0 : root.observer().getId()))
      throw new IllegalStateException("Process observer differs from the focused root");
    return observer;
  }

  /** Require one model cadence, its bound context support and exactly the expected durable ticks. */
  public static boolean scheduleCheck(ContextScope scope, Observation process,
      double step, Time.Resolution.Type unit, String start, String end, int count) {
    if (count < 1) throw new IllegalArgumentException("Expected transition count must be positive");
    long from = Instant.parse(start).toEpochMilli(), until = Instant.parse(end).toEpochMilli();
    var registration = registration(process);
    var plan = plan(registration);
    if (plan.getExecutionRole() != Actuator.ExecutionRole.PROCESS
        || !Boolean.TRUE.equals(process.getMetadata().get(Scheduler.REGISTRATION_METADATA_KEY))
        || plan.getOccurrenceSchedules().size() != 1) return false;
    var declaration = plan.getOccurrenceSchedules().values().iterator().next();
    var bound = declaration.bind(Scale.create(process.getGeometry()).getTime());
    if (declaration.source() != OccurrenceSchedule.Source.MODEL || declaration.step() != step
        || declaration.unit() != unit || bound.start() != from || bound.end() != until
        || bound.anchor() != from) return false;
    var transitions = history(scope, process);
    if (transitions.size() != count) return false;
    var cadence = new OccurrenceSchedule.Cadence(step, unit);
    long cursor = from;
    for (var transition : transitions) {
      long next = Math.min(until, cadence.advance(cursor));
      if (transition.journal().start() != cursor || transition.journal().end() != next
          || next <= cursor) return false;
      cursor = next;
    }
    return cursor == until;
  }

  /** Compare every covered cell at tick n with its own INIT value plus n*delta. */
  public static boolean relativeCheck(ContextScope scope, Observation process, Observation quality,
      int count, double delta, double tolerance) {
    if (count < 1 || !Double.isFinite(delta) || !Double.isFinite(tolerance) || tolerance < 0)
      throw new IllegalArgumentException("Invalid relative-value assertion");
    var transitions = history(scope, process);
    if (transitions.size() != count) return false;
    Object encodedHistory = quality.getMetadata().get(TemporalHistory.KEY);
    if (!(encodedHistory instanceof String text)) return false;
    var revisions = Utils.Json.parseObject(text, TemporalHistory.class).revisions();
    long finite = 0;
    for (int n = 0; n < transitions.size(); n++) {
      var journal = transitions.get(n).journal();
      if (!journal.changedAssets().contains(quality.getId())) return false;
      var matching = revisions.stream().filter(r -> r.event().equals(journal.eventId())).toList();
      var deltas = transitions.get(n).commit().qualities().stream()
          .filter(d -> d.observationId() == quality.getId() && d.eventId().equals(journal.eventId())).toList();
      if (matching.size() != 1 || deltas.size() != 1) return false;
      var revision = matching.getFirst(); var committed = deltas.getFirst();
      var expectedShards = new HashSet<>(committed.shards());
      var revisionTime = Scale.create(Geometry.create(revision.support())).getTime();
      if (revision.start() != journal.start() || revision.end() != journal.end()
          || !revision.support().equals(committed.support()) || revisionTime == null
          || revisionTime.getStart() == null || revisionTime.getEnd() == null
          || revisionTime.getStart().getMilliseconds() != journal.start()
          || revisionTime.getEnd().getMilliseconds() != journal.end()
          || !new HashSet<>(revision.shards()).equals(expectedShards)
          || expectedShards.size() != committed.shards().size()) return false;
      var event = new StorageScan.Slice(journal.kind(), journal.eventId(), journal.start(), journal.end()).event();
      try (var initial = StorageReads.open(quality, scope, Scheduler.Event.initialization(),
              Data.FillCurve.UNSPECIFIED, Storage.DoubleScanner.class);
          var current = StorageReads.open(quality, scope, event,
              Data.FillCurve.UNSPECIFIED, Storage.DoubleScanner.class)) {
        var left = initial.scanners().getFirst();
        var right = current.scanners().getFirst();
        if (!right.view().sources().stream().map(StorageScan.SourceShard::urn)
            .collect(java.util.stream.Collectors.toSet()).equals(expectedShards)) return false;
        if (left.size() != right.size()) return false;
        while (left.hasNext()) {
          if (!right.hasNext() || left.position() != right.position()
              || left.isValid() != right.isValid()) return false;
          if (left.isValid()) {
            double expected = left.peek() + (n + 1) * delta;
            double actual = right.peek();
            if (!Double.isFinite(expected) || !Double.isFinite(actual)
                || Math.abs(actual - expected) > Math.max(tolerance, 4 * Math.ulp(expected)))
              return false;
            finite++;
          }
          left.nextLong(); right.nextLong();
        }
        if (right.hasNext()) return false;
      }
    }
    return finite > 0;
  }

  /** Provenance evidence only; absence of executor dispatch is verified by executor tests. */
  public static boolean provenanceCheck(ContextScope scope, Observation process) {
    long observer = requireObserver(scope, process);
    int existence = 0;
    for (var activity : creators(scope, process)) {
      var recorded = activity.getMetadata().get(Metadata.IM_OBSERVER_SELECTION_ID);
      if (!(recorded instanceof Number id) || id.longValue() != observer) return false;
      var linked = scope.getDigitalTwin().getKnowledgeGraph().query(Observation.class, scope)
          .source(activity).along(GraphModel.Relationship.HAS_OBSERVER).run(scope);
      if (observer == 0 ? !linked.isEmpty() : linked.size() != 1 || linked.getFirst().getId() != observer)
        return false;
      if (activity.getType() == Activity.Type.INITIALIZATION) {
        existence++;
        if (activity.getType().isContextualization()
            || activity.getOutcome() != Activity.Outcome.SUCCESS
            || !activity.getSchedulerTime().isEmpty()
            || activity.getMetadata().containsKey(Metadata.IM_EXECUTION_GEOMETRY)) return false;
      } else if (activity.getSchedulerTime().contains(0L)) return false;
    }
    if (existence != 1) return false;
    if (scope.getDigitalTwin().getKnowledgeGraph().query(Storage.Shard.class, scope)
        .source(process).along(GraphModel.Relationship.HAS_DATA).run(scope).stream()
        .anyMatch(shard -> shard.getTimestamp() == 0)) return false;
    var encoded = plan(registration(process)).getData().get(ProcessPlan.DATA_KEY);
    if (encoded == null) return false;
    var bindings = Utils.Json.parseObject(encoded.toString(), ProcessPlan.class);
    var created = bindings.bindings().stream()
        .filter(b -> b.effect() == ProcessPlan.Effect.CREATED).toList();
    if (!created.isEmpty()) {
      var observations = scope.getDigitalTwin().getKnowledgeGraph().query(Observation.class, scope).run(scope);
      for (var binding : created) for (var quality : observations) {
        long bearer = binding.bearer() == ProcessPlan.Bearer.OCCURRENT ? process.getId() : bindings.bearerId();
        if (quality.getParentId() != bearer) continue;
        if (!quality.getObservable().getSemantics().equals(binding.observable().getSemantics())) continue;
        if (creators(scope, quality).stream().anyMatch(a -> a.getType() == Activity.Type.INITIALIZATION))
          return false;
        // Native INIT access may allocate storage: inspect committed descriptors instead.
        if (scope.getDigitalTwin().getKnowledgeGraph().query(Storage.Shard.class, scope)
            .source(quality).along(GraphModel.Relationship.HAS_DATA).run(scope).stream()
            .anyMatch(shard -> shard.getTimestamp() == 0)) return false;
      }
    }
    return true;
  }
}
