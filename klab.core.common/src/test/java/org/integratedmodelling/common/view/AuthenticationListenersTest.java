package org.integratedmodelling.common.view;

import static org.mockito.Mockito.*;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.junit.jupiter.api.Test;

class AuthenticationListenersTest {
  @Test void repeatedAuthenticationRegistersOnceAndReplacementUnregistersOldScope() {
    var controller = mock(AbstractUIController.class, CALLS_REAL_METHODS);
    var engine = mock(Engine.class);
    var first = mock(UserScope.class);
    var second = mock(UserScope.class);
    doReturn(engine).when(controller).createEngine();
    when(engine.authenticate()).thenReturn(first, first, second);
    when(first.onMessage(any(), any(Message.Queue[].class))).thenReturn("events", "ui", "notifications");
    controller.authenticate();
    controller.authenticate();
    verify(first, times(3)).onMessage(any(), any(Message.Queue[].class));
    controller.authenticate();
    verify(first).unregisterMessageListener("events");
    verify(first).unregisterMessageListener("ui");
    verify(first).unregisterMessageListener("notifications");
    verify(second, times(3)).onMessage(any(), any(Message.Queue[].class));
  }
}
