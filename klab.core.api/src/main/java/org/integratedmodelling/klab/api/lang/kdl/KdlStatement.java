package org.integratedmodelling.klab.api.lang.kdl;

import java.util.Collection;

import org.integratedmodelling.klab.api.lang.Statement;

public interface KdlStatement extends Statement {

	/**
	 * True if there are any errors
	 * 
	 * @return
	 */
	boolean isErrors();

	/**
	 * Return any error messages
	 * 
	 * @return
	 */
	Collection<String> getErrors();

}
