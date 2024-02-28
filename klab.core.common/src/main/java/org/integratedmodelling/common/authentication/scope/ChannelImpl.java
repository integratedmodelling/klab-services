package org.integratedmodelling.common.authentication.scope;

import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;

import java.util.function.Consumer;

/**
 * Basic logging channel. Does not have a {@link org.integratedmodelling.klab.api.services.runtime.MessageBus}
 * linked to {@link #send(Object...)} and {@link #post(Consumer, Object...)}.
 */
public class ChannelImpl implements Channel {

    @Override
    public Identity getIdentity() {
        return null;
    }

    @Override
    public void info(Object... info) {

    }

    @Override
    public void warn(Object... o) {

    }

    @Override
    public void error(Object... o) {

    }

    @Override
    public void debug(Object... o) {

    }

    @Override
    public void send(Object... message) {

    }

    @Override
    public void post(Consumer<Message> handler, Object... message) {

    }

    @Override
    public void interrupt() {

    }

    @Override
    public boolean isInterrupted() {
        return false;
    }

    @Override
    public boolean hasErrors() {
        return false;
    }
}
