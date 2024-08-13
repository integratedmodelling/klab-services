package org.integratedmodelling.common.services.client;

import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.authentication.scope.ChannelImpl;
import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.services.client.resources.CredentialsRequest;
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
import org.integratedmodelling.klab.api.services.impl.AbstractServiceCapabilities;
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
    //    private AtomicBoolean authorized = new AtomicBoolean(false);
    private AtomicBoolean authenticated = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private boolean usingLocalSecret;
    private AtomicReference<ServiceStatus> status = new AtomicReference<>(ServiceStatus.offline());
    private AbstractServiceDelegatingScope scope;
    private URL url;
    private String token;
    private long pollCycleSeconds = 2;
    protected Utils.Http.Client client;
    private ServiceCapabilities capabilities;

    //    // these can be installed from the outside. TODO these should go in the scope and only there
    //    @Deprecated
    //    protected List<BiConsumer<Scope, Message>> listeners = new ArrayList<>();
    private boolean local;

    protected ServiceClient(KlabService.Type serviceType) {
        this.authentication = Authentication.INSTANCE.authenticate(false);
        this.serviceType = serviceType;
        this.url = discoverService(authentication.getFirst(), authentication.getSecond(), serviceType);
        if (this.url != null) {
            establishConnection();
        }
    }

    protected ServiceClient(KlabService.Type serviceType, Identity identity,
                            List<ServiceReference> services) {
        this.authentication = Authentication.INSTANCE.authenticate(false);
        this.serviceType = serviceType;
        this.url = discoverService(authentication.getFirst(), authentication.getSecond(), serviceType);
        if (this.url != null) {
            establishConnection();
        }
    }

    @SafeVarargs
    protected ServiceClient(KlabService.Type serviceType, URL url, Identity identity,
                            List<ServiceReference> services, BiConsumer<Channel, Message>... listeners) {
        this.authentication = Pair.of(identity, services);
        this.serviceType = serviceType;
        this.url = url;
        this.local = Utils.URLs.isLocalHost(url);
        this.scopeListeners = listeners;
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
        if (identity instanceof UserIdentity user) {
            token = user.isAnonymous() ? ServicesAPI.ANONYMOUS_TOKEN : identity.getId();
            if (!user.isAnonymous()) {
                authenticated.set(true);
            }
        }

        for (var service : services) {
            if (service.getServiceType() == serviceType && service.isPrimary() && !service.getUrls().isEmpty()) {
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

            url = serviceType.localServiceUrl();
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

    //    /**
    //     * Find the token on the filesystem installed by a running service of the passed type. If found,
    //     we may
    //     * have a local service that lets us connect with just that token and administer it.
    //     *
    //     * @param serviceType
    //     * @return
    //     */
    //    private String readLocalServiceToken(Type serviceType) {
    //        File secretFile =
    //                Configuration.INSTANCE.getFileWithTemplate("services/" + serviceType.name()
    //                .toLowerCase() + "/secret.key", org.integratedmodelling.klab.api.utils.Utils.Names
    //                .newName());
    //        try {
    //            return Files.readString(secretFile.toPath());
    //        } catch (IOException e) {
    //            throw new KlabIOException(e);
    //        }
    //    }


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
     *
     * @return null if server is remote (the auth key is the ID of the identity) or non-null if using a local
     * server; in that case, the return value is the value for {@link ServicesAPI#SERVER_KEY_HEADER}.
     */
    protected String establishConnection() {

        this.token = this.authentication.getFirst().getId();
        String ret = null;
        this.client = Utils.Http.getServiceClient(token, this);
        var secret = Configuration.INSTANCE.getServiceSecret(serviceType);
        if (secret != null) {
            local = Utils.URLs.isLocalHost(getUrl());
            if (local) {
                client.setHeader(ServicesAPI.SERVER_KEY_HEADER, secret);
                ret = secret;
            }
        }

        /**
         * Service scopes are non-messaging but if the services are local, messaging happens through the
         * user scope. Set up the proper channel according to whether we have messaging or not.
         */

        this.scope =
                new AbstractServiceDelegatingScope(new ChannelImpl(this.authentication.getFirst())) {

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

        scheduler.scheduleAtFixedRate(this::timedTasks, 2, pollCycleSeconds, TimeUnit.SECONDS);

        return ret;
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

                    // add the URL to the capabilities.
                    if (capabilities instanceof AbstractServiceCapabilities asc) {
                        asc.setUrl(this.url);
                    }

                    scope.send(Message.MessageClass.ServiceLifecycle, (connected.get() ?
                                                                       Message.MessageType.ServiceAvailable : Message.MessageType.ServiceUnavailable), capabilities);

                    if (connected.get()) {

                        // see if we have a local service and change the token
                        if ((token == null || token.isEmpty()) && Utils.URLs.isLocalHost(getUrl())) {
                            // may have gotten lost if the service was starting when we booted
                            var secret = Configuration.INSTANCE.getServiceSecret(serviceType);
                            if (secret != null) {
                                token = secret;
                                client.setAuthorization(token);
                                local = true;
                            }
                        }

                        //                        if (Configuration.INSTANCE.pairServiceScopes() && local) {
                        //                            // establish whatever scope "entanglement" is allowed
                        //                            by the service. For now
                        //                            // disable, we try to do everything through REST and
                        //                            see if it's practical.
                        //                            scope.connect(this);
                        //                        }

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
        //        scope.disconnect(this);
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
        var request = new CredentialsRequest();
        request.setHost(host);
        request.setCredentials(credentials);
        return client.post(ServicesAPI.ADMIN.CREDENTIALS, request,
                ExternalAuthenticationCredentials.CredentialInfo.class);
    }
}
