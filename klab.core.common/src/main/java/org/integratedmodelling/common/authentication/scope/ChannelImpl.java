package org.integratedmodelling.common.authentication.scope;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Basic listenable, logging channel not instrumented for messaging. Meant to be use as an on-off scope when
 * one is required or as a parent for {@link org.integratedmodelling.klab.api.scope.ServiceScope}. Calls to
 * {@link #send(Object...)} and related methods have no effect besides invoking the scope's own handlers and
 * listeners.
 * <p>
 * Listeners can be added at any time and only get invoked by send(), never by the handlers when directly
 * called.
 */
public class ChannelImpl implements Channel {

    static class EventMatcher {
        public Message.MessageClass messageClass;
        public Message.MessageType messageType;
        public Object payloadPrototype;
        public Predicate<Message> payloadChecker;
        public Consumer<Message> reaction;
        public Persistence persistence = Persistence.ONE_OFF;
    }

    private final Identity identity;
    private AtomicBoolean interrupted = new AtomicBoolean(false);
    private final AtomicBoolean errors = new AtomicBoolean(false);
    private final List<BiConsumer<Channel, Message>> listeners =
            Collections.synchronizedList(new ArrayList<>());
    private final Multimap<Pair<Message.MessageClass, Message.MessageType>, EventMatcher> eventMatchers;

    public ChannelImpl(Identity identity) {
        this.identity = identity;
        this.eventMatchers =  ArrayListMultimap.create();
    }

    protected ChannelImpl(ChannelImpl other) {
        this.identity = other.identity;
        this.interrupted = other.interrupted;
        this.errors.set(other.errors.get());
        this.listeners.addAll(other.listeners);
        this.eventMatchers = other.eventMatchers;
    }

    @Override
    public Identity getIdentity() {
        return this.identity;
    }

    @Override
    public void info(Object... info) {
        Logging.INSTANCE.info(info);
    }

    @Override
    public void warn(Object... o) {
        Logging.INSTANCE.warn(o);
    }

    @Override
    public void error(Object... o) {
        errors.set(true);
        Logging.INSTANCE.error(o);
    }

    @Override
    public void debug(Object... o) {
        Logging.INSTANCE.debug(o);
    }

    @Override
    public void status(Scope.Status status) {

    }

    @Override
    public void event(Message message) {
        var key = Pair.of(message.getMessageClass(), message.getMessageType());
        var matches = new ArrayList<>(eventMatchers.get(key));
        for (var matcher : matches) {
            handleMatch(key, matcher, message);
        }
    }

    private void handleMatch(Pair<Message.MessageClass, Message.MessageType> key, EventMatcher matcher,
                             Message message) {

        // Quickly check if match is real
        boolean match = false;

        if (matcher.payloadChecker != null) {
            match = matcher.payloadChecker.test(message);
        } else if (matcher.payloadPrototype != null) {
            match = matcher.payloadPrototype.equals(message.getPayload(Object.class));
        }

        if (match) {
            matcher.reaction.accept(message);
            if (matcher.persistence == Persistence.ONE_OFF && matcher.payloadPrototype != null) {
                List<EventMatcher> toRemove = new ArrayList<>();
                for (var m : eventMatchers.values()) {
                    if (m.payloadPrototype != null && m.payloadPrototype.equals(message.getPayload(Object.class))) {
                        toRemove.add(m);
                    }
                }
                for (var m : toRemove) {
                    eventMatchers.remove(Pair.of(m.messageClass, m.messageType), m);
                }
            } else {
                eventMatchers.remove(key, matcher);
            }
        }
    }

    @Override
    public void ui(Message message) {

    }

    @Override
    public Channel onEvent(Message.MessageClass messageClass, Message.MessageType messageType,
                           Consumer<Message> runnable, Object... matchArguments) {

        var matcher = new EventMatcher();
        matcher.messageClass = messageClass;
        matcher.messageType = messageType;
        matcher.reaction = runnable;

        for (var arg : matchArguments) {
            if (arg instanceof Predicate<?>) {
                matcher.payloadChecker = (Predicate<Message>) arg;
            } else if (arg instanceof Persistence persistence) {
                matcher.persistence = persistence;
            } else {
                if (matcher.payloadPrototype != null) {
                    error("Internal: installing multiple payload prototypes for matching, overriding each other (" + arg + ")");
                }
                matcher.payloadPrototype = arg;
            }
        }

        eventMatchers.put(Pair.of(messageClass, messageType), matcher);

        return this;
    }

    @Override
    public Message send(Object... args) {

        var message = Message.create(this, args);
        switch (message.getQueue()) {
            case Events -> {
                this.event(message);
            }
            case Errors -> {
                error(message.getPayload(Notification.class));
            }
            case Warnings -> {
                warn(message.getPayload(Notification.class));
            }
            case Info -> {
                info(message.getPayload(Notification.class));
            }
            case Debug -> {
                debug(message.getPayload(Notification.class));
            }
            case Clock -> {
            }
            case Status -> {
            }
            case UI -> {
                this.ui(message);
            }
            case None -> {
            }
        }
        for (var listener : listeners) {
            listener.accept(this, message);
        }
        return message;
    }

    @Override
    public void close() {
        // TODO signal
    }

    @Override
    public void interrupt() {
        this.interrupted.set(true);
    }

    @Override
    public boolean isInterrupted() {
        return interrupted.get();
    }

    @Override
    public boolean hasErrors() {
        return errors.get();
    }

    public List<BiConsumer<Channel, Message>> listeners() {
        return this.listeners;
    }

    public void addListener(BiConsumer<Channel, Message> listener) {
        this.listeners.add(listener);
    }

}
