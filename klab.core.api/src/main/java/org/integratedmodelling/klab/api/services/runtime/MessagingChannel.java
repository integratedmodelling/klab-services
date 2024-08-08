package org.integratedmodelling.klab.api.services.runtime;

import java.io.Closeable;
import java.util.EnumSet;
import java.util.Set;

/**
 * A channel that has been instrumented for messaging to paired channels. Only a tag interface for now.
 */
public interface MessagingChannel extends Channel, Closeable {

    @Override
    default Set<Message.Queue> defaultQueues() {
        return EnumSet.of(Message.Queue.Errors, Message.Queue.Events, Message.Queue.Status);
    }

    /**
     * True if messaging is available and connected.
     *
     * @return
     */
    boolean hasMessaging();
}
