package org.integratedmodelling.common.authentication.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;

class ChannelNotificationTest {

  @Test
  void directInfoUsesInfoLevelAndMessagingSubscribesToInfoByDefault() {
    var channel =
        new ChannelImpl((org.integratedmodelling.klab.api.identities.Identity) null) {
          @Override
          public String getDispatchId() {
            return "test";
          }
        };
    var received = new AtomicReference<Notification>();
    channel.onMessage(
        (ignored, message) -> received.set(message.getPayload(Notification.class)),
        Message.Queue.Info);

    channel.info("synchronizing component");

    assertEquals(Notification.Level.Info, received.get().getLevel());
    assertTrue(
        mock(MessagingChannel.class, CALLS_REAL_METHODS)
            .defaultQueues()
            .contains(Message.Queue.Info));
  }
}
