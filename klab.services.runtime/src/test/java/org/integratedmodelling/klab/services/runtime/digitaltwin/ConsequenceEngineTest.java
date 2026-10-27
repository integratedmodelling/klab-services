package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.common.cache.CacheBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.impl.CommitImpl;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.scopes.ServiceSessionScope;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConsequenceEngineTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  static CommitImpl commit(long id) {
    var commit = new CommitImpl();
    commit.setId(id);
    commit.getAddedAssets().add(42L);
    return commit;
  }

  static void field(Object object, Class<?> type, String name, Object value) throws Exception {
    var field = type.getDeclaredField(name);
    field.setAccessible(true);
    field.set(object, value);
  }

  static class Fixture implements AutoCloseable {
    final ServiceContextScope scope;
    final LinkedBlockingQueue<Message> events = new LinkedBlockingQueue<>();
    final ConsequenceEngine engine;
    final DigitalTwinImpl twin = mock(DigitalTwinImpl.class);
    final KnowledgeGraphNeo4j graph = mock(KnowledgeGraphNeo4j.class);
    final KnowledgeGraph.Transaction storage = mock(KnowledgeGraph.Transaction.class);
    final org.integratedmodelling.klab.api.provenance.Agent klab =
        org.integratedmodelling.klab.api.provenance.Agent.create(org.integratedmodelling.klab.api.provenance.Agent.KLAB_AGENT_NAME);
    final java.util.Map<Long, Activity> activities = new java.util.HashMap<>();

    Fixture() throws Exception { this(5000, 5000); }

    Fixture(long gracefulCloseMillis, long forcedCloseMillis) throws Exception {
      var user = mock(UserIdentity.class);
      when(user.getUsername()).thenReturn("tester");
      when(user.getId()).thenReturn("tester");
      scope = new ServiceContextScope(
          new ServiceSessionScope(new ServiceUserScope(user, mock(RuntimeService.class))),
          DigitalTwin.Configuration.builder().id("context").name("Twin").build(), user);
      scope.setId("context");
      scope.onMessage((channel, event) -> events.add(event), Message.Queue.Events);
      engine = new ConsequenceEngine(scope, "context", gracefulCloseMillis, forcedCloseMillis);
      when(twin.getConsequenceEngine()).thenReturn(engine);
      field(twin, DigitalTwinImpl.class, "knowledgeGraph", graph);
      field(twin, DigitalTwinImpl.class, "commitCache", CacheBuilder.newBuilder().build());
      when(graph.nextKey()).thenReturn(900L, 901L);
      when(graph.createTransaction(any())).thenReturn(storage);
      org.integratedmodelling.klab.api.provenance.Agent.promote(klab).setId(500);
      when(graph.klab()).thenReturn(klab);
      var original = Activity.of(Activity.Type.SUBMISSION);
      original.setId(100); original.setUrn("context.100");
      activities.put(100L, original);
      when(graph.getAsset(anyLong(), any(), eq(Activity.class)))
          .thenAnswer(call -> activities.get(call.getArgument(0, Long.class)));
      var ids = new AtomicLong(1000);
      doAnswer(call -> {
        if (call.getArgument(0) instanceof ActivityImpl activity) {
          activity.setId(ids.incrementAndGet());
          activity.setUrn("context." + activity.getId());
          activities.put(activity.getId(), activity);
        }
        return null;
      }).when(storage).store(any());
    }

    DigitalTwinImpl.TransactionImpl transaction(ServiceContextScope source) {
      return twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION), source,
          RuntimeAsset.PROVENANCE_ASSET);
    }

    List<Message> messages(int count) throws Exception {
      var result = new ArrayList<Message>();
      for (int i = 0; i < count; i++) {
        var event = events.poll(5, TimeUnit.SECONDS);
        assertNotNull(event, "Missing consequence notification " + i);
        result.add(event);
      }
      return result;
    }

    @Override public void close() { engine.close(); }
  }

  @Test void successfulRootCommitRunsAfterStorageCloseAndIntermediateCommitDoesNotPublish() throws Exception {
    try (var f = new Fixture()) {
      var root = f.transaction(f.scope);
      var child = root.getChild(Activity.of(Activity.Type.RESOLUTION), f.scope);
      var published = new CountDownLatch(1);
      var durable = new CountDownLatch(1);
      doAnswer(call -> { durable.countDown(); return null; }).when(f.storage).close();
      root.afterCommit(published::countDown);
      var boundary = new AtomicReference<Boolean>();
      f.engine.register(Consequence.Kind.CONFIGURATION_DETECTION, (cause, changes, scope) -> {
        boundary.set(durable.getCount() == 0 && published.getCount() == 0);
        return new ConsequenceEngine.Result(Consequence.Outcome.NO_ACTION, "Checked", List.of());
      });
      assertEquals(0, child.commit());
      assertTrue(f.events.isEmpty());
      assertEquals(900, root.commit());
      var messages = f.messages(5);
      assertEquals(Boolean.TRUE, boundary.get());
      assertTrue(messages.stream().allMatch(m -> m.getMessageType() == Message.MessageType.ConsequenceDecision));
      var cause = messages.getFirst().getPayload(Consequence.Decision.class).cause();
      assertEquals(900, cause.triggeringCommitId());
      assertEquals(root.getActivity().getId(), cause.triggeringActivityId());
      assertThrows(IllegalStateException.class, root::commit);
    }
  }

  @Test void rollbackAndDurableCloseFailureNeverScheduleConsequences() throws Exception {
    try (var f = new Fixture()) {
      var failed = f.transaction(f.scope);
      failed.fail(new IllegalStateException("cancelled"));
      assertEquals(-1, failed.commit());
      verify(f.storage, never()).close();
      doThrow(new IllegalStateException("commit failed")).when(f.storage).close();
      assertEquals(-1, f.transaction(f.scope).commit());
      assertTrue(f.events.isEmpty());
    }
  }

  @Test void workerIsLowPriorityAndNonblockingAndUsesDetachedChanges() throws Exception {
    try (var f = new Fixture()) {
      var entered = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      var thread = new AtomicReference<Thread>();
      var assets = new AtomicReference<Set<Long>>();
      f.engine.register(Consequence.Kind.CONFIGURATION_DETECTION, (cause, changes, scope) -> {
        thread.set(Thread.currentThread());
        entered.countDown();
        release.await();
        assets.set(Set.copyOf(changes.getAddedAssets()));
        changes.getAddedAssets().clear();
        return new ConsequenceEngine.Result(Consequence.Outcome.NO_ACTION, "Checked", List.of());
      });
      var second = new AtomicReference<Set<Long>>();
      f.engine.register(Consequence.Kind.CHANGE_PROCESS, (cause, changes, scope) -> {
        second.set(Set.copyOf(changes.getAddedAssets()));
        return new ConsequenceEngine.Result(Consequence.Outcome.NO_ACTION, "Checked", List.of());
      });
      var input = commit(1);
      f.engine.committed(input, f.scope, 100, 0);
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      input.getAddedAssets().add(99L);
      // Both an in-flight duplicate and a later repeat are ignored.
      f.engine.committed(input, f.scope, 100, 0);
      release.countDown();
      f.messages(5);
      f.engine.committed(input, f.scope, 100, 0);
      assertNotSame(Thread.currentThread(), thread.get());
      assertEquals(Thread.MIN_PRIORITY, thread.get().getPriority());
      assertTrue(thread.get().isDaemon());
      assertEquals(Set.of(42L), assets.get());
      assertEquals(Set.of(42L), second.get());
    }
  }

  @Test void newConsequenceCommitHasCausalMetadataAndQueuesAnotherPass() throws Exception {
    try (var f = new Fixture()) {
      var generated = new AtomicReference<Activity>();
      f.engine.register(Consequence.Kind.CONFIGURATION_DETECTION, (cause, changes, scope) -> {
        if (changes.getId() == 1) {
          var transaction = f.transaction(scope);
          generated.set(transaction.getActivity());
          transaction.commit();
          return new ConsequenceEngine.Result(Consequence.Outcome.APPLIED, "Committed consequence", List.of());
        }
        return new ConsequenceEngine.Result(Consequence.Outcome.NO_ACTION, "Already handled", List.of());
      });
      f.engine.committed(commit(1), f.scope, 100, 0);
      var messages = f.messages(11);
      var decision = messages.getFirst().getPayload(Consequence.Decision.class);
      var notification = messages.get(4).getPayload(Consequence.Commit.class);
      assertEquals(Message.MessageType.ConsequenceCommitted, messages.get(4).getMessageType());
      assertEquals(900, notification.commit().getId());
      assertEquals(decision.cause(), notification.cause());
      assertEquals(decision.cause(), notification.commit().getConsequenceCause());
      assertEquals(Activity.Type.CONSEQUENCE_SUBMISSION, generated.get().getType());
      assertEquals("context.100", generated.get().getTriggeringActivityUrn());
      assertTrue(notification.commit().getAddedLinks().contains(org.integratedmodelling.klab.api.collections.Triple.of(
          100L, generated.get().getId(), "TRIGGERED")));
      assertTrue(notification.commit().getAddedLinks().contains(org.integratedmodelling.klab.api.collections.Triple.of(
          generated.get().getId(), 500L, "BY_AGENT")));
      assertEquals(decision.cause(), Utils.Json.parseObject(
          generated.get().getMetadata().get(Consequence.METADATA_KEY).toString(), Consequence.Cause.class));
      assertEquals(decision.cause().decisionId(),
          messages.get(5).getPayload(Consequence.Decision.class).cause().parentDecisionId());
      assertTrue(f.events.isEmpty());
    }
  }

  @Test void recursiveSubmissionsRetainOriginalUserCausalPathAndKlabOwnsEveryPhase() throws Exception {
    try (var f = new Fixture()) {
      var roots = new ArrayList<Activity>();
      var phases = new ArrayList<Activity>();
      var user = org.integratedmodelling.klab.api.provenance.Agent.create("tester");
      org.integratedmodelling.klab.api.provenance.Agent.promote(user).setId(501);
      f.engine.register(Consequence.Kind.CONFIGURATION_DETECTION, (cause, changes, scope) -> {
        if (changes.getId() == 1 || changes.getId() == 900) {
          var root = f.twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION), scope,
              RuntimeAsset.PROVENANCE_ASSET, user);
          roots.add(root.getActivity());
          var phase = Activity.of(Activity.Type.RESOLUTION);
          var child = root.getChild(phase, scope, user);
          phases.add(phase);
          child.commit(); root.commit();
        }
        return new ConsequenceEngine.Result(Consequence.Outcome.NO_ACTION, "Checked", List.of());
      });
      f.engine.committed(commit(1), f.scope, 100, 0);
      var committed = f.messages(17).stream()
          .filter(message -> message.getMessageType() == Message.MessageType.ConsequenceCommitted)
          .map(message -> message.getPayload(Consequence.Commit.class)).toList();
      assertEquals(2, committed.size());
      for (int i = 0; i < 2; i++) {
        var parent = i == 0 ? f.activities.get(100L) : roots.get(i - 1);
        var root = roots.get(i); var phase = phases.get(i);
        assertEquals(Activity.Type.CONSEQUENCE_SUBMISSION, root.getType());
        assertEquals(Activity.Type.RESOLUTION, phase.getType());
        assertEquals(parent.getUrn(), root.getTriggeringActivityUrn());
        assertEquals(parent.getTransientId(), root.getParentTransientId());
        var links = committed.get(i).commit().getAddedLinks();
        assertTrue(links.contains(org.integratedmodelling.klab.api.collections.Triple.of(parent.getId(), root.getId(), "TRIGGERED")));
        assertTrue(links.contains(org.integratedmodelling.klab.api.collections.Triple.of(root.getId(), phase.getId(), "TRIGGERED")));
        assertTrue(links.contains(org.integratedmodelling.klab.api.collections.Triple.of(root.getId(), 500L, "BY_AGENT")));
        assertTrue(links.contains(org.integratedmodelling.klab.api.collections.Triple.of(phase.getId(), 500L, "BY_AGENT")));
        assertFalse(links.stream().anyMatch(link -> "BY_AGENT".equals(link.getThird()) && link.getSecond() == 501));
        assertEquals(root.getMetadata().get(Consequence.METADATA_KEY), phase.getMetadata().get(Consequence.METADATA_KEY));
      }
      assertEquals(Activity.Type.SUBMISSION, f.activities.get(100L).getType());
      verify(f.storage, never()).store(f.activities.get(100L));
    }
  }

  @Test void handlerFailureDoesNotStopOtherDecisionsOrLaterCommits() throws Exception {
    try (var f = new Fixture()) {
      f.engine.register(Consequence.Kind.CONFIGURATION_DETECTION,
          (cause, changes, scope) -> { throw new IllegalStateException("reasoner unavailable"); });
      f.engine.committed(commit(1), f.scope, 100, 0);
      f.engine.committed(commit(2), f.scope, 101, 0);
      var messages = f.messages(10);
      assertEquals(2, messages.stream().map(m -> m.getPayload(Consequence.Decision.class))
          .filter(d -> d.cause().kind() == Consequence.Kind.CONFIGURATION_DETECTION
              && d.outcome() == Consequence.Outcome.FAILED).count());
      assertEquals(2, messages.stream().map(m -> m.getPayload(Consequence.Decision.class))
          .filter(d -> d.cause().kind() == Consequence.Kind.ROLE_CONFERRAL
              && d.outcome() == Consequence.Outcome.DEFERRED).count());
    }
  }

  @Test void missingReasonerSupportIsDeferredAndMakesNoSubmissions() throws Exception {
    try (var f = new Fixture()) {
      f.engine.committed(commit(1), f.scope, 100, 0);
      var decisions = f.messages(5).stream().map(m -> m.getPayload(Consequence.Decision.class)).toList();
      assertTrue(decisions.stream().filter(d -> d.cause().kind() != Consequence.Kind.CHANGE_PROCESS)
          .allMatch(d -> d.outcome() == Consequence.Outcome.DEFERRED));
      assertTrue(decisions.getFirst().explanation().contains("emerges from"));
      assertTrue(decisions.getFirst().explanation().contains("applies to"));
      verifyNoInteractions(f.storage);
    }
  }

  @Test void shutdownInterruptsActiveHandlerAndRejectsFurtherWork() throws Exception {
    try (var f = new Fixture(20, 1000)) {
      var entered = new CountDownLatch(1);
      var stopped = new CountDownLatch(1);
      f.engine.register(Consequence.Kind.CONFIGURATION_DETECTION, (cause, changes, scope) -> {
        entered.countDown();
        try { new CountDownLatch(1).await(); }
        finally { stopped.countDown(); }
        return null;
      });
      f.engine.committed(commit(1), f.scope, 100, 0);
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      f.engine.committed(commit(2), f.scope, 101, 0);
      f.engine.close();
      assertEquals(0, stopped.getCount());
      f.engine.committed(commit(3), f.scope, 102, 0);
      assertTrue(f.events.isEmpty());
      assertThrows(IllegalStateException.class, () -> f.engine.register(Consequence.Kind.CHANGE_PROCESS, null));
    }
  }

  @Test void closeDrainsActiveOperationWithoutInterruptingAndSuppressesQueuedWorkAndDecisions() throws Exception {
    try (var f = new Fixture(1000, 1000)) {
      var entered = new CountDownLatch(1);
      var cancellationSeen = new CountDownLatch(1);
      var releaseOperation = new CountDownLatch(1);
      var interrupted = new java.util.concurrent.atomic.AtomicBoolean();
      var calls = new java.util.concurrent.atomic.AtomicInteger();
      var closeFailure = new AtomicReference<Throwable>();
      f.engine.register(Consequence.Kind.CONFIGURATION_DETECTION, (cause, changes, scope) -> {
        calls.incrementAndGet();
        entered.countDown();
        try {
          while (!scope.isInterrupted()) Thread.sleep(1);
          cancellationSeen.countDown();
          releaseOperation.await(); // an already-running read/atomic operation may finish safely
        } catch (InterruptedException failure) { interrupted.set(true); throw failure; }
        return new ConsequenceEngine.Result(Consequence.Outcome.NO_ACTION, "Drained", List.of());
      });
      f.engine.register(Consequence.Kind.CHANGE_PROCESS, (cause, changes, scope) -> {
        calls.incrementAndGet();
        return new ConsequenceEngine.Result(Consequence.Outcome.NO_ACTION, "Must not run", List.of());
      });
      f.engine.committed(commit(1), f.scope, 100, 0);
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      f.engine.committed(commit(2), f.scope, 101, 0);
      var closer = new Thread(() -> { try { f.engine.close(); } catch (Throwable t) { closeFailure.set(t); } });
      closer.start();
      try {
        assertTrue(cancellationSeen.await(5, TimeUnit.SECONDS));
        assertTrue(closer.isAlive(), "Close must wait for the active operation");
      } finally { releaseOperation.countDown(); }
      closer.join(5000);
      assertFalse(closer.isAlive());
      assertNull(closeFailure.get());
      assertFalse(interrupted.get(), "Healthy active operations must drain without driver interruption");
      assertEquals(1, calls.get(), "Queued work and subsequent handlers must not start");
      assertTrue(f.events.isEmpty(), "Closing work must not publish a decision");
    }
  }

  @Test void closeRefusesToFinishWhenHandlerIgnoresCancellationAndInterruption() throws Exception {
    var f = new Fixture(20, 30);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var interruptionSeen = new CountDownLatch(1);
    f.engine.register(Consequence.Kind.CONFIGURATION_DETECTION, (cause, changes, scope) -> {
      entered.countDown();
      boolean done = false;
      while (!done) {
        try { release.await(); done = true; }
        catch (InterruptedException ignored) { interruptionSeen.countDown(); }
      }
      return new ConsequenceEngine.Result(Consequence.Outcome.NO_ACTION, "Released", List.of());
    });
    try {
      f.engine.committed(commit(1), f.scope, 100, 0);
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      var failure = assertThrows(IllegalStateException.class, f.engine::close);
      assertTrue(failure.getMessage().contains("Consequence handler did not stop"));
      assertEquals(0, interruptionSeen.getCount(), "Bounded fallback must interrupt uncooperative work");
      assertTrue(f.events.isEmpty());
      verifyNoInteractions(f.storage);
    } finally { release.countDown(); f.close(); }
  }
  @Test void interruptedSubmissionRetainsWorkerUntilNaturalCompletionInsteadOfCancellingWrapper() throws Exception {
    var f = new Fixture(20, 100);
    var entered = new CountDownLatch(1);
    var drained = new CountDownLatch(1);
    var submission = spy(new java.util.concurrent.CompletableFuture<
        org.integratedmodelling.klab.api.knowledge.observation.Observation>());
    var interrupted = new java.util.concurrent.atomic.AtomicBoolean();
    f.engine.register(Consequence.Kind.CONFIGURATION_DETECTION, (cause, changes, scope) -> {
      entered.countDown();
      ChangeProcessHandler.awaitSubmissionDrain(submission);
      interrupted.set(Thread.currentThread().isInterrupted());
      drained.countDown();
      return new ConsequenceEngine.Result(Consequence.Outcome.NO_ACTION, "Drained", List.of());
    });
    try {
      f.engine.committed(commit(1), f.scope, 100, 0);
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      assertThrows(IllegalStateException.class, f.engine::close,
          "A pending native submission must prevent disposal even after worker interruption");
      assertEquals(1, drained.getCount());
      assertFalse(submission.isDone());
      verify(submission, never()).cancel(anyBoolean());
      verifyNoInteractions(f.storage);
      assertTrue(f.events.isEmpty());
    } finally {
      submission.complete(org.integratedmodelling.klab.api.knowledge.observation.Observation.empty());
      assertTrue(drained.await(5, TimeUnit.SECONDS));
      f.close();
    }
    assertTrue(interrupted.get(), "Drain restores the worker interruption after work settles");
  }
  @Test void derivedConsequenceScopeDropsTransactionAndCancellationButKeepsCauseOnCopies() throws Exception {
    try (var f = new Fixture()) {
      var original = f.scope.withCancellation(() -> true);
      field(original, ServiceContextScope.class, "currentTransaction", mock(DigitalTwin.Transaction.class));
      var cause = new Consequence.Cause(1, "context", 1, 100, 0, null, null, "decision",
          Consequence.Kind.CONFIGURATION_DETECTION);
      var derived = original.forConsequence(cause);
      assertNull(derived.getCurrentTransaction());
      assertFalse(derived.isInterrupted());
      assertTrue(original.isInterrupted());
      assertNotNull(original.getCurrentTransaction());
      assertSame(original.getUser(), derived.getUser());
      assertSame(cause, new ServiceContextScope(derived).getConsequenceCause());
      assertNull(f.scope.getConsequenceCause());
      var foreign = new Consequence.Cause(1, "foreign", 1, 100, 0, null, null, "decision", cause.kind());
      assertThrows(IllegalArgumentException.class, () -> original.forConsequence(foreign));
      var observable = mock(org.integratedmodelling.klab.api.knowledge.Observable.class);
      var builder = mock(org.integratedmodelling.klab.api.knowledge.Observable.Builder.class, RETURNS_SELF);
      when(observable.builder(derived)).thenReturn(builder);
      when(builder.buildObservable()).thenReturn(observable);
      derived.observation(observable);
      verify(builder).optional(true);
    }
  }

  @Test void uncertaintyYieldsToAllQueuedOrdinaryConsequenceWork() throws Exception {
    try (var f = new Fixture()) {
      var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
      f.engine.register(Consequence.Kind.CONFIGURATION_DETECTION, (cause, changes, scope) -> {
        if (changes.getId() == 1) { entered.countDown(); release.await(); }
        return new ConsequenceEngine.Result(Consequence.Outcome.NO_ACTION, "Checked", List.of());
      });
      f.engine.committed(commit(1), f.scope, 100, 0);
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      f.engine.committed(commit(2), f.scope, 101, 0);
      release.countDown();
      var decisions = f.messages(10).stream().map(m -> m.getPayload(Consequence.Decision.class)).toList();
      assertTrue(decisions.subList(0, 8).stream().noneMatch(d -> d.cause().kind() == Consequence.Kind.UNCERTAINTY));
      assertTrue(decisions.subList(8, 10).stream().allMatch(d -> d.cause().kind() == Consequence.Kind.UNCERTAINTY));
    }
  }

  @Test void optionalFailureProducesOneDecisionAndDoesNotRaiseScopeErrorsOrWarnings() throws Exception {
    try (var f = new Fixture()) {
      var errors = new java.util.concurrent.atomic.AtomicInteger();
      f.scope.onMessage((channel, event) -> errors.incrementAndGet(), Message.Queue.Errors, Message.Queue.Warnings);
      f.engine.register(Consequence.Kind.CONFIGURATION_DETECTION, (cause, changes, scope) -> {
        scope.error("first failure");
        var child = new ServiceContextScope(scope);
        child.error("second failure");
        child.send(org.integratedmodelling.klab.api.services.runtime.Notification.error("third failure"));
        child.warn("optional resolution failed");
        return new ConsequenceEngine.Result(Consequence.Outcome.FAILED, "Optional attempt failed", List.of());
      });
      f.engine.committed(commit(1), f.scope, 100, 0);
      var decisions = f.messages(5);
      assertEquals(Consequence.Outcome.FAILED, decisions.getFirst().getPayload(Consequence.Decision.class).outcome());
      assertTrue(decisions.getFirst().getPayload(Consequence.Decision.class).explanation().contains("first failure"));
      assertEquals(0, errors.get());
      assertFalse(f.scope.hasErrors());
      assertTrue(f.events.isEmpty());
    }
  }

  @Test void remoteScopeRestoresCauseAndTransactionWithoutLeakingIntoOrdinaryRequests() throws Exception {
    try (var f = new Fixture()) {
      var cause = new Consequence.Cause(1, "context", 1, 100, 0, null, null, "decision",
          Consequence.Kind.CHANGE_PROCESS);
      var encoded = java.util.Base64.getEncoder().encodeToString(Utils.Json.asString(cause)
          .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      var manager = new org.integratedmodelling.klab.services.scopes.ScopeManager(
          mock(org.integratedmodelling.klab.api.services.Reasoner.class));
      try {
        var data = org.integratedmodelling.klab.api.scope.ContextScope.parseScopeId("context");
        var remote = (ServiceContextScope) manager.contextualizeScope(f.scope, data, java.util.Map.of(
            org.integratedmodelling.klab.api.ServicesAPI.CONSEQUENCE_HEADER, encoded,
            org.integratedmodelling.klab.api.ServicesAPI.TRANSACTION_ID_HEADER, "transaction"));
        assertEquals(cause, remote.getConsequenceCause());
        assertEquals("transaction", remote.getTransactionId());
        var observable = mock(org.integratedmodelling.klab.api.knowledge.Observable.class);
        remote.observation(observable);
        verify(observable, never()).builder(any()); // remote mandatory dependency is still mandatory
        remote.error("remote optional attempt failed");
        assertTrue(remote.hasErrors());
        assertFalse(f.scope.hasErrors());
        assertNull(f.scope.getConsequenceCause());
        assertNull(f.scope.getTransactionId());
        var ordinary = manager.contextualizeScope(f.scope, data, java.util.Map.of());
        assertNull(ordinary.getConsequenceCause());
        var foreign = new Consequence.Cause(1, "foreign", 1, 100, 0, null, null, "decision", cause.kind());
        var foreignHeader = java.util.Base64.getEncoder().encodeToString(Utils.Json.asString(foreign)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> manager.contextualizeScope(f.scope, data,
            java.util.Map.of(org.integratedmodelling.klab.api.ServicesAPI.CONSEQUENCE_HEADER, foreignHeader)));
      } finally { manager.shutdown(); }
    }
  }
}


