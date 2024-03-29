package org.integratedmodelling.klab.api.engine;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Message;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * The k.LAB engine is a service orchestrator that starts and maintains all services used by the scopes in its
 * purview. Its primary role is to provide {@link UserScope}s, of which it can handle one or more. The scopes
 * give access to all authorized services and expose a messaging system that enables listening to authorized
 * events from all services.
 * <p>
 * The engine instantiates user scopes upon authentication or anonymously. Access to services happens through
 * the services handled by the engine through its {@link UserScope#getService(Class)} and
 * {@link UserScope#getServices(Class)} methods. There is no API related to authentication except defining the
 * API model for {@link org.integratedmodelling.klab.api.authentication.KlabCertificate}s.
 * <p>
 * Methods are exposed for booting and shutting down the engine, for situations when implementations need to
 * control these phases. Those should operate harmlessly where a boot phase is not needed. The engine should
 * not boot automatically upon creation; the {@link #isAvailable()} and {@link #isOnline()} can be used to
 * monitor status, ensuring that the engine is online before using the scope for k.LAB activities. The
 * messaging system must correctly report all
 * {@link org.integratedmodelling.klab.api.services.runtime.Message.MessageClass#EngineLifecycle}  and
 * {@link org.integratedmodelling.klab.api.services.runtime.Message.MessageClass#ServiceLifecycle} events.
 * <p>
 * The engine has a simple REST API defined in {@link org.integratedmodelling.klab.api.ServicesAPI.ENGINE} and
 * is authenticated with certificates, so it inherits from {@link KlabService} and it is one of the service
 * categories reported as {@link KlabService.Type}.
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
    void addEventListener(BiConsumer<Scope, Message> eventListener);

    /**
     * To facilitate implementations, we expose the boot and shutdown as explicitly called phases. Booting the
     * engine should start with authentication. Messages should be sent to listeners after authentication and
     * at each new service activation.
     * <p>
     * There is no requirement for the boot to be reentrant so that it can be called multiple times.
     */
    void boot();

}
