package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;

import java.util.function.LongToDoubleFunction;

/**
 * Base storage providing the general methods. Children enable either boxed I/O or faster native operation
 * (recommended). The runtime makes the choice.
 *
 * @author Ferd
 */
public class DoubleStorage implements Storage {

    public void set(double value, Locator locator) {

    }

    /**
     * Map the passed operator within as many threads as specified by the level of parallelism defined in the
     * constructor. The operator returns the value for the passed long offset, which translates the
     * {@link Locator} produced by iterating a {@link Scale}.
     *
     * @param operator a non-boxing long -> double operator producing the value at the passed offset
     */
    public void map(LongToDoubleFunction operator) {

    }

    @Override
    public Type getType() {
        return Type.DOUBLE;
    }

}
