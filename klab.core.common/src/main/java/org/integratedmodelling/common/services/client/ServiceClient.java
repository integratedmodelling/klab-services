package org.integratedmodelling.common.services.client;

import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Common implementation of a service client, to be specialized for all service types and APIs. Manages the
 * scope and automatically enables messaging with local services.
 */
public abstract class ServiceClient implements KlabService {

    AtomicBoolean connected = new AtomicBoolean(false);
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    ServiceStatus status = ServiceStatus.offline();
    AbstractServiceDelegatingScope scope;
    URL url;

    protected ServiceClient(KlabService.Type serviceType) {
        var authentication = Authentication.INSTANCE.authenticate();
        connect(authentication.getFirst(), authentication.getSecond(), serviceType);
    }
    protected ServiceClient(KlabService.Type serviceType, UserIdentity identity, List<ServiceReference> services) {
        var authentication = Authentication.INSTANCE.authenticate();
        connect(authentication.getFirst(), authentication.getSecond(), serviceType);
    }

    private void connect(Identity user, List<ServiceReference> services, Type serviceType) {

        /*
        Connect to the default service of the passed type; if none is available, try the default local URL
         */

    }




    protected ServiceClient(URL url) {
        this.url = url;
        scheduler.scheduleAtFixedRate(() -> checkConnection(), 0, 1, TimeUnit.SECONDS);
//        this.scope = new AbstractServiceDelegatingScope() {
//            @Override
//            public <T extends KlabService> T getService(Class<T> serviceClass) {
//                return null;
//            }
//
//            @Override
//            public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
//                return null;
//            }
//        }
    }

    private void checkConnection() {
        if (!connected.get()) {
            /*
            try to connect; if successful, retrieve status
             */
        } else {
            /*
            ping status
             */
        }
        /**
         *
         */
    }

    @Override
    public final ServiceScope scope() {
        return scope;
    }

    @Override
    public final URL getUrl() {
        return url;
    }

    public final ServiceStatus status() {
        return status;
    }
}
