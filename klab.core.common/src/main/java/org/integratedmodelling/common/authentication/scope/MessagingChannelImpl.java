package org.integratedmodelling.common.authentication.scope;

import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.MessageBus;

/**
 * Replicates the functionality of the parent
 * {@link org.integratedmodelling.klab.api.services.runtime.Channel} but implements the connection to remote
 * services. If {@link #connect(KlabService)} is called with an actual embedded service as parameter, any
 * listeners will be shared with the service scope; otherwise, the REST API will be used to establish a
 * Websockets connection as long as the service allows it.
 */
public class MessagingChannelImpl extends ChannelImpl {

    MessageBus messageBus;

    public MessagingChannelImpl(Identity identity, MessageBus messageBus) {
        super(identity);
        this.messageBus = messageBus;
    }

    @Override
    public boolean connect(KlabService service) {
        // TODO if service is a client, try to establish connection; warn upon failure
        // TODO if service is embedded implementation, just install same listeners in service scope
        return false;
    }

    @Override
    public boolean disconnect(KlabService service) {
        return false;
    }

}
