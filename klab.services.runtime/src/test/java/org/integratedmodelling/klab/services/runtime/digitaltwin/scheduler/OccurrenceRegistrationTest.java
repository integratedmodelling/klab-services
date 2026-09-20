package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.common.cache.CacheBuilder;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.data.impl.LinkImpl;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

class OccurrenceRegistrationTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  @Test void sharedPrerequisiteIsInitializedOnceAcrossParallelDependencyPaths() throws Exception {
    try(var f=new Fixture()) {
      var root=observation("Root",SemanticType.SUBJECT,51);
      var slope=observation("Slope",SemanticType.QUALITY,52);
      slope.setSubstantialQuality(true);
      var direct=new LinkImpl(f.quality,root,GraphModel.Relationship.AFFECTS);
      var derived=new LinkImpl(slope,root,GraphModel.Relationship.AFFECTS);
      var prerequisite=new LinkImpl(f.quality,slope,GraphModel.Relationship.AFFECTS);
      when(f.kg.getLinks(eq(root),eq(GraphModel.Relationship.Direction.INCOMING),eq(f.scope),
          eq(GraphModel.Relationship.AFFECTS))).thenReturn(List.of(direct,derived));
      when(f.kg.getLinks(eq(slope),eq(GraphModel.Relationship.Direction.INCOMING),eq(f.scope),
          eq(GraphModel.Relationship.AFFECTS))).thenReturn(List.of(prerequisite));
      f.transaction.add(root);f.transaction.add(slope);
      f.transaction.registerExecutors();
      assertTrue(f.scheduler.executeDependency(root,root.getGeometry(),Scheduler.Event.initialization(),f.scope));
      assertEquals(1,f.qualityInit.get());
      assertEquals(0L,f.quality.getEventTimestamps().getFirst());
      assertTrue(f.scheduler.executeDependency(root,root.getGeometry(),Scheduler.Event.initialization(),f.scope));
      assertEquals(1,f.qualityInit.get());
    }
  }

  static ObservationImpl observation(String name, SemanticType type, long id) {
    var concept = new ConceptImpl();
    concept.setUrn("test:" + name); concept.setName(name); concept.setNamespace("test");
    concept.getType().add(type);
    var observation = new ObservationImpl();
    observation.setObservable(ObservableImpl.promote(concept, null));
    observation.setId(id); observation.setUrn("context." + name);
    observation.setGeometry(Geometry.create("T0(1){tstart=1388534400000,tend=1420070400000,ttype=PHYSICAL}"));
    return observation;
  }

  static void field(Object object, String name, Object value) throws Exception {
    var field = DigitalTwinImpl.class.getDeclaredField(name);
    field.setAccessible(true); field.set(object, value);
  }

  class Fixture implements AutoCloseable {
    final DigitalTwinImpl twin = mock(DigitalTwinImpl.class);
    final KnowledgeGraphNeo4j kg = mock(KnowledgeGraphNeo4j.class);
    final KnowledgeGraph.Transaction storage = mock(KnowledgeGraph.Transaction.class);
    final ServiceContextScope scope = mock(ServiceContextScope.class);
    final ObservationImpl process = observation("Erosion", SemanticType.PROCESS, -10);
    final ObservationImpl quality = observation("Elevation", SemanticType.QUALITY, -11);
    final ObservationImpl bearer = observation("Region", SemanticType.SUBJECT, 20);
    final ActuatorImpl plan = new ActuatorImpl();
    final DigitalTwinImpl.TransactionImpl transaction;
    final SchedulerImpl scheduler;
    final AtomicInteger qualityInit = new AtomicInteger();
    boolean failInput;
    String durableRegistration;
    List<String> durableJournal;

    Fixture() throws Exception {
      var user = mock(UserIdentity.class); when(user.getUsername()).thenReturn("tester");
      when(scope.getUser()).thenReturn(user); when(scope.getId()).thenReturn("context");
      when(scope.getContextObservation()).thenReturn(bearer);
      when(scope.getDigitalTwin()).thenReturn(twin);
      when(twin.getKnowledgeGraph()).thenReturn(kg);
      when(twin.getStorageManager()).thenReturn(mock(StorageManager.class));
      when(kg.nextKey()).thenReturn(900L);
      when(kg.createTransaction(scope)).thenReturn(storage);
      when(kg.getScheduledObservations(scope)).thenReturn(List.of());
      field(twin, "knowledgeGraph", kg); field(twin, "commitCache", CacheBuilder.newBuilder().build());
      scheduler = spy(new SchedulerImpl(scope, twin));
      // These fixtures isolate registration/INIT; storage integration covers automatic dispatch.
      doNothing().when(scheduler).advanceCommittedOccurrences();
      field(twin, "scheduler", scheduler); when(twin.getScheduler()).thenReturn(scheduler);
      transaction = twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION), scope,
          RuntimeAsset.PROVENANCE_ASSET, process);
      when(scope.getCurrentTransaction()).thenReturn(transaction);
      var ids = new AtomicLong(1000);
      doAnswer(call -> {
        var asset = call.getArgument(0);
        if (asset instanceof ObservationImpl o) o.setId(ids.incrementAndGet());
        else if (asset instanceof ActivityImpl a) {
          a.setId(ids.incrementAndGet());
          if (a.getMetadata().containsKey(SchedulerJournal.METADATA_KEY))
            durableJournal = new ArrayList<>((List<String>) a.getMetadata().get(SchedulerJournal.METADATA_KEY));
        }
        else if (asset instanceof ActuatorImpl a) a.setId(ids.incrementAndGet());
        return null;
      }).when(storage).store(any());
      doAnswer(call -> {
        if (call.getArgument(0) == process)
          durableRegistration = process.getMetadata().get(OccurrenceRegistration.METADATA_KEY, String.class);
        assertTrue(scheduler.getOccurrenceRegistrations().isEmpty(), "activation must follow graph commit");
        return null;
      }).when(storage).update(any());
      quality.setSubstantialQuality(true);
      plan.setObservation(process); plan.setActuatorType(Actuator.Type.RESOLVE); plan.setName("erosion");
      plan.setExecutionRole(Actuator.ExecutionRole.PROCESS);
      plan.getComputation().add(new ServiceCallImpl("test.erosion"));
      plan.getOccurrenceSchedules().put(0, new OccurrenceSchedule(1, "", "", 1,
          Time.Resolution.Type.MONTH, true, OccurrenceSchedule.Source.MODEL));
      var input = new ActuatorImpl(); input.setObservation(quality); input.setName("elevation");
      input.setActuatorType(Actuator.Type.RESOLVE);
      input.getComputation().add(new ServiceCallImpl("test.elevation"));
      plan.getChildren().add(input);
      transaction.add(quality); transaction.add(plan); transaction.add(input);
      transaction.resolveWith(quality, new DigitalTwin.Executor() {
        public List<ServiceCall> serialized() { return input.getComputation(); }
        public boolean run(Geometry geometry, Scheduler.Event event, ContextScope scope) {
          assertEquals(Scheduler.Event.Type.INITIALIZATION, event.getType());
          qualityInit.incrementAndGet(); return !failInput;
        }
      });
      transaction.resolveWith(process, new DigitalTwin.Executor() {
        public Actuator getActuator() { return plan; }
        public List<ServiceCall> serialized() { return plan.getComputation(); }
        public boolean run(Geometry geometry, Scheduler.Event event, ContextScope context) {
          assertEquals(1, qualityInit.get(), "quality INIT must precede occurrence registration");
          return true; // models the compiled INIT guard, tested separately
        }
      });
      when(kg.getLinks(process, GraphModel.Relationship.Direction.INCOMING, scope,
          GraphModel.Relationship.AFFECTS)).thenReturn(List.of(new LinkImpl(quality, process, GraphModel.Relationship.AFFECTS)));
    }
    public void close() { scheduler.close(); }
  }

  @Test void registrationCommitsAfterInputInitAndRestartsWithoutRunningAnything() throws Exception {
    try (var f = new Fixture()) {
      var declaration = f.plan.getOccurrenceSchedules().get(0);
      var negotiation = new OccurrenceNegotiation(1, "test:model", null,
          List.of(new OccurrenceNegotiation.Declaration("test:model", declaration)), declaration);
      f.plan.getData().put(OccurrenceNegotiation.DATA_KEY, Utils.Json.asString(negotiation));
      var unresolved = observation("Wet", SemanticType.QUALITY, -44);
      var semanticPlan = new ProcessPlan(2, f.bearer.getId(), "test:model",
          List.of(new ProcessPlan.Binding("elevation", f.quality.getObservable(), ProcessPlan.Effect.AFFECTED, true, false)),
          List.of(), List.of(new org.integratedmodelling.klab.api.knowledge.SemanticInfluence(
              unresolved.getObservable().getSemantics(), f.quality.getObservable().getSemantics(),
              org.integratedmodelling.klab.api.knowledge.SemanticInfluence.Kind.INCREASES_WITH, "test:Wet restriction")));
      f.plan.getData().put(ProcessPlan.DATA_KEY, Utils.Json.asString(semanticPlan));
      f.transaction.registerExecutors();
      assertTrue(f.scheduler.submit(f.process, f.scope));
      assertEquals(1, f.qualityInit.get());
      assertTrue(f.process.getEventTimestamps().isEmpty());
      assertTrue(f.scheduler.getOccurrenceRegistrations().isEmpty());
      assertEquals(900, f.transaction.commit());
      assertNotNull(f.durableRegistration);
      assertEquals(negotiation, Utils.Json.parseObject(f.process.getMetadata().get(OccurrenceNegotiation.DATA_KEY).toString(),
          OccurrenceNegotiation.class));
      var registration = f.scheduler.getOccurrenceRegistrations().get(f.process.getId());
      var semanticSnapshot = Utils.Json.parseObject(registration.plan(), Actuator.class);
      assertEquals(semanticPlan, Utils.Json.parseObject(semanticSnapshot.getData().get(ProcessPlan.DATA_KEY).toString(), ProcessPlan.class));
      assertEquals(Time.Resolution.Type.MONTH, registration.schedules().getFirst().bound().unit());
      var restoredPlan = Utils.Json.parseObject(registration.plan(), Actuator.class);
      assertEquals(negotiation, Utils.Json.parseObject(restoredPlan.getData().get(OccurrenceNegotiation.DATA_KEY).toString(),
          OccurrenceNegotiation.class));
      assertEquals(f.quality.getId(), restoredPlan.getChildren().getFirst().getObservation().getId());
      assertEquals("elevation", restoredPlan.getChildren().getFirst().getName());
      assertThrows(UnsupportedOperationException.class, () -> f.scheduler.switchToRealTime(-1));
      when(f.kg.getScheduledObservations(f.scope)).thenReturn(List.of(f.process));
      try (var restarted = new SchedulerImpl(f.scope, f.twin)) {
        assertEquals(registration, restarted.getOccurrenceRegistrations().get(f.process.getId()));
        assertEquals(1, f.qualityInit.get());
      }
    }
  }

  @Test void temporalDispatchUsesFreshTransactionsAndAtomicRetryableCursors() throws Exception {
    try (var f = new Fixture()) {
      assertTrue(f.scheduler.submit(f.process, f.scope));
      assertEquals(900, f.transaction.commit());
      doAnswer(call -> {
        if (call.getArgument(0) instanceof Activity activity && activity.getMetadata().containsKey(SchedulerJournal.METADATA_KEY))
          f.durableJournal = new ArrayList<>((List<String>)activity.getMetadata().get(SchedulerJournal.METADATA_KEY));
        return null;
      }).when(f.storage).update(any());
      var attempts = new ArrayList<DigitalTwin.Transaction>();
      var completed = new ArrayList<String>();
      var user = f.scope.getUser();
      when(f.scope.executingFresh(any(Activity.class), any(Observation.class))).thenAnswer(call -> {
        var scope = mock(ServiceContextScope.class);
        when(scope.getId()).thenReturn("context"); when(scope.getUser()).thenReturn(user);
        when(scope.getDigitalTwin()).thenReturn(f.twin);
        var transaction = f.twin.new TransactionImpl(call.getArgument(0), scope,
            RuntimeAsset.PROVENANCE_ASSET, call.getArgument(1, Observation.class));
        assertNull(transaction.getParent()); assertNotSame(f.transaction, transaction);
        attempts.add(transaction);
        when(scope.getCurrentTransaction()).thenReturn(transaction);
        when(f.kg.createTransaction(scope)).thenReturn(f.storage);
        when(scope.commit()).thenAnswer(ignored -> transaction.commit());
        doAnswer(failure -> { transaction.fail(failure.getArgument(0, Throwable.class)); return null; })
            .when(scope).fail(any(Throwable.class));
        return scope;
      });
      var scheduler = f.scheduler;
      doAnswer(call -> (org.integratedmodelling.klab.api.lang.TriFunction<Geometry, Scheduler.Event, ContextScope, Boolean>)
          (geometry, event, scope) -> {
            assertNotEquals(Scheduler.Event.Type.INITIALIZATION, event.getType());
            assertEquals(event.getTime().getEnd().getMilliseconds(),
                org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(geometry).getTime().getEnd().getMilliseconds());
            scope.getCurrentTransaction().afterCommit(() -> completed.add(event.toKey()));
            return true;
          }).when(scheduler).restoreExecutor(eq(f.process), any());
      long february = SimulatedDispatchTest.date("2014-02-01");
      doThrow(new IllegalStateException("commit fails")).when(f.storage).close();
      assertFalse(scheduler.advanceTo(february));
      assertFalse(f.process.getMetadata().containsKey(DispatchProgress.KEY));
      assertTrue(completed.isEmpty());
      doNothing().when(f.storage).close();
      assertTrue(scheduler.advanceTo(february));
      assertEquals(1, completed.size());
      assertTrue(scheduler.advanceTo(february));
      assertEquals(2, attempts.size());
      assertNotEquals(attempts.getFirst(), attempts.getLast());
      assertEquals(1, f.qualityInit.get());
      assertNotNull(f.durableJournal);
      var journal = Utils.Json.parseObject(f.durableJournal.getFirst(), SchedulerJournal.class);
      assertEquals(completed.getFirst(), journal.eventId()); assertEquals(900, journal.commitId());
      when(f.kg.getScheduledObservations(f.scope)).thenReturn(List.of(f.process));
      try (var restarted = new SchedulerImpl(f.scope, f.twin)) {
        assertTrue(restarted.advanceTo(february));
        assertEquals(2, attempts.size(), "Restart must use durable completion, not cache/timestamps");
      }
      var eventA = observation("eventA", SemanticType.EVENT, 700);
      var eventB = observation("eventB", SemanticType.EVENT, 701);
      assertTrue(scheduler.dispatchObserved(eventA));
      assertTrue(scheduler.dispatchObserved(eventB));
      assertTrue(scheduler.dispatchObserved(eventA));
      assertEquals(4, attempts.size(), "Two equal-time identities commit separately; redelivery is skipped");
      assertNotEquals(eventA.getMetadata().get(DispatchProgress.KEY), eventB.getMetadata().get(DispatchProgress.KEY));
    }
  }

  @Test void rootCommitActivatesAllRegistrationsBeforeRequestingOneAdvance() throws Exception {
    try (var f = new Fixture()) {
      var second = observation("SecondProcess", SemanticType.PROCESS, -30);
      var plan = new ActuatorImpl();
      plan.setObservation(second); plan.setName("second");
      plan.setActuatorType(Actuator.Type.RESOLVE);
      plan.setExecutionRole(Actuator.ExecutionRole.PROCESS);
      plan.getOccurrenceSchedules().putAll(f.plan.getOccurrenceSchedules());
      plan.getComputation().add(new ServiceCallImpl("test.second"));
      f.transaction.add(second);
      f.transaction.resolveWith(second, new DigitalTwin.Executor() {
        public Actuator getActuator() { return plan; }
        public List<ServiceCall> serialized() { return plan.getComputation(); }
        public boolean run(Geometry geometry, Scheduler.Event event, ContextScope scope) { return true; }
      });
      doAnswer(call -> {
        assertEquals(2, f.scheduler.getOccurrenceRegistrations().size());
        return null;
      }).when(f.scheduler).advanceCommittedOccurrences();
      assertTrue(f.scheduler.submit(f.process, f.scope));
      assertTrue(f.scheduler.submit(second, f.scope));
      verify(f.scheduler, never()).advanceCommittedOccurrences();
      assertTrue(f.transaction.commit() > 0);
      verify(f.scheduler).advanceCommittedOccurrences();
    }
  }

  @Test void failedInputAndRolledBackRegistrationNeverActivate() throws Exception {
    try (var f = new Fixture()) {
      f.failInput = true;
      assertFalse(f.scheduler.submit(f.process, f.scope));
      f.transaction.fail(new IllegalStateException("input failed"));
      assertEquals(-1, f.transaction.commit());
      assertTrue(f.scheduler.getOccurrenceRegistrations().isEmpty());
      verify(f.storage, never()).store(any());
      verify(f.scheduler, never()).advanceCommittedOccurrences();
    }
    try (var f = new Fixture()) {
      assertTrue(f.scheduler.submit(f.process, f.scope));
      f.transaction.fail(new IllegalStateException("later sibling failed"));
      assertFalse(f.process.getMetadata().containsKey(Scheduler.REGISTRATION_METADATA_KEY));
      assertTrue(f.quality.getEventTimestamps().isEmpty());
      assertTrue(f.scheduler.getOccurrenceRegistrations().isEmpty());
      verify(f.scheduler, never()).advanceCommittedOccurrences();
    }
  }

  @Test void graphCommitFailureRollsBackPreparedMetadataAndChildCommitDoesNotActivate() throws Exception {
    try (var f = new Fixture()) {
      assertTrue(f.scheduler.submit(f.process, f.scope));
      var child = f.transaction.getChild(Activity.of(Activity.Type.RESOLUTION), f.scope);
      assertEquals(0, child.commit());
      assertTrue(f.scheduler.getOccurrenceRegistrations().isEmpty());
      doThrow(new IllegalStateException("graph commit failed")).when(f.storage).close();
      assertEquals(-1, f.transaction.commit());
      assertFalse(f.process.getMetadata().containsKey(OccurrenceRegistration.METADATA_KEY));
      assertTrue(f.scheduler.getOccurrenceRegistrations().isEmpty());
    }
  }

  @Test void journalEnvelopeIsCommittedWithItsRootTransaction() throws Exception {
    var envelope = new SchedulerJournal(1, "event-1", "parent-1", Scheduler.Event.Type.INITIALIZATION,
        100, 200, "registration-1", "revision-1", "support", 0, List.of(20L), true);
    try (var f = new Fixture()) {
      var child = f.transaction.getChild(Activity.of(Activity.Type.RESOLUTION), f.scope);
      child.stageSchedulerJournal(envelope);
      assertEquals(0, child.commit());
      assertNull(f.durableJournal);
      assertEquals(900, f.transaction.commit());
      assertEquals(1, f.durableJournal.size());
      var restored = Utils.Json.parseObject(f.durableJournal.getFirst(), SchedulerJournal.class);
      assertEquals(envelope.committed(900), restored);
      assertTrue(restored.publicationPending());
      assertThrows(IllegalArgumentException.class, () -> f.transaction.stageSchedulerJournal(restored));
    }
    try (var f = new Fixture()) {
      f.transaction.stageSchedulerJournal(envelope);
      f.transaction.fail(new IllegalStateException("abort"));
      assertEquals(-1, f.transaction.commit());
      verify(f.storage, never()).store(any());
      assertNull(f.durableJournal);
    }
  }

  @Test void causalFeedbackIsNotAnInitPrerequisiteAndWritersCannotCompete() throws Exception {
    try (var f = new Fixture()) {
      var influence = new LinkImpl(f.process, f.quality, GraphModel.Relationship.AFFECTS);
      influence.properties().put(ProcessPlan.EDGE_ROLE, ProcessPlan.INFLUENCE);
      when(f.kg.getLinks(f.quality, GraphModel.Relationship.Direction.INCOMING, f.scope,
          GraphModel.Relationship.AFFECTS)).thenReturn(List.of(influence));
      assertTrue(f.scheduler.submit(f.process, f.scope));
      assertEquals(1, f.qualityInit.get());
      f.transaction.linkProcessInfluence(f.process, f.quality, "test:model", "elevation");
      var competitor = observation("OtherProcess", SemanticType.PROCESS, -30);
      assertThrows(IllegalStateException.class,
          () -> f.transaction.linkProcessInfluence(competitor, f.quality, "test:other", "elevation"));
      var concurrent = f.twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION), f.scope,
          RuntimeAsset.PROVENANCE_ASSET, competitor);
      assertThrows(IllegalStateException.class,
          () -> concurrent.linkProcessInfluence(competitor, f.quality, "test:other", "elevation"));
      f.transaction.fail(new IllegalStateException("rollback writer claim"));
      assertDoesNotThrow(() -> concurrent.linkProcessInfluence(competitor, f.quality, "test:other", "elevation"));
      concurrent.fail(new IllegalStateException("test cleanup"));
    }
  }

  @Test void descriptiveCyclesNeverBecomeInitPrerequisites() throws Exception {
    try (var f = new Fixture()) {
      var link = new LinkImpl(f.quality, f.quality, GraphModel.Relationship.AFFECTS);
      link.properties().put(ProcessPlan.EDGE_ROLE, ProcessPlan.DESCRIPTIVE);
      when(f.kg.getLinks(f.quality, GraphModel.Relationship.Direction.INCOMING, f.scope,
          GraphModel.Relationship.AFFECTS)).thenReturn(List.of(link));
      assertTrue(f.scheduler.submit(f.process, f.scope));
      assertEquals(1, f.qualityInit.get());
    }
  }
}
