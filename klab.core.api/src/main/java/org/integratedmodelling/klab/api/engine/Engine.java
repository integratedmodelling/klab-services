package org.integratedmodelling.klab.api.engine;

import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Message;

import java.util.function.BiConsumer;

/**
 * The k.LAB engine is a service orchestrator that has "current" and "available" services for all categories.
 * It has methods to switch services and a messaging system that reacts to events from all services.
 * <p>
 * The engine always operates within an authenticated (possibly anonymous) user scope, which gives access to
 * the services selected in the engine through its {@link UserScope#getService(Class)} and
 * {@link UserScope#getServices(Class)} methods.
 * <p>
 * Methods are exposed for booting and shutting down the engine, for situations when implementations need to
 * control these phases. The engine should not boot automatically upon creation.
 */
public interface Engine {


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

    /**
     * To facilitate implementations, we expose the boot and shutdown as explicitly called phases. The
     * shutdown phases should do what's appropriate also with the services (e.g. invoke a cleanup and shutdown
     * phases if the service is embedded). The engine must be non-functional after this is called and all
     * methods should throw {@link org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException}
     * exceptions.
     */
    void shutdown();

}
