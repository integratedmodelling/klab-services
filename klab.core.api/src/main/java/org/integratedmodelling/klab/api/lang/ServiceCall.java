package org.integratedmodelling.klab.api.lang;

import java.util.Collection;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.lang.impl.ServiceCallImpl;

/**
 * A parsed function call. Parameters can be named by user or by default.
 * 
 * @author Ferd
 *
 */
public interface ServiceCall extends Encodeable {

	/**
	 * If the function does not have named parameters, any parameters passed are
	 * named like this; if there is more than one parameter they will be grouped
	 * into a list returned with this key.
	 */
	public static String DEFAULT_PARAMETER_NAME = "value";

	/**
	 * Name of the function being called. Never null.
	 * 
	 * @return the function name
	 */
	String getName();

	/**
	 * Parameters passed to the call. If a function call was passed, it is invoked
	 * before this is returned.
	 * 
	 * @return the parameters for the call. See {@link #DEFAULT_PARAMETER_NAME} for
	 *         naming rules if no names are given.
	 */
	Parameters<String> getParameters();
	
	/**
	 * Number of <i>user</i> parameters. May be different from getParameters().size().
	 * 
	 * @return
	 */
	int getParameterCount();

	/**
	 * Prototype. May be null if the function is unknown.
	 * 
	 * @return
	 */
	Prototype getPrototype();
	
	/**
	 * Return any parameter IDs that were passed with a syntax that defines those
	 * that can be changed by the user.
	 * 
	 * @return
	 */
	Collection<String> getInteractiveParameters();
	
    /**
     * Create a function call from the passed parameters. All parameters after the name must be
     * given in pairs: (string, value)*
     * 
     * @param name
     * @param parameters
     * @return a new service call
     */
    public static ServiceCall create(String name, Object... parameters) {
        return new ServiceCallImpl(name, parameters);
    }

}
