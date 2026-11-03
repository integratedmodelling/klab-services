package org.integratedmodelling.klab.services.scopes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.junit.jupiter.api.Test;

class ContextActivityHintScopeTest {
  private ServiceContextScope scope() {
    var user = mock(UserIdentity.class); when(user.getUsername()).thenReturn("alice");
    var session = new ServiceSessionScope(new ServiceUserScope(user, mock(KlabService.class)));
    var root = new ServiceContextScope(session, DigitalTwin.Configuration.builder().id("s.a").name("owned").build(), user);
    root.setId("s.a"); return root;
  }

  @Test void copiesShareOnlyTheHintSinkAndDoNotOwnItsLifecycle() {
    var root = scope(); var sink = mock(ServiceContextScope.ActivityNotificationSink.class);
    root.installActivityNotifications(sink);
    assertThrows(IllegalStateException.class, () -> root.installActivityNotifications(sink));
    var derived = new ServiceContextScope(root);
    derived.send(Message.MessageClass.DigitalTwin, Message.MessageType.ActivityStarted, new ActivityImpl());
    verify(sink).accept(any(Message.class));
    // This fixture has no twin; even a failing root resource close must close the sink once.
    try { derived.close(); } catch (RuntimeException expected) {}
    verify(sink, never()).close();
    try { root.close(); } catch (RuntimeException expected) {}
    try { root.close(); } catch (RuntimeException expected) {}
    verify(sink, times(1)).close();
  }

  @Test void ordinaryMessagesAndHintFailuresDoNotBecomeObservationFailures() {
    var root = scope(); var sink = mock(ServiceContextScope.ActivityNotificationSink.class);
    root.installActivityNotifications(sink);
    root.send(Message.MessageClass.DigitalTwin, Message.MessageType.ContextClosed, "s.a");
    verify(sink, never()).accept(any());
    doThrow(new IllegalStateException("owned publisher failure")).when(sink).accept(any());
    assertNotNull(root.send(Message.MessageClass.DigitalTwin, Message.MessageType.ActivityFinished, new ActivityImpl()));
    doThrow(new IllegalStateException("owned close failure")).when(sink).close();
    try { root.close(); } catch (RuntimeException expected) {}
    verify(sink).close();
  }

  @Test void additionalHintSinkDoesNotReplaceTheInheritedFederationChannel() throws Exception {
    var root = scope();
    var channel = mock(org.integratedmodelling.common.authentication.scope.AMQPChannel.class);
    var field = org.integratedmodelling.common.authentication.scope.MessagingChannelImpl.class.getDeclaredField("amqpChannel");
    field.setAccessible(true); field.set(root, channel);
    var sink = mock(ServiceContextScope.ActivityNotificationSink.class); root.installActivityNotifications(sink);
    var derived = new ServiceContextScope(root);
    assertSame(channel, field.get(root)); assertSame(channel, field.get(derived));
    derived.send(Message.MessageClass.DigitalTwin, Message.MessageType.ActivityStarted, new ActivityImpl());
    verify(sink).accept(any(Message.class));
    assertSame(channel, field.get(root)); assertSame(channel, field.get(derived));
    verify(channel, never()).close(); verify(channel, never()).disconnect();
  }
}
