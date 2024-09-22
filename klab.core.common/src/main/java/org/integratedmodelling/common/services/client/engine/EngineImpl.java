package org.integratedmodelling.common.services.client.engine;

import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.authentication.scope.ChannelImpl;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.common.services.client.scope.ClientUserScope;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.impl.ParametersImpl;
import org.integratedmodelling.klab.api.configuration.PropertyHolder;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.api.view.UI;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * The engine runs under a user scope and uses clients for all services and in local configurations can use a
 * local distribution and deploy local services as needed (downloading products from a transparently
 * maintained {@link org.integratedmodelling.klab.api.engine.distribution.Distribution}) if so desired or if
 * online services are not available. The local configuration is usable even if the engine runs in anonymous
 * scope. This implementation is lightweight (depending only on the API and commons packages) and can be
 * embedded into applications such as command-line or graphical IDEs.
 */
public class EngineImpl implements Engine, PropertyHolder {

    AtomicBoolean online = new AtomicBoolean(false);
    AtomicBoolean available = new AtomicBoolean(false);
    AtomicBoolean booted = new AtomicBoolean(false);
    AtomicBoolean stopped = new AtomicBoolean(false);
    Map<KlabService.Type, KlabService> currentServices = new HashMap<>();
    Set<Resolver> availableResolvers = new HashSet<>();
    Set<RuntimeService> availableRuntimeServices = new HashSet<>();
    Set<ResourcesService> availableResourcesServices = new HashSet<>();
    Set<Reasoner> availableReasoners = new HashSet<>();
    UserScope defaultUser;
    // park any listeners here before boot() to install them in the default scope.
    List<BiConsumer<Channel, Message>> scopeListeners = new ArrayList<>();
    private Pair<Identity, List<ServiceReference>> authData;
    List<UserScope> users = new ArrayList<>();
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private boolean firstCall = true;
    String serviceId = Utils.Names.shortUUID();
    //    private boolean reasoningAvailable;
    //    private boolean reasonerDisabled;
    private Worldview worldview;
    private AtomicReference<Status> status = new AtomicReference<>(EngineStatusImpl.inop());
    private Parameters<Setting> settings = Parameters.createSynchronized();

    public EngineImpl() {
        settings.put(Setting.POLLING, "on");
        settings.put(Setting.POLLING_INTERVAL, 5);
        settings.put(Setting.LOG_EVENTS, false);
        settings.put(Setting.LAUNCH_PRODUCT, true);
    }

    public UserScope getUser() {
        return !this.users.isEmpty() ? users.getFirst() : null;
    }

    @Override
    public List<UserScope> getUsers() {
        return users;
    }

    public SessionScope getCurrentSession(UserScope userScope) {
        return null;
    }

    public ContextScope getCurrentContext(UserScope userScope) {
        return null;
    }

    @Override
    public ServiceCapabilities capabilities(Scope scope) {
        return null;
    }

    @Override
    public ServiceStatus status() {
        return null;
    }

    @Override
    public URL getUrl() {
        return null;
    }

    @Override
    public String getLocalName() {
        return null;
    }

    /**
     * The client engine works under a user scope.
     *
     * @return
     */
    @Override
    public UserScope serviceScope() {
        return defaultUser;
    }

    public void addScopeListener(BiConsumer<Channel, Message> listener) {
        this.scopeListeners.add(listener);
    }

    @Override
    public boolean shutdown() {

        serviceScope().send(Message.MessageClass.EngineLifecycle, Message.MessageType.ServiceUnavailable,
                capabilities(serviceScope()));

        /* shutdown all services that were launched in our scope */
        for (KlabService.Type type : new KlabService.Type[]{KlabService.Type.RUNTIME,
                                                            KlabService.Type.RESOLVER,
                                                            KlabService.Type.REASONER,
                                                            KlabService.Type.RESOURCES}) {
            for (var service : getServices(type)) {
                if (service instanceof ServiceClient client && client.isLocal()) {
                    client.shutdown();
                }
            }
        }
        stopped.set(true);
        return true;
    }

    @Override
    public String registerSession(SessionScope sessionScope) {

        var sessionId = getUser().getService(RuntimeService.class).registerSession(sessionScope);
        if (sessionId != null) {
            // TODO advertise the session to all other services that will use it. Keep only the
            //  services that accepted it.
        }
        return sessionId;
    }

    @Override
    public String registerContext(ContextScope contextScope) {

        var contextId = getUser().getService(RuntimeService.class).registerContext(contextScope);
        if (contextId != null) {
            // TODO advertise the session to all other services that will use it. Keep only the
            // services that accept it.

        }
        return contextId;
    }

    @Override
    public boolean isExclusive() {
        // the engine is just an orchestrator so we can assume every client is local.
        return true;
    }

    @Override
    public ResourcePrivileges getRights(String resourceUrn, Scope scope) {
        return null;
    }

    @Override
    public boolean setRights(String resourceUrn, ResourcePrivileges resourcePrivileges, Scope scope) {
        return false;
    }

    @Override
    public void boot() {

        this.defaultUser = authenticate();
        this.scopeListeners.add((channel, message) -> {

            // basic listener for knowledge management
            if (message.is(Message.MessageClass.KnowledgeLifecycle, Message.MessageType
                    .WorkspaceChanged)) {
                var changes = message.getPayload(ResourceSet.class);
                var reasoner = defaultUser.getService(Reasoner.class);
                if (reasoner.status().isAvailable() && reasoner.isExclusive() && reasoner
                        instanceof Reasoner.Admin admin) {
                    var notifications = admin.updateKnowledge(changes, getUser());
                    // send the notifications around for display
                    serviceScope().send(Message.MessageClass.KnowledgeLifecycle, Message
                            .MessageType.LogicalValidation, notifications);
                    if (Utils.Resources.hasErrors(notifications)) {
                        defaultUser.warn("Worldview update caused logical" +
                                " errors in the reasoner", UI.Interactivity.DISPLAY);
                    } else {
                        defaultUser.info("Worldview was updated in the reasoner", UI.Interactivity.DISPLAY);
                    }
                }
            }
        });
        if (this.defaultUser instanceof ChannelImpl channel) {
            for (var listener : scopeListeners) {
                channel.addListener(listener);
            }
        }
        this.users.add(this.defaultUser);
        this.defaultUser.send(Message.MessageClass.EngineLifecycle,
                Message.MessageType.ServiceInitializing, capabilities(serviceScope()));
        this.defaultUser.send(Message.MessageClass.Authorization, Message.MessageType.UserAuthorized,
                authData.getFirst());
        scheduler.scheduleAtFixedRate(() -> timedTasks(), 0, 15, TimeUnit.SECONDS);
        booted.set(true);
    }

    protected UserScope authenticate() {
        this.authData = Authentication.INSTANCE.authenticate(settings);
        return createUserScope(authData);
    }


    private void timedTasks() {

        if ("off".equals(settings.get(Engine.Setting.POLLING, String.class))) {
            return;
        }

        boolean wasAvailable = available.get();

        /*
        check all needed services; put self offline if not available or not there, online otherwise; if
        there's a change in online status, report it through the service scope
         */
        var ok = true;
        for (var type : List.of(KlabService.Type.RESOURCES, KlabService.Type.REASONER,
                KlabService.Type.RUNTIME, KlabService.Type.RESOLVER, KlabService.Type.COMMUNITY)) {

            var service = currentServices.get(type);
            if (service == null) {
                service = Authentication.INSTANCE.findService(type, getUser(), authData.getFirst(),
                        authData.getSecond(), settings);
            }
            if (service == null && serviceIsEssential(type)) {
                ok = false;
            }
            if (service != null) {
                registerService(type, service);
            }
            firstCall = false;
        }

        recomputeEngineStatus();

        /**
         * Check if we have reasoning until we do
         */
        /* if (!reasoningAvailable && !reasonerDisabled) {

            var reasoner = serviceScope().getService(Reasoner.class);

            if (reasoner != null && reasoner.status().isAvailable() && reasoner.capabilities(serviceScope()
            ).getWorldviewId() != null) {

                // reasoner is online and able
                reasoningAvailable = true;
                serviceScope().send(Message.MessageClass.EngineLifecycle,
                        Message.MessageType.ReasoningAvailable, reasoner.capabilities(serviceScope()));

            } else if (reasoner != null && reasoner.isExclusive() && reasoner.status().isAvailable() &&
            reasoner.capabilities(serviceScope()).getWorldviewId() == null) {

                var resources = serviceScope().getService(ResourcesService.class);
                if (resources != null && resources.status().isAvailable() && resources.capabilities
                (serviceScope()).isWorldviewProvider() && reasoner instanceof Reasoner.Admin admin) {

                    var notifications = admin.loadKnowledge(this.worldview = resources.getWorldview(),
                    getUser());

                    serviceScope().send(Message.MessageClass.KnowledgeLifecycle, Message.MessageType
                    .LogicalValidation, notifications);

                    if (Utils.Resources.hasErrors(notifications)) {
                        reasonerDisabled = true;
                        serviceScope().warn("Worldview loading failed: reasoner is disabled");
                    } else {
                        reasoningAvailable = true;
                        serviceScope().send(Message.MessageClass.EngineLifecycle,
                                Message.MessageType.ReasoningAvailable,
                                reasoner.capabilities(serviceScope()));
                        serviceScope().info("Worldview loaded into local reasoner");
                    }
                }
            }*/
        //        }

        // inform listeners
        //        if (wasAvailable != ok) {
        //            if (ok) {
        //                serviceScope().send(Message.MessageClass.EngineLifecycle,
        //                        Message.MessageType.ServiceAvailable, capabilities(serviceScope()));
        //            } else {
        //                serviceScope().send(Message.MessageClass.EngineLifecycle,
        //                        Message.MessageType.ServiceUnavailable, capabilities(serviceScope()));
        //            }
        //        }

        available.set(ok);
    }

    public void updateStatus(Message message) {

    }

    /**
     * TODO remove the polling from the clients, put it here explicitly, check status and update
     * capablities if not available previously
     */
    private synchronized void recomputeEngineStatus() {

        // explore state of all services, determine what we
        EngineStatusImpl engineStatus = new EngineStatusImpl();

        var changes = false;
        var noperational = 0;
        for (var scl : List.of(Reasoner.class, ResourcesService.class, Resolver.class,
                RuntimeService.class)) {
            var service = serviceScope().getService(scl);
            var sertype = KlabService.Type.classify(scl);
            if (service != null) {
                var newStatus = service.status();
                if (newStatus != null) {
                    if (newStatus.isOperational()) {
                        noperational++;
                    }
                    var oldStatus = status.get().getServicesStatus().get(sertype);
                    if (oldStatus == null || newStatus.hasChangedComparedTo(oldStatus)) {
                        changes = true;
                        engineStatus.getServicesStatus().put(sertype, newStatus);
                    } else {
                        engineStatus.getServicesStatus().put(sertype, oldStatus);
                    }
                    if (status.get().getServicesCapabilities().get(sertype) == null) {
                        status.get().getServicesCapabilities().put(sertype, service.capabilities(getUser()));
                    }
                }
            } else if (status.get().getServicesStatus() != null) {
                changes = true;
            }
        }

        if (users.size() != status.get().getConnectedUsernames().size()) {
            engineStatus.getConnectedUsernames().addAll(users.stream().map(userScope -> userScope.getUser().getUsername()).toList());
        }

        engineStatus.setOperational(noperational == 4);

        // if state has changed, swap and send message
        if (changes) {
            this.status.set(engineStatus);
            serviceScope().send(Message.MessageClass.EngineLifecycle,
                    Message.MessageType.EngineStatusChanged, engineStatus);
        }
    }

    private void registerService(KlabService.Type serviceType, KlabService service) {
        if (!currentServices.containsKey(serviceType)) {
            currentServices.put(serviceType, service);
        }
        getServices(serviceType).add(service);
    }

    /**
     * Override to define which services must be there for the engine client to report as available.
     * TODO currently set up for testing, default should be everything except COMMUNITY
     */
    protected boolean serviceIsEssential(KlabService.Type type) {
        return type == KlabService.Type.REASONER || type == KlabService.Type.RESOURCES;
    }

    private UserScope createUserScope(Pair<Identity, List<ServiceReference>> authData) {

        var ret = new ClientUserScope(authData.getFirst(), this,
                (serviceScope() instanceof ChannelImpl channel) ?
                channel.listeners().toArray(new BiConsumer[]{}) : new BiConsumer[]{}) {
            @Override
            public <T extends KlabService> T getService(Class<T> serviceClass) {
                return (T) currentServices.get(KlabService.Type.classify(serviceClass));
            }

            @Override
            public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
                return EngineImpl.this.getServices(KlabService.Type.classify(serviceClass));
            }
        };

        if (Authentication.INSTANCE.getDistribution() != null) {
            serviceScope().send(Message.MessageClass.EngineLifecycle, Message.MessageType.UsingDistribution
                    , Authentication.INSTANCE.getDistribution());
        }

        return ret;
    }

    private <T extends KlabService> Collection<T> getServices(KlabService.Type serviceType) {

        switch (serviceType) {
            case REASONER -> {
                return (Collection<T>) availableReasoners;
            }
            case RESOURCES -> {
                return (Collection<T>) availableResourcesServices;
            }
            case RESOLVER -> {
                return (Collection<T>) availableResolvers;
            }
            case RUNTIME -> {
                return (Collection<T>) availableRuntimeServices;
            }
            case COMMUNITY -> {
                var ret = currentServices.get(KlabService.Type.COMMUNITY);
                if (ret != null) {
                    return (Collection<T>) List.of(ret);
                }
            }
            case ENGINE -> {
                return List.of((T) EngineImpl.this);
            }
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isAvailable() {
        return this.available.get();
    }

    @Override
    public boolean isOnline() {
        return this.online.get();
    }

    @Override
    public String configurationPath() {
        return "engine/client";
    }

    public static void main(String[] args) {

        var client = new EngineImpl();
        client.boot();
        while (!client.isStopped()) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private boolean isStopped() {
        return this.stopped.get();
    }

    @Override
    public String getServiceName() {
        return null;
    }

    public String serviceId() {
        return serviceId;
    }

    public void setDefaultService(ServiceCapabilities service) {

        boolean found = false;
        var currentService = currentServices.get(service.getType());
        if (currentService == null || !currentService.serviceId().equals(service.getServiceId())) {
            for (var s : getServices(service.getType())) {
                if (s.serviceId().equals(service.getServiceId())) {
                    currentServices.put(service.getType(), s);
                    found = true;
                    break;
                }
            }
        } else {
            // no change needed, things are already as requested
            found = true;
        }

        if (!found) {
            serviceScope().error("EngineImpl: cannot set unknown " + service.getType() + " service with " +
                    "ID " + service.getServiceId() +
                    " as default: service is not available to the engine");
        }
    }

    @Override
    public List<ExternalAuthenticationCredentials.CredentialInfo> getCredentialInfo(Scope scope) {
        return Authentication.INSTANCE.getCredentialInfo(scope);
    }

    @Override
    public ExternalAuthenticationCredentials.CredentialInfo addCredentials(String host,
                                                                           ExternalAuthenticationCredentials credentials, Scope scope) {
        return Authentication.INSTANCE.addExternalCredentials(host, credentials, scope);
    }

    @Override
    public Map<Setting, Object> getSettings() {
        return settings;
    }
}
