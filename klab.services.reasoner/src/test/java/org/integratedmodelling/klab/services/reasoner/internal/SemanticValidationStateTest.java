package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class SemanticValidationStateTest {
  @Test void mutationIsNotPublishedUntilItSettlesAndFailureNeverLooksReady() {
    var state = new SemanticValidationState();
    var boot = state.signal().split(":")[0];
    assertFalse(state.ready());
    state.begin(0);
    assertEquals(boot + ":0:loading", state.signal());
    state.settle(1, true);
    assertEquals(boot + ":1:ready", state.signal());
    state.begin(1);
    assertFalse(state.ready());
    state.settle(2, false);
    assertFalse(state.ready());
    assertEquals(boot + ":2:failed", state.signal());
    state.begin(2);
    state.settle(3, true);
    assertTrue(state.ready());
  }

  @Test void restartHasADifferentIdentityEvenAtTheSameRevision() {
    var before = new SemanticValidationState();
    var after = new SemanticValidationState();
    before.settle(1, true); after.settle(1, true);
    assertNotEquals(before.signal(), after.signal());
  }

  @Test void pollingDoesNotAcquireTheIngestionMonitor() throws Exception {
    var state = new SemanticValidationState();
    state.begin(1);
    try (var worker = Executors.newSingleThreadExecutor()) {
      synchronized (state) {
        assertTrue(worker.submit(state::signal).get(1, TimeUnit.SECONDS).endsWith(":loading"));
      }
    }
  }

  @Test void fullReloadRepairsOutstandingItemsButAnUnrelatedUpdateDoesNot() {
    var state = new SemanticValidationState(); state.settle(1, true);
    state.beginUpdate(1, java.util.Set.of("ontology:A", "ontology:B"));
    state.settleUpdate(2, java.util.Set.of("ontology:A", "ontology:B"), false);
    state.beginUpdate(2, java.util.Set.of("ontology:A"));
    state.settleUpdate(3, java.util.Set.of("ontology:A"), true);
    assertFalse(state.ready());
    state.begin(3); state.settle(4, false);
    state.beginUpdate(4, java.util.Set.of("ontology:B"));
    state.settleUpdate(5, java.util.Set.of("ontology:B"), true);
    assertFalse(state.ready());
    state.begin(5); state.settle(6, true);
    assertTrue(state.ready());
  }
}
