package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.List;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Collect synchronous ingestion diagnostics without requiring or modifying a user scope. */
public final class IngestionNotifications implements AutoCloseable {
  private final Channel channel;
  private final String listener;

  public IngestionNotifications(Channel channel, List<Notification> notifications) {
    this.channel = channel;
    var thread = Thread.currentThread();
    listener = channel.onMessage((source, message) -> {
      if (Thread.currentThread() == thread
          && message.getPayload(Object.class) instanceof Notification notification) {
        notifications.add(notification);
      }
    }, Message.Queue.Errors, Message.Queue.Warnings, Message.Queue.Info);
  }

  @Override
  public void close() {
    channel.unregisterMessageListener(listener);
  }
}
