package org.integratedmodelling.klab.api.scope;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;

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
     * A session may represent a raw session, a script or an application. In each case a name is supplied and
     * can be retrieved. The name may not be unique.
     *
     * @return
     */
    String getName();

    /**
     * SessionScopes and ContextScopes have a mandatory ID that will be used to rebuild the scope at server
     * side.
     *
     * @return
     */
    String getId();

    /**
     * Create a context scope in this session. The scope is empty, initially focused on the geometry that the
     * session was focused on at the time of the call (also empty by default). Because of this, only direct
     * observables may be observed in it initially.
     *
     * @param contextName  a name for the context. Can be anything and does not uniquely identify the
     *                     context.
     * @param observerData anything that may specify the observer, including geometry or extents and semantics
     *                     (particularly roles)
     * @return a new context, or null if the request failed
     */
    ContextScope createContext(String contextName);

    /**
     * Stop the session, interrupt all tasks and free resources.
     */
    void logout();

}
