package org.integratedmodelling.klab.runtime.scale;

import com.google.common.primitives.Longs;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.*;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.LogicalConnector;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class DimensionScanner2DImpl implements DimensionScanner2D {

    ScaleImpl originalScale;
    int changingDimension;
    long[] changingDimensionShape;

    public DimensionScanner2DImpl(ScaleImpl scale) {
        // must have a single extent with size() > 1 and dimensionality == 2, or be a singleton
        this.originalScale = scale;
        boolean assigned = false;
        int i = 0;
        for (var e : originalScale.getExtents()) {
            if (e.size() > 1) {
                if (assigned) {
                    throw new KlabIllegalStateException("Scanner2D cannot be applied to a scale where 2 or more extents " +
                            "have size() > 1");
                }
                changingDimension = i;
                changingDimensionShape = Longs.toArray(e.getShape());

                if (changingDimensionShape.length != 2) {
                    throw new KlabIllegalStateException("Scanner2D cannot be applied to a scale the scanned extent " +
                            "has dimensionality != 2");
                }

                assigned = true;
            }
            i++;
        }
    }

    @Override
    public Offset locate(long x, long y) {
        return null;
    }

    @Override
    public Iterator<Offset2D> iterator() {
        return new OffsetIterator();
    }

    public void define(List<Extent<?>> extents) {
        originalScale.define(extents);
    }

    public void adoptExtents(Collection<Extent<?>> extents) {
        originalScale.adoptExtents(extents);
    }

    public boolean contains(Scale o) {
        return originalScale.contains(o);
    }

    public boolean overlaps(Scale o) {
        return originalScale.overlaps(o);
    }

    public boolean intersects(Scale o) {
        return originalScale.intersects(o);
    }

    public Space getSpace() {
        return originalScale.getSpace();
    }

    public Time getTime() {
        return originalScale.getTime();
    }

    public boolean isTemporallyDistributed() {
        return originalScale.isTemporallyDistributed();
    }

    public boolean isSpatiallyDistributed() {
        return originalScale.isSpatiallyDistributed();
    }

    public int getExtentCount() {
        return originalScale.getExtentCount();
    }

    public List<Extent<?>> getExtents() {
        return originalScale.getExtents();
    }

    public Scale mergeContext(Scale scale, Dimension.Type... dimensions) {
        return originalScale.mergeContext(scale, dimensions);
    }

    public Geometry merge(Scale other, LogicalConnector how) {
        return originalScale.merge(other, how);
    }

    public Scale initialization() {
        return originalScale.initialization();
    }

    public Scale termination() {
        return originalScale.termination();
    }

    public Scale without(Dimension.Type dimension) {
        return originalScale.without(dimension);
    }

    public Scale collapse(Dimension.Type... dimensions) {
        return originalScale.collapse(dimensions);
    }

    public Extent<?> extent(Dimension.Type extentType) {
        return originalScale.extent(extentType);
    }

    public Scale mergeExtent(Extent<?> extent) {
        return originalScale.mergeExtent(extent);
    }

    @Override
    public String encode(Encoder... encoders) {
        return originalScale.encode(encoders);
    }

    @Override
    public boolean isGeneric() {
        return originalScale.isGeneric();
    }

    @Override
    public List<Dimension> getDimensions() {
        return originalScale.getDimensions();
    }

    @Override
    public Dimension dimension(Dimension.Type type) {
        return originalScale.dimension(type);
    }

    @Override
    public Granularity getGranularity() {
        return originalScale.getGranularity();
    }

    @Override
    public boolean isEmpty() {
        return originalScale.isEmpty();
    }

    @Override
    public boolean isScalar() {
        return originalScale.isScalar();
    }

    @Override
    public long size() {
        return originalScale.size();
    }

    @Override
    public Geometry at(Locator dimension) {
        return originalScale.at(dimension);
    }

    @Override
    public long[] getExtentOffsets() {
        return new long[]{0,0};
    }

    @Override
    public List<Geometry> split() {
        return List.of(this);
    }

    @Override
    public boolean infiniteTime() {
        return originalScale.infiniteTime();
    }

    @Override
    public <T extends Locator> T as(Class<T> cls) {
        return originalScale.as(cls);
    }

    class OffsetIterator implements Iterator<Offset2D> {

        long nextPosition;
        long[] offsets = new long[originalScale.getExtentCount()];

        OffsetIterator() {
            int i = 0;
            for (var e : originalScale.getExtents()) {
                offsets[i] = (i == changingDimension) ? 0 : e.size();
                i++;
            }
        }

        ScannedOffset ret = new ScannedOffset() {

            @Override
            public long position() {
                return nextPosition - 1;
            }

            @Override
            public long[] offsets() {
                return offsets;
            }
        };

        @Override
        public boolean hasNext() {
            return nextPosition < size();
        }

        @Override
        public Offset2D next() {
            offsets[changingDimension] = nextPosition;
            nextPosition++;
            return ret;
        }
    }

    private abstract class ScannedOffset implements Offset2D {
        @Override
        public long x() {
            return position() % changingDimensionShape[0];
        }

        @Override
        public long y() {
            return changingDimensionShape[1] - (position() / changingDimensionShape[0]) - 1;
        }

    }

    ;
}
