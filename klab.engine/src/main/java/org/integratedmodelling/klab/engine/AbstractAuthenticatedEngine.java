package org.integratedmodelling.klab.engine;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * An abstract engine implementation that operates under one or more {@link UserScope}s, the first one
 * authenticated at boot and "privileged" in methods that assume a "default" user so that single-user engines
 * can be easily implemented. Implementations will need to provide the services and the authentication
 * logics.
 * <p>
 * The engine catches messages from the services and maintains an up-to-date catalog of user-indexed sessions
 * and contexts in them. The last session and context created by each user are set as "current" and can be
 * retrieved quickly.
 */
public abstract class AbstractAuthenticatedEngine implements Engine {

    List<UserScope> users = new ArrayList<>();
    List<BiConsumer<Scope, Message>> listeners = Collections.synchronizedList(new ArrayList<>());

    /**
     * Authenticate the default user. If this returns null, the engine won't have a default user after boot,
     * and services won't be available.
     *
     * @return
     */
    protected abstract UserScope authenticate();

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
    public void addEventListener(BiConsumer<Scope, Message>... eventListeners) {
        for (var listener : eventListeners) {
            listeners.add(listener);
        }
    }

    @Override
    public void boot() {
        var user = authenticate();
        if (user != null) {
            users.add(user);
        }
    }

    @Override
    public void shutdown() {

    }
}
