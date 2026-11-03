package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CandidateRestorationCoordinatorTest {
  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static CandidatePublicationCoordinator.Input<String> input(String value) {
    return new CandidatePublicationCoordinator.Input<>(Map.of("source", value), List.of("source"),
        "dependency", "configuration", value, CandidatePublicationCoordinator.BeanStatus.VALID, List.of());
  }
  private static final class State implements AutoCloseable {
    final AtomicInteger closed = new AtomicInteger();
    @Override public void close() { closed.incrementAndGet(); }
  }
  private static CandidatePublicationCoordinator.Result<State> valid(State value) {
    return new CandidatePublicationCoordinator.Result<>(value, CandidatePublicationCoordinator.LogicalStatus.VALID,
        true, List.of("accepted diagnostic"));
  }

  @Test void restorationRequiresTheExactStillAcceptedInputAndDoesNotAdvanceItsRevision() throws Exception {
    var state = new State(); var original = input("A");
    try (var coordinator = new CandidatePublicationCoordinator<String, State>((in, cancel) -> valid(state), in -> true)) {
      assertNull(coordinator.acceptedInput()); assertFalse(coordinator.currentIsAccepted());
      assertFalse(coordinator.restoreAccepted(null, () -> true));
      assertFalse(coordinator.restoreAccepted(original, () -> true));
      coordinator.submit(original); assertTrue(coordinator.awaitIdle(TIMEOUT));
      assertSame(original, coordinator.acceptedInput()); assertTrue(coordinator.currentIsAccepted());
      assertFalse(coordinator.restoreAccepted(null, () -> true));
      long accepted = coordinator.status().acceptedGeneration();
      assertFalse(coordinator.restoreAccepted(input("A"), () -> true), "Equal input data alone is not a retained admission proof");
      assertTrue(coordinator.restoreAccepted(original, () -> true));
      long current = coordinator.status().inputGeneration();
      assertTrue(coordinator.restoreAccepted(original, () -> true));
      assertEquals(current, coordinator.status().inputGeneration(), "Already-current restoration is a no-op");
      coordinator.invalidate(); assertFalse(coordinator.currentIsAccepted());
      try (var held = coordinator.accepted().orElseThrow()) {
        assertTrue(coordinator.restoreAccepted(original, () -> true));
        assertSame(state, held.state());
        assertEquals(accepted, coordinator.status().acceptedGeneration());
        assertEquals(List.of("accepted diagnostic"), coordinator.status().diagnostics());
        assertEquals(0, state.closed.get());
      }
    }
    assertEquals(1, state.closed.get());
  }

  @Test void deniedOrThrowingProofDoesNotReconcileTheCandidate() throws Exception {
    var state = new State(); var original = input("A");
    var allowed = new java.util.concurrent.atomic.AtomicBoolean(true);
    try (var coordinator = new CandidatePublicationCoordinator<String, State>((in, cancel) -> valid(state), in -> allowed.get())) {
      coordinator.submit(original); assertTrue(coordinator.awaitIdle(TIMEOUT)); coordinator.invalidate();
      var before = coordinator.status();
      allowed.set(false);
      assertFalse(coordinator.restoreAccepted(original, () -> { throw new AssertionError("Guard must short-circuit admission proof"); }));
      allowed.set(true);
      assertFalse(coordinator.restoreAccepted(original, () -> false));
      var failure = new IllegalStateException("controlled admission failure");
      assertSame(failure, assertThrows(IllegalStateException.class,
          () -> coordinator.restoreAccepted(original, () -> { throw failure; })));
      assertEquals(before, coordinator.status());
    }
  }

  @Test void closureDuringGuardCannotRestoreOrReopenTheCoordinator() throws Exception {
    var state = new State(); var original = input("A");
    var hook = new AtomicReference<Runnable>(() -> {});
    var coordinator = new CandidatePublicationCoordinator<String, State>((in, cancel) -> valid(state), in -> {
      hook.getAndSet(() -> {}).run(); return true;
    });
    try {
      coordinator.submit(original); assertTrue(coordinator.awaitIdle(TIMEOUT));
      hook.set(coordinator::close);
      assertFalse(coordinator.restoreAccepted(original, () -> true));
      assertNull(coordinator.acceptedInput()); assertFalse(coordinator.currentIsAccepted());
      assertFalse(coordinator.restoreAccepted(original, () -> true));
      assertEquals(1, state.closed.get());
    } finally { coordinator.close(); }
  }

  @Test void replacementPublishedDuringGuardCannotBeOverwrittenByOldAdmission() throws Exception {
    var first = new State(); var next = new State(); var original = input("A"); var replacement = input("B");
    var hook = new AtomicReference<Runnable>(() -> {});
    try (var coordinator = new CandidatePublicationCoordinator<String, State>(
        (in, cancel) -> valid(in == original ? first : next), in -> { hook.getAndSet(() -> {}).run(); return true; })) {
      coordinator.submit(original); assertTrue(coordinator.awaitIdle(TIMEOUT));
      long accepted = coordinator.status().acceptedGeneration();
      try (var held = coordinator.accepted().orElseThrow()) {
        hook.set(() -> {
          coordinator.submit(replacement);
          try { assertTrue(coordinator.awaitIdle(TIMEOUT)); }
          catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
        });
        assertFalse(coordinator.restoreAccepted(original, () -> true));
        assertSame(replacement, coordinator.acceptedInput());
        assertEquals(accepted + 1, coordinator.status().acceptedGeneration());
        assertEquals(0, first.closed.get());
        assertSame(first, held.state());
      }
      assertEquals(1, first.closed.get());
    }
    assertEquals(1, next.closed.get());
  }
}
