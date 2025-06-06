/*
 * This file is part of k.LAB.
 *
 * k.LAB is free software: you can redistribute it and/or modify it under the terms of the Affero
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root directory of the k.LAB
 * distribution (LICENSE.txt). If this cannot be found see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned in author tags. All
 * rights reserved.
 */
package org.integratedmodelling.klab.api.services.runtime;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.services.runtime.Message.MessageClass;
import org.integratedmodelling.klab.api.services.runtime.kactors.VM;

/**
 * A channel represents the current identity and is used to report status and send messages to any
 * subscribing identity. Channels are the base class for {@link
 * org.integratedmodelling.klab.api.scope.Scope} and their behavior depends on the kind of scope
 * they implement.
 *
 * <p>The {@link #error(Object...)}, {@link #warn(Object...)}, {@link #info(Object...)}, {@link
 * #ui(Message)}, {@link #debug(Object...)} and {@link #event(Message)} methods are the point of
 * entry into the channel. Each corresponds to the handler for one of the messaging queues
 * classified by {@link org.integratedmodelling.klab.api.services.runtime.Message.Queue}. They can
 * be called explicitly from the API or be called in response to a message sent through {@link
 * #send(Object...)}, either on the channel itself or on a channel that is paired to this through
 * the messaging system. Channels instrumented for messaging (implementing MessagingChannel) may
 * send to one or more other channels in addition to their own handlers. The paired channels will
 * receive the messages through their respective handlers, packed as needed, only on the queues that
 * they have subscribed to. Others just send the messages to their own handlers according to the
 * {@link org.integratedmodelling.klab.api.services.runtime.Message.Queue} associated with the
 * {@link Message} sent unless the channel has unsubscribed. The default queues are specified by the
 * interface.
 *
 * <p>An important function of the monitor is to obtain the current identity that owns the
 * computation. This is done through {@link #getIdentity()}. From that, any other identity (such as
 * the network session, the engine etc., up to the node and partner that owns the engine) can be
 * obtained.
 *
 * <p>
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface Channel {

  String LOCAL_BROKER_URL = "amqp://127.0.0.1:";
  int LOCAL_BROKER_PORT = 20937;

  default Set<Message.Queue> defaultQueues() {
    return EnumSet.of(Message.Queue.Info);
  }

  /**
   * All channels (and their children, the {@link org.integratedmodelling.klab.api.scope.Scope}s)
   * are owned by an Identity, which gives access to parent identities through its methods. In
   * remote scopes, the access token associated with request must allow maintenance of the identity
   * hierarchy representing the identity and its permissions in the requesting scope.
   *
   * @return
   */
  Identity getIdentity();

  /**
   * The dispatch ID is the identifier of the channel that gets into messages sent through the
   * messaging system. It is used to set the {@link Message#getDispatchId()} field, used to dispatch
   * messages to their respective channels. Channels will filter all messages and only keep those
   * that have their same ID.
   *
   * @return
   */
  String getDispatchId();

  /**
   * For info to be seen by users: pass a string. Will also take an exception, but usually
   * exceptions shouldn't turn into warnings. These will be reported to the user unless the
   * verbosity is set low. Do not abuse of these - there should be only few, really necessary info
   * messages so that things do not get lost.
   *
   * <p>In addition to the main object, you can pass a string that will be interpreted as the info
   * message class. The class parameter is used by the client to categorize messages so they can be
   * shown in special ways and easily identified in a list of info messages. Other objects can also
   * be sent along with the message, according to implementation.
   *
   * @param info
   */
  void info(Object... info);

  /**
   * Pass a string. Will also take an exception, but usually exceptions shouldn't turn into
   * warnings. These will be reported to the user unless the verbosity is set lowest.
   *
   * @param o a {@link java.lang.Object} object.
   */
  void warn(Object... o);

  /**
   * Pass a string or an exception (usually the latter as a reaction to an exception in the
   * execution). These will interrupt execution from outside, so you should return after raising one
   * of these.
   *
   * <p>In addition, you can pass a statement to communicate errors in k.IM, or other objects that
   * can be sent and used as necessary.
   *
   * @param o a {@link java.lang.Object} object.
   */
  void error(Object... o);

  /**
   * Any message that is just for you or is too verbose to be an info message should be sent as
   * debug, which is not shown by default unless you enable a higher verbosity. Don't abuse of these
   * - it's never cheap or good to show hundreds of messages even when testing.
   *
   * @param o a {@link java.lang.Object} object.
   */
  void debug(Object... o);

  //  /**
  //   * Send
  //   * @param status
  //   */
  //  void status(Scope.Status status);

  void event(Message message);

  void ui(Message message);

  /**
   * Registers a consumer to handle messages from a specific message queue. The consumer will be
   * invoked whenever a message is received from the specified queue.
   *
   * @param consumer the {@link BiConsumer} that will process messages. The first parameter is the
   *     {@link Channel} that received the message, and the second parameter is the {@link Message}.
   * @param queues the {@link Message.Queue} from which messages will be consumed.
   * @return a consumer ID that can be used to unregister the consumer.
   */
  String onMessage(BiConsumer<Channel, Message> consumer, Message.Queue... queues);

  //  /**
  //   * Install a consumer to specific messages getting through the event queue.
  //   *
  //   * TODO check if we need this in the API or we should filter in other ways. This is probably
  //   *  overkill here.
  //   *
  //   * @param messageClass mandatory message class
  //   * @param messageType mandatory message type
  //   * @param runnable code to invoke on match
  //   * @param matchArguments optional match arguments, including a {@link
  //   *     org.integratedmodelling.klab.api.scope.Persistence} value to define what to do after
  // match
  //   *     (default is {@link org.integratedmodelling.klab.api.scope.Persistence#ONE_OFF}, i.e.
  // the
  //   *     handler disappears after matching), or any {@link
  // java.util.function.Predicate<Message>} to
  //   *     apply to the message, or any other Object that will be matched to the payload using
  //   *     equals(). If objects are passed, all the messages that use that object as a match and
  // have
  //   *     ONE_OFF as persistence will be removed after one of them has matched.
  //   * @return a consumer ID that can be used to unregister the consumer.
  //   */
  //  String onEvent(
  //      MessageClass messageClass,
  //      Message.MessageType messageType,
  //      Consumer<Message> runnable,
  //      Object... matchArguments);

  /**
   * Unregister a consumer that was previously registered with {@link #onMessage(BiConsumer,
   * Message.Queue...)}.
   *
   * @param listenerId
   */
  void unregisterMessageListener(String listenerId);

  /**
   * This is to send out serializable objects or other messages through any messaging channel
   * registered with the runtime. Information sent through this channel will only be received by
   * receivers that have subscribed. The messages are signed with the monitor's {@link
   * #getIdentity() identity string}. If the channel is a channel to an agent, this should
   * automatically dispatch any objects of {@link VM.AgentMessage} class to the agent reference
   * embedded in the scope.
   *
   * @param message anything that may be sent as a message: either a preconstructed {@link Message}
   *     or the necessary info to build one, including a {@link MessageClass} and {@IMessage.Type}
   *     along with any payload (any serializable object). Sending a {@link Notification} should
   *     automatically promote it to a suitable logging message and enforce any logging level
   *     filtering configured.
   * @return the completed message that was sent, for reference, or null if sending failed
   */
  Message send(Object... message);

  void close();

  void interrupt();

  /**
   * Check if the monitored identity has been interrupted by a client action. Applies to any task,
   * such as an observation task, application, test case or script. In other identities it will
   * always return false.
   *
   * @return true if interrupted
   */
  boolean isInterrupted();

  /**
   * Tells us that errors have happened in the context we're monitoring.
   *
   * @return true if errors have happened in this context of monitoring.
   */
  boolean hasErrors();
}
