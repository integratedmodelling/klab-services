package org.integratedmodelling.klab.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.klab.api.scope.Scope;
import org.junit.jupiter.api.Test;

class JobManagerTest {

  @Test
  void cancellationTargetsSubmittedTaskAndRemainsVisibleAsInterrupted() {
    var manager = new JobManager();
    var submittedTask = new CompletableFuture<String>();
    var id = manager.submit(submittedTask, "test task");

    assertEquals(Scope.Status.WAITING, manager.status(id).getStatus());
    assertTrue(manager.cancel(id));
    assertTrue(submittedTask.isCancelled());
    assertEquals(Scope.Status.INTERRUPTED, manager.status(id).getStatus());
    assertFalse(manager.cancel(id));
    assertEquals(Scope.Status.INTERRUPTED, manager.status(id).getStatus());
  }

  @Test
  void successfulResultTransitionsFromWaitingToFinished() throws Throwable {
    var manager = new JobManager();
    var submittedTask = new CompletableFuture<String>();
    var id = manager.submit(submittedTask, "test task");

    submittedTask.complete("done");

    assertEquals(Scope.Status.FINISHED, manager.status(id).getStatus());
    assertEquals("\"done\"", manager.getResult(id));
  }

  @Test
  void cancellationInvokesDelegateForWrappedTask() {
    var manager = new JobManager();
    var sourceTask = new CompletableFuture<String>();
    var wrappedTask = sourceTask.thenApply(String::toUpperCase);
    var delegateCancelled = new AtomicBoolean();
    var id =
        manager.submit(
            wrappedTask,
            "wrapped task",
            () -> {
              delegateCancelled.set(true);
              sourceTask.cancel(true);
            });

    assertTrue(manager.cancel(id));

    assertTrue(delegateCancelled.get());
    assertTrue(sourceTask.isCancelled());
    assertEquals(Scope.Status.INTERRUPTED, manager.status(id).getStatus());
  }
}
