package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.geometry.Locator;

/**
 * Base storage providing the general methods. Children enable either boxed I/O
 * or faster native operation (recommended). The runtime makes the choice.
 * 
 * @author Ferd
 *
 */
public class IntStorage extends Storage {
	
	@Override
	public Type getType() {
		return Type.INTEGER;
	}

	public void set(int value, Locator locator) {
		
	}

}
