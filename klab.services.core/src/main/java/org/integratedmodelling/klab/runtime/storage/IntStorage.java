package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;

import java.util.function.LongToIntFunction;

/**
 * Base storage providing the general methods. Children enable either boxed I/O
 * or faster native operation (recommended). The runtime makes the choice.
 * 
 * @author Ferd
 *
 */
public class IntStorage implements Storage {
	
	@Override
	public Type getType() {
		return Type.INTEGER;
	}

	public void set(int value, Locator locator) {
		
	}

	/**
	 * Map the passed operator within as many threads as specified by the level of parallelism defined in the
	 * constructor. The operator returns the value for the passed long offset, which translates the
	 * {@link Locator} produced by iterating a {@link Scale}.
	 *
	 * @param operator a non-boxing long -> int operator producing the value at the passed offset
	 */
	public void map(LongToIntFunction operator) {

	}
}
