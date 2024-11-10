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
     * Engine settings that can be changed at runtime through the CLI or the API. Most of these are useful for
     * debugging. Using an enum eases validation.
     */
    enum Setting {

        // TODO add these as the need arises
        POLLING("Enable or disable server polling in all service clients", "on", "off"),
        POLLING_INTERVAL("Set the service polling interval in seconds", Integer.class),
        LAUNCH_PRODUCT("Launch a local service if there is no online service and a distribution is " +
                "available", Boolean.class),
        LOG_EVENTS("Log server-side events", Boolean.class);

        // if this is empty, any string value is admitted
        public final String[] values;
        public final Class<?> valueClass;
        public final String description;

        private Setting(String description, Class<?> valueClass) {
            this.description = description;
            this.valueClass = valueClass;
            this.values = new String[]{};
        }

        private Setting(String description, String... stringValues) {
            this.description = description;
            this.values = stringValues;
            this.valueClass = String.class;
        }

        public boolean validate(Object value) {
            if (String.class.equals(valueClass)) {
                if (value instanceof  String && values.length > 0) {
                    for (var v : values) {
                        if (value.equals(v)) {
                            return true;
                        }
                    }
                    return false;
                }
            }
            return value != null && valueClass.isAssignableFrom(value.getClass());
        }
    }

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


    // TODO UserScope login(...)

    /**
     * To facilitate implementations, we expose the boot and shutdown as explicitly called phases. Booting the
     * engine should start with authentication. Messages should be sent to listeners after authentication and
     * at each new service activation.
     * <p>
     * There is no requirement for the boot to be reentrant so that it can be called multiple times.
     */
    void boot();

    /**
     * Return all settings for the engine. This is passed around to clients and any other object for checking
     * when necessary without referencing the engine itself, so it should not be copied and should be a
     * synchronized map. All settings <em>must</em> have a non-null value.
     *
     * @return the current settings.
     */
    Map<Setting, Object> getSettings();

}
