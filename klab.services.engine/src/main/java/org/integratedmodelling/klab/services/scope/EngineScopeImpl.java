package org.integratedmodelling.klab.services.scope;

import java.util.function.Consumer;

import org.integratedmodelling.klab.api.authentication.scope.SessionScope;
import org.integratedmodelling.klab.api.authentication.scope.SessionScope.Status;
import org.integratedmodelling.klab.api.authentication.scope.UserScope;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.services.actors.messages.user.CreateApplication;
import org.integratedmodelling.klab.services.actors.messages.user.CreateSession;

/**
 * Implementations must fill in the getService() strategy.
 * 
 * @author Ferd
 *
 */
public abstract class EngineScopeImpl implements UserScope {

    private static final long serialVersionUID = 605310381727313326L;

    private Parameters<String> data = Parameters.create();
    private UserIdentity user;
    private Ref agent;
    protected EngineScopeImpl parentScope;

    public EngineScopeImpl(UserIdentity user) {
        this.user = user;
//        ((EngineService) Services.INSTANCE.getEngine()).registerScope(this);
    }

    protected EngineScopeImpl(EngineScopeImpl parent) {
        this.user = parent.user;
        this.parentScope = parent;
    }

    @Override
    public SessionScope runSession(String sessionName) {

        final EngineSessionScopeImpl ret = new EngineSessionScopeImpl(this);
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

        final EngineSessionScopeImpl ret = new EngineSessionScopeImpl(this);
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

    // public void setToken(String token) {
    // this.token = token;
    // }

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
        // TODO Auto-generated method stub

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
