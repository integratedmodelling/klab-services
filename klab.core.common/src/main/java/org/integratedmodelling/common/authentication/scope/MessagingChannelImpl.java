package org.integratedmodelling.common.authentication.scope;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Hosts the AMQP connections and channels for all the subscribed queues. In service use, creates the queues
 * and advertises them. In client configurations, consumes the queues and dispatches the messages to their
 * respective handlers. Calling {@link #post(Consumer, Object...)} will react appropriately when the message
 * receive a response,
 */
public class MessagingChannelImpl extends ChannelImpl implements MessagingChannel {

    private Channel channel_;
    private boolean sender;
    private boolean receiver;
    ConnectionFactory connectionFactory = null;
    Connection connection = null;
    Map<Message.Queue, String> queueNames = new HashMap<>();

    public MessagingChannelImpl(Identity identity, boolean isSender, boolean isReceiver) {
        super(identity);
        this.sender = isSender;
        this.receiver = isReceiver;
    }

    @Override
    public Message send(Object... args) {
        if (this.sender) {
            // dispatch to queue if the queue is there
            var message = Message.create(this, args);
            var queue = queueNames.get(message.getQueue());
            if (queue != null) {
                try {
                    getChannel(message.getQueue()).basicPublish("", queue, null,
                            Utils.Json.asString(message).getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    error(e);
                }
            }
        }
        // this will dispatch to the local handlers
        return super.send(args);
    }

    private Channel getChannel(Message.Queue queue) {

        /*
        in this implementation it looks like we can do with just one channel
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

        if (this instanceof Scope scope && scope.getParentScope() instanceof MessagingChannelImpl parent) {
            return parent.findParent(filter);
        }

        return null;
    }

    /**
     * Called on the intended target, should use the local connection fields. This is normally only called on
     * the session scope.
     *
     * @param brokerUrl
     * @param queuesHeader
     * @return
     */
    public Collection<Message.Queue> setupMessaging(String brokerUrl, String scopeId,
                                                    Collection<Message.Queue> queuesHeader) {

        if (brokerUrl == null) {
            return EnumSet.noneOf(Message.Queue.class);
        }

        try {
            this.connectionFactory = new ConnectionFactory();
            this.connectionFactory.setUri(brokerUrl);
            this.connection = this.connectionFactory.newConnection();
            return setupMessagingQueues(scopeId, queuesHeader);
        } catch (Throwable t) {
            return EnumSet.noneOf(Message.Queue.class);
        }
    }

    /**
     * Called on anything that has a broker connection either locally or in one of the parents.
     *
     * @param queuesHeader
     * @return
     */
    public Collection<Message.Queue> setupMessagingQueues(String scopeId,
                                                          Collection<Message.Queue> queuesHeader) {

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
                    getChannel(queue).queueDeclare(queueId, true, false, false, Map.of());
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
                        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                            var message = Utils.Json.parseObject(new String(delivery.getBody(),
                                    StandardCharsets.UTF_8), Message.class);
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
                                case None -> {
                                }
                            }
                        };
                        getChannel(queue).basicConsume(queueId, true, deliverCallback, consumerTag -> {
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

            return ret;
        }

        return EnumSet.noneOf(Message.Queue.class);
    }

    @Override
    public boolean hasMessaging() {
        return connection != null && connection.isOpen();
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
    }

    public boolean isSender() {
        return sender;
    }

    public boolean isReceiver() {
        return receiver;
    }
}
