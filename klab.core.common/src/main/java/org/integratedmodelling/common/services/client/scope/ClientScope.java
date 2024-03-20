package org.integratedmodelling.common.services.client.scope;

import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessageBus;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
    private boolean errors;

    public void setId(String id) {
        this.id = id;
    }

    private MessageBus messageBus;
    private String id;

    private List<BiConsumer<Scope, Message>> listeners = new ArrayList<>();

    //	private Map<Long, Pair<AgentMessage, BiConsumer<AgentMessage, AgentResponse>>> responseHandlers =
    //	Collections
    //			.synchronizedMap(new HashMap<>());

    public BiConsumer<Scope, Message>[] getListeners() {
        return listeners.toArray(new BiConsumer[]{});
    }

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    public ClientScope(Identity user, BiConsumer<Scope, Message>... listeners) {
        super(user, null);
        this.user = user;
        this.data = Parameters.create();
        this.id = user.getId();
        if (listeners != null) {
            for (var listener : listeners) {
                this.listeners.add(listener);
            }
        }
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

    protected ClientScope(ClientScope parent) {
        super(parent.getIdentity(), parent.messageBus);
        this.user = parent.user;
        this.parentScope = parent;
        this.data = parent.data;
    }

    @Override
    public SessionScope runSession(String sessionName) {

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
    public void info(Object... info) {
        if (!listeners.isEmpty() || messageBus != null) {
            var notification = Notification.create(info);
            send(Message.MessageClass.Notification, Message.MessageType.Info, notification);
        } else {
            Logging.INSTANCE.info(info);
        }
    }

    @Override
    public void warn(Object... o) {
        if (!listeners.isEmpty() || messageBus != null) {
            var notification = Notification.create(o);
            send(Message.MessageClass.Notification, Message.MessageType.Warning, notification);
        } else {
            Logging.INSTANCE.warn(o);
        }
    }

    @Override
    public void error(Object... o) {
        errors = true;
        if (!listeners.isEmpty() || messageBus != null) {
            var notification = Notification.create(o);
            send(Message.MessageClass.Notification, Message.MessageType.Error, notification);
        } else {
            Logging.INSTANCE.error(o);
        }
    }

    @Override
    public void debug(Object... o) {
        if (!listeners.isEmpty() || messageBus != null) {
            var notification = Notification.create(o);
            send(Message.MessageClass.Notification, Message.MessageType.Debug, notification);
        } else {
            Logging.INSTANCE.debug(o);
        }
    }

    @Override
    public void post(Consumer<Message> responseHandler, Object... messageElements) {

        var message = Message.create(this, messageElements);
        for (var listener : listeners) {
            listener.accept(this, message);
        }

        if (messageBus != null) {
            // TODO handle response if handler is != null
            messageBus.post(message);
        }

        /*
         * Agent scopes will intercept the response from an agent and pair it with the
         * response handler. All response handlers are scheduled and executed in
         * sequence.
         */
        //		if (message != null && message.length == 1 && message[0] instanceof AgentResponse) {
        //			Pair<AgentMessage, BiConsumer<AgentMessage, AgentResponse>> handler = responseHandlers
        //					.get(((AgentResponse) message[0]).getId());
        //			if (handler != null) {
        //				executor.execute(() -> {
        //					handler.getSecond().accept(handler.getFirst(), (AgentResponse) message[0]);
        //					if (((AgentResponse)message[0]).isRemoveHandler()) {
        //						responseHandlers.remove(((AgentResponse) message[0]).getId());
        //					}
        //				});
        //			}
        //			return;
        //		} else if (message != null && message.length == 1 && message[0] instanceof VM.AgentMessage) {
        //			/*
        //			 * dispatch to the agent. If there's a handler, make a responseHandler and
        //			 * ensure that it gets a message
        //			 */
        //			if (responseHandler != null) {
        //				// TODO needs an asynchronous ask()
        //				// Message m = Message.create(getIdentity().getId(),
        //				// Message.MessageClass.ActorCommunication, Message.Type.AgentResponse,
        //				// message[0]);
        //				// this.getAgent().ask(m, (VM.AgentMessage)message[0]);
        //			} else {
        //				this.getAgent().tell((VM.AgentMessage) message[0]);
        //			}
        //
        //		} else {
        //			/*
        //			 * usual behavior: make a message and send through whatever channel we have.
        //			 */
        //		}

    }

    @Override
    public void send(Object... message) {
        post(null, message);
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
        return errors;
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
