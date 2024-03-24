package org.integratedmodelling.common.authentication.scope;

import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Basic listenable, logging channel. Does not attempt to pair scopes when {@link #connect(KlabService)} is
 * called.  Calls to {@link #post(Consumer, Object...)} and {@link #send(Object...)} have no effect besides
 * invoking listeners and functors.
 */
public class ChannelImpl implements Channel {

    Identity identity;
    AtomicBoolean interrupted = new AtomicBoolean(false);
    AtomicBoolean errors = new AtomicBoolean(false);
    List<BiConsumer<Identity, Message>> listeners = Collections.synchronizedList(new ArrayList<>());
    List<BiFunction<Message, Identity, Message>> functors = Collections.synchronizedList(new ArrayList<>());

    public ChannelImpl(Identity identity) {
        this.identity = identity;
    }

    @Override
    public Identity getIdentity() {
        return this.identity;
    }

    @Override
    public void info(Object... info) {
        Logging.INSTANCE.info(info);
        if (!listeners.isEmpty()) {
            // TODO should have level to select what gets sent
            // TODO turn arguments into Notification and Message and pass through send()
        }
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
    public Message send(Object... message) {
        var me = Message.create(this, message);
        for (var listener : listeners) {
            listener.accept(this.identity, me);
        }
        return me;
    }

    @Override
    public Message post(Consumer<Message> handler, Object... message) {
        var me = Message.create(this, message);
        for (var listener : listeners) {
            listener.accept(this.identity, me);
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


    @Override
    public boolean connect(KlabService service) {
        return false;
    }

    @Override
    public boolean disconnect(KlabService service) {
        return false;
    }
}
