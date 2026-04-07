package org.integratedmodelling.klab.api.services;

import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URL;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;

/**
 * Services may be locally implemented or clients to remote services: each service implementation
 * should provide both forms. The latter ones must publish a URL. In all cases they are added in
 * serialized form to ResourceSet and other responses, so they should abide to Java bean conventions
 * and only use set/get methods to expose fields that are themselves serializable. All service
 * methods should NOT use getXxx/setXxx syntax.
 *
 * <p>The API of a service is designed in a way that the serialized version of a full service can
 * deserialize directly to a service client that communicates with it.
 *
 * @author Ferd
 */
public interface KlabService extends Service {

  /**
   * A set of services is identified in the hub response to certificate authentication, both for
   * engine (user-level) certificates and for service (partner-level) certificates (the latter is an
   * addition w.r.t. k.LAB 0.11). The service descriptor in the hub response should have a
   * serviceType field that is one of these; if the field isn't there, LEGACY_NODE is assumed.
   */
  enum Type {

    /** */
    REASONER(8091),

    /** */
    RESOURCES(8092),

    /** */
    RESOLVER(8093),

    /** */
    RUNTIME(8094),

    /**
     * The database port is for maintenance and health checks and is only used in a local
     * configuration. The port is not used for database communication, which happens through other
     * configured protocols.
     */
    DATABASE(8382),

    /** The LSP server operates through I/O redirection, not REST */
    LANGUAGE_SERVER(0),

    /**
     * The AMQP broker exposes the amqp:// protocol to the outside world and is configured
     * separately for now.'
     */
    AMQP_BROKER(0),

    /**
     * The engine is an orchestrator of other k.LAB Services and a provider of scopes at user level
     * and below. It serves the public observation API. It's not implemented as a service in the
     * base stack.
     */
    ENGINE(8283),

    /**
     * Discovery service for other services. To be implemented. When a certificate provides a
     * discovery service, it should interact with the community service and used to supply the other
     * services, including filtering by worldview and allowing global refactoring.
     */
    DISCOVERY(8096),

    /**
     * These are the pre-1.0 nodes and may or may not be used at some point in the transition to
     * 1.0.
     */
    LEGACY_NODE(8287),
    /** TODO: change to LEGACY_NODE To do it, all actual node certificate must change */
    NODE(8287);

    public int defaultPort;
    public String defaultServicePath;

    private Type(int defaultPort) {
      this.defaultServicePath = this.name().toLowerCase();
      this.defaultPort = defaultPort;
    }

    public URL localServiceUrl() {
      try {
        return new URI("http://127.0.0.1:" + defaultPort + "/" + defaultServicePath).toURL();
      } catch (Exception e) {
        // naah
        throw new RuntimeException(e);
      }
    }

    public Class<? extends KlabService> classify() {
      return switch (this) {
        case REASONER -> Reasoner.class;
        case RESOURCES -> ResourcesService.class;
        case RESOLVER -> Resolver.class;
        case RUNTIME -> RuntimeService.class;
        case DISCOVERY, LEGACY_NODE, NODE, ENGINE, DATABASE, AMQP_BROKER, LANGUAGE_SERVER -> null;
      };
    }

    public static Type classify(KlabService service) {
      return switch (service) {
        case ResourcesService s -> RESOURCES;
        case RuntimeService s -> RUNTIME;
        case Reasoner s -> REASONER;
        case Resolver s -> RESOLVER;
        default -> null;
      };
    }

    public static <T extends KlabService> Type classify(Class<T> serviceClass) {
      if (Reasoner.class.isAssignableFrom(serviceClass)) {
        return Type.REASONER;
      } else if (ResourcesService.class.isAssignableFrom(serviceClass)) {
        return Type.RESOURCES;
      } else if (Resolver.class.isAssignableFrom(serviceClass)) {
        return Type.RESOLVER;
      } else if (RuntimeService.class.isAssignableFrom(serviceClass)) {
        return Type.RUNTIME;
      }
      throw new KlabIllegalArgumentException(
          "Unexpected service class " + serviceClass.getCanonicalName());
    }

    public static Set<Type> operationCritical() {
      return EnumSet.of(RESOURCES, REASONER, RESOLVER, RUNTIME);
    }
  }

  /**
   * Service status should be cheap to obtain and may be polled by monitoring clients to visualize
   * service status at regular intervals. Only the known fields may be reported, with negative
   * values representing unknown values.
   */
  interface ServiceStatus extends Serializable {

    KlabService.Type getServiceType();

    String getServiceId();

    /**
     * Available means that the service has been initialized and is accepting connections. It does
     * not mean that the API is fully functional: it may not have gathered enough information from
     * other services, or it may be configured in ways that make it inoperative. Use {@link
     * #isOperational()} to check for that, and/or use the capabilities to check what functions are
     * supported.
     *
     * @return
     */
    boolean isAvailable();

    /**
     * Operational means that the entire API of the service is available to support the functions
     * declared as supported in the capabilities.
     *
     * @return
     */
    boolean isOperational();

    /**
     * Busy status should be temporary. Requests will be queued so checking this should not be
     * routinely necessary.
     *
     * @return
     */
    boolean isBusy();

    int getHealthPercentage();

    /**
     * Load is actually on a 0-1000 basis, not 0-100.
     *
     * @return
     */
    int getLoadPercentage();

    long getMemoryAvailableBytes();

    long getMemoryUsedBytes();

    int getConnectedSessionCount();

    int getKnownSessionCount();

    long getUptimeMs();

    boolean isShutdown();

    List<Notification> getAdvisories();

    Metadata getMetadata();

    static ServiceStatus offline(Type serviceType, String serviceId) {
      var ret = new ServiceStatusImpl();
      ret.setServiceType(serviceType);
      ret.setServiceId(serviceId);
      return ret;
    }

    default boolean hasChangedComparedTo(ServiceStatus statusBeforeChecking) {
      return this.isAvailable() != statusBeforeChecking.isAvailable()
          || this.isBusy() != statusBeforeChecking.isBusy()
          || this.isConnected() != statusBeforeChecking.isConnected()
          || this.isOperational() != statusBeforeChecking.isOperational()
          || !this.getAdvisories().equals(statusBeforeChecking.getAdvisories());
    }

    /**
     * Always true for service side, false at client side if there's no connection to the remote
     * service.
     *
     * @return
     */
    boolean isConnected();
  }

  /**
   * At the very minimum, each service advertises its type, an instance ID and a local name. There
   * is also a secret key that's not advertised but can be read by clients on the same machine.
   *
   * @author Ferd
   */
  interface ServiceCapabilities extends Serializable {

    Type getType();

    String getServiceName();

    /**
     * The service ID should be unique and generated at the first service boot, then persisted in
     * configuration so it never changes for as long as the service exists.
     *
     * @return
     */
    String getServiceId();

    /**
     * A unique server ID implemented as a hash based on the hardware and persisted in configuration
     * so it never changes.
     *
     * @return
     */
    String getServerId();

    /**
     * The URL may be null in an embedded server. Obviously getUrl should never be called in that
     * case, so this should throw an exception rather than returning null.
     *
     * @return
     */
    URL getUrl();

    Map<String, List<ResourceTransport.Schema>> getExportSchemata();

    Map<String, List<ResourceTransport.Schema>> getImportSchemata();

    List<Extensions.ComponentDescriptor> getComponents();
  }

  /**
   * Each service publishes capabilities, overridden to the specific capability class for each
   * service.
   *
   * @param scope the scope under which capabilities are computed. The scope can be null or generic,
   *     which will return general capabilities without scope-specific resources or services.
   * @return
   */
  ServiceCapabilities capabilities(Scope scope);

  /**
   * The service status should be cheap to obtain and small enough to enable multiple and frequent
   * polling. Contents should be prioritized in favor of efficiency.
   *
   * @return
   */
  ServiceStatus status();

  /**
   * Get the URL to this service. If this is null, the service cannot be used except through direct
   * injection. If it's a local URL, it can only be used locally. All these properties will be
   * reflected in the service scope. Otherwise, this could be a full-fledged service or a client for
   * one, and will speak the service API.
   *
   * @return
   */
  URL getUrl();

  /**
   * Service name should be unique within a cluster, with the pattern <code><partnerId.serviceName
   * </code> for public services, and with the username of the running user for local ones.
   * Uniqueness is important and should be enforced, but for critical operations the {@link
   * #serviceId()} must always be used.
   *
   * @return
   */
  String serviceName();

  /**
   * The service ID is an ugly, unique string that uniquely identifies a server instance. Any
   * clients that use a service must report the same ID as the remote service. The service ID is
   * also available in the {@link ServiceCapabilities#serviceId()} but it must be public and
   * available for quick access so it's also exposed in the core service API.
   *
   * @return the service ID. Never null.
   */
  String serviceId();

  /**
   * The service should expose its configurable settings so that its users can check for them. In
   * services, these are managed through the settings admin API.
   *
   * @return settings, possibly empty
   */
  Settings settings();

  /**
   * Change a setting on the service and return a future for the value once the change is complete.
   *
   * <p>TODO also add a get() API as the settings() should be local to the service implementation
   *
   * @param setting
   * @param value
   * @param returnType
   * @return
   * @param <T>
   */
  <T> CompletableFuture<T> set(Setting setting, Object value, Class<T> returnType);

  /**
   * Each service operates under a root scope that is used to report issues, talk to clients and
   * derive child scopes for users and when appropriate, sessions and contexts. The service scope
   * may be a {@link ServiceScope} except in the {@link Engine} service, which may run under a
   * {@link UserScope} or a according to who owns and operates the engine.
   *
   * @return
   */
  Scope serviceScope();

  /**
   * All services provide a shutdown call to clean up things upon normal termination. The service
   * should not be expected to exist after this is called.
   *
   * @return
   */
  boolean shutdown();

  /**
   * Register a session created by the scope manager after receiving a CREATE_SESSION request.
   * Return a unique session ID that may be requested with the session or generated within the
   * service.
   *
   * @param sessionScope a client scope that should record the ID for future communication. If the
   *     ID is null, the call has failed.
   * @param userScope used to set up federated behavior
   * @param behavior the application, script or test case we want to run. If null, the result should
   *     be the default user or federation session.
   * @return the ID of the new session created at server side, or null in case of failure.
   */
  String declareSessionScope(
      SessionScope sessionScope, UserScope userScope, KActorsBehavior behavior);

  /**
   * Register a context scope created by the scope manager. Return a unique session ID that may be
   * requested with the session or generated within the service. Context starts empty with the
   * default observer for the worldview, using the services available to the user and passed as
   * parameters. The same runtime that hosts the context must become the one and only runtime
   * accessible to the resulting scope. If the service is not a runtime, the request must come from
   * another service and the scope should be instrumented as necessary for its purposes.
   *
   * @param contextScope a client scope that should record the ID for future communication. If the
   *     ID is null, the call has failed.
   * @param sessionScope used to set up federated behavior
   * @param userScope used to establish the agent making changes (same as sessionScope's unless
   *     federated)
   * @return the ID of the new context scope created at server side, or null in case of failure.
   */
  String declareContextScope(
      ContextScope contextScope, SessionScope sessionScope, UserScope userScope);

  /**
   * Get the access rights for the passed resource. If the resource does not exist or is
   * inaccessible to the scope, return empty rights.
   *
   * @param resourceUrn
   * @param scope
   * @return
   */
  ResourcePrivileges getRights(String resourceUrn, Scope scope);

  /**
   * Set the access rights for the named resource.
   *
   * @param resourceUrn
   * @param resourcePrivileges
   * @param scope
   * @return true if the resource was accessible and the rights were set.
   */
  boolean setRights(String resourceUrn, ResourcePrivileges resourcePrivileges, Scope scope);

  /**
   * Retrieve all stored credential information for the passed scope.
   *
   * @param scope
   * @return
   */
  List<ExternalAuthenticationCredentials.CredentialInfo> getCredentialInfo(Scope scope);

  /**
   * Add the passed credentials to the service's credential store. Scope determines what the
   * credentials can apply to.
   *
   * @param host stripped-down hostname (possibly with port and path), e.g. github.com/user
   * @param credentials
   * @param scope
   * @return true if successful
   */
  ExternalAuthenticationCredentials.CredentialInfo addCredentials(
      String host, ExternalAuthenticationCredentials credentials, Scope scope);

  /**
   * Retrieve a specific asset under the purview of this service. The URN must identify a
   * first-class asset such as an observation or a namespace, expecting it to be hosted on this
   * service. The asset class may refer to descriptors or other intems related to it, e.g. metadata,
   * a color map, or other information that can be unambiguously obtained about the asset.
   *
   * @param <T>
   * @param urn
   * @param locator may be null if not relevant to the asset being extracted or documented
   * @param assetClass
   * @param scope
   * @return
   */
  <T extends Serializable> T retrieveAsset(
      String urn, Scheduler.Event locator, Class<T> assetClass, Scope scope);

  /**
   * Load any resources that can be hosted in this service and are referenced in the resource set.
   * Ignore anything that the service does not know how to handle. Return true if no errors in the
   * resource set, all pertinent resources were loaded, and the resource set is not empty.
   *
   * @param resourceSet
   * @param scope
   * @return
   */
  boolean loadResources(ResourceSet resourceSet, Scope scope);

  /**
   * This will find any export schema installed at service side to honor the request.
   *
   * @param urn
   * @param mediaType
   * @param scope
   * @return
   */
  InputStream exportAsset(
      String urn,
      KlabAsset.KnowledgeClass knowledgeClass,
      String mediaType,
      Parameters<String> parameters,
      Scope scope);

  /**
   * @param schema a valid schema that comes from those admitted in the service
   * @param assetCoordinates the submission, either a file or URL that specifies a byte stream or a
   *     set of properties.
   * @param suggestedUrn the desired URN, which may be honored if valid and unambiguous, but may
   *     also be modified. Pass {@link org.integratedmodelling.klab.api.knowledge.Urn#UNDEFINED_URN}
   *     to request that the URN is generated at service side.
   * @param scope
   * @return the result of the import. If not {@link ResourceSet#empty()}, the result should be a
   *     {@link ResourceSet.Resource} accessible through {@link ResourceSet#getResults()}.
   *     Notifications may be added to the main resource.
   */
  CompletableFuture<ResourceSet> importAsset(
      ResourceTransport.Schema schema,
      ResourceTransport.Schema.Asset assetCoordinates,
      String suggestedUrn,
      Scope scope);
}
