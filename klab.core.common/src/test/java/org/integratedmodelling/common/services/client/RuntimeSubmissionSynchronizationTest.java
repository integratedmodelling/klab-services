package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.integratedmodelling.common.services.client.digitaltwin.ClientDigitalTwin;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

class RuntimeSubmissionSynchronizationTest {
  @Test
  @ResourceLock("submissionDiagnosticsProperty")
  void diagnosticReceptionPreservesPollingFutureAndEmptyFailure() throws Exception {
    var previous = System.getProperty(org.integratedmodelling.common.runtime.SubmissionDiagnostics.PROPERTY);
    try {
      System.setProperty(org.integratedmodelling.common.runtime.SubmissionDiagnostics.PROPERTY, "true");
      var http = mock(org.integratedmodelling.common.utils.Utils.Http.Client.class);
      var status = new org.integratedmodelling.klab.api.services.runtime.objects.JobStatus();
      status.setStatus(org.integratedmodelling.klab.api.scope.Scope.Status.FINISHED);
      var empty = Observation.empty(org.integratedmodelling.klab.api.services.runtime.Notification.error("test failure"));
      when(http.get(org.integratedmodelling.klab.api.ServicesAPI.JOBS.STATUS,
          org.integratedmodelling.klab.api.services.runtime.objects.JobStatus.class, "id", 12L)).thenReturn(status);
      when(http.get(org.integratedmodelling.klab.api.ServicesAPI.JOBS.RETRIEVE,
          Observation.class, "id", 12L)).thenReturn(empty);
      var future = new org.integratedmodelling.common.utils.Utils.Http.PollingFuture<>(http, Observation.class, 12L, -1, 10);
      var method = RuntimeClient.class.getDeclaredMethod("receiveSubmission",
          java.util.concurrent.CompletableFuture.class, Observation.class, ContextScope.class);
      method.setAccessible(true);
      var returned = method.invoke(mock(RuntimeClient.class), future, new ObservationImpl(), mock(ContextScope.class));
      assertSame(future, returned);
      assertEquals(12L, future.getJobId());
      assertSame(empty, future.get(2, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(1, empty.getNotifications().size());
      verify(http).get(org.integratedmodelling.klab.api.ServicesAPI.JOBS.RETRIEVE, Observation.class, "id", 12L);
    } finally {
      if (previous == null) System.clearProperty(org.integratedmodelling.common.runtime.SubmissionDiagnostics.PROPERTY);
      else System.setProperty(org.integratedmodelling.common.runtime.SubmissionDiagnostics.PROPERTY, previous);
    }
  }

  @Test
  void receptionDoesNotReplaceExceptionalCompletion() throws Exception {
    var failure = new IllegalStateException("original failure");
    var future = java.util.concurrent.CompletableFuture.<Observation>failedFuture(failure);
    var method = RuntimeClient.class.getDeclaredMethod("receiveSubmission",
        java.util.concurrent.CompletableFuture.class, Observation.class, ContextScope.class);
    method.setAccessible(true);
    var returned = (java.util.concurrent.CompletableFuture<?>)method.invoke(
        mock(RuntimeClient.class), future, new ObservationImpl(), mock(ContextScope.class));
    var thrown = assertThrows(java.util.concurrent.ExecutionException.class,
        () -> returned.get(2, java.util.concurrent.TimeUnit.SECONDS));
    assertSame(failure, thrown.getCause());
  }

  @Test
  void synchronizesOnlyPersistedSubmissionResults() throws Exception {
    var client = mock(RuntimeClient.class);
    var scope = mock(ContextScope.class);
    var twin = mock(ClientDigitalTwin.class);
    var graph = mock(ClientKnowledgeGraph.class);
    when(scope.getDigitalTwin()).thenReturn(twin);
    when(twin.getKnowledgeGraph()).thenReturn(graph);
    var method = RuntimeClient.class.getDeclaredMethod(
        "synchronizeSubmission", Observation.class, ContextScope.class);
    method.setAccessible(true);
    assertNull(method.invoke(client, null, scope));
    for (long id : new long[] {Observation.QUERY_ID, 0, -20}) {
      var response = new ObservationImpl();
      response.setId(id);
      assertSame(response, method.invoke(client, response, scope));
    }
    verifyNoInteractions(graph);
    var committed = new ObservationImpl();
    committed.setId(42);
    assertSame(committed, method.invoke(client, committed, scope));
    verify(graph).ingest(committed);
  }
}
