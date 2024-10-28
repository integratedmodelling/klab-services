package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.geometry.Offset;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.data.histogram.SPDTHistogram;
import org.ojalgo.array.BufferArray;

import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Base storage providing the general methods. Children enable either boxed I/O or faster native operation
 * (recommended). The runtime makes the choice.
 *
 * @author Ferd
 */
public class DoubleStorage implements Storage {

    @Override
    public long getId() {
        return 0;
    }

    @FunctionalInterface
    public interface OffsetToDoubleFunction {
        public double applyToOffset(Offset offset);
    }

    public interface OffsetToFloatFunction {
        public float applyToOffset(Offset offset);
    }

    public interface OffsetToBooleanFunction {
        public boolean applyToOffset(Offset offset);
    }

    private final Scale scale;
    long sliceSize;
    private NavigableMap<Long, DirectSliceBuffer> buffers = new TreeMap<>();
    private StateStorageImpl scope;

    public DoubleStorage(Scale scale, StateStorageImpl scope) {
        this.scope = scope;
        this.scale = scale;
        this.sliceSize = scale.without(Geometry.Dimension.Type.TIME).size();
    }

    public void set(double value, Offset locator) {

    }

    /**
     * Retrieve the merged histogram. TODO we should cache if the owning state is finalized.
     *
     * @return
     */
    public SPDTHistogram histogram() {
        if (buffers.size() == 1) {
            return buffers.values().iterator().next().histogram;
        } else if (buffers.size() > 1) {
            SPDTHistogram ret = new SPDTHistogram(20);
            for (DirectSliceBuffer buffer : buffers.values()) {
                if (buffer.histogram != null) {
                    ret.merge(buffer.histogram);
                }
            }
            // TODO cache if storage is finalized
            return ret;
        }
        return new SPDTHistogram(20);
    }

    /**
     * A quick-access buffer that simply addresses a dimension using a long. Obtained through
     * {@link #getSliceBuffer(Locator)}.
     */
    public class DirectSliceBuffer {

        long startTime;
        private final BufferArray data;
        SPDTHistogram histogram;

        public DirectSliceBuffer(long startTime) {
            this.startTime = startTime;
            this.data = scope.getDoubleBuffer(sliceSize);
            if (scope.isRecordHistogram()) {
                this.histogram = new SPDTHistogram(scope.getHistogramBinSize());
            }
        }

        /**
         * <code>set</code> will set the data but not insert the value in the histogram. Use to modify the values. It
         * will invalidate the histogram when used.
         *
         * @param value
         * @param position
         */
        public void set(double value, long position) {
            this.data.set(position, value);
        }

        /**
         * <code>add</code> should be used for successive inserts that cover the entire value space. It will insert the
         * value in the histogram if one is configured.
         *
         * @param value
         * @param position
         */
        public void add(double value, long position) {
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

    @Override
    public Histogram getHistogram() {
        // TODO return new HistogramImpl(histogram());
        return null;
    }

}
