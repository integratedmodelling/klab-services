package org.integratedmodelling.klab.services.scope;

import io.reacted.core.messages.reactors.ReActorStop;
import org.integratedmodelling.common.authentication.scope.ChannelImpl;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.kactors.VM;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.services.runtime.kactors.AgentMessage;
import org.integratedmodelling.klab.api.services.runtime.kactors.AgentResponse;
import org.integratedmodelling.klab.services.actors.messages.kactor.RunBehavior;
import org.integratedmodelling.klab.services.actors.messages.user.CreateApplication;
import org.integratedmodelling.klab.services.actors.messages.user.CreateSession;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Implementations must fill in the getService() strategy. This is a scope that
 * contains an agent ref. Any communication with the agent will pass the scope,
 * so if the agent is remote the scope must be reconstructed from authorization
 * tokens into something that maintains communication with the original one.
 * <p>
 * Each scope contains a hash of generic data. Creating "child" scopes will only
 * build a new hash when the scope is of a different class, otherwise the same
 * data is passed to all children.
 * 
 * @author Ferd
 *
 */
public abstract class EngineScope extends ChannelImpl implements UserScope {

	// the data hash is the SAME OBJECT throughout the child
	protected Parameters<String> data;
	private UserIdentity user;
	private Ref agent;
	protected EngineScope parentScope;
	private Status status = Status.STARTED;

	public void setId(String id) {
		this.id = id;
	}

	private String id;

	private Map<Long, Pair<AgentMessage, BiConsumer<AgentMessage, AgentResponse>>> responseHandlers = Collections
			.synchronizedMap(new HashMap<>());

	private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

	public EngineScope(UserIdentity user) {
		super(user);
		this.user = user;
		this.data = Parameters.create();
		this.id = user.getId();
	}

	/**
	 * Obtain a message to an agent that is set up to intercept a response sent to
	 * this channel using send()
	 *
	 * @param <T>
	 * @param messageClass
	 * @param handler
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected <T extends AgentMessage> T registerMessage(Class<T> messageClass, BiConsumer<T, AgentResponse> handler) {

		T ret = null;
		try {
			ret = (T) messageClass.getDeclaredConstructor().newInstance();
			this.responseHandlers.put(ret.getId(),
					Pair.of((AgentMessage) ret, (BiConsumer<AgentMessage, AgentResponse>) handler));
		} catch (Throwable e) {
			error(e);
		}

		return ret;
	}

	/**
	 * Return a future for the result of an agent message which encodes the
	 * request/response using AgentMessage/AgentResponse
	 *
	 * @param <T>
	 * @param message
	 * @param resultClass
	 * @return
	 */
	protected <T> Future<T> responseFuture(AgentMessage message, Class<T> resultClass) {
		Future<T> ret = new FutureTask<T>(new Callable<T>() {

			@Override
			public T call() throws Exception {
				// TODO Auto-generated method stub
				return null;
			}

		});

		// TODO enqueue

		return ret;
	}

	protected EngineScope(EngineScope parent) {
		super(parent.user);
		this.user = parent.user;
		this.parentScope = parent;
		this.data = parent.data;
	}

	@Override
	public SessionScope runSession(String sessionName) {

		final EngineSessionScope ret = new EngineSessionScope(this);
		ret.setStatus(Status.WAITING);
		Ref sessionAgent = this.agent.ask(new CreateSession(ret, sessionName), Ref.class);
		if (!sessionAgent.isEmpty()) {
			ret.setName(sessionName);
			ret.setStatus(Status.STARTED);
			ret.setAgent(sessionAgent);
		} else {
			ret.setStatus(Status.ABORTED);
		}
		return ret;
	}

	@Override
	public SessionScope run(String behaviorName, KActorsBehavior.Type behaviorType) {

		final EngineSessionScope ret = new EngineSessionScope(this);
		ret.setStatus(Status.WAITING);
		Ref sessionAgent = this.agent.ask(new CreateApplication(ret, behaviorName, behaviorType), Ref.class);
		if (!sessionAgent.isEmpty()) {
			ret.setStatus(Status.STARTED);
			ret.setAgent(sessionAgent);
			ret.setName(behaviorName);
			sessionAgent.tell(new RunBehavior(behaviorName));
		} else {
			ret.setStatus(Status.ABORTED);
		}
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

	@Override
	public Ref getAgent() {
		return this.agent;
	}

	public void setAgent(Ref agent) {
		this.agent = agent;
	}

	@Override
	public void info(Object... info) {
		// TODO Auto-generated method stub
		Logging.INSTANCE.info(info);
	}

	@Override
	public void warn(Object... o) {
		// TODO Auto-generated method stub
		Logging.INSTANCE.warn(o);
	}

	@Override
	public void error(Object... o) {
		// TODO Auto-generated method stub
		Logging.INSTANCE.error(o);
	}

	@Override
	public void debug(Object... o) {
		// TODO Auto-generated method stub
		Logging.INSTANCE.debug(o);
	}

	@Override
	public Message post(Consumer<Message> responseHandler, Object... message) {

		/*
		 * Agent scopes will intercept the response from an agent and pair it with the
		 * response handler. All response handlers are scheduled and executed in
		 * sequence.
		 */
		if (message != null && message.length == 1 && message[0] instanceof AgentResponse) {
			Pair<AgentMessage, BiConsumer<AgentMessage, AgentResponse>> handler = responseHandlers
					.get(((AgentResponse) message[0]).getId());
			if (handler != null) {
				executor.execute(() -> {
					handler.getSecond().accept(handler.getFirst(), (AgentResponse) message[0]);
					if (((AgentResponse)message[0]).isRemoveHandler()) {
						responseHandlers.remove(((AgentResponse) message[0]).getId());
					}
				});
			}
			return null;
		} else if (message != null && message.length == 1 && message[0] instanceof VM.AgentMessage) {
			/*
			 * dispatch to the agent. If there's a handler, make a responseHandler and
			 * ensure that it gets a message
			 */
			if (responseHandler != null) {
				// TODO needs an asynchronous ask()
				// Message m = Message.create(getIdentity().getId(),
				// Message.MessageClass.ActorCommunication, Message.Type.AgentResponse,
				// message[0]);
				// this.getAgent().ask(m, (VM.AgentMessage)message[0]);
			} else {
				this.getAgent().tell((VM.AgentMessage) message[0]);
			}

		} else {
			return super.post(responseHandler, message);
		}

		return null;

	}

	@Override
	public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
		// TODO if a service resolver is available, that should be used.
		return Collections.singleton(getService(serviceClass));
	}

	@Override
	public Message send(Object... message) {
		return post(null, message);
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

//	@Override
	public void stop() {
		agent.tell(ReActorStop.STOP);
		this.data.clear();
		this.agent = null;
		setStatus(Status.EMPTY);
	}

	@Override
	public void switchService(KlabService service) {
		// TODO, or just avoid in this implementation.
	}
}
