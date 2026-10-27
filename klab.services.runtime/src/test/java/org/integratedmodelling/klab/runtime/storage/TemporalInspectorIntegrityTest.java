package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.ObserverProvenance;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.runtime.CompiledDataflow;
import org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.OccurrenceRegistration;
import org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.SchedulerImpl;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Corrupt durable evidence after a real scheduler/storage commit; never certify by timestamps alone. */
class TemporalInspectorIntegrityTest {
  @TempDir Path directory;

  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  private TemporalProcessIntegrationTest.Fixture registered(String expression, boolean actualObserver)
      throws Exception {
    var owner = new TemporalProcessIntegrationTest();
    owner.directory = directory;
    var f = owner.new Fixture(expression);
    if (actualObserver) f.selectInitialObserver(owner.observation("observer", SemanticType.AGENT, 500));
    f.quality.setEventTimestamps(new ArrayList<>(List.of(0L)));
    f.creators.remove(f.process.getId());
    var registration = registration(f);
    f.process.getMetadata().remove(OccurrenceRegistration.METADATA_KEY);
    var plan = Utils.Json.parseObject(registration.plan(), Actuator.class);
    f.scheduler.close();
    when(f.graph.getScheduledObservations(f.root)).thenReturn(List.of());
    f.scheduler = new SchedulerImpl(f.root, f.twin);
    var submission = Activity.of(Activity.Type.SUBMISSION);
    ObserverProvenance.recordSelection(submission, f.observer);
    var scope = f.root.executingFresh(submission, f.bearer);
    if (f.observer != null) scope.getCurrentTransaction()
        .link(submission, f.observer, GraphModel.Relationship.HAS_OBSERVER);
    scope.getCurrentTransaction().resolveWith(f.process,
        new CompiledDataflow(f.runtime, f.process, scope).restoreOccurrenceExecutor(plan));
    assertTrue(f.scheduler.submit(f.process, scope), f::failureDescription);
    assertTrue(scope.commit() > 0, f::failureDescription);
    assertEquals(12, f.journals.size(), f::failureDescription);
    assertTrue(schedule(f), "Uncorrupted scheduler evidence must pass before mutation");
    return f;
  }

  private static OccurrenceRegistration registration(TemporalProcessIntegrationTest.Fixture f) {
    return Utils.Json.parseObject(f.process.getMetadata().get(OccurrenceRegistration.METADATA_KEY)
        .toString(), OccurrenceRegistration.class);
  }

  private static boolean schedule(TemporalProcessIntegrationTest.Fixture f) {
    return TemporalInspector.scheduleCheck(f.root, f.process, 1, Time.Resolution.Type.MONTH,
        "2014-01-01T00:00:00Z", "2015-01-01T00:00:00Z", 12);
  }

  private static boolean relative(TemporalProcessIntegrationTest.Fixture f, double delta) {
    return TemporalInspector.relativeCheck(f.root, f.process, f.quality, 12, delta, 1e-8);
  }

  private static List<Activity> simulations(TemporalProcessIntegrationTest.Fixture f) {
    return f.creators.get(f.process.getId()).stream()
        .filter(a -> a.getType() == Activity.Type.SIMULATION).toList();
  }

  private static TransitionCommit envelope(Activity activity) {
    return Utils.Json.parseObject(activity.getMetadata().get(TransitionCommit.METADATA_KEY)
        .toString(), TransitionCommit.class);
  }

  private static void envelope(Activity activity, TransitionCommit envelope) {
    activity.getMetadata().put(TransitionCommit.METADATA_KEY, Utils.Json.asString(envelope));
  }

  private static void journal(Activity activity, UnaryOperator<SchedulerJournal> mutation) {
    var old = envelope(activity);
    envelope(activity, new TransitionCommit(old.version(), old.contextId(), old.commit(),
        List.of(mutation.apply(old.journals().getFirst())), old.qualities()));
  }

  private static SchedulerJournal journalIdentity(SchedulerJournal old, String registration, String revision) {
    return new SchedulerJournal(old.version(), old.eventId(), old.causalParentId(), old.kind(),
        old.start(), old.end(), registration, revision, old.support(), old.commitId(),
        old.changedAssets(), old.publicationPending(), old.observedEvent());
  }

  private static void plan(TemporalProcessIntegrationTest.Fixture f, Actuator plan) {
    var old = registration(f);
    f.process.getMetadata().put(OccurrenceRegistration.METADATA_KEY, Utils.Json.asString(
        new OccurrenceRegistration(old.version(), old.id(), old.planRevision(), old.role(),
            old.bearerId(), old.schedules(), Utils.Json.asString(plan))));
  }

  @Test void committedHistoryPassesForActualAndNullObserverDespiteUnrelatedOrdinaryPlan() throws Exception {
    for (boolean selected : List.of(false, true)) try (var f = registered("elevation - 10", selected)) {
      assertTrue(relative(f, -10));
      assertTrue(TemporalInspector.provenanceCheck(f.root, f.process));
      var unrelated = new ActuatorImpl();
      unrelated.setExecutionRole(Actuator.ExecutionRole.INITIALIZATION);
      f.process.getMetadata().put(Scheduler.PLAN_METADATA_KEY, Utils.Json.asString(unrelated));
      assertTrue(schedule(f), "Occurrence registration must override ordinary prerequisite metadata");
      assertTrue(relative(f, -10));
      assertTrue(TemporalInspector.provenanceCheck(f.root, f.process));
    }
  }

  @Test void foreignRegistrationRevisionAndContextCannotCertifyProcess() throws Exception {
    try (var f = registered("elevation - 10", false)) {
      var activity = simulations(f).getFirst();
      var original = envelope(activity);
      journal(activity, j -> journalIdentity(j, "another-process", j.planRevision()));
      assertThrows(IllegalStateException.class, () -> schedule(f));
      assertThrows(IllegalStateException.class, () -> relative(f, -10));
      envelope(activity, original);
      journal(activity, j -> journalIdentity(j, j.registrationId(), "stale-revision"));
      assertThrows(IllegalStateException.class, () -> schedule(f));
      envelope(activity, new TransitionCommit(original.version(), "another-context", original.commit(),
          original.journals(), original.qualities()));
      assertThrows(IllegalStateException.class, () -> schedule(f));
      envelope(activity, original);
      assertTrue(schedule(f));
    }
  }

  @Test void copiedPlanAndStaleCadenceCannotCertifyRegisteredProcess() throws Exception {
    try (var f = registered("elevation - 10", false)) {
      var original = registration(f);
      var copied = Utils.Json.parseObject(original.plan(), Actuator.class);
      ((ObservationImpl) copied.getObservation()).setId(f.process.getId() + 1);
      plan(f, copied);
      assertThrows(IllegalStateException.class, () -> schedule(f));
      copied = Utils.Json.parseObject(original.plan(), Actuator.class);
      var changed = copied.getOccurrenceSchedules().get(0);
      copied.getOccurrenceSchedules().put(0, new OccurrenceSchedule(changed.version(),
          changed.start(), changed.end(), 2, changed.unit(), changed.overridable(), changed.source()));
      plan(f, copied);
      assertThrows(IllegalStateException.class, () -> schedule(f));
      f.process.getMetadata().put(OccurrenceRegistration.METADATA_KEY, Utils.Json.asString(original));
      assertTrue(schedule(f));
    }
  }

  @Test void missingExactHistoryAndMissingCommittedDeltaRejectEvenZeroDelta() throws Exception {
    // Unchanged writes may be omitted by the runtime. Force real revisions with changes
    // smaller than the 1e-8 assertion tolerance, so zero remains a valid numeric control.
    try (var f = registered("elevation + 0.00000000001", false)) {
      var committedHistory = Utils.Json.parseObject(
          f.quality.getMetadata().get(TemporalHistory.KEY).toString(), TemporalHistory.class);
      assertEquals(12, committedHistory.revisions().size(), "Zero delta still requires twelve actual writes");
      assertEquals(12, f.deltas.stream().filter(d -> d.observationId() == f.quality.getId()).count());
      assertTrue(relative(f, 0));
      var original = f.quality.getMetadata().remove(TemporalHistory.KEY);
      assertFalse(relative(f, 0), "INIT fallback must not supply twelve nonexistent temporal revisions");
      var history = Utils.Json.parseObject(original.toString(), TemporalHistory.class);
      f.quality.getMetadata().put(TemporalHistory.KEY, Utils.Json.asString(new TemporalHistory(1,
          history.revisions().subList(1, history.revisions().size()))));
      assertFalse(relative(f, 0));
      f.quality.getMetadata().put(TemporalHistory.KEY, original);
      var activity = simulations(f).getFirst(); var commit = envelope(activity);
      envelope(activity, new TransitionCommit(commit.version(), commit.contextId(), commit.commit(),
          commit.journals(), List.of()));
      assertFalse(relative(f, 0), "changedAssets alone cannot certify a quality state");
      envelope(activity, commit);
      assertTrue(relative(f, 0));
    }
  }

  @Test void equalSupportWrongEventAndMismatchedShardManifestAreRejected() throws Exception {
    try (var f = registered("elevation - 10", false)) {
      var original = f.quality.getMetadata().get(TemporalHistory.KEY);
      var history = Utils.Json.parseObject(original.toString(), TemporalHistory.class);
      var revisions = new ArrayList<>(history.revisions()); var first = revisions.getFirst();
      revisions.set(0, new TemporalHistory.Revision("foreign-event-at-identical-time", first.start(),
          first.end(), first.support(), first.shards()));
      f.quality.getMetadata().put(TemporalHistory.KEY, Utils.Json.asString(new TemporalHistory(1, revisions)));
      assertFalse(relative(f, -10), "A covering revision from another event is insufficient");
      revisions.set(0, new TemporalHistory.Revision(first.event(), first.start(), first.end(),
          first.support(), List.of("different-committed-shard")));
      f.quality.getMetadata().put(TemporalHistory.KEY, Utils.Json.asString(new TemporalHistory(1, revisions)));
      assertFalse(relative(f, -10));
      f.quality.getMetadata().put(TemporalHistory.KEY, original);
      assertTrue(relative(f, -10));
    }
  }

  @Test void duplicateJournalAndDuplicateQualityHistoryAreRejected() throws Exception {
    try (var f = registered("elevation - 10", false)) {
      var creators = f.creators.get(f.process.getId());
      var repeated = simulations(f).getFirst(); creators.add(repeated);
      assertThrows(IllegalStateException.class, () -> schedule(f));
      creators.remove(creators.size() - 1);
      var original = f.quality.getMetadata().get(TemporalHistory.KEY);
      var revisions = new ArrayList<>(Utils.Json.parseObject(original.toString(), TemporalHistory.class).revisions());
      revisions.add(revisions.getFirst());
      // Write malformed wire evidence without invoking the validating record constructor first.
      f.quality.getMetadata().put(TemporalHistory.KEY,
          Utils.Json.asString(Map.of("version", 1, "revisions", revisions)));
      assertThrows(RuntimeException.class, () -> relative(f, -10));
      f.quality.getMetadata().put(TemporalHistory.KEY, original);
      assertTrue(relative(f, -10));
    }
  }

  @Test void wrongActualObserverCannotCertifyScheduleOrValues() throws Exception {
    try (var f = registered("elevation - 10", true)) {
      var activity = simulations(f).getFirst();
      var original = activity.getMetadata().get(Metadata.IM_OBSERVER_SELECTION_ID);
      activity.getMetadata().put(Metadata.IM_OBSERVER_SELECTION_ID, 0L);
      assertThrows(IllegalStateException.class, () -> schedule(f));
      assertThrows(IllegalStateException.class, () -> relative(f, -10));
      assertThrows(IllegalStateException.class, () -> TemporalInspector.provenanceCheck(f.root, f.process));
      activity.getMetadata().put(Metadata.IM_OBSERVER_SELECTION_ID, original);
      assertTrue(schedule(f));
    }
  }

  @Test void processInitShardIsRejectedWithoutOpeningProcessStorage() throws Exception {
    try (var f = registered("elevation - 10", false)) {
      assertTrue(TemporalInspector.provenanceCheck(f.root, f.process));
      var shard = mock(Storage.Shard.class); when(shard.getTimestamp()).thenReturn(0L);
      when(f.graph.query(Storage.Shard.class, f.root).source(f.process)
          .along(GraphModel.Relationship.HAS_DATA).run(f.root)).thenReturn(List.of(shard));
      assertFalse(TemporalInspector.provenanceCheck(f.root, f.process));
      assertFalse(f.stores.containsKey(f.process.getId()), "Provenance inspection must not allocate process storage");
    }
  }

  @Test void nonfiniteInitialEvidenceAndChangedMissingnessAreRejected() throws Exception {
    try (var f = registered("elevation - 10", false)) {
      assertTrue(relative(f, -10));
      var initial = (StorageImpl.BaseScanner) f.store(f.quality)
          .scan(Scheduler.Event.initialization(), f.layout, Storage.DoubleScanner.class, false).getFirst();
      f.store(f.quality).finalizeRun(initial);
      // Deliberately corrupt the native INIT buffer, preserving the committed event descriptors.
      initial.data.set(0, Double.POSITIVE_INFINITY);
      assertFalse(relative(f, -10));
      initial.data.set(0, Double.NaN);
      assertFalse(relative(f, -10), "Missingness must agree at each position");
      initial.data.set(0, 100.0);
      assertTrue(relative(f, -10));
    }
  }
}
