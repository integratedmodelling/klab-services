package org.integratedmodelling.klab.services.scope;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.integratedmodelling.klab.api.authentication.scope.SessionScope;
import org.integratedmodelling.klab.api.authentication.scope.UserScope;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.services.actors.messages.AgentMessage;
import org.integratedmodelling.klab.services.actors.messages.AgentResponse;
import org.integratedmodelling.klab.services.actors.messages.user.CreateApplication;
import org.integratedmodelling.klab.services.actors.messages.user.CreateSession;

/**
 * Implementations must fill in the getService() strategy. This is a scope that contains an agent
 * ref. Any communication with the agent will pass the scope, so if the agent is remote the scope
 * must be serialized to something that maintains communication with the original one.
 * 
 * @author Ferd
 *
 */
public abstract class EngineScope implements UserScope {

    private Parameters<String> data = Parameters.create();
    private UserIdentity user;
    private Ref agent;
    protected EngineScope parentScope;
    private Map<Long, Pair<AgentMessage, BiConsumer<AgentMessage, AgentResponse>>> responseHandlers = Collections
            .synchronizedMap(new HashMap<>());

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    public EngineScope(UserIdentity user) {
        this.user = user;
//        EngineService.INSTANCE.getEngine().registerScope(this);
    }

    /**
     * Obtain a message to an agent that is set up to intercept a response sent to this channel
     * using send()
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
     * Return a future for the result of an agent message which encodes the request/response using
     * AgentMessage/AgentResponse
     * 
     * @param <T>
     * @param message
     * @param resultClass
     * @return
     */
    protected <T> Future<T> responseFuture(AgentMessage message, Class<T> resultClass) {
        Future<T> ret = new FutureTask<T>(new Callable<T>(){

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
        this.user = parent.user;
        this.parentScope = parent;
    }

    @Override
    public SessionScope runSession(String sessionName) {

        final EngineSessionScope ret = new EngineSessionScope(this);
        ret.setStatus(Status.WAITING);
        Ref sessionAgent = this.agent.ask(new CreateSession(this, sessionName), Ref.class);
        if (!sessionAgent.isEmpty()) {
            ret.setStatus(Status.STARTED);
            ret.setAgent(sessionAgent);
        } else {
            ret.setStatus(Status.ABORTED);
        }
        return ret;
    }

    @Override
    public SessionScope runApplication(String behaviorName) {

        final EngineSessionScope ret = new EngineSessionScope(this);
        ret.setStatus(Status.WAITING);
        Ref sessionAgent = this.agent.ask(new CreateApplication(this, behaviorName), Ref.class);
        if (!sessionAgent.isEmpty()) {
            ret.setStatus(Status.STARTED);
            ret.setAgent(sessionAgent);
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

    public Ref getAgent() {
        return this.agent;
    }

    public void setAgent(Ref agent) {
        this.agent = agent;
    }

    @Override
    public void info(Object... info) {
        // TODO Auto-generated method stub

    }

    @Override
    public void warn(Object... o) {
        // TODO Auto-generated method stub

    }

    @Override
    public void error(Object... o) {
        // TODO Auto-generated method stub

    }

    @Override
    public void debug(Object... o) {
        // TODO Auto-generated method stub

    }

    @Override
    public void send(Object... message) {

        /*
         * Agent scopes will intercept the response from an agent and pair it with the response
         * handler. All response handlers are scheduled and executed in sequence.
         */
        if (message != null && message.length == 1 && message[0] instanceof AgentResponse) {
            Pair<AgentMessage, BiConsumer<AgentMessage, AgentResponse>> handler = responseHandlers
                    .get(((AgentResponse) message[0]).getId());
            if (handler != null) {
                executor.execute(() -> {
                    handler.getSecond().accept(handler.getFirst(), (AgentResponse) message[0]);
                    responseHandlers.remove(((AgentResponse)message[0]).getId());
                });
            }
            return;
        }

    }

    @Override
    public void addWait(int seconds) {
        // TODO Auto-generated method stub

    }

    @Override
    public int getWaitTime() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean isInterrupted() {
        // TODO Auto-generated method stub
        return false;
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
    public void post(Consumer<Message> handler, Object... message) {
        // TODO Auto-generated method stub

    }

}
