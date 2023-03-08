package org.integratedmodelling.klab.api.lang.kactors;

import org.integratedmodelling.klab.api.lang.Statement;

/**
 * Any k.Actors code element, including whole behaviors. Actual statements are
 * {@link KActorsStatement} and represent individual executable instructions.
 * 
 * @author Ferd
 *
 */
public interface KActorsCodeStatement extends Statement {

	String getTag();

}
