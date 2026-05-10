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
   * The service ID of the runtime that hosts the session and the digital twins connected to it.
   *
   * @return
   */
  String getHostServiceId();

  /**
   * Return all the active observation scopes. These may be the currently "alive" ones or any
   * persistent observation scope left out previously. Active means they haven't expired, not that
   * there has been any recent activity.
   *
   * @return
   */
  List<ContextScope> getActiveContexts();

  /**
   * Create a context scope in this session. If the configuration includes a scope ID, the scope is
   * created only if it does not pre-exist, otherwise the named scope is returned. The requesting
   * scope must obviously have access to the scope or the request fails.
   *
   * <p>Upon creation, the context is empty except for the observer, which will be created and
   * resolved according to the configuration in the worldview.
   *
   * <p>FIXME we should have an empty() scope with notifications instead of a null return for
   * failures of any kind (authentication or otherwise). That's the pattern for observations and
   * resource data.
   *
   * @param configuration the configuration options for the digital twin. Only federated users can
   *     submit a pre-chosen ID or a URL with one.
   * @return a new context, or null if the request failed
   */
  ContextScope createContext(DigitalTwin.Configuration configuration);
}
