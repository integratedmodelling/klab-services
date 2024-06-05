package org.integratedmodelling.klab.api.scope;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;

/**
 * Session scopes are stateful.
 *
 * @author ferd
 */
public interface SessionScope extends UserScope {

    /**
     * The session scope's scale is the "focal" geometry of the user (where/when the user agent is looking);
     * the geometry in each {@link ContextScope} is the actual view during observation. If there is no current
     * focus the result is the empty geometry.
     *
     * @return the current viewing scale, empty if the session is still "blind", never null.
     * @deprecated this should be just the scale of the observer in the session and eventually scope. Not
     * right otherwise, so TODO we should bring the default observer here and potentially specialize it in the
     * context scope.
     */
    Scale getScale();

    /**
     * A session may represent a raw session, a script or an application. In each case a name is supplied and
     * can be retrieved. The name may not be unique.
     *
     * @return
     */
    String getName();

    /**
     * Create a context scope in this session. The scope is empty, initially focused on the geometry that the
     * session was focused on at the time of the call (also empty by default). Because of this, only direct
     * observables may be observed in it initially.
     *
     * @param urn the context URN, which will be the ID in its URL for external reference. Must be unique
     *            within the session. Optionally this can be the http-based URL of a remote context, which
     *            will create a "proxy" for the remote.
     * @return
     */
    ContextScope createContext(String urn, Geometry geometry);

    /**
     * Return the existing context scope with the passed name, or null.
     *
     * @param urn
     * @return
     */
    ContextScope getContext(String urn);

    /**
     * Stop the session, interrupt all tasks and free resources.
     */
    void logout();

}
