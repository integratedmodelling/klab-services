package org.integratedmodelling.klab.services;

import org.integratedmodelling.common.utils.Utils;
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

public enum JobManager {
  INSTANCE;

  private Map<Long, CompletableFuture<?>> jobs = Collections.synchronizedMap(new HashMap<>());
  private AtomicLong nextId = new AtomicLong(0L);

  public Long submit(CompletableFuture<?> task) {
    var ret = nextId.incrementAndGet();
    // TODO add a whenCompleted or an Exceptionally() stage, put away the result and exception, and
    //  remove self from the map. This allows caching of result/exception by taskId
    jobs.put(ret, task);
    return ret;
  }

  public JobStatus status(long id) {

    var ret = new JobStatus();
    var task = jobs.get(id);
    if (task != null) {
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

    ret.setStatus(Scope.Status.EMPTY);
    return ret;
  }

  public String getResult(long id) {
    var task = jobs.remove(id);
    try {
      return Utils.Json.asString(task.get());
    } catch (Exception e) {
      // TODO could return the same as the Spring exception catcher
      throw new KlabResourceAccessException();
    }
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
