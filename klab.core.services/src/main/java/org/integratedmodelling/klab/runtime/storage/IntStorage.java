package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.geometry.Offset;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;

import java.util.function.LongToIntFunction;

/**
 * Base storage providing the general methods. Children enable either boxed I/O or faster native operation
 * (recommended). The runtime makes the choice.
 *
 * @author Ferd
 */
public class IntStorage implements Storage {

    public IntStorage(Scale scale, StateStorageImpl scope) {
    }

    @Override
    public Type getType() {
        return Type.INTEGER;
    }

    public void set(int value, Offset locator) {

    }
    /**
     * A quick-access buffer that simply addresses a dimension using a long. Obtained through
     * {@link #getSliceBuffer(Locator)}.
     */
    interface DirectSliceBuffer {
        public void set(int value, long position);

        public int get(long position);
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
     * constructor. The operator returns the value for the passed offset, which translates the {@link Locator} produced
     * by iterating a {@link Scale}.
     *
     * @param operator a non-boxing long -> int operator producing the value at the passed offset
     */
    public void map(LongToIntFunction operator) {

    }

    @Override
    public Histogram getHistogram() {
        return null;
    }
}
