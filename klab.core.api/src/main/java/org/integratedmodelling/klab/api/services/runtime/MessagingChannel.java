package org.integratedmodelling.klab.api.services.runtime;

import java.util.EnumSet;
import java.util.Set;

/**
 * A channel that has been instrumented for messaging to paired channels. Only a tag interface for
 * now.
 */
public interface MessagingChannel extends Channel {

  @Override
  default Set<Message.Queue> defaultQueues() {
    return EnumSet.of(Message.Queue.Errors, Message.Queue.Events, Message.Queue.Warnings);
  }

  /**
   * True if messaging is available and connected.
   *
   * @return
   */
  boolean hasMessaging();

  /**
   * True if connections have been established.
   *
   * @return
   */
  boolean isConnected();

  /**
   * True if the scope is connected to one or more queues and is set up for sending messages.
   *
   * @return
   */
  boolean isSender();

  /**
   * True if the scope is connected to one or more queues and is set up for receiving messages.
   *
   * @return
   */
  boolean isReceiver();
}
