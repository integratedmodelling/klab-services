package org.integratedmodelling.klab.runtime.storage;

import org.eclipse.collections.api.block.function.primitive.LongToFloatFunction;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.geometry.Offset;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;

/**
 * Base storage providing the general methods. Children enable either boxed I/O
 * or faster native operation (recommended). The runtime makes the choice.
 * 
 * @author Ferd
 *
 */
public class FloatStorage implements Storage {

	public FloatStorage(Scale scale, StateStorageImpl scope) {

	}

	@Override
	public Type getType() {
		return Type.FLOAT	;
	}

	public void set(float value, Offset locator) {

	}

	@Override
	public long getId() {
		return 0;
	}

	/**
	 * A quick-access buffer that simply addresses a dimension using a long. Obtained through
	 * {@link #getSliceBuffer(Locator)}.
	 */
	interface DirectSliceBuffer {
		public void set(float value, long position);

		public float get(long position);
	}

	/**
	 * Using the slice buffer provides the quickest access with little code impact. Using the buffer is alternative to
	 * the standard get/set and should be done only when one dimension is scanned at the time and the dimension size is
	 * high.
	 *
	 * @param locator
	 * @return
	 */
	public DirectSliceBuffer getSliceBuffer(Locator locator) {
		return null;
	};


	/**
	 * Map the passed operator within as many threads as specified by the level of parallelism defined in the
	 * constructor. The operator returns the value for the passed long offset, which translates the
	 * {@link Locator} produced by iterating a {@link Scale}.
	 *
	 * @param operator a non-boxing long -> float operator producing the value at the passed offset
	 */
	public void map(LongToFloatFunction operator) {

	}

	@Override
	public Histogram getHistogram() {
		return null;
	}
}
