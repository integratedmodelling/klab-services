package org.integratedmodelling.klab.api.engine;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.engine.distribution.Stack;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;

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
 * <p>The engine detects and exposes k.LAB local {@link
 * org.integratedmodelling.klab.api.engine.distribution.Distribution} and if one is present, methods
 * are exposed for booting and shutting down local services, which are transparently added to the
 * list of available services for all scopes. If the user has a compiled source distribution in its
 * filesystem that can be found in the standard ~/git/klab-services directory, that takes over the
 * downloaded k.LAB distribution. The local distribution is able to operate, in limited mode, even
 * if the engine runs in anonymous scope without a certificate or with an expired one.
 *
 * <p>All service events visible to the service clients are reported through the user scopes that
 * own them. In turn, the events are dispatched to the Engine's own service scope. Users of the
 * engine API can listen to all relevant events by installing specific listeners directly on the
 * other scopes exposed. The engine will not re-broadcast events below the user level.
 *
 * <p>Engine functions can be exposed through the simple REST API defined in {@link
 * org.integratedmodelling.klab.api.ServicesAPI.ENGINE} and is a {@link KlabService} to ensure it
 * can be implemented as a service; for this reason <code>ENGINE</code> is one of the service
 * categories listed as {@link KlabService.Type}.
 */
public interface Engine /*extends KlabService*/ {

  /**
   * Comprehensive engine status is kept up to date by polling or listening to local services.
   * Whenever the status changes, either because of service lifecycle or because of the user
   * choosing a different service as the current one, a message is sent (intercepted by the modeler
   * and also sent to the UI).
   */
  interface Status extends KlabService.ServiceStatus {

    /**
     * Each service type can be in any one of these states. Computed at each service change and used
     * to inform clients of what they can do or visualize.
     */
    enum ServiceProvision {
      /** Nothing is available */
      INOPERATIVE(false, false),
      /** A single operational service is available remotely, none locally. */
      REMOTE_SINGLE(false, true),
      /** Multiple operational services are available remotely, none locally. */
      REMOTE_MULTI(false, true),
      /** A single operational service is available, none remotely */
      LOCAL_SINGLE(true, true),
      /** A single operational service is available, one remotely */
      LOCAL_REMOTE_SINGLE(true, true),
      /** A single operational service is available, more than one remotely */
      LOCAL_REMOTE_MULTI(true, true),
      /** A single non-operational service is available, none remotely */
      LOCAL_INOP_SINGLE(true, false),
      /** A single non-operational service is available, one remotely */
      LOCAL_INOP_REMOTE_SINGLE(true, false),
      /** A single non-operational service is available, more than one remotely */
      LOCAL_INOP_REMOTE_MULTI(true, false);

      final boolean local;
      final boolean operational;

      ServiceProvision(boolean local, boolean operational) {
        this.local = local;
        this.operational = operational;
      }

      public boolean isLocal() {
        return local;
      }

      public boolean isOperational() {
        return operational;
      }
    }

    enum EngineCondition {
      INOPERATIVE,
      TRANSITIONING,
      ACTIVE_REMOTE_ONLY,
      ACTIVE_LOCAL_ONLY,
      ACTIVE_LOCAL_AND_REMOTE,
    }

    Map<KlabService.Type, ServiceProvision> getServicesProvision();

    EngineCondition getCondition();

    /**
     * Return the current status of each specific service. If the service is not even connected, a
     * non-null inactive status is returned.
     *
     * @return
     */
    Map<KlabService.Type, KlabService.ServiceStatus> getServicesStatus();

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

  UserScope authenticate(KlabCertificate certificate);

  /**
   * The engine has booted successfully and it's available for use.
   *
   * @return
   */
  boolean isOnline();

  //  Distribution.Status getDistributionStatus();

  /**
   * Return all the user scopes currently connected to the engine.
   *
   * @return
   */
  List<UserScope> getUsers();

  /**
   * The engine runs under a valid certificate. The owning user scope serves as a service scope for
   * the engine.
   *
   * @return
   */
  UserScope getOwner();

  boolean shutdown();

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
  Map<KlabService.Type, KlabService> startLocalServices();

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
  Settings getSettings();

  /**
   * Return the k.LAB software stack configured for this engine.
   *
   * @return
   */
  Stack getSoftwareStack();

  /**
   * Return the distribution tag for the software stack. The default should be {@link
   * Stack.Tag#LATEST_STABLE}.
   *
   * @return
   */
  Stack.Tag getDistributionTag();

  /**
   * Return true if the software stack is valid and is tuned on a locally verifiable distribution
   *
   * @return
   */
  boolean hasValidSoftwareStack();
}
