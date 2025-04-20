package org.integratedmodelling.klab.api.engine;

import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The k.LAB engine is a service orchestrator that maintains scopes and clients for all services
 * used by these scopes. Its primary role is to create and maintain {@link UserScope}s, of which it
 * can handle one or more. The scopes give access to all authorized services and expose a messaging
 * system that enables listening to authorized events from all services. This engine implementation
 * is meant to be lightweight (depending only on the API and commons packages) to be embedded into
 * applications such as command-line or graphical IDEs.
 *
 * <p>The engine instantiates user scopes upon authentication, or anonymously. All scopes access
 * their services through the {@link UserScope#getService(Class)} and {@link
 * UserScope#getServices(Class)} methods. There is no specific API related to authentication, except
 * defining the model for {@link org.integratedmodelling.klab.api.authentication.KlabCertificate}s.
 *
 * <p>The engine detects and exposes k.LAB local {@link Distribution} and if one is present, methods
 * are exposed for booting and shutting down local services, which are transparently added to the
 * list of available services for all scopes. If the user has a compiled source distribution in its
 * filesystem that can be found in the standard ~/git/klab-services directory, that takes over the
 * downloaded k.LAB distribution. The local distribution is able to operate, in limited mode, even
 * if the engine runs in anonymous scope without a certificate or with an expired one.
 *
 * <p>All service events visible to the service clients are reported through the user scopes that
 * own them. In turn, the events are dispatched to the Engine's own service scope. Users of the
 * engine API can listen to all relevant events using the {@link #serviceScope()} handle, or they
 * can install specific listeners directly on the other scopes exposed. The engine will not
 * re-broadcast events below the user level.
 *
 * <p>Engine functions can be exposed through the simple REST API defined in {@link
 * org.integratedmodelling.klab.api.ServicesAPI.ENGINE} and is a {@link KlabService} to ensure it
 * can be implemented as a service; for this reason <code>ENGINE</code> is one of the service
 * categories listed as {@link KlabService.Type}.
 */
public interface Engine extends KlabService {

  /**
   * Engine settings that can be changed at runtime through the CLI or the API. Most of these are
   * useful for debugging. Using an enum eases validation.
   */
  enum Setting {

    // TODO add these as the need arises
    POLLING("Enable or disable server polling in all service clients", "on", "off"),
    POLLING_INTERVAL("Set the service polling interval in seconds", Integer.class),
    LAUNCH_PRODUCT(
        "Launch a local service if there is no online service and a distribution is " + "available",
        Boolean.class),
    LOG_EVENTS("Log server-side events", Boolean.class);

    // if this is empty, any string value is admitted
    public final String[] values;
    public final Class<?> valueClass;
    public final String description;

    Setting(String description, Class<?> valueClass) {
      this.description = description;
      this.valueClass = valueClass;
      this.values = new String[] {};
    }

    Setting(String description, String... stringValues) {
      this.description = description;
      this.values = stringValues;
      this.valueClass = String.class;
    }

    public boolean validate(Object value) {
      if (String.class.equals(valueClass)) {
        if (value instanceof String && values.length > 0) {
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
   * Comprehensive engine status is kept up to date by polling or listening to local services.
   * Whenever the status changes, either because of service lifecycle or because of the user
   * choosing a different service as the current one, a message is sent (intercepted by the modeler
   * and also sent to the UI).
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
     * User names for all users that have currently active scopes. List may be filtered according to
     * who's asking.
     *
     * @return
     */
    Collection<String> getConnectedUsernames();

  }

  /**
   * The engine must be authenticated and have a "default" user, even if more users are created
   * afterwards. This can be called explicitly before {@link #boot()} if the API user wants to
   * screen the default user. If it was not called, {@link #boot()} must invoke it before anything
   * else is done.
   *
   * <p>This should be callable more than once without consequences.
   *
   * @return
   */
  UserScope authenticate();

  /**
   * The engine has booted successfully and it's available for use.
   *
   * @return
   */
  boolean isOnline();

  Distribution.Status getDistributionStatus();

  /**
   * Return all the user scopes currently connected to the engine.
   *
   * @return
   */
  List<UserScope> getUsers();

  /**
   * Stop any local services that were started by calling {@link #startLocalServices()}. This does
   * not wait for the services to stop.
   *
   * @return the number of service shutdowns initiated
   */
  int stopLocalServices();

  /**
   * Start all available local services and return them categorized by type. The services are added
   * to the available for the scopes.
   *
   * @return
   */
  Map<Type, KlabService> startLocalServices();

  /**
   * To facilitate implementations, we expose the boot and shutdown as explicitly called phases.
   * Booting the engine should start with authentication. Messages should be sent to listeners after
   * authentication and at each new service activation.
   *
   * <p>There is no requirement for the boot to be reentrant so that it can be called multiple
   * times.
   */
  void boot();

  /**
   * Return all settings for the engine. This is passed around to clients and any other object for
   * checking when necessary without referencing the engine itself, so it should not be copied and
   * should be a synchronized map. All settings <em>must</em> have a non-null value.
   *
   * @return the current settings.
   */
  Map<Setting, Object> getSettings();
}
