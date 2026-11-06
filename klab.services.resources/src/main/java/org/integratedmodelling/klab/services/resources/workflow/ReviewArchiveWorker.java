package org.integratedmodelling.klab.services.resources.workflow;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.*;
import org.integratedmodelling.klab.resources.*;

/** Startup/periodic replay plus explicitly authorized, single-closure local commit attempts.
 * Scheduled ticks never write projects: no cached request scope is retained for background use.
 * Pending jobs survive restart and require a fresh authorized request to retry Git. */
public final class ReviewArchiveWorker implements AutoCloseable {
  public record Status(String state, boolean automaticBackgroundDispatch, String relativePath,
      String manifestSha256, String commitId) {}
  private final WorkflowStore store;
  private final Path directory;
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
    var thread = new Thread(task, "review-archive-replay"); thread.setDaemon(true); return thread;
  });
  private ReviewArchiveOutbox outbox;
  private volatile boolean closed;
  private boolean started;
  private volatile String state = "STOPPED";
  public ReviewArchiveWorker(WorkflowStore store, Path directory) {
    this.store = Objects.requireNonNull(store); this.directory = Objects.requireNonNull(directory);
  }
  public synchronized void start() {
    if (closed) throw new IllegalStateException("Archive worker closed");
    if (!started) { started = true; executor.scheduleWithFixedDelay(this::tick, 0, 1, TimeUnit.MINUTES); }
  }
  synchronized void tick() {
    if (closed) return;
    try { replay(); state = "REPLAY_ONLY"; }
    catch (IOException | RuntimeException failure) { state = "UNAVAILABLE"; closeOutbox(); }
  }
  private void replay() throws IOException {
    if (outbox == null) outbox = new ReviewArchiveOutbox(directory, 10000);
    ReviewWorkflowArchives.replayCommitted(store, outbox);
  }
  public synchronized Status status(ReviewArchiveOutbox.Intent intent) {
    if (closed) return new Status("STOPPED", false, null, null, null);
    if (outbox != null) for (var item : outbox.items()) if (item.intent().equals(intent)) {
      var receipt = item.receipt();
      return new Status(item.status().name(), false, receipt == null ? null : receipt.relativePath(),
          receipt == null ? null : receipt.manifestSha256(), receipt == null ? null : receipt.commitId());
    }
    return new Status(state.equals("UNAVAILABLE") ? state : "PENDING", false, null, null, null);
  }
  /** Caller independently checks current workflow editor authority and supplies a trusted
   * project adapter that checks current project write access while holding mutation ownership. */
  public synchronized Status attempt(ReviewArchiveOutbox.Intent intent, ReviewArchiveOutbox.ProjectAccess access) {
    if (closed) return status(intent);
    try {
      replay(); outbox.enqueueCommitted(intent); outbox.dispatchOnly(intent, access); state = "REPLAY_ONLY";
    } catch (IOException | RuntimeException failure) { state = "UNAVAILABLE"; closeOutbox(); }
    return status(intent);
  }
  private void closeOutbox() {
    if (outbox != null) try { outbox.close(); } catch (IOException ignored) { }
    finally { outbox = null; }
  }
  @Override public void close() {
    closed = true; executor.shutdownNow();
    try { if (!executor.awaitTermination(30, TimeUnit.SECONDS)) throw new IllegalStateException("Archive replay has not stopped"); }
    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Archive shutdown interrupted"); }
    synchronized (this) { closeOutbox(); state = "STOPPED"; }
  }
}
