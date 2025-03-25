package org.integratedmodelling.klab.services;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.exceptions.KlabException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.objects.JobStatus;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * There is one job manager per session at service side. Jobs go away when the session is closed.
 */
public class JobManager {

  private Map<Long, CompletableFuture<?>> jobs = Collections.synchronizedMap(new HashMap<>());
  private Cache<Long, Pair<Object, Throwable>> results =
      CacheBuilder.newBuilder().maximumSize(400).build();
  private AtomicLong nextId = new AtomicLong(0L);

  /**
   * Submission of a completable future adds a stage that offloads the result (whether an object or
   * an exception) to a local cache where it is kept for a while, then removes the job so that all
   * resources can be freed.
   *
   * @param task
   * @return
   */
  public Long submit(CompletableFuture<?> task, String description) {
    var ret = nextId.incrementAndGet();
    jobs.put(
        ret,
        task.handle(
            (o, t) -> {
              if (o == null) {
                Logging.INSTANCE.error(
                    "Job " + description + " failed\n" + Utils.Exceptions.stackTrace(t));
              } else {
                Logging.INSTANCE.info("Job " + description + " completed successfully");
              }
              // put away result
              results.put(ret, Pair.of(o, t));
              // dereference self
              jobs.remove(ret);
              return this;
            }));
    return ret;
  }

  public JobStatus status(long id) {

    var ret = new JobStatus();

    var result = results.getIfPresent(id);
    if (result != null) {
      if (result.getFirst() != null) {
        ret.setStatus(Scope.Status.FINISHED);
      } else if (result.getSecond() != null) {
        ret.setStatus(Scope.Status.ABORTED);
        ret.setStackTrace(Utils.Exceptions.stackTrace(result.getSecond()));
      }
      return ret;
    }

    var task = jobs.get(id);
    if (task != null) {
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
    throw new KlabResourceAccessException("results of job " + id);
  }

  public boolean cancel(long id) {
    var task = jobs.remove(id);
    if (task != null) {
      task.cancel(true);
      return true;
    }
    return false;
  }
}
