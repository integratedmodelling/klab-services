package org.integratedmodelling.klab.services.resources.workflow;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.klab.resources.WorkflowStore;
import org.integratedmodelling.klab.services.base.ReviewNotificationOutbox;

/** Lifecycle-owned replay only. There is deliberately no transport/SMTP or enable-delivery switch. */
public final class ReviewNotificationWorker implements AutoCloseable {
  public record Health(String state, boolean deliveryEnabled, Instant lastAttempt, Instant lastSuccess,
      String reason, int totalDeliveries, Map<String, Long> counts, List<ReviewNotificationOutbox.Delivery> deliveries) {
    public Health { counts = Map.copyOf(counts); deliveries = List.copyOf(deliveries); }
  }
  private final WorkflowStore store;
  private final Path directory;
  private final Duration interval;
  private final ScheduledExecutorService executor;
  private ReviewNotificationOutbox outbox;
  private boolean started;
  private volatile boolean closed;
  private volatile Health health = new Health("STOPPED", false, null, null, "NOT_STARTED", 0, Map.of(), List.of());

  public ReviewNotificationWorker(WorkflowStore store, Path directory) {
    this(store, directory, Duration.ofMinutes(1));
  }
  ReviewNotificationWorker(WorkflowStore store, Path directory, Duration interval) {
    this.store = Objects.requireNonNull(store); this.directory = Objects.requireNonNull(directory);
    if (interval.toMillis() < 1) throw new IllegalArgumentException("Positive replay interval required");
    this.interval = interval;
    executor = Executors.newSingleThreadScheduledExecutor(task -> {
      var thread = new Thread(task, "review-notification-replay"); thread.setDaemon(true); return thread;
    });
  }
  public synchronized void start() {
    if (closed) throw new IllegalStateException("Worker closed");
    if (!started) {
      started = true; health = new Health("STARTING", false, null, null, "DELIVERY_DISABLED", 0, Map.of(), List.of());
      executor.scheduleWithFixedDelay(this::tick, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }
  }
  /** Independent worker tick; package access supports deterministic fixture verification. */
  synchronized void tick() {
    if (closed) return;
    Instant now = Instant.now();
    try {
      if (outbox == null) {
        requirePrivatePaths();
        outbox = new ReviewNotificationOutbox(directory, Clock.systemUTC(), 10000, 3, Duration.ofMinutes(5));
      }
      ReviewWorkflowNotifications.replayCommitted(store, outbox);
      var deliveries = outbox.deliveries();
      var counts = new TreeMap<String, Long>();
      deliveries.forEach(d -> counts.merge(d.status().name(), 1L, Long::sum));
      health = new Health("REPLAY_ONLY", false, now, Instant.now(), "DELIVERY_DISABLED",
          deliveries.size(), counts, deliveries.stream().limit(100).toList());
    } catch (IOException | RuntimeException failure) {
      // Never leak directory paths, identity details, raw persistence exceptions or credentials.
      health = new Health("DEGRADED", false, now, health.lastSuccess(), "REPLAY_FAILED",
          health.totalDeliveries(), health.counts(), health.deliveries());
      closeOutbox(); // Next periodic tick reopens/reconciles a poisoned store.
    }
  }
  private void requirePrivatePaths() throws IOException {
    Path absolute = directory.toAbsolutePath().normalize();
    for (Path part = absolute; part != null; part = part.getParent())
      if (Files.isSymbolicLink(part) || (Files.exists(part, LinkOption.NOFOLLOW_LINKS)
          && !part.toRealPath().equals(part))) throw new IOException("Private notification path alias");
    Files.createDirectories(absolute);
    for (String name : List.of("review-notifications.bin", "review-notifications.lock", "review-notifications.tmp")) {
      Path child = absolute.resolve(name);
      if (Files.isSymbolicLink(child) || (Files.exists(child, LinkOption.NOFOLLOW_LINKS)
          && (!Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS) || !child.toRealPath().equals(child))))
        throw new IOException("Private notification file alias");
    }
  }
  public Health health(int limit) {
    if (limit < 1 || limit > 100) throw new IllegalArgumentException("Health row limit must be 1..100");
    var snapshot = health;
    return new Health(snapshot.state(), false, snapshot.lastAttempt(), snapshot.lastSuccess(), snapshot.reason(),
        snapshot.totalDeliveries(), snapshot.counts(), snapshot.deliveries().stream().limit(limit).toList());
  }
  private void closeOutbox() {
    if (outbox != null) try { outbox.close(); }
    catch (IOException ignored) { /* Retain failure status; the next open still must acquire the OS lock. */ }
    finally { outbox = null; }
  }
  @Override public void close() {
    closed = true;
    executor.shutdownNow();
    try {
      if (!executor.awaitTermination(30, TimeUnit.SECONDS)) throw new IllegalStateException("Replay worker has not stopped");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt(); throw new IllegalStateException("Replay shutdown interrupted");
    }
    synchronized (this) {
      closeOutbox();
      health = new Health("STOPPED", false, health.lastAttempt(), health.lastSuccess(), "SHUTDOWN",
          health.totalDeliveries(), health.counts(), health.deliveries());
    }
  }
}
