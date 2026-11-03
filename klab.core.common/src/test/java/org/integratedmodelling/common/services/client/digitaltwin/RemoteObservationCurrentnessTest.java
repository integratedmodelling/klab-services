package org.integratedmodelling.common.services.client.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.integratedmodelling.common.services.client.RuntimeClient;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.Test;

class RemoteObservationCurrentnessTest {
  private ContextScope scope(String id) {
    var scope = mock(ContextScope.class);
    when(scope.getId()).thenReturn(id);
    return scope;
  }

  private ObservationImpl observation(long id) {
    var observation = new ObservationImpl();
    observation.setId(id);
    return observation;
  }

  @Test void cachedObservationCannotConcealCessationOrTransportFailure() {
    var root = scope("context");
    var request = scope("context");
    var remote = mock(RuntimeClient.class);
    var graph = new ClientKnowledgeGraph(root, remote);
    var cached = observation(42);
    graph.ingestObserver(cached);
    when(remote.getAsset(42L, RuntimeAsset.class, request)).thenReturn(cached, null);
    assertTrue(graph.isObservationCurrent(cached, request));
    assertFalse(graph.isObservationCurrent(cached, request));
    when(remote.getAsset(42L, RuntimeAsset.class, request))
        .thenThrow(new IllegalStateException("unavailable"));
    assertThrows(IllegalStateException.class, () -> graph.isObservationCurrent(cached, request));
    verify(remote, times(3)).getAsset(42L, RuntimeAsset.class, request);
    verifyNoMoreInteractions(remote);
  }

  @Test void wrongIdentityAndForeignContextCannotCertifyCurrentness() {
    var root = scope("context");
    var remote = mock(RuntimeClient.class);
    var graph = new ClientKnowledgeGraph(root, remote);
    var requested = observation(42);
    assertFalse(graph.isObservationCurrent(requested, scope("foreign")));
    assertFalse(graph.isObservationCurrent(requested, null));
    assertFalse(graph.isObservationCurrent(null, root));
    assertFalse(graph.isObservationCurrent(observation(0), root));
    verifyNoInteractions(remote);
    when(remote.getAsset(42L, RuntimeAsset.class, root)).thenReturn(observation(99));
    assertThrows(IllegalStateException.class, () -> graph.isObservationCurrent(requested, root));
  }

  @Test void prospectiveObservationRequiresTransactionAndAuthoritativeAdmission() {
    var root = scope("context");
    var remote = mock(RuntimeClient.class);
    var graph = new ClientKnowledgeGraph(root, remote);
    var prospective = observation(-42);
    assertFalse(graph.isObservationCurrent(prospective, root));
    verifyNoInteractions(remote);
    when(root.getTransactionId()).thenReturn("owned-transaction");
    when(remote.getAsset(-42L, RuntimeAsset.class, root)).thenReturn(prospective);
    assertTrue(graph.isObservationCurrent(prospective, root));
    when(remote.getAsset(-42L, RuntimeAsset.class, root)).thenReturn(null);
    assertFalse(graph.isObservationCurrent(prospective, root));
  }
}
