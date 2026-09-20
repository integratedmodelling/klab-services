package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;

/** Shared prerequisites run once per root transaction, including through different graph snapshots. */
final class InitializationRuns {
  private final Map<DigitalTwin.Transaction, ConcurrentHashMap<Long, CompletableFuture<Boolean>>> roots =
      new IdentityHashMap<>();

  boolean execute(DigitalTwin.Transaction transaction, long observationId, BooleanSupplier computation) {
    if(transaction==null) return computation.getAsBoolean();
    while(transaction.getParent()!=null) transaction=transaction.getParent();
    final var root=transaction;
    ConcurrentHashMap<Long, CompletableFuture<Boolean>> runs;
    boolean created=false;
    synchronized(roots) {
      runs=roots.get(root);
      if(runs==null) {
        runs=new ConcurrentHashMap<>();
        roots.put(root,runs);
        created=true;
      }
    }
    if(created) {
      Runnable release=() -> { synchronized(roots) { roots.remove(root); } };
      root.afterCommit(release);
      root.afterRollback(release);
    }
    var result=new CompletableFuture<Boolean>();
    var previous=runs.putIfAbsent(observationId,result);
    if(previous!=null) return previous.join();
    try {
      boolean success=computation.getAsBoolean();
      result.complete(success);
      return success;
    } catch(RuntimeException | Error failure) {
      result.completeExceptionally(failure);
      throw failure;
    }
  }
}
