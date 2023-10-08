package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.geometry.Offset;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.data.histogram.Histogram;
import org.ojalgo.array.*;

import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.LongToDoubleFunction;

/**
 * Base storage providing the general methods. Children enable either boxed I/O or faster native operation
 * (recommended). The runtime makes the choice.
 *
 * @author Ferd
 */
public class DoubleStorage implements Storage {

    @FunctionalInterface
    public interface OffsetToDoubleFunction {
        public double applyToOffset(Offset offset);
    }

    private final Scale scale;
    long sliceSize;
    private NavigableMap<Long, DirectSliceBuffer> buffers = new TreeMap<>();
    private StorageScope scope;
    private Histogram histogram;

    public DoubleStorage(Scale scale, StorageScope scope) {
        this.scope = scope;
        this.scale = scale;
        this.sliceSize = scale.without(Geometry.Dimension.Type.TIME).size();
        if (scope.isRecordHistogram()) {
            this.histogram = new Histogram(scope.getHistogramBinSize());
        }
    }

    public void set(double value, Offset locator) {

    }

    /**
     * A quick-access buffer that simply addresses a dimension using a long. Obtained through
     * {@link #getSliceBuffer(Locator)}.
     */
    public class DirectSliceBuffer {

        long startTime;
        private final BufferArray data;
        Histogram histogram;


        public DirectSliceBuffer(long startTime) {
            this.startTime = startTime;
            this.data = scope.getDoubleBuffer(sliceSize);
            if (DoubleStorage.this.histogram != null) {
                this.histogram = new Histogram(DoubleStorage.this.histogram.getMaxBins());
            }
        }

        public void set(double value, long position) {
            this.data.set(position, value);
            if (histogram != null) {
                histogram.insert(value);
            }
        }

        public double get(long position) {
            return this.data.get(position);
        }
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
        var time = scale.isTemporallyDistributed() ? locator.as(Time.class) : null;
        return (time == null || time.is(Time.Type.INITIALIZATION)) ? getOrCreateBuffer(0l, 0l) :
                getOrCreateBuffer(time.getFocus() == null ? time.getStart().getMilliseconds() :
                        time.getFocus().getMilliseconds(), time.getStart().getMilliseconds());
    }

    private DirectSliceBuffer getOrCreateBuffer(long requestedTime, long startTime) {
        var ret = buffers.get(requestedTime);
        if (ret == null) {
            ret = new DirectSliceBuffer(startTime);
            this.buffers.put(startTime, ret);
        }
        return ret;
    }

    /**
     * Map the passed operator within as many threads as specified by the level of parallelism defined in the
     * constructor. The operator returns the value for the passed long offset, which translates the {@link Locator}
     * produced by iterating a {@link Scale}.
     *
     * @param operator a non-boxing offset -> double operator producing the value at the passed offset
     */
    public void map(OffsetToDoubleFunction operator) {

    }

    @Override
    public Type getType() {
        return Type.DOUBLE;
    }

}
