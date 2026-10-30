package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;

/** Shared prerequisites run once per root transaction, including through different graph snapshots. */
final class InitializationRuns {
  private final Map<DigitalTwin.Transaction, ConcurrentHashMap<Object, CompletableFuture<Boolean>>> roots =
      new IdentityHashMap<>();

  boolean execute(DigitalTwin.Transaction transaction, long observationId, BooleanSupplier computation) {
    return executeKey(transaction, observationId, computation);
  }

  private record Contribution(long observationId, String support) {}

  boolean executeContribution(DigitalTwin.Transaction transaction, long observationId, String support,
      BooleanSupplier computation) {
    return executeKey(transaction, new Contribution(observationId, support), computation);
  }

  private boolean executeKey(DigitalTwin.Transaction transaction, Object key, BooleanSupplier computation) {
    if(transaction==null) return computation.getAsBoolean();
    while(transaction.getParent()!=null) transaction=transaction.getParent();
    final var root=transaction;
    ConcurrentHashMap<Object, CompletableFuture<Boolean>> runs;
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
    var previous=runs.putIfAbsent(key,result);
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
