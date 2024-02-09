package org.integratedmodelling.klab.engine;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EventListener;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * An abstract engine implementation that operates under a single {@link UserScope} authenticated on boot.
 * Implementations will need to provide the services and the authentication logics.
 */
public abstract class AbstractAuthenticatedEngine implements Engine {

    UserScope user;
    List<BiConsumer<Scope, Message>> listeners = Collections.synchronizedList(new ArrayList<>());

    protected abstract UserScope authenticate();

    public UserScope getUser() {
        return this.user;
    }

    @Override
    public void addEventListener(BiConsumer<Scope, Message>... eventListeners) {
        for (var listener : eventListeners) {
            listeners.add(listener);
        }
    }

    @Override
    public void boot() {

    }

    @Override
    public void shutdown() {

    }
}
