package org.integratedmodelling.common.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletionException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("submissionDiagnosticsProperty")
class SubmissionDiagnosticsTest {
  private String previous;

  @BeforeEach void remember() { previous = System.getProperty(SubmissionDiagnostics.PROPERTY); }
  @AfterEach void restore() {
    if (previous == null) System.clearProperty(SubmissionDiagnostics.PROPERTY);
    else System.setProperty(SubmissionDiagnostics.PROPERTY, previous);
  }

  @Test void disabledDoesNotInspectInputsOrScope() {
    System.setProperty(SubmissionDiagnostics.PROPERTY, "false");
    var scope = mock(ContextScope.class);
    var observation = mock(Observation.class);
    assertNull(SubmissionDiagnostics.summary("test", scope, observation, observation, 7L, null));
    assertSame(observation, SubmissionDiagnostics.result("test", scope, observation, observation, 7L, null));
    verifyNoInteractions(scope, observation);
  }

  @Test void reportsActualEmptyResponseWithoutMessagesUrlsOrMetadata() {
    System.setProperty(SubmissionDiagnostics.PROPERTY, "true");
    var result = Observation.empty(Notification.error("Resolution of private:secret failed: empty dataflow"));
    result.getMetadata().put("Authorization", "Bearer do-not-log");
    result.getNotifications().add(Notification.error("https://example.invalid/?token=do-not-log"));
    var scope = mock(ContextScope.class);
    when(scope.getId()).thenReturn("private-user-context");
    var text = SubmissionDiagnostics.summary("inspector.G01", scope, null, result, 42L, null);
    assertNotNull(text);
    assertTrue(text.contains("RESOLUTION_EMPTY_DATAFLOW_OR_COMPILE_COMMIT"));
    assertTrue(text.contains("scopeHash"));
    assertTrue(text.contains("messageHash"));
    for (var secret : new String[]{"private:secret", "private-user-context", "https:", "token=", "Authorization", "do-not-log"})
      assertFalse(text.contains(secret), secret);
    var fields = SubmissionDiagnostics.snapshot("test", null, result, 42L, null);
    assertEquals(Observation.UNASSIGNED_ID, fields.get("resultId"));
    assertEquals(true, fields.get("empty"));
    assertEquals(false, fields.get("hasObservable"));
    assertEquals(0.0, fields.get("resolvedCoverage"));
    assertEquals(2, fields.get("notificationCount"));
    assertSame(result, SubmissionDiagnostics.result("test", scope, null, result, 42L, null));
    assertEquals(2, result.getNotifications().size());
    assertEquals("Bearer do-not-log", result.getMetadata().get("Authorization"));
  }

  @Test void inspectionFailureCannotReplaceResult() {
    System.setProperty(SubmissionDiagnostics.PROPERTY, "true");
    var result = mock(Observation.class);
    when(result.getId()).thenThrow(new IllegalStateException("do-not-log"));
    assertNull(SubmissionDiagnostics.summary("test", null, null, result, null, null));
    assertSame(result, SubmissionDiagnostics.result("test", null, null, result, null, null));
  }

  @Test void notificationOutputIsBoundedAndNonfiniteCoverageIsExplicit() {
    var result = new ObservationImpl();
    result.setId(Observation.QUERY_ID);
    result.setResolvedCoverage(Double.NaN);
    for (int i = 0; i < 20; i++) result.getNotifications().add(Notification.error("message " + i));
    var fields = SubmissionDiagnostics.snapshot("test", null, result, null, null);
    assertEquals("nonfinite", fields.get("resolvedCoverage"));
    assertEquals(20, fields.get("notificationCount"));
    assertEquals(8, ((java.util.List<?>)fields.get("notifications")).size());
    assertEquals(false, fields.get("empty"));
    assertEquals(Observation.QUERY_ID, fields.get("resultId"));
  }

  @Test void exceptionalDeliveryReportsTypeWithoutExceptionText() {
    var failure = new CompletionException(new IllegalStateException("secret https://example.invalid"));
    var fields = SubmissionDiagnostics.snapshot("client.exception", null, null, 9L, failure);
    assertEquals(IllegalStateException.class.getName(), fields.get("failureType"));
    assertEquals(false, fields.get("resultPresent"));
    assertFalse(fields.toString().contains("secret"));
    assertFalse(fields.toString().contains("https://"));
  }
}
