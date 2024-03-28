package org.integratedmodelling.common.services.client.engine;

import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.common.services.client.scope.ClientScope;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.configuration.PropertyHolder;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * The engine client uses client services and can be configured to use a local server for all services
 * (possibly as a downloaded product) and use the local services if so desired or if online services are not
 * available. The distribution up to this point should remain light so that it can be embedded in a IDE or
 * other product.
 */
public class EngineClient implements Engine, PropertyHolder {

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
    private Pair<Identity, List<ServiceReference>> authData;
    List<UserScope> users = new ArrayList<>();
    List<BiConsumer<Scope, Message>> listeners = Collections.synchronizedList(new ArrayList<>());
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private boolean firstCall = true;

    public UserScope getUser() {
        return this.users.size() > 0 ? users.get(0) : null;
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
    public void addEventListener(BiConsumer<Scope, Message> eventListener) {
        listeners.add(eventListener);
    }

    @Override
    public ServiceCapabilities capabilities() {
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

    @Override
    public Scope serviceScope() {
        return defaultUser;
    }

    @Override
    public boolean shutdown() {
        /*
        send shutdown to all services that were launched in our scope
        TODO check for embedded services, which should stop themselves when the JVM exits but should also
         made to shutdown correctly.
         */
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
    public void boot() {
        this.defaultUser = authenticate();
        this.users.add(this.defaultUser);
        scheduler.scheduleAtFixedRate(() -> timedTasks(), 0, 15, TimeUnit.SECONDS);
        booted.set(true);
    }

    protected UserScope authenticate() {
        this.authData = Authentication.INSTANCE.authenticate(false);
        var ret = createUserScope(authData);
        ret.send(Message.MessageClass.Authorization, Message.MessageType.UserAuthorized, authData.getFirst());
        return ret;
    }


    private void timedTasks() {

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
                        authData.getSecond(), firstCall, true);
            }
            if (service == null && serviceIsEssential(type)) {
                ok = false;
            }
            if (service != null) {
                registerService(type, service);
            }
            firstCall = false;
        }

        // inform listeners
        if (wasAvailable != ok) {
            if (ok) {
                serviceScope().send(Message.MessageClass.EngineLifecycle,
                        Message.MessageType.ServiceAvailable, capabilities());
            } else {
                serviceScope().send(Message.MessageClass.EngineLifecycle,
                        Message.MessageType.ServiceUnavailable, capabilities());
            }
        }

        available.set(ok);
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

        var ret = new ClientScope(authData.getFirst(), Scope.Type.SERVICE,
                listeners.toArray(new BiConsumer[]{})) {
            @Override
            public <T extends KlabService> T getService(Class<T> serviceClass) {
                return (T) currentServices.get(KlabService.Type.classify(serviceClass));
            }

            @Override
            public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
                return EngineClient.this.getServices(KlabService.Type.classify(serviceClass));
            }
        };

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
                return List.of((T) EngineClient.this);
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

        var client = new EngineClient();
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
}
