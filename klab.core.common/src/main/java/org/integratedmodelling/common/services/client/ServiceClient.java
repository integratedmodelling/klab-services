package org.integratedmodelling.common.services.client;

import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Common implementation of a service client, to be specialized for all service types and APIs. Manages the
 * scope and automatically enables messaging with local services.
 */
public abstract class ServiceClient implements KlabService {

    private BiConsumer<Channel, Message>[] scopeListeners;
    private Type serviceType;
    private Pair<Identity, List<ServiceReference>> authentication;
    private AtomicBoolean connected = new AtomicBoolean(false);
    private AtomicBoolean authorized = new AtomicBoolean(false);
    private AtomicBoolean authenticated = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    private boolean usingLocalSecret;
    private AtomicReference<ServiceStatus> status = new AtomicReference<>(ServiceStatus.offline());
    private AbstractServiceDelegatingScope scope;
    private URL url;
    private String token;
    private long pollCycleSeconds = 1;
    protected Utils.Http.Client client;
    private ServiceCapabilities capabilities;

    //    // these can be installed from the outside. TODO these should go in the scope and only there
    //    @Deprecated
    //    protected List<BiConsumer<Scope, Message>> listeners = new ArrayList<>();
    private boolean local;

    protected ServiceClient(KlabService.Type serviceType) {
        this.authentication = Authentication.INSTANCE.authenticate(false);
        this.serviceType = serviceType;
        if ((this.url =
                discoverService(authentication.getFirst(), authentication.getSecond(), serviceType)) != null) {
            establishConnection();
        }
    }

    protected ServiceClient(KlabService.Type serviceType, Identity identity,
                            List<ServiceReference> services) {
        this.authentication = Authentication.INSTANCE.authenticate(false);
        this.serviceType = serviceType;
        if ((this.url =
                discoverService(authentication.getFirst(), authentication.getSecond(), serviceType)) != null) {
            establishConnection();
        }
    }

    protected ServiceClient(KlabService.Type serviceType, URL url, Identity identity,
                            List<ServiceReference> services, BiConsumer<Channel, Message>... listeners) {
        this.authentication = Pair.of(identity, services);
        this.serviceType = serviceType;
        this.url = url;
        this.local = url.equals(serviceType.localServiceUrl());
        this.scopeListeners = listeners;
        this.token = this.local ? Configuration.INSTANCE.getServiceSecret(serviceType) : identity.getId();
        establishConnection();
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
            if (token != null && !token.isEmpty()) {
                authenticated.set(true);
            }
        }

        if (token == null || token.isEmpty()) {
            // even anonymous users can use local services
            var secret = Configuration.INSTANCE.getServiceSecret(serviceType);
            if (secret != null) {
                token = secret;
            }
        }

        authorized.set(token != null);

        for (var service : services) {
            if (service.getServiceType() == serviceType && service.isPrimary() && service.getUrls().size() > 0) {
                for (var url : service.getUrls()) {
                    var status = readServiceStatus(url, scope);
                    if (status != null) {
                        ret = url;
                        // we are connected but we leave setting the connected flag to the timed task
                        this.status.set(status);
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
                String localServiceToken = readLocalServiceToken(serviceType);
                if (localServiceToken != null) {
                    token = localServiceToken;
                    usingLocalSecret = true;
                }
            }

            url = serviceType.localServiceUrl();
            var secret = readLocalServiceToken(serviceType);
            if (secret != null) {
                token = makeSecretToken(secret, identity);
            }
            var status = readServiceStatus(url, scope);

            if (status != null) {
                ret = url;
                // we are connected but we leave setting the connected flag to the timed task
                this.status.set(status);
                this.local = true;
            }
        }

        return ret;
    }

    private String makeSecretToken(String secret, Identity identity) {
        StringBuffer ret = new StringBuffer(secret);
        if (identity instanceof UserIdentity userIdentity) {
            ret.append("/");
            ret.append(userIdentity.getUsername());
            ret.append("/");
            ret.append(userIdentity.getEmailAddress());
            for (Group group : userIdentity.getGroups()) {
                ret.append("/");
                ret.append(group.getName());
            }
        }
        return ret.toString();
    }

    /**
     * Find the token on the filesystem installed by a running service of the passed type. If found, we may
     * have a local service that lets us connect with just that token and administer it.
     *
     * @param serviceType
     * @return
     */
    private String readLocalServiceToken(Type serviceType) {
        File secretFile =
                Configuration.INSTANCE.getFileWithTemplate("services/" + serviceType.name().toLowerCase() +
                        "/secret.key", org.integratedmodelling.klab.api.utils.Utils.Names.newName());
        try {
            return Files.readString(secretFile.toPath());
        } catch (IOException e) {
            throw new KlabIOException(e);
        }
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
    public static ServiceStatus readServiceStatus(URL url, Scope scope) {
        try (var client = Utils.Http.getClient(url, scope)) {
            return client.get(ServicesAPI.STATUS, ServiceStatusImpl.class, Notification.Mode.Silent);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Connection has succeeded; we have a URL and (possibly) a token. Create client with the token we have
     * stored and if needed, authenticate further using the token. Start polling at regular intervals to
     * ensure the connection remains alive. Build the service scope and if we're on the LAN or LOCALHOST
     * locality, establish the Websocket link between the client and the server so we can listen to events.
     */
    private void establishConnection() {

        this.client = Utils.Http.getServiceClient(token, this);

        /*
        TODO revise the websockets strategy by calling the scope controller if the conditions are there, and
         obtaining a channel to pair the scopes.
         */
        this.scope =
                new AbstractServiceDelegatingScope(new MessagingChannelImpl(this.authentication.getFirst(),
                        client, Scope.Type.SERVICE)) {
                    @Override
                    public UserScope createUser(String username, String password) {
                        return null;
                    }

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

        if (this.scopeListeners != null) {
            for (var listener : scopeListeners) {
                this.scope.addListener(listener);
            }
        }

        scheduler.scheduleAtFixedRate(this::timedTasks, 0, pollCycleSeconds, TimeUnit.SECONDS);
    }

    private void timedTasks() {

        try {

            boolean currentStatus = connected.get();

            /*
            TODO check for changes of status and send messages over
             */

            try {
                var s = readServiceStatus(this.url, scope);
                if (s == null) {
                    connected.set(false);
                    status.set(ServiceStatus.offline());
                } else {
                    status.set(s);
                    connected.set(true);
                    if (capabilities == null) {
                        this.capabilities = capabilities(scope);
                    }
                }
            } finally {
                if (connected.get() != currentStatus) {
                    scope.send(Message.MessageClass.ServiceLifecycle,
                            (connected.get() ? Message.MessageType.ServiceAvailable :
                             Message.MessageType.ServiceUnavailable), capabilities);
                    if (Configuration.INSTANCE.pairServiceScopes() && local && connected.get()) {
                        // establish whatever scope "entanglement" is allowed by the service. For now
                        // disable, we try to do everything through REST and see if it's practical.
                        scope.connect(this);
                    }
                }
            }

            /**
             *
             */
            // send the status
            if (connected.get() && status != null) {
                scope.send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceStatus,
                        status.get());
            }

        } catch (Throwable t) {
            scope.error(t);
        }

    }

    @Override
    public final ServiceScope serviceScope() {
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
        scope.disconnect(this);
        this.scheduler.shutdown();
        if (local) {
            return client.put(ServicesAPI.ADMIN.SHUTDOWN);
        }
        return false;
    }

    public boolean isLocal() {
        return this.local;
    }

    @Override
    public boolean isExclusive() {
        /**
         * TODO isLocal() could be a prerequisite but locking the service should precede returning true here.
         */
        return isLocal();
    }

    @Override
    public String serviceId() {
        return capabilities.getServiceId();
    }


    @Override
    public ResourcePrivileges getRights(String resourceUrn, Scope scope) {
        return client.get(ServicesAPI.RESOURCES.RESOURCE_RIGHTS, ResourcePrivileges.class, "urn",
                resourceUrn);
    }

    @Override
    public boolean setRights(String resourceUrn, ResourcePrivileges resourcePrivileges, Scope scope) {
        return client.put(ServicesAPI.RESOURCES.RESOURCE_RIGHTS, resourcePrivileges, "urn", resourceUrn);
    }

    @Override
    public List<ExternalAuthenticationCredentials.CredentialInfo> getCredentialInfo(Scope scope) {
        return client.getCollection(ServicesAPI.ADMIN.CREDENTIALS,
                ExternalAuthenticationCredentials.CredentialInfo.class);
    }

    @Override
    public ExternalAuthenticationCredentials.CredentialInfo addCredentials(String host,
                                                                           ExternalAuthenticationCredentials credentials, Scope scope) {
        return client.post(ServicesAPI.ADMIN.SET_HOST_CREDENTIALS, credentials,
                ExternalAuthenticationCredentials.CredentialInfo.class, "host", host);
    }
}
