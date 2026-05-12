package org.integratedmodelling.klab.api.scope;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.services.RuntimeService;

import java.net.URL;
import java.util.List;

/**
 * User scopes restrict a service's permissions to those available to a specific user. The group
 * enables federation-wide messaging iif the user is part of a federation that advertises a broker.
 *
 * <p>If there are federation info in the user identity, the scope should initialize messaging in
 * send/receive mode. Session and Context scopes created from it will advertise the broker to the
 * runtime service so that applications and digital twins can use the messaging features.
 *
 * @author ferd
 */
public interface UserScope extends ReactiveScope {

  default Type getType() {
    return Type.USER;
  }

  /**
   * The scope is created for an authenticated user by the engine.
   *
   * @return
   */
  UserIdentity getUser();

  /**
   * The user scope is committed to a specific worldview. Calling this will retrieve the worldview
   * syntactic description from the set of connected services, considering the user groups and
   * permissions. The worldview contains all ontologies and is a large object. Also it may change at
   * any time.
   *
   * @return
   */
  Worldview getWorldview();

  /**
   * Connect to a remote digital twin identified by the passed URL. Connecting user must be
   * authorized by the DT configuration; its rights will determine the capabilities of the scope.
   *
   * @param digitalTwinURL
   * @return
   */
  ContextScope connect(URL digitalTwinURL);

  /**
   * Create or connect to a shared digital twin. The session hosting the digital twin will be
   * transparently created if needed. The configuration will need to specify the service URL unless
   * the method is called on a session or context scope.
   *
   * @param configuration
   * @return
   */
  ContextScope connect(DigitalTwin.Configuration configuration);

  /**
   * Any active sessions that have not expired, including running applications and scripts. They may
   * or may not have contexts available.
   *
   * @return
   */
  List<SessionScope> getActiveSessions();

  /**
   * Retrieve the scope for the user session attributed to the user or, if the user is federated, to
   * the federation. The session will be hosted within the passed runtime. There is only one user
   * session per service, with ID equal to the user ID or the federation ID if the user is
   * federated.
   *
   * @return
   */
  SessionScope getUserSession(RuntimeService hostService);

  /**
   * Run an individual application, test case or script and return the scope that controls it.
   * Different VMs and agent behaviors are used according to the type, which can only be one of the
   * independently runnable behaviors: APP, SCRIPT or TESTCASE. Each behavior at this level creates
   * an independent SessionScope. The session created with this method will expire and disappear
   * after termination of the application or script.
   *
   * @param behaviorName
   * @param hostService
   * @return
   */
  SessionScope run(String behaviorName, RuntimeService hostService);
}
