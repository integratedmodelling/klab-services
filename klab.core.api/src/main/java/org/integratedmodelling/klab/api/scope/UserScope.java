package org.integratedmodelling.klab.api.scope;

import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.services.KlabService;

import java.net.URL;
import java.util.List;

/**
 * User scopes restrict a service's permissions to those available to a specific user.
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
   * Connect to a remote digital twin identified by the passed URL. Connecting user must be
   * authorized by the DT configuration; its rights will determine the capabilities of the scope.
   *
   * @param digitalTwinURL
   * @return
   */
  ContextScope connect(URL digitalTwinURL);

  /**
   * Any active sessions that have not expired, including running applications and scripts. They may
   * or may not have contexts available.
   *
   * @return
   */
  List<SessionScope> getActiveSessions();

  /**
   * Start a raw session with a given identifier and return the scope that controls it. This will
   * locate and connect an available runtime among those that are visible to the user.
   *
   * @param sessionName
   * @return
   */
  SessionScope createSession(String sessionName);

  /**
   * Run an individual application, test case or script and return the scope that controls it.
   * Different VMs and agent behaviors are used according to the type, which can only be one of the
   * independently runnable behaviors: APP, SCRIPT or TESTCASE. Each behavior at this level creates
   * an independent SessionScope. The session created with this method will expire and disappear
   * after termination of the application or script.
   *
   * @param behaviorName
   * @param behaviorType
   * @return
   */
  SessionScope run(String behaviorName, KActorsBehavior.Type behaviorType);

  /**
   * Switch the "current" service for the service class to the passed one, adding it to the
   * available services in the scope if it's not there already. After this call, the user scope's
   * {@link #getService(Class)} will return the passed service for that class.
   */
  void switchService(KlabService service);
}
