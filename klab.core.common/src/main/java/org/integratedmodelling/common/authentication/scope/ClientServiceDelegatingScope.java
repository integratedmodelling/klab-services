package org.integratedmodelling.common.authentication.scope;

import org.integratedmodelling.klab.api.services.runtime.Channel;

/**
 * A delegating base scope that finds its services on the network (local or remote) if they are not provided
 * at initialization.
 */
public class ClientServiceDelegatingScope extends AbstractDelegatingScope {
    public ClientServiceDelegatingScope(Channel messageBus) {
        super(messageBus);
    }
}
