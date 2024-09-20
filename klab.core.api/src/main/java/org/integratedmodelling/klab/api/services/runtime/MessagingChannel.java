package org.integratedmodelling.klab.api.services.runtime;

import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.services.KlabService;

import java.io.Closeable;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A channel that has been instrumented for messaging to paired channels. Only a tag interface for now.
 */
public interface MessagingChannel extends Channel {

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

    /**
     * When a service advertises message queues, connect to the available admin user service-side queues using
     * the passed handler.
     *
     * @param capabilities
     * @param identity
     * @param consumer
     */
    void connectToService(KlabService.ServiceCapabilities capabilities, UserIdentity identity,
                          Consumer<Message> consumer);

    /**
     * True if {@link #connectToService(KlabService.ServiceCapabilities, UserIdentity, Consumer)} has been
     * successfully called.
     *
     * @return
     */
    boolean isConnected();
}
