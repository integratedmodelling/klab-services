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
      scheduler = new SchedulerImpl(scope, twin);
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
      f.transaction.registerExecutors();
      assertTrue(f.scheduler.submit(f.process, f.scope));
      assertEquals(1, f.qualityInit.get());
      assertTrue(f.process.getEventTimestamps().isEmpty());
      assertTrue(f.scheduler.getOccurrenceRegistrations().isEmpty());
      assertEquals(900, f.transaction.commit());
      assertNotNull(f.durableRegistration);
      var registration = f.scheduler.getOccurrenceRegistrations().get(f.process.getId());
      assertEquals(Time.Resolution.Type.MONTH, registration.schedules().getFirst().bound().unit());
      var restoredPlan = Utils.Json.parseObject(registration.plan(), Actuator.class);
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

  @Test void failedInputAndRolledBackRegistrationNeverActivate() throws Exception {
    try (var f = new Fixture()) {
      f.failInput = true;
      assertFalse(f.scheduler.submit(f.process, f.scope));
      f.transaction.fail(new IllegalStateException("input failed"));
      assertEquals(-1, f.transaction.commit());
      assertTrue(f.scheduler.getOccurrenceRegistrations().isEmpty());
      verify(f.storage, never()).store(any());
    }
    try (var f = new Fixture()) {
      assertTrue(f.scheduler.submit(f.process, f.scope));
      f.transaction.fail(new IllegalStateException("later sibling failed"));
      assertFalse(f.process.getMetadata().containsKey(Scheduler.REGISTRATION_METADATA_KEY));
      assertTrue(f.quality.getEventTimestamps().isEmpty());
      assertTrue(f.scheduler.getOccurrenceRegistrations().isEmpty());
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
}
