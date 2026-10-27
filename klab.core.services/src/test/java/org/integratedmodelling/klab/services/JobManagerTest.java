package org.integratedmodelling.klab.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.scope.Scope;
import org.junit.jupiter.api.Test;

class JobManagerTest {
  @Test void loggingFailureCannotPreventPublicationOfAnyCompletionEnvelope() throws Throwable {
    var logging = Logging.INSTANCE;
    var originalInfo = logging.getInfoWriter();
    var originalError = logging.getErrorWriter();
    var calls = new AtomicInteger();
    try {
      logging.setInfoWriter(message -> {
        if (message.contains("Job logging-publication")) {
          calls.incrementAndGet(); throw new IllegalStateException("info writer failed");
        }
        if (originalInfo != null) originalInfo.accept(message);
      });
      logging.setErrorWriter(message -> {
        if (message.contains("Job logging-publication")) {
          calls.incrementAndGet(); throw new IllegalStateException("error writer failed");
        }
        if (originalError != null) originalError.accept(message);
      });
      var manager = new JobManager();
      var success = new CompletableFuture<String>();
      long succeeded = manager.submit(success, "logging-publication success");
      success.complete("done");
      assertEquals(Scope.Status.FINISHED, manager.status(succeeded).getStatus());
      assertEquals("\"done\"", manager.getResult(succeeded));
      var failure = new CompletableFuture<String>();
      var originalFailure = new IllegalStateException("worker failed");
      long failed = manager.submit(failure, "logging-publication failure");
      failure.completeExceptionally(originalFailure);
      assertEquals(Scope.Status.ABORTED, manager.status(failed).getStatus());
      assertSame(originalFailure, assertThrows(IllegalStateException.class, () -> manager.getResult(failed)));
      var empty = new CompletableFuture<String>();
      long nullResult = manager.submit(empty, "logging-publication null");
      empty.complete(null);
      assertEquals(Scope.Status.ABORTED, manager.status(nullResult).getStatus());
      assertEquals("null", manager.getResult(nullResult));
      var cancellation = new CompletableFuture<String>();
      long cancelled = manager.submit(cancellation, "logging-publication cancellation");
      cancellation.cancel(true);
      assertEquals(Scope.Status.INTERRUPTED, manager.status(cancelled).getStatus());
      assertThrows(CancellationException.class, () -> manager.getResult(cancelled));
      assertEquals(4, calls.get(), "All completion log paths must exercise the failing writers");
    } finally {
      logging.setInfoWriter(originalInfo);
      logging.setErrorWriter(originalError);
    }
  }

  @Test void completedSuccessWaitsUntilItsResultIsPublished() throws Throwable {
    var task = new CompletableFuture<String>();
    publicationBoundary(task, () -> task.complete("done"), Scope.Status.FINISHED, "\"done\"", null);
  }

  @Test void completedFailureWaitsUntilItsFailureEnvelopeIsPublished() throws Throwable {
    var task = new CompletableFuture<String>();
    var failure = new IllegalStateException("worker failed");
    publicationBoundary(task, () -> task.completeExceptionally(failure), Scope.Status.ABORTED, null, failure);
  }

  @Test void completedNullWaitsUntilItsTerminalNullEnvelopeIsPublished() throws Throwable {
    var task = new CompletableFuture<String>();
    publicationBoundary(task, () -> task.complete(null), Scope.Status.ABORTED, "null", null);
  }

  @Test void externalCancellationWaitsUntilItsCancellationEnvelopeIsPublished() throws Throwable {
    var task = new CompletableFuture<String>();
    publicationBoundary(task, () -> task.cancel(true), Scope.Status.INTERRUPTED, null, null);
  }

  private void publicationBoundary(CompletableFuture<String> task, Runnable completion,
      Scope.Status terminal, String serialized, Throwable expectedFailure) throws Throwable {
    var manager = new JobManager();
    long id = manager.submit(task, "publication boundary");
    var callbackEntered = new CountDownLatch(1);
    var releaseCallback = new CountDownLatch(1);
    // CompletableFuture dispatches later callbacks first. Hold one after the future becomes
    // done but before JobManager's already registered callback publishes the result.
    task.whenComplete((value, failure) -> {
      callbackEntered.countDown();
      try { releaseCallback.await(); }
      catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    });
    var worker = new Thread(completion, "job-publication-test");
    worker.start();
    try {
      assertTrue(callbackEntered.await(5, TimeUnit.SECONDS), "Completion callback did not start");
      assertTrue(task.isDone());
      assertEquals(Scope.Status.WAITING, manager.status(id).getStatus());
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException.class,
          () -> manager.getResult(id));
    } finally {
      releaseCallback.countDown();
      worker.join(5000);
    }
    assertFalse(worker.isAlive(), "Completion callback did not finish");
    assertEquals(terminal, manager.status(id).getStatus());
    if (expectedFailure != null) {
      assertSame(expectedFailure, assertThrows(expectedFailure.getClass(), () -> manager.getResult(id)));
      assertTrue(manager.status(id).getStackTrace().contains("worker failed"));
    } else if (terminal == Scope.Status.INTERRUPTED) {
      assertThrows(CancellationException.class, () -> manager.getResult(id));
    } else {
      assertEquals(serialized, manager.getResult(id));
    }
    assertEquals(Scope.Status.EMPTY, manager.status(id + 1000).getStatus());
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException.class,
        () -> manager.getResult(id + 1000));
  }

  @Test void completionDoesNotClaimApplicationSuccess() {
    assertEquals("completed with resolution outcome FAILED",JobManager.completionDescription(
        org.integratedmodelling.klab.api.services.runtime.Dataflow.empty(java.util.List.of())));
    assertEquals("completed with resolution outcome NO_MODEL",JobManager.completionDescription(
        org.integratedmodelling.klab.api.services.runtime.Dataflow.noModel(java.util.List.of())));
    assertFalse(JobManager.completionDescription(false).contains("success"));
    assertFalse(JobManager.completionDescription(null).contains("success"));
  }

  @Test
  void completedNullResultIsTerminalInsteadOfPollingForever() {
    var manager = new JobManager();
    var task = new CompletableFuture<Object>();
    var id = manager.submit(task, "legacy importer");
    task.complete(null);
    assertEquals(Scope.Status.ABORTED, manager.status(id).getStatus());
    assertTrue(manager.status(id).getStackTrace().contains("without returning a result"));
    assertEquals(Scope.Status.ABORTED, manager.status(id).getStatus());
  }

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
