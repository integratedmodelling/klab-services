package org.integratedmodelling.common.authentication.scope;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;

import java.net.URI;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Hosts the AMQP connections and channels for all the subscribed queues. In service use, creates the queues
 * and advertises them. In client configurations, consumes the queues and dispatches the messages to their
 * respective handlers. Calling {@link #post(Consumer, Object...)} will react appropriately when the message
 * receive a response,
 */
public class MessagingChannelImpl extends ChannelImpl implements MessagingChannel {

    public MessagingChannelImpl(Identity identity) {
        super(identity);
        // TODO ensure we have a channel and a connection (depending on what we are it could be in our
        //  parent channel, which we only access if we are a scope)
        // TODO install the consumer that will call send() for each incoming message
        // TODO declare all queues we subscribe to; set their IDs in a map indexed by queue type
    }

    ConnectionFactory connectionFactory = null;
    Connection connection = null;
    Map<Message.Queue, String> queueNames = new HashMap<>();

    @Override
    public Message send(Object... args) {
        var message = Message.create(this, args);
        // dispatch to queue if the queue is there
        if (subscriptions.contains(message.getQueue())) {
            var channel = getChannel(message.getQueue());
            if (channel != null) {
                switch (message.getQueue()) {
                    case Events -> {
                    }
                    case Errors -> {
                    }
                    case Warnings -> {
                    }
                    case Info -> {
                    }
                    case Debug -> {
                    }
                    case Clock -> {
                    }
                    case Status -> {
                    }
                    case UI -> {
                    }
                    case None -> {
                    }
                }
            }
        }
        // this will dispatch to the local handlers
        return super.send(args);
    }

    private Channel getChannel(Message.Queue queue) {



        return null;
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

    public static void main(String[] strings) throws Exception {
        // just test the messaging
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri("amqp://localhost:20937");
        //        factory.useSslProtocol();

        var connection = factory.newConnection();
        //get a channel for sending the "kickoff" message
        var channel = connection.createChannel();
    }

    /**
     * Called on the intended target, should use the local connection fields.
     *
     * @param brokerUrl
     * @param queuesHeader
     * @return
     */
    public Collection<Message.Queue> setupMessaging(String brokerUrl, Collection<Message.Queue> queuesHeader) {
        return EnumSet.noneOf(Message.Queue.class);
    }

    /**
     * Called on anything that has a broker connection either locally or in one of the parents.
     * @param queuesHeader
     * @return
     */
    public Collection<Message.Queue> setupMessagingQueues(Collection<Message.Queue> queuesHeader) {
        return EnumSet.noneOf(Message.Queue.class);
    }

    @Override
    public boolean hasMessaging() {
        return connection != null && connection.isOpen();
    }
}
