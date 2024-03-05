package org.integratedmodelling.common.services.client;

import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.authentication.scope.ChannelImpl;
import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.messaging.WebsocketsClientMessageBus;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Common implementation of a service client, to be specialized for all service types and APIs. Manages the
 * scope and automatically enables messaging with local services.
 */
public abstract class ServiceClient implements KlabService {

    private Type serviceType;
    private Pair<UserIdentity, List<ServiceReference>> authentication;
    AtomicBoolean connected = new AtomicBoolean(false);
    AtomicBoolean authorized = new AtomicBoolean(false);
    AtomicBoolean authenticated = new AtomicBoolean(false);
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    boolean usingLocalSecret;
    AtomicReference<ServiceStatus> status = new AtomicReference<>(ServiceStatus.offline());
    AbstractServiceDelegatingScope scope;
    URL url;
    String token;
    private long pollCycleSeconds = 1;
    protected Utils.Http.Client client;
    private ServiceCapabilities capabilities;

    protected ServiceClient(KlabService.Type serviceType) {
        this.authentication = Authentication.INSTANCE.authenticate();
        this.serviceType = serviceType;
        if ((this.url =
                discoverService(authentication.getFirst(), authentication.getSecond(), serviceType)) != null) {
            establishConnection();
        }
    }

    protected ServiceClient(KlabService.Type serviceType, UserIdentity identity,
                            List<ServiceReference> services) {
        this.authentication = Authentication.INSTANCE.authenticate();
        this.serviceType = serviceType;
        if ((this.url =
                discoverService(authentication.getFirst(), authentication.getSecond(), serviceType)) != null) {
            establishConnection();
        }
    }

    /**
     * After this is run,  we may have any combination of {no URL, local URL, remote URL} * {no token, local
     * secret, validated remote token}.
     *
     * @param identity
     * @param services
     * @param serviceType
     * @return
     */
    private URL discoverService(Identity identity, List<ServiceReference> services, Type serviceType) {

        URL ret = null;

        /*
        Connect to the default service of the passed type; if none is available, try the default local URL
         */
        if (!(identity instanceof UserIdentity user && user.isAnonymous())) {
            token = identity.getId();
            if (token != null) {
                authenticated.set(true);
            }
        }

        authorized.set(token != null);

        for (var service : services) {
            if (service.getServiceType() == serviceType && service.isPrimary() && service.getUrls().size() > 0) {
                for (var url : service.getUrls()) {
                    var status = readServiceStatus(url);
                    if (status != null) {
                        ret = url;
                        this.status.set(status);
                        connected.set(true);
                        break;
                    }
                }
            }
            if (ret != null) {
                break;
            }
        }

        if (ret == null) {

            if (token == null) {
                String localServiceToken = Authentication.INSTANCE.readLocalServiceToken(serviceType);
                if (localServiceToken != null) {
                    token = localServiceToken;
                    usingLocalSecret = true;
                }
            }

            url = serviceType.localServiceUrl();
            var status = readServiceStatus(url);

            if (status != null) {
                ret = url;
                this.status.set(status);
                connected.set(true);
            }
        }

        return ret;
    }


    protected ServiceClient(URL url) {
        this.url = url;
        establishConnection();
    }

    /**
     * Read status from arbitrary service. Uses own client, no authentication needed, also used as first
     * "ping" to ensure the URL is responding.
     *
     * @param url
     * @return
     */
    public static ServiceStatus readServiceStatus(URL url) {
        try (var client = Utils.Http.getClient(url)) {
            return client.get(ServicesAPI.STATUS, ServiceStatusImpl.class);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Connection has succeeded; we have a URL and (possibly) a token. Create client with the token we have
     * stored and if needed, authenticate further and read the capabilities using the token. Start polling at
     * regular intervals to ensure the connection remains alive. Build the service scope and if we're on the
     * LAN or LOCALHOST locality, establish the Websocket link between the client and the server so we can
     * listen to events.
     */
    private void establishConnection() {

        this.scope =
                new AbstractServiceDelegatingScope(status.get().getLocality() == ServiceScope.Locality.WAN
                                                   ? new MessagingChannelImpl(this.authentication.getFirst(),
                                                           new WebsocketsClientMessageBus(this.url.toString()))
                                                   : new ChannelImpl(this.authentication.getFirst())) {
                    @Override
                    public <T extends KlabService> T getService(Class<T> serviceClass) {
                        return KlabService.Type.classify(serviceClass) == serviceType ?
                               (T) ServiceClient.this : null;
                    }

                    @Override
                    public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
                        return KlabService.Type.classify(serviceClass) == serviceType ?
                               (Collection<T>) List.of(ServiceClient.this) : Collections.emptyList();
                    }
                };

        this.client = Utils.Http.getServiceClient(scope, this);
        this.capabilities = capabilities();

        if (capabilities == null) {
            connected.set(false);
        } else {
            scheduler.scheduleAtFixedRate(() -> checkConnection(), 0, pollCycleSeconds, TimeUnit.SECONDS);
        }
    }

    private void checkConnection() {

        if (!connected.get()) {
            if ((this.url =
                    discoverService(authentication.getFirst(), authentication.getSecond(), serviceType)) != null) {
                establishConnection();
            }
        } else {
            var s = readServiceStatus(this.url);
            if (s == null) {
                connected.set(false);
                status.set(ServiceStatus.offline());
                // TODO send ServerUnavailable to scope
            } else {
                status.set(s);
            }
            // TODO send ServerStatus to scope
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
        return status.get();
    }

    @Override
    public final String getLocalName() {
        return this.capabilities == null ? null : this.capabilities.getLocalName();
    }

    @Override
    public final boolean shutdown() {
        this.scheduler.shutdown();
        // TODO see if we need to log out
        return false;
    }

}
