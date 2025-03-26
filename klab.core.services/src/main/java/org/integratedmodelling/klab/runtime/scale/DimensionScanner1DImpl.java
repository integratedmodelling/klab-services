package org.integratedmodelling.klab.runtime.scale;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.geometry.Offset;
import org.integratedmodelling.klab.api.geometry.DimensionScanner1D;
import org.integratedmodelling.klab.api.geometry.impl.NDCursor;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class DimensionScanner1DImpl implements DimensionScanner1D {

    ScaleImpl originalScale;
    NDCursor cursor;

    public DimensionScanner1DImpl(ScaleImpl scale) {
        // must have a single extent with size() > 1 and dimensionality == 1, or be a singleton
        this.originalScale = scale;
        this.cursor = scale.cursor();
    }

    @Override
    public Offset locate(long x) {
        return null;
    }

    @NotNull
    @Override
    public Iterator<Offset> iterator() {
        return null;
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
        return null;
    }

    @Override
    public String key() {
        return originalScale.key;
    }

    @Override
    public boolean isGeneric() {
        return false;
    }

    @Override
    public List<Dimension> getDimensions() {
        return null;
    }

    @Override
    public Dimension dimension(Dimension.Type type) {
        return null;
    }

    @Override
    public Granularity getGranularity() {
        return null;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean isScalar() {
        return false;
    }

    @Override
    public long size() {
        return 0;
    }

    @Override
    public Geometry at(Locator dimension) {
        return null;
    }

    @Override
    public long[] getExtentOffsets() {
        return new long[]{0};
    }

    @Override
    public List<Geometry> split() {
        return List.of(this);
    }

    @Override
    public boolean infiniteTime() {
        return false;
    }

    @Override
    public <T extends Locator> T as(Class<T> cls) {
        return null;
    }
}
