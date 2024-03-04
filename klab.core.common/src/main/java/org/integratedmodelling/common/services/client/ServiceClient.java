package org.integratedmodelling.common.services.client;

import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;

import java.net.URL;

/**
 * Common implementation of a service client, to be specialized for all service types and APIs. Manages the
 * scope and automatically enables messaging with local services.
 */
public abstract class ServiceClient implements KlabService {

    protected ServiceClient(URL url) {

    }

    @Override
    public final ServiceScope scope() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public final URL getUrl() {
        // TODO Auto-generated method stub
        return null;
    }

    public final ServiceStatus status() {
        return null;
    }
}
