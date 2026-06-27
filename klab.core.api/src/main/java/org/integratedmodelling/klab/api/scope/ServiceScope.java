package org.integratedmodelling.klab.api.scope;

import org.integratedmodelling.klab.api.services.KlabService;

/**
 * A service scope is obtained upon authentication of a service. It is a top-level scope akin to a
 * UserScope for the partner identity running a service, enabling access to any service-level other
 * services needed outside of user scopes. Messaging in the service scope should bridge to the
 * logging service, optionally also enabling remote messaging to service client scopes at the
 * discretion of the implementation.
 *
 * @author ferd
 */
public interface ServiceScope extends Scope {

  enum Locality {

    /**
     * Service is not REST-enabled and runs as an application in the same JVM, only to be used by
     * code that owns the pointer to the {@link KlabService}.
     */
    EMBEDDED,
    /**
     * Service runs on a machine as a REST-enabled application and will only accept requests from
     * clients that use a service secret, available only on the shared filesystem, with their
     * authorization token.
     */
    LOCALHOST,

    /** Service is a REST application that can serve clients located on the local network only. */
    LAN,
    /**
     * Service is a REST application that has been authorized by a k.LAB hub and is available for
     * authorized k.LAB users from remote clients.
     */
    WAN
  }

  default Type getType() {
    return Type.SERVICE;
  }

  /**
   * Locality reflects which clients can use the service.
   *
   * @return
   */
  Locality getLocality();

  /**
   * Availability is a long-term status: if this returns false, the service should not be used and
   * any endpoints should redirect to a maintenance mode response. If it returns true, service calls
   * may be made.
   *
   * @return
   */
  boolean isAvailable();

  /**
   * Put the service in maintenance mode. This will prevent any service calls from being made.
   *
   * @param maintenance
   */
  void setMaintenanceMode(boolean maintenance);

  /**
   * Put the service in atomic operation mode. Service calls will be queued and not served until the
   * operation is complete.
   *
   * @param atomicOperation
   */
  void setAtomicOperationMode(boolean atomicOperation);

  /**
   * Locality reflects which clients can use the service.
   *
   * @param locality
   */
  void setLocality(Locality locality);

  /**
   * Busy is a temporary status that can always happen and it means that service calls may be made
   * but may be slower to respond or fail. If this returns true before serving a request, the
   * service should wait until this status changes or a reasonable timeout expires. If the service
   * becomes not busy, the request should be served normally; if a timeout happens, the response
   * should be the same as when {@link #isAvailable()} returns false.
   *
   * @return
   */
  boolean isBusy();
}
