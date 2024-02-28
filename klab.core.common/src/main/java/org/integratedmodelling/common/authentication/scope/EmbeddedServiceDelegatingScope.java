package org.integratedmodelling.common.authentication.scope;

import org.integratedmodelling.klab.api.services.runtime.Channel;

/**
 * A delegating base scope that has provisions to create embedded services for situations where the network
 * has no usable services.
 */
public class EmbeddedServiceDelegatingScope extends AbstractDelegatingScope {

    public EmbeddedServiceDelegatingScope(Channel messageBus) {
        super(messageBus);
    }
}
