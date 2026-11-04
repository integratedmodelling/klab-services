package org.integratedmodelling.klab.runtime.computation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.Test;

class ScalarFailureDiagnosticsTest {
  @Test
  void excludesExceptionMessagesAndArgumentPayloads() {
    var missing =
        new groovy.lang.MissingMethodException(
            "get", String.class, new Object[] {"CREDENTIAL_CANARY"});
    var cause =
        new IllegalStateException(
            "https://u:SECRET_CANARY@example.test token=SECRET_CANARY", missing);
    var facts = ScalarComputationGroovy.safeFailureFacts(cause);
    var encoded = org.integratedmodelling.klab.utilities.Utils.Json.asString(facts);
    assertFalse(encoded.contains("SECRET_CANARY"));
    assertFalse(encoded.contains("CREDENTIAL_CANARY"));
    assertFalse(encoded.contains("https://"));
    assertEquals("get", facts.get(1).get("method"));
    assertEquals(1, facts.get(1).get("arity"));
    assertFalse(facts.getFirst().containsKey("message"));
  }

  @Test
  void boundsAndStopsCyclicCauseChains() {
    var cyclic =
        new Throwable() {
          @Override
          public synchronized Throwable getCause() {
            return this;
          }
        };
    assertEquals(1, ScalarComputationGroovy.safeFailureFacts(cyclic).size());
    Throwable chain = null;
    for (int i = 0; i < 20; i++) chain = new IllegalStateException("not logged", chain);
    assertEquals(8, ScalarComputationGroovy.safeFailureFacts(chain).size());
  }

  @Test
  void disabledDiagnosticsDoNotReadScopesOrTargets() {
    var key = "klab.diagnostics.configurationRecompute";
    var old = System.getProperty(key);
    try {
      System.clearProperty(key);
      var scope = mock(ContextScope.class);
      var target = mock(Observation.class);
      var event = mock(Scheduler.Event.class);
      ScalarComputationGroovy.diagnoseFailure(
          new IllegalStateException("private"), target, event, scope);
      verifyNoInteractions(scope, target, event);
    } finally {
      if (old == null) System.clearProperty(key);
      else System.setProperty(key, old);
    }
  }
}
