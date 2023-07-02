package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.geometry.Locator;

/**
 * Base storage providing the general methods. Children enable either boxed I/O
 * or faster native operation (recommended). The runtime makes the choice.
 * 
 * @author Ferd
 *
 */
public class DoubleStorage extends Storage {
	
	public void set(double value, Locator locator) {
		
	}

	@Override
	public Type getType() {
		return Type.DOUBLE;
	}

}
