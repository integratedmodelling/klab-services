package org.integratedmodelling.klab.api.engine;

import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The k.LAB engine is a service orchestrator that maintains scopes and the services used by these scopes. Its
 * primary role is to provide {@link UserScope}s, of which it can handle one or more. The scopes give access
 * to all authorized services and expose a messaging system that enables listening to authorized events from
 * all services.
 * <p>
 * The engine instantiates user scopes upon authentication or anonymously. Access to services happens through
 * the {@link UserScope#getService(Class)} and {@link UserScope#getServices(Class)} methods. There is no
 * specific API related to authentication, except defining the model for
 * {@link org.integratedmodelling.klab.api.authentication.KlabCertificate}s.
 * <p>
 * Methods are exposed for booting and shutting down the engine, for situations when implementations need to
 * control these phases. Those should operate harmlessly where a boot phase is not needed. The engine should
 * not boot automatically upon creation; the {@link #isAvailable()} and {@link #isOnline()} can be used to
 * monitor status, ensuring that the engine is online before using the scope for k.LAB activities. The
 * messaging system must correctly report all
 * {@link org.integratedmodelling.klab.api.services.runtime.Message.MessageClass#EngineLifecycle}  and
 * {@link org.integratedmodelling.klab.api.services.runtime.Message.MessageClass#ServiceLifecycle} events.
 * <p>
 * Engine functions can be exposed through the simple REST API defined in
 * {@link org.integratedmodelling.klab.api.ServicesAPI.ENGINE} and is a {@link KlabService} to ensure it can
 * be implemented as a service; for this reason <code>ENGINE</code> is one of the service categories listed as
 * {@link KlabService.Type}.
 */
public interface Engine extends KlabService {

    /**
     * Comprehensive engine status is kept up to date by polling or listening to services. Whenever the status
     * changes, either because of service lifecycle or because of the user choosing a different service as the
     * current one, a message is sent (intercepted by the modeler and also sent to the UI).
     */
    interface Status extends ServiceStatus {

        /**
         * Return the current status of each specific service. If the service is not even connected, a
         * non-null inactive status is returned.
         *
         * @param serviceType
         * @return
         */
        Map<Type, ServiceStatus> getServicesStatus();

        /**
         * User names for all users that have currently active scopes. List may be filtered according to who's
         * asking.
         *
         * @return
         */
        Collection<String> getConnectedUsernames();

        /**
         * Return service capabilities for all known current services. Capabilities may be filtered according
         * to who's asking.
         *
         * @return
         */
        Map<Type, ServiceCapabilities> getServicesCapabilities();

    }

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
     * To facilitate implementations, we expose the boot and shutdown as explicitly called phases. Booting the
     * engine should start with authentication. Messages should be sent to listeners after authentication and
     * at each new service activation.
     * <p>
     * There is no requirement for the boot to be reentrant so that it can be called multiple times.
     */
    void boot();

    @Override
    default boolean scopesAreReactive() {
        return false;
    }

}
