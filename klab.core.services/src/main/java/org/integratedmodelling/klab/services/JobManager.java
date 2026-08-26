package org.integratedmodelling.klab.services;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.exceptions.KlabException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.objects.JobStatus;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * There is one job manager per session at service side. Jobs expire when the session is closed.
 */
public class JobManager {

  private record Job(
      CompletableFuture<?> task, Runnable cancellation, AtomicBoolean cancellationRequested) {}

  private final Map<Long, Job> jobs = new ConcurrentHashMap<>();
  private final Cache<Long, Pair<Object, Throwable>> results =
      CacheBuilder.newBuilder().maximumSize(400).build();
  private final AtomicLong nextId = new AtomicLong(0L);

  /**
   * Submission of a completable future adds a stage that offloads the result (whether an object or
   * an exception) to a local cache where it is kept for a while, then removes the job so that all
   * resources can be freed.
   *
   * @param task
   * @return
   */
  public Long submit(CompletableFuture<?> task, String description) {
    return submit(task, description, () -> task.cancel(true));
  }

  /** Submit a job with an explicit cancellation action for wrapped or transformed futures. */
  public Long submit(CompletableFuture<?> task, String description, Runnable cancellation) {
    var ret = nextId.incrementAndGet();
    var job = new Job(task, cancellation, new AtomicBoolean());
    jobs.put(ret, job);
    task.whenComplete(
        (result, failure) -> {
          var storedFailure =
              job.cancellationRequested().get()
                  ? new CancellationException("Job was cancelled")
                  : failure;
          if (storedFailure instanceof CancellationException || task.isCancelled()) {
            Logging.INSTANCE.info("Job " + description + " was cancelled");
          } else if (storedFailure != null) {
            Logging.INSTANCE.error(
                "Job " + description + " failed\n" + Utils.Exceptions.stackTrace(storedFailure));
          } else {
            Logging.INSTANCE.info("Job " + description + " completed successfully");
          }
          results.put(ret, Pair.of(result, storedFailure));
          jobs.remove(ret, job);
        });
    return ret;
  }

  public JobStatus status(long id) {

    var ret = new JobStatus();

    var result = results.getIfPresent(id);
    if (result != null) {
      if (result.getSecond() instanceof CancellationException) {
        ret.setStatus(Scope.Status.INTERRUPTED);
      } else if (result.getFirst() != null) {
        ret.setStatus(Scope.Status.FINISHED);
      } else if (result.getSecond() != null) {
        ret.setStatus(Scope.Status.ABORTED);
        ret.setStackTrace(Utils.Exceptions.stackTrace(result.getSecond()));
      }
      return ret;
    }

    var job = jobs.get(id);
    if (job != null) {
      if (job.cancellationRequested().get()) {
        ret.setStatus(Scope.Status.INTERRUPTED);
        return ret;
      }
      var task = job.task();
      // most of these should never happen
      if (task.isCompletedExceptionally()) {
        ret.setStatus(Scope.Status.ABORTED);
      } else if (task.isCancelled()) {
        ret.setStatus(Scope.Status.INTERRUPTED);
      } else if (task.isDone()) {
        ret.setStatus(Scope.Status.FINISHED);
      } else {
        ret.setStatus(Scope.Status.WAITING);
      }

      return ret;
    }

    // this also happens after cancel
    ret.setStatus(Scope.Status.EMPTY);
    return ret;
  }

  public String getResult(long id) throws Throwable {
    var result = results.getIfPresent(id);
    if (result != null) {
      if (result.getSecond() != null) {
        throw result.getSecond();
      }
      return Utils.Json.asString(result.getFirst());
    }
    throw new KlabResourceAccessException("results of job " + id + " are not available");
  }

  // special case as this gets binary-encoded using Avro
  public Data getDataResult(long id) throws Throwable {
    var result = results.getIfPresent(id);
    if (result != null) {
      if (result.getSecond() != null) {
        throw result.getSecond();
      }
      if (result.getFirst() instanceof Data) {
        return (Data) result.getFirst();
      }
    }
    throw new KlabResourceAccessException("results of job " + id + " are not a data object");
  }

  public boolean cancel(long id) {
    var job = jobs.get(id);
    if (job != null) {
      if (!job.cancellationRequested().compareAndSet(false, true)) {
        return false;
      }
      try {
        job.cancellation().run();
      } catch (Throwable failure) {
        Logging.INSTANCE.error("Error while cancelling job " + id, failure);
      }
      job.task().cancel(true);
      return true;
    }
    return false;
  }
}
