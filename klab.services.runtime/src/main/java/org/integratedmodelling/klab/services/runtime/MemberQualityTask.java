package org.integratedmodelling.klab.services.runtime;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Request-local cancellation bridge; cancelling never rolls back already committed observations. */
final class MemberQualityTask<T> extends CompletableFuture<T> {
  private final AtomicReference<CompletableFuture<?>> active=new AtomicReference<>();
  void active(CompletableFuture<?> child) {
    active.set(child);
    if (isCancelled()) child.cancel(true);
  }
  void requireActive() {
    if(isCancelled())throw new CancellationException("Native member-quality request cancelled");
  }
  void finish(T result,Throwable error) {
    if(isDone())return;
    if(error!=null)completeExceptionally(error);else complete(result);
  }
  @Override public boolean cancel(boolean interrupt) {
    var cancelled=super.cancel(interrupt);
    if(cancelled) { var child=active.get();if(child!=null)child.cancel(interrupt); }
    return cancelled;
  }
}
