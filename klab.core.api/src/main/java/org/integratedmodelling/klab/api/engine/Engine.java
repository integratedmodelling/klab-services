package org.integratedmodelling.klab.api.engine;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Message;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * The k.LAB engine is a service orchestrator that has "current" and "available" services for all categories.
 * It handles one or more users and contains a messaging system that enables listening to events from all
 * services.
 * <p>
 * The engine instantiates user scopes upon authentication (or anonymously), enabling them to access the
 * services handled by the engine through its {@link UserScope#getService(Class)} and
 * {@link UserScope#getServices(Class)} methods. In this interface there is no API related to authentication
 * of user scopes, which can be implemented as needed downstream.
 * <p>
 * Methods are exposed for booting and shutting down the engine, for situations when implementations need to
 * control these phases. The engine should not boot automatically upon creation; the {@link #isAvailable()}
 * and {@link #isOnline()} can be used to monitor status, and the messaging system must report all
 * {@link org.integratedmodelling.klab.api.services.runtime.Message.MessageClass#EngineLifecycle} events.
 */
public interface Engine extends KlabService {

    /**
     * The engine is available to boot.
     *
     * @return
     */
    boolean isAvailable();

    /**
     * The engine has booted successfully and it's available for use.
     *
     * @return
     */
    boolean isOnline();

    /**
     * Return all the user scopes currently connected to the engine.
     *
     * @return
     */
    List<UserScope> getUsers();

    /**
     * Event listeners can be added to react to anything happening and must be propagated to the services.
     * This can be called before or after boot. For now there is no provision to remove listeners.
     *
     * @param eventListeners
     */
    void addEventListener(BiConsumer<Scope, Message>... eventListeners);

    /**
     * To facilitate implementations, we expose the boot and shutdown as explicitly called phases. Booting the
     * engine should start with authentication. Messages should be sent to listeners after authentication and
     * at each new service activation.
     * <p>
     * There is no requirement for the boot to be reentrant so that it can be called multiple times.
     */
    void boot();

}
