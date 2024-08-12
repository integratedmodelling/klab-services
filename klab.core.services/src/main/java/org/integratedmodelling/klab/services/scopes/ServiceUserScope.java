package org.integratedmodelling.klab.services.scopes;

import io.reacted.core.messages.reactors.ReActorStop;
import org.integratedmodelling.common.authentication.scope.AbstractReactiveScopeImpl;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.services.application.security.Role;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Service-side user scope and parent class for other scopes, created and maintained on request upon
 * authentication. The services exposed are the ones authorized passed explicitly from the client side after
 * authentication, except for the service hosting the scope, which is the one and only provided for its class.
 * In this implementation (currently) the only scope that has services is the context scope, and the other
 * scopes have empty service maps. The {@link ScopeManager} contains the logic.
 * <p>
 * Relies on external instrumentation after creation.
 * <p>
 * Maintained by the {@link ScopeManager}
 *
 * @author Ferd
 */
public class ServiceUserScope extends AbstractReactiveScopeImpl implements UserScope {

    // the data hash is the SAME OBJECT throughout the child
    protected Parameters<String> data;
    private UserIdentity user;
    protected ServiceUserScope parentScope;
    private Status status = Status.STARTED;
    private Collection<Role> roles;
    private String id;
    private boolean local;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    protected Map<KlabService.Type, List<? extends KlabService>> serviceMap = new HashMap<>();
    protected Map<KlabService.Type, KlabService> defaultServiceMap = new HashMap<>();

    public ServiceUserScope(UserIdentity user) {
        super(user, true, false);
        this.user = user;
        this.data = Parameters.create();
    }

    /**
     * Must be called with clients for all services accessible from the client's environment, plus the
     * singleton of the hosting service. If this is called on a scope with non-empty services, the scope will
     * use these services instead of the default.
     *
     * @param resources
     * @param resolvers
     * @param reasoners
     * @param runtimes
     */
    public void setServices(List<ResourcesService> resources, List<Resolver> resolvers,
                            List<Reasoner> reasoners, List<RuntimeService> runtimes) {

        serviceMap.clear();
        defaultServiceMap.clear();

        serviceMap.put(KlabService.Type.REASONER, reasoners);
        serviceMap.put(KlabService.Type.RESOLVER, resolvers);
        serviceMap.put(KlabService.Type.RESOURCES, resources);
        serviceMap.put(KlabService.Type.RUNTIME, runtimes);

        if (!reasoners.isEmpty()) {
            defaultServiceMap.put(KlabService.Type.REASONER, reasoners.getFirst());
        }
        if (!resolvers.isEmpty()) {
            defaultServiceMap.put(KlabService.Type.RESOLVER, resolvers.getFirst());
        }
        if (!resources.isEmpty()) {
            defaultServiceMap.put(KlabService.Type.RESOURCES, resources.getFirst());
        }
        if (!runtimes.isEmpty()) {
            defaultServiceMap.put(KlabService.Type.RUNTIME, runtimes.getFirst());
        }
    }

    protected ServiceUserScope(ServiceUserScope parent) {
        super(parent.user, parent.isSender(), parent.isReceiver());
        this.user = parent.user;
        this.parentScope = parent;
        this.data = parent.data;
        this.local = parent.local;
        this.serviceMap.putAll(parent.serviceMap);
        this.defaultServiceMap.putAll(parent.defaultServiceMap);
    }

    @Override
    public SessionScope runSession(String sessionName) {
        final ServiceSessionScope ret = new ServiceSessionScope(this);
        ret.setStatus(Status.WAITING);
        ret.setName(sessionName);
        // Scope is incomplete and will be instrumented with ID, messaging connection, queues and agent by
        // the caller explicitly calling the methods.
        return ret;
    }

    @Override
    public SessionScope run(String behaviorName, KActorsBehavior.Type behaviorType) {

        var ret = runSession(behaviorName);
        // TODO add the behavior info
        return ret;
    }

    @Override
    public UserIdentity getUser() {
        return this.user;
    }

    @Override
    public Parameters<String> getData() {
        return this.data;
    }

    //
    //    @Override
    //    public Message post(Consumer<Message> responseHandler, Object... message) {
    //
    //        /*
    //         * Agent scopes will intercept the response from an agent and pair it with the
    //         * response handler. All response handlers are scheduled and executed in
    //         * sequence.
    //         */
    //        if (message != null && message.length == 1 && message[0] instanceof AgentResponse) {
    //            Pair<AgentMessage, BiConsumer<AgentMessage, AgentResponse>> handler = responseHandlers
    //                    .get(((AgentResponse) message[0]).getId());
    //            if (handler != null) {
    //                executor.execute(() -> {
    //                    handler.getSecond().accept(handler.getFirst(), (AgentResponse) message[0]);
    //                    if (((AgentResponse) message[0]).isRemoveHandler()) {
    //                        responseHandlers.remove(((AgentResponse) message[0]).getId());
    //                    }
    //                });
    //            }
    //            return null;
    //        } else if (message != null && message.length == 1 && message[0] instanceof VM.AgentMessage) {
    //            /*
    //             * dispatch to the agent. If there's a handler, make a responseHandler and
    //             * ensure that it gets a message
    //             */
    //            if (responseHandler != null) {
    //                // TODO needs an asynchronous ask()
    //                // Message m = Message.create(getIdentity().getId(),
    //                // Message.MessageClass.ActorCommunication, Message.Type.AgentResponse,
    //                // message[0]);
    //                // this.getAgent().ask(m, (VM.AgentMessage)message[0]);
    //            } else {
    //                this.getAgent().tell((VM.AgentMessage) message[0]);
    //            }
    //
    //        } else {
    //            return super.post(responseHandler, message);
    //        }
    //
    //        return null;
    //
    //    }

    @Override
    public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
        return new Utils.Casts<KlabService, T>().cast((Collection<KlabService>) serviceMap.get(KlabService.Type.classify(serviceClass)));
    }

    //    @Override
    //    public Message send(Object... message) {
    //        return post(null, message);
    //    }

    @Override
    public boolean hasErrors() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Identity getIdentity() {
        return getUser();
    }

    @Override
    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public Status getStatus() {
        return this.status;
    }

    @Override
    public void setData(String key, Object value) {
        this.data.put(key, value);
    }

    @Override
    public <T extends KlabService> T getService(Class<T> serviceClass) {
        return (T) defaultServiceMap.get(KlabService.Type.classify(serviceClass));
    }

    @Override
    public <T extends KlabService> T getService(String serviceId, Class<T> serviceClass) {
        for (var service : getServices(serviceClass)) {
            if (serviceId.equals(service.serviceId())) {
                return service;
            }
        }
        throw new KlabResourceAccessException("cannot find service with ID=" + serviceId + " in the scope");
    }

    //	@Override
    public void stop() {
        if (agent != null) {
            agent.tell(ReActorStop.STOP);
            this.agent = null;
        }
        this.data.clear();
        setStatus(Status.EMPTY);
    }

    @Override
    public void switchService(KlabService service) {
        // no switching at server side. TODO Consider raising an exception or when that could be appropriate.
    }

    public Collection<Role> getRoles() {
        return roles;
    }

    public void setRoles(Collection<Role> roles) {
        this.roles = roles;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isLocal() {
        return local;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }


    @Override
    public ServiceUserScope getParentScope() {
        return parentScope;
    }

    public void setParentScope(ServiceUserScope parentScope) {
        this.parentScope = parentScope;
    }

    public String toString() {
        return user.toString();
    }

}
