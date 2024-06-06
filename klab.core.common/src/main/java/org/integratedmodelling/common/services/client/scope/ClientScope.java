package org.integratedmodelling.common.services.client.scope;

import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.kactors.AgentMessage;
import org.integratedmodelling.klab.api.services.runtime.kactors.AgentResponse;
import org.integratedmodelling.klab.api.services.runtime.kactors.VM;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Implementations must fill in the getService() strategy. This is a scope that contains an agent ref. Any
 * communication with the agent will pass the scope, so if the agent is remote the scope must be reconstructed
 * from authorization tokens into something that maintains communication with the original one.
 * <p>
 * Each scope contains a hash of generic data. Creating "child" scopes will only build a new hash when the
 * scope is of a different class, otherwise the same data is passed to all children.
 *
 * @author Ferd
 */
public abstract class ClientScope extends MessagingChannelImpl implements UserScope {

    // the data hash is the SAME OBJECT throughout the child
    protected Parameters<String> data;
    private Identity user;
    private Ref agent;
    protected Scope parentScope;
    private Status status = Status.STARTED;

    public void setId(String id) {
        this.id = id;
    }

    private String id;

    private List<BiConsumer<Scope, Message>> listeners = new ArrayList<>();

    private Map<Long, Pair<AgentMessage, BiConsumer<AgentMessage, AgentResponse>>> responseHandlers =
            Collections
            .synchronizedMap(new HashMap<>());

    //	private Map<Long, Pair<AgentMessage, BiConsumer<AgentMessage, AgentResponse>>> responseHandlers =
    //	Collections
    //			.synchronizedMap(new HashMap<>());

    public BiConsumer<Scope, Message>[] getListeners() {
        return listeners.toArray(new BiConsumer[]{});
    }

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    public ClientScope(Identity user, Scope.Type scopeType, BiConsumer<Scope, Message>... listeners) {
        super(user, null, scopeType);
        this.user = user;
        this.data = Parameters.create();
        this.id = user.getId();
        if (listeners != null) {
            for (var listener : listeners) {
                this.listeners.add(listener);
            }
        }
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


    //	/**
    //	 * Obtain a message to an agent that is set up to intercept a response sent to
    //	 * this channel using send()
    //	 *
    //	 * @param <T>
    //	 * @param messageClass
    //	 * @param handler
    //	 * @return
    //	 */
    //	@SuppressWarnings("unchecked")
    //	protected <T extends AgentMessage> T registerMessage(Class<T> messageClass, BiConsumer<T,
    //	AgentResponse> handler) {
    //
    //		T ret = null;
    //		try {
    //			ret = (T) messageClass.getDeclaredConstructor().newInstance();
    //			this.responseHandlers.put(ret.getId(),
    //					Pair.of((AgentMessage) ret, (BiConsumer<AgentMessage, AgentResponse>) handler));
    //		} catch (Throwable e) {
    //			error(e);
    //		}
    //
    //		return ret;
    //	}

    //	/**
    //	 * Return a future for the result of an agent message which encodes the
    //	 * request/response using AgentMessage/AgentResponse
    //	 *
    //	 * @param <T>
    //	 * @param message
    //	 * @param resultClass
    //	 * @return
    //	 */
    //	protected <T> Future<T> responseFuture(AgentMessage message, Class<T> resultClass) {
    //		Future<T> ret = new FutureTask<T>(new Callable<T>() {
    //
    //			@Override
    //			public T call() throws Exception {
    //				// TODO Auto-generated method stub
    //				return null;
    //			}
    //
    //		});
    //
    //		// TODO enqueue
    //
    //		return ret;
    //	}

    @Override
    public SessionScope runSession(String sessionName) {

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

        var sessionId = runtime.createSession(this, sessionName);

        //		final EngineSessionScope ret = new EngineSessionScope(this);
        //		ret.setStatus(Status.WAITING);
        //		Ref sessionAgent = this.agent.ask(new CreateSession(ret, sessionName), Ref.class);
        //		if (!sessionAgent.isEmpty()) {
        //			ret.setName(sessionName);
        //			ret.setStatus(Status.STARTED);
        //			ret.setAgent(sessionAgent);
        //		} else {
        //			ret.setStatus(Status.ABORTED);
        //		}
        //		return ret;
        return null;
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
    public Ref getAgent() {
        return this.agent;
    }

    public void setAgent(Ref agent) {
        this.agent = agent;
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

}
