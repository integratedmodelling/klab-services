package org.integratedmodelling.common.authentication.scope;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.ReactiveScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Hosts the AMQP connections and channels for all the subscribed queues. In service use, creates the queues
 * and advertises them. In client configurations, consumes the queues and dispatches the messages to their
 * respective handlers.
 */
public class MessagingChannelImpl extends ChannelImpl implements MessagingChannel {

    private Channel channel_;
    private boolean sender;
    private boolean receiver;
    private ConnectionFactory connectionFactory = null;
    private Connection connection = null;
    private Map<Message.Queue, String> queueNames = new HashMap<>();
    private Set<EventResultSupplier<?, ?>> eventResultSupplierSet =
            Collections.synchronizedSet(new LinkedHashSet<>());
    private Map<String, List<Consumer<Message>>> queueConsumers = new HashMap<>();

    public MessagingChannelImpl(Identity identity, boolean isSender, boolean isReceiver) {
        super(identity);
        this.sender = isSender;
        this.receiver = isReceiver;
    }

    /**
     * Convenience Task implementation that delegates to a {@link CompletableFuture} while exposing the
     * tracking key all along.
     *
     * @param <P>
     * @param <T>
     */
    class TrackingTask<P, T> implements ReactiveScope.Task<P, T> {

        private final T value;
        private final CompletableFuture<P> delegate;

        public TrackingTask(Set<Message.MessageType> matchTypes, T value, Function<T, P> payloadConverter) {
            this.value = value;
            delegate = CompletableFuture.supplyAsync(new EventResultSupplier<>(matchTypes, value,
                    payloadConverter));
        }

        @Override
        public T trackingKey() {
            return value;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return delegate.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        @Override
        public boolean isDone() {
            return delegate.isDone();
        }

        @Override
        public P get() throws InterruptedException, ExecutionException {
            return (P) delegate.get();
        }

        @Override
        public P get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
                TimeoutException {
            return (P) delegate.get(timeout, unit);
        }
    }

    /**
     * Blocking supplier that waits for an event match, then returns the event payload. Can be used in a
     * {@link java.util.concurrent.CompletableFuture#supplyAsync(Supplier)} to wait for an event.
     *
     * @param <T>
     */
    private class EventResultSupplier<P, T> implements Supplier<P> {

        private final AtomicReference<Message> match = new AtomicReference<>();
        private final Function<T, P> converter;
        Set<Message.MessageType> matchTypes;
        T value;

        EventResultSupplier(Set<Message.MessageType> matchTypes, T value, Function<T, P> payloadConverter) {
            this.matchTypes = matchTypes;
            this.value = value;
            this.converter = payloadConverter;
        }

        public boolean match(Message message) {
            if (matchTypes != null && matchTypes.contains(message.getMessageType())) {
                if (value != null && value.equals(message.getPayload(Object.class))) {
                    match.set(message);
                    match.notify();
                    return true;
                }
            }
            return false;
        }

        @Override
        public P get() {

            synchronized (match) {
                while (match.get() == null) {
                    try {
                        match.wait();
                    } catch (InterruptedException e) {
                        return null;
                    }
                }
            }
            eventResultSupplierSet.remove(this);
            return converter.apply((T) match.get().getPayload(Object.class));
        }
    }

    /**
     * Return a future that exposes the tracking ID and produces the payload when the event message matches.
     *
     * @param matchTypes
     * @param value
     * @param payloadConverter this could be skipped and just use .thenApply on the enclosing future
     * @param <P>
     * @param <T>
     * @return
     */
    protected <P, T> ContextScope.Task<P, T> newMessageTrackingTask(Set<Message.MessageType> matchTypes,
                                                                    T value,
                                                                    Function<T, P> payloadConverter) {
        var ret = new EventResultSupplier<>(matchTypes, value, payloadConverter);
        eventResultSupplierSet.add(ret);
        return new TrackingTask<>(matchTypes, value, payloadConverter);
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

    public void installQueueConsumer(String queueId, Consumer<Message> consumer) {

    }

    protected Channel getOrCreateChannel(Message.Queue queue) {

        //        if (!queueNames.containsKey(queue)) {

        /*
        in this implementation it looks like we can do with just one channel - so one connection factory, one
        connection, one channel. Maybe the whole API could be simpler. Maybe channels are synchronizing? In
         all cases
        we now can have a queue name w/o a channel so we would need to keep a hash of channels and dispose
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
        //        }

        //        return null;
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

        if (this instanceof Scope scope && scope.getParentScope() instanceof MessagingChannelImpl parent) {
            return parent.findParent(filter);
        }

        return null;
    }

    @Override
    public void event(Message message) {
        Set<EventResultSupplier<?, ?>> done = new HashSet<>();
        for (var supplier : eventResultSupplierSet) {
            if (supplier.match(message)) {
                done.add(supplier);
            }
        }
        eventResultSupplierSet.removeAll(done);
        super.event(message);
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
                    getOrCreateChannel(queue).queueDeclare(queueId, true, false, false, Map.of());
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

                            // if there is a consumer installed fo this queue, run it. Then if it returns
                            //  continue, continue, else stop
                            var consumers = queueConsumers.get(queueId);
                            if (consumers != null) {
                                for (var consumer : consumers) {
                                    consumer.accept(message);
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

    /**
     * Only called in advance of setupMessaging() when setup happens in {@link #getChannel(Message.Queue)} and
     * can only be called after scope creation, which is in local services when the user must receive
     * notifications. TODO check if we can make this simpler.
     *
     * @param queue
     * @param s
     */
    public void presetMessagingQueue(Message.Queue queue, String s) {
        queueNames.put(queue, s + "." + queue.name().toLowerCase());
    }


    public boolean isSender() {
        return sender;
    }

    public boolean isReceiver() {
        return receiver;
    }
}
