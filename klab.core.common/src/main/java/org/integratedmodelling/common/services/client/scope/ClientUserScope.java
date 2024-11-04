package org.integratedmodelling.common.services.client.scope;

import org.integratedmodelling.common.authentication.scope.AbstractReactiveScopeImpl;
import org.integratedmodelling.common.services.client.engine.EngineImpl;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.Message;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;

/**
 * Implementations must fill in the getService() strategy. This is a scope that contains an agent ref. Any
 * communication with the agent will pass the scope, so if the agent is remote the scope must be reconstructed
 * from authorization tokens into something that maintains communication with the original one.
 * <p>
 * Each scope contains a hash of generic data. Creating "child" scopes will only build a new hash when the
 * scope is of a different class, otherwise the same data is passed to all children.
 * <p>
 * The scope classes inherit from each other, so care is needed if using <code>instanceof</code> to
 * discriminate.
 *
 * @author Ferd
 */
public abstract class ClientUserScope extends AbstractReactiveScopeImpl implements UserScope {

    protected final EngineImpl engine;
    // the data hash is the SAME OBJECT throughout the child
    protected Parameters<String> data;
    private Identity user;
    protected Scope parentScope;
    private Status status = Status.STARTED;
    private String id;
    protected Type type;
    private List<BiConsumer<Scope, Message>> listeners = new ArrayList<>();
    private Map<Long, Pair<Message, BiConsumer<Message, Message>>> responseHandlers =
            Collections
                    .synchronizedMap(new HashMap<>());

    public BiConsumer<Scope, Message>[] getListeners() {
        return listeners.toArray(new BiConsumer[]{});
    }

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    public ClientUserScope(Identity user, EngineImpl engine, BiConsumer<Scope, Message>... listeners) {
        super(user, false, true);
        this.user = user;
        this.data = Parameters.create();
        this.id = user.getId();
        this.engine = engine;
        if (listeners != null) {
            for (var listener : listeners) {
                this.listeners.add(listener);
            }
        }
    }

    @Override
    public <T extends KlabService> T getService(String serviceId, Class<T> serviceClass) {

        for (var service : getServices(serviceClass)) {

            if (service == null) {
                System.out.println("DIO POCO");
                return null;
            }

            if (serviceId.equals(service.serviceId())) {
                return service;
            }
        }
        throw new KlabResourceAccessException("cannot find service with ID=" + serviceId + " in the scope");
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }


    @Override
    public SessionScope createSession(String sessionName) {

        /**
         * Must have runtime
         *
         * Send REGISTER_SCOPE to runtime; returned ID becomes part of the token for requests
         * Wait for result, set ID and data/permissions/metadata, expirations, quotas into metadata
         * Create peer object and return
         */

        var runtime = getService(RuntimeService.class);
        if (runtime == null) {
            throw new KlabResourceAccessException("Runtime service is not accessible: cannot create session");
        }

        /**
         * Registration with the runtime succeeded. Return a peer scope locked to the
         * runtime service that hosts it.
         */
        var ret =new ClientSessionScope(this, sessionName, runtime) {

            @Override
            public <T extends KlabService> T getService(Class<T> serviceClass) {
                if (serviceClass.isAssignableFrom(RuntimeService.class)) {
                    return (T) runtime;
                }
                return ClientUserScope.this.getService(serviceClass);
            }

            @Override
            public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
                if (serviceClass.isAssignableFrom(RuntimeService.class)) {
                    return List.of((T) runtime);
                }
                return ClientUserScope.this.getServices(serviceClass);
            }
        };

        var id = engine.registerSession(ret);
        if (id != null) {
            ret.setId(id);
        }

        return ret;

    }

    @Override
    public SessionScope run(String behaviorName, KActorsBehavior.Type behaviorType) {

        /**
         * Same as above plus:
         * Send URN as URL or content as request body if available
         */

        //		final EngineSessionScope ret = new EngineSessionScope(this);
        //		ret.setStatus(Status.WAITING);
        //		Ref sessionAgent = this.agent.ask(new CreateApplication(ret, behaviorName, behaviorType),
        //		Ref.class);
        //		if (!sessionAgent.isEmpty()) {
        //			ret.setStatus(Status.STARTED);
        //			ret.setAgent(sessionAgent);
        //			ret.setName(behaviorName);
        //			sessionAgent.tell(new RunBehavior(behaviorName));
        //		} else {
        //			ret.setStatus(Status.ABORTED);
        //		}
        //		return ret;
        return null;
    }

    @Override
    public UserIdentity getUser() {
        return this.user instanceof UserIdentity user ? user : null;
    }

    @Override
    public Parameters<String> getData() {
        return this.data;
    }

    @Override
    public boolean isInterrupted() {
        return status == Status.INTERRUPTED;
    }

    @Override
    public void interrupt() {
        this.status = Status.INTERRUPTED;
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
    public void switchService(KlabService service) {
        // TODO, or just avoid in this implementation.
    }

    @Override
    public Scope getParentScope() {
        return parentScope;
    }

    public void setParentScope(Scope parentScope) {
        this.parentScope = parentScope;
    }

    @Override
    public List<SessionScope> getActiveSessions() {
        return List.of();
    }
}
