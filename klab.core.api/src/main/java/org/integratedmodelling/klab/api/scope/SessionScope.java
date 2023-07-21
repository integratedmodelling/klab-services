package org.integratedmodelling.klab.api.scope;

import java.net.URL;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;

/**
 * Session scopes are stateful.
 * 
 * @author ferd
 *
 */
public interface SessionScope extends UserScope {
	
	/**
	 * Never null. The session scope's geometry is the "focal" geometry of the user
	 * (where/when the user agent is looking); the geometry in each
	 * {@link ContextScope} is the actual view during observation. If there is no
	 * current focus the result is the empty geometry.
	 * 
	 * @return
	 */
	Scale getGeometry();

	/**
	 * A session may represent a raw session, a script or an application. In each
	 * case a name is supplied and can be retrieved.
	 * 
	 * @return
	 */
	String getName();

	/**
	 * Create a context scope in this session. The scope is empty, initially focused
	 * on the geometry that the session was focused on at the time of the call (also
	 * empty by default). Because of this, only direct observables may be observed
	 * in it initially.
	 * 
	 * @param urn the context URN, which will be the ID in its URL for external
	 *            reference. Must be unique within the session. Optionally this can
	 *            be the http-based URL of a remote context, which will create a
	 *            "proxy" for the remote.
	 * 
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

}
