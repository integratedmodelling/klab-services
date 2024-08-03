package org.integratedmodelling.common.authentication.scope;

import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Basic listenable, logging channel not instrumented for messaging. Meant to be use as an on-off scope when
 * one is required or as a parent for {@link org.integratedmodelling.klab.api.scope.ServiceScope}. Calls to
 * {@link #post(Consumer, Object...)} and {@link #send(Object...)} have no effect besides invoking the scope's
 * own handlers and listeners. Because messaging is not enabled, calling {@link #post(Consumer, Object...)}
 * will not produce a response.
 * <p>
 * Listeners can be added at any time and only get invoked by send() and post(), never by the handlers when
 * directly called.
 */
public class ChannelImpl implements Channel {

    Identity identity;
    AtomicBoolean interrupted = new AtomicBoolean(false);
    AtomicBoolean errors = new AtomicBoolean(false);
    List<BiConsumer<Channel, Message>> listeners = Collections.synchronizedList(new ArrayList<>());
    List<BiFunction<Message, Identity, Message>> functors = Collections.synchronizedList(new ArrayList<>());
    Set<Message.Queue> subscriptions = EnumSet.noneOf(Message.Queue.class);

    public ChannelImpl(Identity identity) {
        this.identity = identity;
        this.subscriptions.addAll(defaultQueues());
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

    }

    @Override
    public void ui(Message message) {

    }

    @Override
    public void subscribe(Message.Queue... queues) {

    }

    @Override
    public void unsubscribe(Message.Queue... queues) {

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
    public Message post(Consumer<Message> handler, Object... message) {
        var me = Message.create(this, message);
        for (var listener : listeners) {
            listener.accept(this, me);
        }
        return me;
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

    @Override
    public void close() {
        // TODO
    }
}
