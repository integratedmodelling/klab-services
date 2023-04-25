package org.integratedmodelling.klab.api.authentication.scope;

import org.integratedmodelling.klab.api.geometry.Geometry;

/**
 * Session scopes are stateful.
 * 
 * @author ferd
 *
 */
public interface SessionScope extends UserScope {

    public enum Status {
        WAITING, STARTED, CHANGED, FINISHED, ABORTED, /* this only sent by UIs for now */ INTERRUPTED, EMPTY
    }

    /**
     * Never null. The session scope's geometry is the "focal" geometry of the user (where/when the
     * user agent is looking); the geometry in each {@link ContextScope} is the actual view during
     * observation. If there is no current focus the result is the empty geometry.
     * 
     * @return
     */
    Geometry getGeometry();

    /**
     * A session may represent a raw session, a script or an application. In each case a name is
     * supplied and can be retrieved.
     * 
     * @return
     */
    String getName();

    /**
     * Create a context scope in this session. The scope is initially focused on the geometry that
     * the session was focused on at the time of the call.
     * 
     * @return
     */
    ContextScope createContext(String id);

    /**
     * Return the status of the session scope at the time of the call.
     * 
     * @return
     */
    Status getStatus();

}
