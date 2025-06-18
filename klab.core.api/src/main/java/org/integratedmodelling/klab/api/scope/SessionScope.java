package org.integratedmodelling.klab.api.scope;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;

import java.util.List;

/**
 * Session scopes are stateful.
 *
 * @author ferd
 */
public interface SessionScope extends UserScope {

  @Override
  default Type getType() {
    return Type.SESSION;
  }

  /**
   * A session may represent a raw session, a script or an application. In each case a name is
   * supplied and can be retrieved. The name may not be unique.
   *
   * @return
   */
  String getName();

  /**
   * SessionScopes and ContextScopes have a mandatory ID that will be used to rebuild the scope at
   * server side.
   *
   * @return
   */
  String getId();

  /**
   * Return all the active observation scopes. These may be the currently "alive" ones or any
   * persistent observation scope left out previously. Active means they haven't expired, not that
   * there has been any recent activity.
   *
   * @return
   */
  List<ContextScope> getActiveContexts();

  /**
   * Create a context scope in this session. The scope is empty, initially focused on the geometry
   * that the session was focused on at the time of the call (also empty by default). Because of
   * this, only direct observables may be observed in it initially.
   *
   * <p>If the configuration includes a scope ID, the scope is created only if it does not
   * pre-exist, otherwise the named scope is returned. Only federated users in the default
   * federation session can access this option.
   *
   * @param configuration the configuration options for the digital twin. Only federated users can
   *     submit a pre-chosen ID or a URL with one.
   * @return a new context, or null if the request failed
   */
  ContextScope createContext(DigitalTwin.Configuration configuration);
}
