package org.integratedmodelling.common.services.client.scope;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import org.integratedmodelling.common.services.client.digitaltwin.ClientDigitalTwin;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.junit.jupiter.api.Test;

class ClientScopeShutdownTest {
  @SuppressWarnings("unchecked")
  private Map<String, ClientSessionScope> registeredScopes() throws Exception {
    var field = ClientScopeManager.class.getDeclaredField("scopes");
    field.setAccessible(true);
    return (Map<String, ClientSessionScope>) field.get(ClientScopeManager.INSTANCE);
  }

  @Test void shutdownDisconnectsAllPeersWithoutReleasingRemoteScopes() throws Exception {
    var session = mock(ClientSessionScope.class);
    var context = mock(ClientContextScope.class);
    var scopes = registeredScopes();
    scopes.put("shutdown-session", session);
    scopes.put("shutdown-context", context);
    try {
      // Local unregistration must not cause the remaining peers to be skipped.
      doAnswer(invocation -> { scopes.remove("shutdown-context"); return null; })
          .when(context).closePeer();
      ClientScopeManager.INSTANCE.closePeers();
      verify(session).closePeer();
      verify(context).closePeer();
      verify(session, never()).close();
      verify(context, never()).close();
      assertTrue(scopes.isEmpty());
    } finally {
      scopes.remove("shutdown-session");
      scopes.remove("shutdown-context");
    }
  }

  @Test void contextDisconnectDisposesLocalTwinAndMessagingOnly() throws Exception {
    var context = mock(ClientContextScope.class, CALLS_REAL_METHODS);
    var runtime = mock(RuntimeService.class);
    var twin = mock(ClientDigitalTwin.class);
    doReturn("shutdown-context").when(context).getId();
    doReturn(runtime).when(context).getService(RuntimeService.class);
    var messaging = mock(org.integratedmodelling.common.authentication.scope.AMQPChannel.class);
    var messagingField = org.integratedmodelling.common.authentication.scope.MessagingChannelImpl.class
        .getDeclaredField("amqpChannel");
    messagingField.setAccessible(true);
    messagingField.set(context, messaging);
    var field = ClientContextScope.class.getDeclaredField("digitalTwin");
    field.setAccessible(true);
    field.set(context, twin);
    context.closePeer();
    verify(twin).dispose();
    verify(messaging).close();
    verifyNoInteractions(runtime);
  }

  @Test void explicitContextCloseStillRequestsDeletion() {
    var context = mock(ClientContextScope.class, CALLS_REAL_METHODS);
    var runtime = mock(RuntimeService.class);
    doNothing().when(context).closePeer();
    doReturn(runtime).when(context).getService(RuntimeService.class);
    context.close();
    verify(context).closePeer();
    verify(runtime).releaseContext(context);
  }

  @Test void explicitSessionCloseStillReleasesSession() {
    var session = mock(ClientSessionScope.class, CALLS_REAL_METHODS);
    var runtime = mock(RuntimeService.class);
    doNothing().when(session).closePeer();
    doReturn(runtime).when(session).getService(RuntimeService.class);
    session.close();
    verify(session).closePeer();
    verify(runtime).releaseSession(session);
  }
}
