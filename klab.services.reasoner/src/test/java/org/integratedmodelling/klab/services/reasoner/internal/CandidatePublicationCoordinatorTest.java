package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.integratedmodelling.klab.services.reasoner.internal.CandidatePublicationCoordinator.BeanStatus.*;
import static org.integratedmodelling.klab.services.reasoner.internal.CandidatePublicationCoordinator.LogicalStatus.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

class CandidatePublicationCoordinatorTest {
  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static CandidatePublicationCoordinator.Input<String> input(String id) {
    return input(id, CandidatePublicationCoordinator.BeanStatus.VALID);
  }
  private static CandidatePublicationCoordinator.Input<String> input(String id,
      CandidatePublicationCoordinator.BeanStatus status) {
    return new CandidatePublicationCoordinator.Input<>(Map.of("worldview", id),
        List.of("worldview"), "imports-1", "configuration-1", id, status,
        status == CandidatePublicationCoordinator.BeanStatus.INVALID ? List.of("bean error") : List.of());
  }
  private static final class State implements AutoCloseable {
    final String id; final AtomicInteger closed = new AtomicInteger();
    boolean cleanupFailure;
    Runnable cleanupHook;
    State(String id) { this.id = id; }
    @Override public void close() {
      closed.incrementAndGet();
      if (cleanupHook != null) cleanupHook.run();
      if (cleanupFailure) throw new IllegalStateException("cleanup probe");
    }
  }
  private static CandidatePublicationCoordinator.Result<State> valid(State state) {
    return new CandidatePublicationCoordinator.Result<>(state,
        CandidatePublicationCoordinator.LogicalStatus.VALID, true, List.of());
  }
  private static void idle(CandidatePublicationCoordinator<?, ?> coordinator) throws Exception {
    assertTrue(coordinator.awaitIdle(TIMEOUT), "candidate worker did not finish");
  }
  private static void latch(CountDownLatch latch) throws Exception {
    assertTrue(latch.await(5, TimeUnit.SECONDS), "test synchronization did not complete");
  }

  @Test void beanErrorsScheduleNoBuilderAndLeaveAcceptedStateIntact() throws Exception {
    var builds = new AtomicInteger(); var first = new State("first");
    try (var coordinator = new CandidatePublicationCoordinator<String, State>((i, c) -> {
      builds.incrementAndGet(); return valid(first);
    }, i -> true)) {
      coordinator.submit(input("first")); idle(coordinator);
      coordinator.submit(input("bad", CandidatePublicationCoordinator.BeanStatus.INVALID));
      assertEquals(1, builds.get());
      assertEquals(CandidatePublicationCoordinator.BeanStatus.INVALID, coordinator.status().beanStatus());
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.NOT_RUN, coordinator.status().logicalStatus());
      assertEquals(Map.of("worldview", "first"), coordinator.status().acceptedSources());
      try (var lease = coordinator.accepted().orElseThrow()) { assertSame(first, lease.state()); }
      assertEquals(0, first.closed.get());
    }
    assertEquals(1, first.closed.get());
  }

  @Test void pendingCandidateDoesNotBlockAcceptedReadsAndRetirementWaitsForLease() throws Exception {
    var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
    var first = new State("first"); var second = new State("second");
    try (var coordinator = new CandidatePublicationCoordinator<String, State>((i, c) -> {
      if (i.payload().equals("first")) return valid(first);
      entered.countDown(); latch(release); return valid(second);
    }, i -> true)) {
      coordinator.submit(input("first")); idle(coordinator);
      var lease = coordinator.accepted().orElseThrow();
      coordinator.submit(input("second")); latch(entered);
      assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID, coordinator.status().beanStatus());
      assertEquals(PENDING, coordinator.status().logicalStatus());
      try (var read = coordinator.accepted().orElseThrow()) { assertSame(first, read.state()); }
      release.countDown(); idle(coordinator);
      assertEquals(2, coordinator.status().acceptedGeneration());
      assertEquals(0, first.closed.get());
      assertSame(first, lease.state());
      lease.close(); lease.close(); assertEquals(1, first.closed.get());
      try (var read = coordinator.accepted().orElseThrow()) { assertSame(second, read.state()); }
    } finally { release.countDown(); }
    assertEquals(1, second.closed.get());
  }

  @Test void coalescesLatestAndDiscardsUncooperativeStaleResult() throws Exception {
    var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
    var stale = new State("running"); var latest = new State("latest");
    var built = new java.util.concurrent.CopyOnWriteArrayList<String>();
    try (var coordinator = new CandidatePublicationCoordinator<String, State>((i, c) -> {
      built.add(i.payload());
      if (i.payload().equals("running")) { entered.countDown(); latch(release); return valid(stale); }
      return valid(latest);
    }, i -> true)) {
      coordinator.submit(input("running")); latch(entered);
      coordinator.submit(input("discarded-queued")); coordinator.submit(input("latest"));
      release.countDown(); idle(coordinator);
      assertEquals(List.of("running", "latest"), built);
      assertEquals(1, stale.closed.get());
      assertEquals(1, coordinator.status().acceptedGeneration());
      assertEquals(Map.of("worldview", "latest"), coordinator.status().acceptedSources());
    } finally { release.countDown(); }
  }

  @Test void invalidBeanSupersedesRunningBuildWithoutPublishingItsLateSuccess() throws Exception {
    var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
    var state = new State("late");
    try (var coordinator = new CandidatePublicationCoordinator<String, State>((i, c) -> {
      entered.countDown(); latch(release); assertTrue(c.cancelled()); return valid(state);
    }, i -> true)) {
      coordinator.submit(input("running")); latch(entered);
      coordinator.submit(input("bad", CandidatePublicationCoordinator.BeanStatus.INVALID));
      release.countDown(); idle(coordinator);
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.NOT_RUN, coordinator.status().logicalStatus());
      assertTrue(coordinator.accepted().isEmpty()); assertEquals(1, state.closed.get());
    } finally { release.countDown(); }
  }

  @Test void publicationGuardRejectsChangedDependencySnapshot() throws Exception {
    var state = new State("stale");
    try (var coordinator = new CandidatePublicationCoordinator<String, State>((i, c) -> valid(state), i -> false)) {
      coordinator.submit(input("stale")); idle(coordinator);
      assertEquals(CANCELLED, coordinator.status().logicalStatus());
      assertEquals(0, coordinator.status().acceptedGeneration()); assertEquals(1, state.closed.get());
    }
  }

  @Test void reentrantSourceInvalidationWinsOverSuccessfulGuard() throws Exception {
    var holder = new AtomicReference<CandidatePublicationCoordinator<String, State>>();
    var state = new State("stale");
    try (var coordinator = new CandidatePublicationCoordinator<String, State>((i, c) -> valid(state), i -> {
      holder.get().invalidate(); return true;
    })) {
      holder.set(coordinator); coordinator.submit(input("stale")); idle(coordinator);
      assertEquals(CANCELLED, coordinator.status().logicalStatus());
      assertTrue(coordinator.accepted().isEmpty()); assertEquals(1, state.closed.get());
    }
  }

  @Test void unresolvedOrUnverifiedResultsNeverAcquireValidity() throws Exception {
    var unknown = new State("unknown"); var shared = new State("shared");
    try (var coordinator = new CandidatePublicationCoordinator<String, State>((i, c) -> {
      return i.payload().equals("unknown")
          ? new CandidatePublicationCoordinator.Result<>(unknown, UNRESOLVED, true, List.of("missing proof"))
          : new CandidatePublicationCoordinator.Result<>(shared, CandidatePublicationCoordinator.LogicalStatus.VALID,
              false, List.of("shared provider"));
    }, i -> true)) {
      coordinator.submit(input("unknown")); idle(coordinator);
      assertEquals(UNRESOLVED, coordinator.status().logicalStatus());
      coordinator.submit(input("shared")); idle(coordinator);
      assertEquals(FAILED, coordinator.status().logicalStatus()); assertTrue(coordinator.accepted().isEmpty());
      assertEquals(1, unknown.closed.get()); assertEquals(1, shared.closed.get());
    }
  }

  @Test void builderFailureRetainsAcceptedGenerationAndWorkerCanRecover() throws Exception {
    var first = new State("first"); var recovered = new State("recovered");
    try (var coordinator = new CandidatePublicationCoordinator<String, State>((i, c) -> {
      if (i.payload().equals("broken")) throw new AssertionError("build probe");
      return valid(i.payload().equals("first") ? first : recovered);
    }, i -> true)) {
      coordinator.submit(input("first")); idle(coordinator);
      coordinator.submit(input("broken")); idle(coordinator);
      assertEquals(FAILED, coordinator.status().logicalStatus());
      assertEquals(1, coordinator.status().acceptedGeneration());
      try (var read = coordinator.accepted().orElseThrow()) { assertSame(first, read.state()); }
      coordinator.submit(input("recovered")); idle(coordinator);
      assertEquals(2, coordinator.status().acceptedGeneration()); assertEquals(1, first.closed.get());
    }
  }

  @Test void refusesBorrowedAcceptedStateAndTracksCleanupFailureWithoutPoisoningNewState() throws Exception {
    var first = new State("first"); var second = new State("second"); first.cleanupFailure = true;
    try (var coordinator = new CandidatePublicationCoordinator<String, State>((i, c) ->
        valid(i.payload().equals("second") ? second : first), i -> true)) {
      coordinator.submit(input("first")); idle(coordinator);
      coordinator.submit(input("borrowed")); idle(coordinator);
      assertEquals(FAILED, coordinator.status().logicalStatus()); assertEquals(0, first.closed.get());
      coordinator.submit(input("second")); idle(coordinator);
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, coordinator.status().logicalStatus());
      assertEquals(1, coordinator.status().cleanupFailures().size()); assertEquals(1, first.closed.get());
      coordinator.submit(input("retired-borrow")); idle(coordinator);
      assertEquals(FAILED, coordinator.status().logicalStatus()); assertEquals(1, first.closed.get());
    }
  }

  @Test void shutdownDoesNotInterruptBuildOrDisposeLeasedStateEarly() throws Exception {
    var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
    var first = new State("first"); var pending = new State("pending");
    var coordinator = new CandidatePublicationCoordinator<String, State>((i, c) -> {
      if (i.payload().equals("first")) return valid(first);
      entered.countDown(); latch(release); assertFalse(Thread.currentThread().isInterrupted());
      return valid(pending);
    }, i -> true);
    try {
      coordinator.submit(input("first")); idle(coordinator);
      var lease = coordinator.accepted().orElseThrow();
      coordinator.submit(input("pending")); latch(entered); coordinator.close();
      assertEquals(0, first.closed.get()); assertTrue(coordinator.accepted().isEmpty());
      assertThrows(IllegalStateException.class, () -> coordinator.submit(input("new")));
      release.countDown(); idle(coordinator); assertEquals(1, pending.closed.get());
      lease.close(); assertEquals(1, first.closed.get());
    } finally { release.countDown(); coordinator.close(); }
  }

  @Test void executorRejectionIsFailedRatherThanPerpetuallyPending() throws Exception {
    var executor = Executors.newSingleThreadExecutor(); executor.shutdown();
    var builds = new AtomicInteger();
    try (var coordinator = new CandidatePublicationCoordinator<String, State>((i, c) -> {
      builds.incrementAndGet(); return valid(new State("never"));
    }, i -> true, executor)) {
      coordinator.submit(input("rejected")); idle(coordinator);
      assertEquals(FAILED, coordinator.status().logicalStatus()); assertEquals(0, builds.get());
    }
  }

  @Test void guardErrorDisposesCandidateAndWorkerRecovers() throws Exception {
    var first = new State("failed"); var good = new State("good");
    try (var coordinator = new CandidatePublicationCoordinator<String, State>((i, c) ->
        valid(i.payload().equals("failed") ? first : good), i -> {
      if (i.payload().equals("failed")) throw new AssertionError("guard probe");
      return true;
    })) {
      coordinator.submit(input("failed")); idle(coordinator);
      assertEquals(FAILED, coordinator.status().logicalStatus()); assertEquals(1, first.closed.get());
      coordinator.submit(input("good")); idle(coordinator);
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, coordinator.status().logicalStatus());
      assertEquals(1, coordinator.status().acceptedGeneration());
    }
  }

  @Test void reentrantGuardShutdownCleansUpOutsideMonitor() throws Exception {
    var holder = new AtomicReference<CandidatePublicationCoordinator<String, State>>();
    var reader = Executors.newSingleThreadExecutor();
    var first = new State("first"); var second = new State("second");
    first.cleanupHook = () -> {
      try { reader.submit(() -> holder.get().status()).get(2, TimeUnit.SECONDS); }
      catch (Exception e) { throw new IllegalStateException("cleanup held monitor", e); }
    };
    try (var coordinator = new CandidatePublicationCoordinator<String, State>((i, c) ->
        valid(i.payload().equals("first") ? first : second), i -> {
      if (i.payload().equals("second")) holder.get().close();
      return true;
    })) {
      holder.set(coordinator); coordinator.submit(input("first")); idle(coordinator);
      coordinator.submit(input("second")); idle(coordinator);
      assertEquals(1, first.closed.get()); assertEquals(1, second.closed.get());
      assertTrue(coordinator.status().cleanupFailures().isEmpty());
      assertTrue(coordinator.accepted().isEmpty());
    } finally { reader.shutdownNow(); }
  }

  private static final class DlState implements AutoCloseable {
    final org.semanticweb.owlapi.model.OWLOntologyManager manager;
    final OWLReasoner reasoner;
    final AtomicInteger closed = new AtomicInteger();
    DlState(boolean impossible) throws Exception {
      manager = OWLManager.createOWLOntologyManager();
      var ontology = manager.createOntology(); var f = manager.getOWLDataFactory();
      var a = f.getOWLClass(IRI.create("urn:source:A"));
      var b = f.getOWLClass(IRI.create("urn:source:B"));
      manager.addAxiom(ontology, f.getOWLSubClassOfAxiom(a, b));
      if (impossible) manager.addAxiom(ontology, f.getOWLDisjointClassesAxiom(a, b));
      reasoner = new org.semanticweb.HermiT.Reasoner.ReasonerFactory().createReasoner(ontology);
    }
    @Override public void close() { closed.incrementAndGet(); reasoner.dispose(); manager.ontologies().toList().forEach(manager::removeOntology); }
  }

  @Test void privateHermitCandidateWithImpossibleClassDoesNotReplaceAcceptedOntology() throws Exception {
    var states = new java.util.concurrent.CopyOnWriteArrayList<DlState>();
    try (var coordinator = new CandidatePublicationCoordinator<String, DlState>((i, c) -> {
      var state = new DlState(i.payload().equals("bad")); states.add(state);
      boolean consistent = state.reasoner.isConsistent();
      boolean possible = state.reasoner.isSatisfiable(state.manager.getOWLDataFactory().getOWLClass(IRI.create("urn:source:A")));
      if (i.payload().equals("bad")) { assertTrue(consistent); assertFalse(possible); }
      return new CandidatePublicationCoordinator.Result<>(state,
          consistent && possible ? CandidatePublicationCoordinator.LogicalStatus.VALID
              : CandidatePublicationCoordinator.LogicalStatus.INVALID, true, List.of());
    }, i -> true)) {
      coordinator.submit(input("good")); idle(coordinator);
      var accepted = states.getFirst();
      coordinator.submit(input("bad")); idle(coordinator);
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.INVALID, coordinator.status().logicalStatus());
      try (var read = coordinator.accepted().orElseThrow()) {
        assertSame(accepted, read.state());
        assertTrue(read.state().reasoner.isSatisfiable(read.state().manager.getOWLDataFactory().getOWLClass(IRI.create("urn:source:A"))));
      }
      assertNotSame(accepted.manager, states.get(1).manager);
      assertEquals(0, accepted.closed.get()); assertEquals(1, states.get(1).closed.get());
    }
    assertEquals(1, states.getFirst().closed.get());
  }
}
