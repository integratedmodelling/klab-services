package org.integratedmodelling.klab.services.runtime.server.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.junit.jupiter.api.Test;

class ObservationCurrentnessControllerTest {
  @Test void cachedAssetRequiresCurrentnessInTheAuthorizedScope() {
    var scope = mock(ContextScope.class);
    var twin = mock(DigitalTwin.class);
    var graph = mock(KnowledgeGraph.class);
    var authorization = mock(EngineAuthorization.class);
    when(authorization.getScope(ContextScope.class)).thenReturn(scope);
    when(scope.getDigitalTwin()).thenReturn(twin);
    when(twin.getKnowledgeGraph()).thenReturn(graph);
    var observation = new ObservationImpl();
    observation.setId(42);
    when(graph.getAsset(42L, scope, RuntimeAsset.class)).thenReturn(observation);
    var controller = new RuntimeServerController();
    when(graph.isObservationCurrent(observation, scope)).thenReturn(true, false);
    assertSame(observation, controller.retrieveAsset(42L, authorization));
    assertNull(controller.retrieveAsset(42L, authorization));
    when(graph.isObservationCurrent(observation, scope))
        .thenThrow(new IllegalStateException("lifecycle unavailable"));
    assertThrows(IllegalStateException.class, () -> controller.retrieveAsset(42L, authorization));
    verify(graph, times(3)).isObservationCurrent(observation, scope);

    var otherAsset = mock(RuntimeAsset.class);
    when(graph.getAsset(43L, scope, RuntimeAsset.class)).thenReturn(otherAsset);
    assertSame(otherAsset, controller.retrieveAsset(43L, authorization));
    assertNull(controller.retrieveAsset(44L, authorization));
    verify(graph, times(3)).isObservationCurrent(any(), any());
  }
}
