package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public abstract class AbstractExecutor implements CompiledDataflow.ContextualExecutor {

  protected final ContextScope scope;
  protected final CompiledDataflow.CallDescriptors callInfo;
  protected final Observation observation;
  protected Throwable cause;
  protected Storage storage;

  public AbstractExecutor(
      CompiledDataflow.CallDescriptors callInfo, Observation observation, ContextScope scope) {
    this.callInfo = callInfo;
    this.observation = observation;
    this.scope = scope;
  }

  @Override
  public boolean execute(Scheduler.Event event) {

    if (storage == null) {
      storage = scope.getDigitalTwin().getStorageManager().getStorage(observation);
    }
    if (storage == null) {
      cause = new KlabIllegalStateException("No storage available for " + observation);
      return false;
    }
    var localShardingStrategy =
        callInfo == null ? storage.getNativeShardingStrategy() : callInfo.shardingStrategy();
    if (localShardingStrategy == null) {
      cause = new KlabIllegalStateException("No sharding strategy available for " + observation);
      return false;
    }

    List<Callable<Object>> tasks = new ArrayList<>();

    for (var scanner :
        storage.scan(
            event, localShardingStrategy, localShardingStrategy.getScannerClass(), false)) {
      tasks.add(() -> run(event, scanner));
    }

    try (var executorService = Executors.newVirtualThreadPerTaskExecutor()) {
      var results = executorService.invokeAll(tasks);
      return results.stream()
          .noneMatch(objectFuture -> objectFuture.state() == Future.State.FAILED);
    } catch (Throwable t) {
      cause = t;
      return false;
    }
  }

  protected abstract boolean run(Scheduler.Event event, Storage.Scanner scanner);

  @Override
  public Throwable getCause() {
    return cause;
  }
}
