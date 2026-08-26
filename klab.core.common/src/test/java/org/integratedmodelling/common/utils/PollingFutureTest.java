package org.integratedmodelling.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.objects.JobStatus;
import org.junit.jupiter.api.Test;

class PollingFutureTest {

  @Test
  void cancelInvokesJobApiAndCompletesFutureAsCancelled() {
    var client = mock(Utils.Http.Client.class);
    when(client.get(ServicesAPI.JOBS.CANCEL, Boolean.class, "id", 42L)).thenReturn(true);
    var future = new Utils.Http.PollingFuture<>(client, String.class, 42, -1, 10);

    assertEquals(42, future.getJobId());
    assertTrue(future.cancel(true));
    verify(client).get(ServicesAPI.JOBS.CANCEL, Boolean.class, "id", 42L);
    assertTrue(future.isCancelled());
  }

  @Test
  void remotelyInterruptedJobCancelsLocallyWithoutCallingCancelAgain() throws Exception {
    var client = mock(Utils.Http.Client.class);
    when(client.get(ServicesAPI.JOBS.STATUS, JobStatus.class, "id", 42L))
        .thenReturn(status(Scope.Status.INTERRUPTED));
    var future = new Utils.Http.PollingFuture<>(client, String.class, 42, -1, 10);

    assertThrows(CancellationException.class, () -> future.get(2, TimeUnit.SECONDS));
    assertTrue(future.isCancelled());
    verify(client, never()).get(ServicesAPI.JOBS.CANCEL, Boolean.class, "id", 42L);
  }

  @Test
  void transformsResultBeforeExposingCompletionAndKeepsPollingFuture() throws Exception {
    var client = mock(Utils.Http.Client.class);
    when(client.get(ServicesAPI.JOBS.STATUS, JobStatus.class, "id", 42L))
        .thenReturn(status(Scope.Status.FINISHED));
    when(client.get(ServicesAPI.JOBS.RETRIEVE, String.class, "id", 42L))
        .thenReturn("resolved");
    var future = new Utils.Http.PollingFuture<>(client, String.class, 42, -1, 10);

    var returned = future.transformResult(String::toUpperCase);

    assertTrue(returned instanceof Utils.Http.PollingFuture<?>);
    assertEquals("RESOLVED", returned.get(2, TimeUnit.SECONDS));
  }

  @Test
  void completionDoesNotInterruptDependentStages() throws Exception {
    var client = mock(Utils.Http.Client.class);
    when(client.get(ServicesAPI.JOBS.STATUS, JobStatus.class, "id", 42L))
        .thenReturn(status(Scope.Status.FINISHED));
    when(client.get(ServicesAPI.JOBS.RETRIEVE, String.class, "id", 42L))
        .thenReturn("resolved");
    var future = new Utils.Http.PollingFuture<>(client, String.class, 42, -1, 10);

    var dependentStage =
        future.thenApply(
            result -> {
              assertFalse(Thread.currentThread().isInterrupted());
              return result;
            });

    assertEquals("resolved", dependentStage.get(2, TimeUnit.SECONDS));
  }

  private static JobStatus status(Scope.Status status) {
    var ret = new JobStatus();
    ret.setStatus(status);
    return ret;
  }
}
