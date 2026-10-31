package org.integratedmodelling.klab.services.scopes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.Test;

class ObserverScopeIdentityTest {
  @Test void serverTransportUsesSelectedIdentityWithoutRefreshingTheGraph() throws Exception {
    var scope = mock(ServiceContextScope.class);
    var observer = new ObservationImpl(); observer.setId(8173);
    var field = ServiceContextScope.class.getDeclaredField("observer");
    field.setAccessible(true); field.set(scope, observer);
    when(scope.getId()).thenReturn("session.context");
    when(scope.getObserver()).thenThrow(new AssertionError("Transport must not refresh the observer"));
    doCallRealMethod().when(scope).getObserverId();
    assertEquals("session.context#8173", ContextScope.getScopeId(scope));
    verify(scope, never()).getObserver();
    field.set(scope, null);
    assertEquals("session.context", ContextScope.getScopeId(scope));
  }
}
