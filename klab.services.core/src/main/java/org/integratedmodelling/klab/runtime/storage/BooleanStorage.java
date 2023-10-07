package org.integratedmodelling.klab.runtime.storage;

import org.eclipse.collections.api.block.function.primitive.LongToBooleanFunction;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.geometry.Offset;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;

/**
 * Base storage providing the general methods. Children enable either boxed I/O or faster native operation
 * (recommended). The runtime makes the choice.
 *
 * @author Ferd
 */
public class BooleanStorage implements Storage {

    public BooleanStorage(Scale scale, StorageScope scope) {
    }

    @Override
    public Type getType() {
        return Type.BOOLEAN;
    }

    public void set(boolean value, Offset locator) {

    }

    /**
     * A quick-access buffer that simply addresses a dimension using a long. Obtained through
     * {@link #getSliceBuffer(Locator)}.
     */
    interface DirectSliceBuffer {
        public void set(boolean value, long position);

        public boolean get(long position);
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
    }

    /**
     * Map the passed operator within as many threads as specified by the level of parallelism defined in the
     * constructor. The operator returns the value for the passed long offset, which translates the {@link Locator}
     * produced by iterating a {@link Scale}.
     *
     * @param operator a non-boxing long -> boolean operator producing the value at the passed offset
     */
    public void map(LongToBooleanFunction operator) {

    }
}
