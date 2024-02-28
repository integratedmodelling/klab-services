package org.integratedmodelling.common.authentication.scope;

import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessageBus;

import java.util.function.Consumer;

/**
 * Replicates the functionality of the parent
 * {@link org.integratedmodelling.klab.api.services.runtime.Channel} but also routes everything through a
 * MessageBus.
 */
public class MessagingChannelImpl extends ChannelImpl {

    MessageBus messageBus;

    public MessagingChannelImpl(MessageBus messageBus) {
        this.messageBus = messageBus;
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
}
