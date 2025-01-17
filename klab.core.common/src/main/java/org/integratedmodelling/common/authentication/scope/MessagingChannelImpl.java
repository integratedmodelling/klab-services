package org.integratedmodelling.common.authentication.scope;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A channel instrumented for messaging, containing the AMQP connections and channels for all the
 * subscribed queues. In service use, creates the queues and advertises them. In client
 * configurations, consumes the queues and dispatches the messages to their respective handlers. Can
 * be configured as a sender, receiver or both.
 */
public abstract class MessagingChannelImpl extends ChannelImpl implements MessagingChannel {

  private Channel channel_;
  private boolean sender;
  private boolean receiver;
  private ConnectionFactory connectionFactory = null;
  private Connection connection = null;
  private final Map<Message.Queue, String> queueNames = new HashMap<>();
  private boolean connected;

  private static final Map<String, Map<String, List<Consumer<Message>>>> queueConsumers =
      new HashMap<>();
  private static final Map<String, Map<Message.Match, MessageFuture<?>>> messageFutures =
      Collections.synchronizedMap(new HashMap<>());
  private static final Map<String, Set<Message.Match>> messageMatchers =
      Collections.synchronizedMap(new HashMap<>());

  public abstract String getId();

  public MessagingChannelImpl(Identity identity, boolean isSender, boolean isReceiver) {
    super(identity);
    this.sender = isSender;
    this.receiver = isReceiver;
  }

  protected MessagingChannelImpl(MessagingChannelImpl other) {
    super(other);
    copyMessagingSetup(other);
  }

  protected void copyMessagingSetup(MessagingChannelImpl parent) {
    this.channel_ = parent.channel_;
    this.sender = parent.sender;
    this.receiver = parent.receiver;
    this.connectionFactory = parent.connectionFactory;
    this.connection = parent.connection;
    this.connected = parent.connected;
    this.queueNames.putAll(parent.queueNames);
    copyListeners(parent);
  }

  @Override
  public Message send(Object... args) {
    if (this.sender) {
      // dispatch to queue if the queue is there
      var message = Message.create(this, args);
      var queue = queueNames.get(message.getQueue());
      if (queue != null) {
        try {
          getChannel(message.getQueue())
              .basicPublish(
                  "", queue, null, Utils.Json.asString(message).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
          error(e);
        }
      }
    }
    // this will dispatch to the local handlers
    return super.send(args);
  }

  public void installQueueConsumer(
      String scopeId, Message.Queue queue, Consumer<Message> consumer) {
    queueConsumers
        .computeIfAbsent(scopeId, k -> new HashMap<>())
        .computeIfAbsent(
            scopeId + "." + queue.name().toLowerCase(), q -> new ArrayList<Consumer<Message>>())
        .add(consumer);
  }

  public void installQueueConsumer(String queueId, Consumer<Message> consumer) {
    queueConsumers
        .computeIfAbsent(getId(), k -> new HashMap<>())
        .computeIfAbsent(queueId, q -> new ArrayList<Consumer<Message>>())
        .add(consumer);
  }

  protected void closeMessaging() {
    if (this.channel_ != null) {
      try {
        for (var queue : queueNames.values()) {
          this.channel_.queueDelete(queue);
        }
        queueNames.clear();
      } catch (Exception e) {
        error("Error closing messaging channel", e);
      }
    }
  }

  protected Channel getOrCreateChannel(Message.Queue queue) {

    //        if (!queueNames.containsKey(queue)) {

    /*
    Looks like just one channel is enough - so one connection factory, one
    connection, one channel. Maybe the whole API could be simpler. Maybe channels are synchronizing? In
     all cases we now can have a queue name w/o a channel so we would need to keep a hash of channels
     and dispose
    properly.
     */
    if (this.channel_ == null) {
      var holder = findParent((p) -> p.channel_ != null);
      if (holder != null) {
        return holder.channel_;
      }
      try {
        this.channel_ = this.connection.createChannel();
      } catch (IOException e) {
        // just return null
      }
    }

    return this.channel_;
  }

  protected Channel getChannel(Message.Queue queue) {
    return getOrCreateChannel(queue);
  }

  /*
   * Channels are not hierarchically organized but most of their derivatives are. If this is a scope,
   * this methods finds the first parent that meets the passed condition.
   *
   * @param filter
   * @return
   */
  private MessagingChannelImpl findParent(Predicate<MessagingChannelImpl> filter) {

    if (filter.test(this)) {
      return this;
    }

    if (this instanceof Scope scope
        && scope.getParentScope() instanceof MessagingChannelImpl parent) {
      return parent.findParent(filter);
    }

    return null;
  }

  /**
   * Called on the intended target, should use the local connection fields. This is normally only
   * called on scopes below the user scope.
   *
   * @param brokerUrl
   * @param queuesHeader
   * @return
   */
  public Collection<Message.Queue> setupMessaging(
      String brokerUrl, String scopeId, Collection<Message.Queue> queuesHeader) {

    if (brokerUrl == null) {
      return EnumSet.noneOf(Message.Queue.class);
    }

    try {
      this.connectionFactory = new ConnectionFactory();
      this.connectionFactory.setUri(brokerUrl);
      this.connection = this.connectionFactory.newConnection();
      return setupMessagingQueues(scopeId, queuesHeader);
    } catch (Throwable t) {
      error("Error connecting to broker: no messaging available", t);
      return EnumSet.noneOf(Message.Queue.class);
    }
  }

  /**
   * Called on anything that has a broker connection either locally or in one of the parents.
   *
   * @param queuesHeader
   * @return
   */
  public Collection<Message.Queue> setupMessagingQueues(
      String scopeId, Collection<Message.Queue> queuesHeader) {

    Connection conn = this.connection;
    if (conn == null) {
      var holder = findParent((s) -> s.connection != null);
      if (holder != null) {
        conn = holder.connection;
      }
    }

    Set<Message.Queue> ret = EnumSet.noneOf(Message.Queue.class);
    if (conn != null) {
      for (var queue : queuesHeader) {
        try {
          String queueId = scopeId + "." + queue.name().toLowerCase();
          getOrCreateChannel(queue)
              .queueDeclare(queueId, true, false, true /* TODO LINK TO SCOPE
                     PERSISTENCE */, Map.of());
          this.queueNames.put(queue, queueId);
          ret.add(queue);
        } catch (Throwable e) {
          // just don't add the queue, THis includes channel_ == null.
        }
      }

      if (this.receiver) {
        Set<Message.Queue> toRemove = EnumSet.noneOf(Message.Queue.class);
        // setup consumers. The service-side scopes only receive messages from paired DTs.
        for (var queue : ret) {
          String queueId = scopeId + "." + queue.name().toLowerCase();
          try {

            DeliverCallback deliverCallback =
                (consumerTag, delivery) -> {
                  var message =
                      Utils.Json.parseObject(
                          new String(delivery.getBody(), StandardCharsets.UTF_8), Message.class);

//                  System.out.println("ZIO PERA " + message);

                  // if there is a consumer installed for this queue, run it
                  var consumers = queueConsumers.get(scopeId);
                  if (consumers != null && consumers.containsKey(queueId)) {
                    for (var consumer : consumers.get(queueId)) {
                      consumer.accept(message);
                      // TODO the consumer may call reply() on the message and if that was done,
                      //  we could reply with the message ID as long as the channel is also a
                      //  sender.
                      //  reply() would take all the parameters of Message.create() and would
                      //  automatically
                      //  install the requesting message ID.
                    }
                  }

                  // TODO skip this and put the ID in MessagingScope
                  if (this instanceof SessionScope scope) {
                    var id = scope.getId();
                    var mMatchers = messageMatchers.get(id);
                    var mFutures = messageFutures.get(id);

                    if (mMatchers != null) {
                      List<Message.Match> remove = new ArrayList<>();
                      for (var match : mMatchers) {
                        if (matchApplies(match, message)) {
                          if (match.getMessageConsumer() != null) {
                            // TODO put this in a virtual thread?
                            match.getMessageConsumer().accept(message);
                          }
                          if (!match.isPersistent()) {
                            remove.add(match);
                          }
                        }
                      }
                      remove.forEach(mMatchers::remove);
                    }

                    if (mFutures != null) {
                      List<Message.Match> remove = new ArrayList<>();
                      for (var match : mFutures.keySet()) {
                        if (matchApplies(match, message)) {
                          if (match.getMessageConsumer() != null) {
                            // TODO put this in a virtual thread?
                            match.getMessageConsumer().accept(message);
                          }
                          mFutures.get(match).resolve(message);
                          remove.add(match);
                        }
                      }
                      remove.forEach(mFutures::remove);
                    }
                  }

                  switch (queue) {
                    case Events -> {
                      event(message);
                    }
                    case Errors -> {
                      error(message);
                    }
                    case Warnings -> {
                      warn(message);
                    }
                    case Info -> {
                      info(message);
                    }
                    case Debug -> {
                      debug(message);
                    }
                    case Clock -> {
                      // TODO
                    }
                    case Status -> {
                      // TODO
                    }
                    case UI -> {
                      ui(message);
                    }
                    case None -> {}
                  }
                };
            getChannel(queue)
                .basicConsume(
                    queueId,
                    true,
                    deliverCallback,
                    consumerTag -> {
                      queueNames.remove(queue);
                    });
          } catch (IOException e) {
            this.queueNames.remove(queue);
            error(e);
            toRemove.add(queue);
          }
        }

        if (!toRemove.isEmpty()) {
          ret.removeAll(toRemove);
        }
      }

      if (connectionFactory != null) {
        info(
            this.getClass().getCanonicalName()
                + " scope connected to queues "
                + ret
                + " through broker "
                + connectionFactory.getHost()
                + (receiver ? " (R)" : "")
                + (sender ? " (T)" : ""));

        if (!ret.isEmpty()) {
          configureQueueConsumers(ret);
        }
      }

      return ret;
    }

    return EnumSet.noneOf(Message.Queue.class);
  }

  private boolean matchApplies(Message.Match match, Message message) {

    if (!match.getApplicableClasses().isEmpty()) {
      if (!match.getApplicableClasses().contains(message.getMessageClass())) {
        return false;
      }
    }
    if (!match.getApplicableTypes().isEmpty()) {
      if (!match.getApplicableTypes().contains(message.getMessageType())) {
        return false;
      }
    }

    if (match.getMessagePredicate() != null) {
      if (!match.getMessagePredicate().test(message)) {
        return false;
      }
    }

    if (match.getPayloadMatch() != null) {
      if (!match.getPayloadMatch().equals(message.getPayload(Object.class))) {
        return false;
      }
    }

    return true;
  }

  @Override
  public boolean hasMessaging() {
    return connection != null && connection.isOpen();
  }

  /**
   * Do nothing implementation, override to install consumers when needed.
   *
   * @param availableQueues
   */
  protected void configureQueueConsumers(Set<Message.Queue> availableQueues) {}

  @Override
  public void connectToService(
      KlabService.ServiceCapabilities capabilities,
      UserIdentity identity,
      Consumer<Message> consumer) {

    this.connected = true;

    if (capabilities.getAvailableMessagingQueues().isEmpty()
        || capabilities.getBrokerURI() == null) {
      return;
    }
    setupMessaging(
        capabilities.getBrokerURI().toString(),
        capabilities.getType().name().toLowerCase() + "." + identity.getUsername(),
        capabilities.getAvailableMessagingQueues());

    for (var queue : capabilities.getAvailableMessagingQueues()) {
      installQueueConsumer(
          capabilities.getType().name().toLowerCase()
              + "."
              + identity.getUsername()
              + "."
              + queue.name().toLowerCase(),
          consumer);
    }
  }

  public void trackMessages(Message.Match... matchers) {
    if (this instanceof SessionScope scope && matchers != null) {
      for (var matcher : matchers) {
        messageMatchers
            .computeIfAbsent(scope.getId(), s -> Collections.synchronizedSet(new HashSet<>()))
            .add(matcher);
      }
    }
    // TODO skip this and put the ID in MessagingScope
    throw new KlabInternalErrorException("trackMessages called on unexpected object");
  }

  @Override
  public <T> CompletableFuture<T> trackMessages(
      Message.Match match, Function<Message, T> supplier) {
    if (this instanceof SessionScope scope) {
      var ret = new MessageFuture<T>(match, supplier, scope.getId());
      messageFutures
          .computeIfAbsent(scope.getId(), s -> Collections.synchronizedMap(new HashMap<>()))
          .put(match, ret);
      return ret;
    }
    // TODO skip this and put the ID in MessagingScope
    throw new KlabInternalErrorException("trackMessages called on unexpected object");
  }

  @Override
  public void close() {
    if (this.connection != null) {
      try {
        this.connection.close();
      } catch (Throwable t) {
        this.connection = null;
      }
    }
    // TODO skip this and put the ID in MessagingScope
    if (this instanceof SessionScope scope) {
      messageFutures.remove(scope.getId());
      messageMatchers.remove(scope.getId());
      queueConsumers.remove(scope.getId());
    }
  }

  @Override
  public boolean isConnected() {
    return connected;
  }

  /**
   * Only called in advance of setupMessaging() when setup happens in {@link
   * #getChannel(Message.Queue)} and can only be called after scope creation, which is in local
   * services when the user must receive notifications. TODO check if we can make this simpler.
   *
   * @param queue
   * @param s
   */
  public void presetMessagingQueue(Message.Queue queue, String s) {
    queueNames.put(queue, s + "." + queue.name().toLowerCase());
  }

  @Override
  public boolean isSender() {
    return sender;
  }

  @Override
  public boolean isReceiver() {
    return receiver;
  }

  private static class MessageFuture<T> extends CompletableFuture<T> {

    private AtomicReference<T> payload = new AtomicReference<>();
    private AtomicBoolean resolved = new AtomicBoolean(false);
    private boolean cancelled;
    private final Message.Match match;
    private final Function<Message, T> supplier;
    private String scopeId;

    public MessageFuture(Message.Match match, Function<Message, T> supplier, String scopeId) {
      this.match = match;
      this.supplier = supplier;
      this.scopeId = scopeId;
    }

    public void resolve(Message message) {
      this.resolved.set(true);
      this.payload.set(supplier.apply(message));
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      this.cancelled = true;
      return messageFutures.get(scopeId).remove(match) != null;
    }

    @Override
    public boolean isCancelled() {
      return this.cancelled;
    }

    @Override
    public boolean isDone() {
      return this.resolved.get();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
      while (!this.resolved.get()) {
        Thread.sleep(200);
      }
      return payload.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      long mss = System.currentTimeMillis() + unit.toMillis(timeout);
      while (!this.resolved.get()) {
        Thread.sleep(200);
        if (System.currentTimeMillis() > mss) {
          break;
        }
      }
      return payload.get();
    }
  }
}
