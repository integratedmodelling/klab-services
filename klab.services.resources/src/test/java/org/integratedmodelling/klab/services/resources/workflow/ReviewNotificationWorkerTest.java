package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.Audience;
import org.integratedmodelling.klab.resources.WorkflowStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewNotificationWorkerTest {
  @TempDir Path directory;
  @Test void startupReplayAndRestartRetainPendingWithoutAnyDeliveryAttempt() throws Exception {
    var fixture = new ReviewCycleProtocolTest(); fixture.round(fixture.open(), Audience.COMMUNITY);
    for (int restart = 0; restart < 2; restart++) {
      try (var worker = new ReviewNotificationWorker(fixture.store, directory)) {
        worker.tick(); worker.tick(); var health = worker.health(100);
        assertEquals("REPLAY_ONLY", health.state()); assertFalse(health.deliveryEnabled());
        assertEquals(1, health.totalDeliveries()); assertEquals(0, health.deliveries().getFirst().attempts());
        assertEquals("PENDING", health.deliveries().getFirst().status().name());
        assertThrows(IllegalArgumentException.class, () -> worker.health(101));
      }
    }
  }
  @Test void scheduledWorkerStopsBeforeItsStoreIsReleased() throws Exception {
    var fixture = new ReviewCycleProtocolTest(); var observed = new Semaphore(0); var closedStore = new AtomicBoolean();
    var callsAfterClose = new AtomicInteger();
    var store = (WorkflowStore) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{WorkflowStore.class}, (p, method, args) -> {
      if (method.getName().equals("listFlows")) {
        if (closedStore.get()) callsAfterClose.incrementAndGet(); observed.release();
      }
      return method.invoke(fixture.store, args);
    });
    var worker = new ReviewNotificationWorker(store, directory, Duration.ofMillis(10));
    worker.start(); worker.start(); assertTrue(observed.tryAcquire(5, TimeUnit.SECONDS)); worker.close();
    closedStore.set(true); observed.drainPermits();
    assertFalse(observed.tryAcquire(100, TimeUnit.MILLISECONDS)); assertEquals(0, callsAfterClose.get());
    assertEquals("STOPPED", worker.health(1).state()); assertThrows(IllegalStateException.class, worker::start);
    worker.close();
  }
  @Test void replayFailureIsSanitizedRecoverableAndDoesNotChangeCommittedFlow() throws Exception {
    var fixture = new ReviewCycleProtocolTest(); fixture.round(fixture.open(), Audience.REVIEWER);
    long revision = fixture.store.getFlow(fixture.flowId).getRevision(); var fail = new AtomicBoolean(true);
    var store = (WorkflowStore) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{WorkflowStore.class}, (p, method, args) -> {
      if (method.getName().equals("listFlows") && fail.get()) throw new IllegalStateException("SECRET@example.invalid C:/private/path");
      return method.invoke(fixture.store, args);
    });
    try (var worker = new ReviewNotificationWorker(store, directory)) {
      worker.tick(); assertEquals("DEGRADED", worker.health(100).state()); assertFalse(worker.health(100).toString().contains("SECRET"));
      assertEquals(revision, fixture.store.getFlow(fixture.flowId).getRevision());
      fail.set(false); worker.tick(); assertEquals("REPLAY_ONLY", worker.health(100).state()); assertEquals(1, worker.health(100).totalDeliveries());
    }
  }
  @Test void storeOwnershipAndUnexpectedPathsFailClosedThenRecover() throws Exception {
    var fixture = new ReviewCycleProtocolTest(); var first = new ReviewNotificationWorker(fixture.store, directory);
    var second = new ReviewNotificationWorker(fixture.store, directory);
    try {
      first.tick(); second.tick(); assertEquals("DEGRADED", second.health(1).state());
      first.close(); second.tick(); assertEquals("REPLAY_ONLY", second.health(1).state());
    } finally { first.close(); second.close(); }
    Path invalid = directory.resolve("invalid"); Files.createDirectories(invalid.resolve("review-notifications.tmp"));
    try (var worker = new ReviewNotificationWorker(fixture.store, invalid)) {
      worker.tick(); assertEquals("DEGRADED", worker.health(1).state()); assertTrue(Files.isDirectory(invalid.resolve("review-notifications.tmp")));
    }
  }
}
