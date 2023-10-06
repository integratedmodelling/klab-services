package org.integratedmodelling.klab.runtime.scale;

import com.google.common.primitives.Longs;
import org.integratedmodelling.klab.api.exceptions.KUnimplementedException;
import org.integratedmodelling.klab.api.geometry.*;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension.Type;
import org.integratedmodelling.klab.api.geometry.impl.NDCursor;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.runtime.scale.space.SpaceImpl;
import org.integratedmodelling.klab.runtime.scale.time.TimeImpl;

import java.io.Serial;
import java.util.*;
import java.util.stream.Collectors;

public class ScaleImpl implements Scale {

    @Serial
    private static final long serialVersionUID = -4518044986262539876L;

    Extent<?>[] extents;
    long size;

    /**
     * Internal locator class f. Uses the enclosing scale in a lazy fashion for everything and just maintains the
     * offset[s] and the index[es] of the extend that is changing. This is the default locator if the scale hasn't been
     * adapted to one of the fast scanners.
     * <p>
     * TODO make 2 more that consider masking in extents
     *
     * @author Ferd
     */
    abstract class ScaleLocator extends DelegatingScale {

        @Serial
        private static final long serialVersionUID = 797929992176158102L;
        long offset;

        // lazy container for localized extents. Initialized to those that don't change.
        public Scale advance() {
            return doAdvance();
        }

        abstract Scale doAdvance();

        abstract boolean isDone();

        @Override
        public <T extends Locator> T as(Class<T> cls) {
            // TODO adapt to the various scanners
            return super.as(cls);
        }

        @Override
        public Extent<?> extent(Type extentType) {
            return super.extent(extentType);
        }
    }

    /**
     * For >1 extents changing at the same time.
     *
     * @author Ferd
     */
    class ScaleLocatorND extends ScaleLocator {

        private static final long serialVersionUID = 2969366247696737476L;
        NDCursor cursor;

        public ScaleLocatorND(Extent<?>[] extents, List<Long> dims) {
            super();
            cursor.defineDimensions(dims);
        }

        Scale doAdvance() {
            offset++;
            return this;
        }

        @Override
        boolean isDone() {
            return offset == this.cursor.getMultiplicity();
        }

    }

    /**
     * Internal locator class for the situation where a single dimension is changing and the others are locked at a
     * specified extent.
     *
     * @author Ferd
     */
    class ScaleLocator1D extends ScaleLocator {

        @Serial
        private static final long serialVersionUID = -4207775306893203109L;
        int changingIndex;
        long[] extents;

        public ScaleLocator1D(Extent<?>[] extents, List<Long> dims, int changingIndex) {
            super();
            this.extents = Longs.toArray(dims);
            this.changingIndex = changingIndex;
        }

        Scale doAdvance() {
            offset++;
            return this;
        }

        @Override
        boolean isDone() {
            return offset == extents[changingIndex];
        }

    }

    /**
     * The scale iterator uses a threadlocal locator to avoid constant object instantiation.
     *
     * @author Ferd
     */
    class ScaleIterator implements Iterator<Scale> {

        ThreadLocal<ScaleLocator> locator = new ThreadLocal<>();

        ScaleIterator() {
            this.locator.set(createLocator(extents));
        }

        @Override
        public boolean hasNext() {
            return !locator.get().isDone();
        }

        @Override
        public Scale next() {
            return locator.get().advance();
        }

    }


    private ScaleLocator createLocator(Extent<?>[] extents) {
        int changingExtents = 0;
        int i = 0, changingExtent = 0;
        List<Long> dims = new ArrayList<>();
        for (Extent extent : extents) {
            if (extent.size() > 1) {
                changingExtents++;
                changingExtent = i;
            }
            i++;
        }
        return switch (changingExtents) {
            case 0 -> new ScaleLocator() {

                boolean done;

                @Override
                Scale doAdvance() { // not called
                    done = true;
                    return this;
                }

                @Override
                boolean isDone() {
                    return done;
                }
            };
            case 1 -> new ScaleLocator1D(extents, dims, changingExtent);
            default -> new ScaleLocatorND(extents, dims);
        };
    }

    ;

    public ScaleImpl(Geometry geometry) {
        List<Extent<?>> extents = new ArrayList<>(3);
        for (Geometry.Dimension dimension : geometry.getDimensions()) {
            if (dimension.getType() == Type.SPACE) {
                extents.add(SpaceImpl.create(dimension));
            } else if (dimension.getType() == Type.TIME) {
                extents.add(TimeImpl.create(dimension));
            } else if (dimension.getType() == Type.NUMEROSITY) {
                // TODO
                throw new KUnimplementedException("numerosity extent");
            }
        }
        define(extents);
    }

    public ScaleImpl(List<Extent<?>> extents) {
        define(extents);
    }

    /**
     * Dumb private constructor for pre-defined, pre-sorted inputs
     *
     * @param extents
     * @param size
     */
    private ScaleImpl(Extent[] extents, long size) {
        this.extents = extents;
        this.size = size;
    }

    protected void define(List<Extent<?>> extents) {
        Collections.sort(extents, new Comparator<>() {
            // use the natural order in the dimension type enum
            @Override
            public int compare(Extent<?> o1, Extent<?> o2) {
                return o1.getType().compareTo(o2.getType());
            }

        });
        this.extents = extents.toArray(new Extent[extents.size()]);
        this.size = 1;
        for (Extent<?> extent : extents) {
            size *= extent.size();
        }
    }

    protected void adoptExtents(Collection<Extent<?>> extents) {
        // TODO was setExtents()
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Locator> T as(Class<T> cls) {
        if (DimensionScanner2D.class.isAssignableFrom(cls)) {
			/*
			must have a single extent with size() > 1 and dimensionality = 2, or be a singleton
			 */
            return (T) new DimensionScanner2DImpl(this);
        } else if (DimensionScanner1D.class.isAssignableFrom(cls)) {
            return (T) new DimensionScanner1DImpl(this);
        } else if (Geometry.class.equals(cls)) {
            return null; // TODO
        } else if (Coverage.class.equals(cls)) {
            return (T) new CoverageImpl(this, 1.0);
        }
        return null;
    }

    @Override
    public Iterator<Scale> iterator() {
        return new ScaleIterator();
    }

    @Override
    public String encode(Encoding... options) {
        return as(Geometry.class).encode(options);
    }

    @Override
    public List<Dimension> getDimensions() {
        return Arrays.asList(extents);
    }

    @Override
    public Dimension dimension(Type type) {
        for (Extent<?> extent : extents) {
            if (extent.getType() == type) {
                return extent;
            }
        }
        return null;
    }

    @Override
    public Granularity getGranularity() {
        Dimension gr = dimension(Type.NUMEROSITY);
        return (gr == null || gr.size() == 1) ? Granularity.SINGLE : Granularity.MULTIPLE;
    }

    @Override
    public boolean isScalar() {
        return this.size == 1;
    }

    @Override
    public long size() {
        return this.size;
    }

    @Override
    public boolean infiniteTime() {
        return false;
    }

    @Override
    public boolean contains(Scale o) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean overlaps(Scale o) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean intersects(Scale o) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Space getSpace() {
        return (Space) extent(Dimension.Type.SPACE);
    }

    @Override
    public Time getTime() {
        return (Time) extent(Dimension.Type.TIME);
    }

    @Override
    public boolean isTemporallyDistributed() {
        return getTime() != null && getTime().distributed();
    }

    @Override
    public boolean isSpatiallyDistributed() {
        return getSpace() != null && getSpace().distributed();
    }

    @Override
    public int getExtentCount() {
        return this.extents.length;
    }

    @Override
    public List<Extent<?>> getExtents() {
        return Arrays.asList(this.extents);
    }

    @Override
    public boolean isEmpty() {
        return extents == null || extents.length == 0;
    }

    //	@Override
    public Scale mergeContext(Scale scale, Type... dimensions) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Scale merge(Scale other, LogicalConnector how) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Scale initialization() {
        Time time = getTime();
        if (time == null) {
            return this;
        }
        return new ScaleImpl(Arrays.stream(extents).map(e -> e.getType() == Type.TIME ? ((Time) e).initialization() :
                e).collect(Collectors.toList()));
    }

    @Override
    public Scale termination() {
        Time time = getTime();
        if (time == null) {
            return this;
        }
        return new ScaleImpl(Arrays.stream(extents).map(e -> e.getType() == Type.TIME ? ((Time) e).termination() :
                e).collect(Collectors.toList()));
    }

    @Override
    public Scale with(Extent<?> extent) {
        List<Extent<?>> exts = new ArrayList<>();
        boolean wasThere = false;
        for (var e : this.extents) {
            if ((extent.getType() == e.getType())) {
                wasThere = true;
                exts.add(extent);
            } else {
                exts.add(e);
            }
        }
        if (!wasThere) {
            exts.add(extent);
        }
        return new ScaleImpl(exts);
    }

    @Override
    public Scale without(Type dimension) {
        if (getDimension(dimension) == null) {
            return this;
        }
        Extent[] extents = new Extent[this.extents.length - 1];
        long size = 1;
        int next = 0;
        for (Extent extent : this.extents) {
            if (extent.getType() != dimension) {
                extents[next++] = extent;
                size *= extent.size();
            }
        }
        return new ScaleImpl(extents, size);
    }

    private Extent getDimension(Type dimension) {
        for (Extent extent : this.extents) {
            if (extent.getType() == dimension) {
                return extent;
            }
        }
        return null;
    }

    @Override
    public Scale at(Locator dimension) {
        /*
        if the dimension is a scale, locate all extents. If an extent, locate all. If an offset, locate whatever is
        in the offset.
         */
        if (dimension instanceof Offset offset) {
            // TODO
        } else if (dimension instanceof Extent extent) {
            Extent<?> mine = extent(extent.getType());
            if (mine != null) {
                return with(mine.at(extent));
            }
        } else if (dimension instanceof Scale scale) {
            List<Extent<?>> exts = new ArrayList<>();
            Set<Dimension.Type> dims = new HashSet<>();
            for (var extent : scale.getExtents()) {
                var mine = extent(extent.getType());
                if (mine != null) {
                    exts.add(mine.at(extent));
                } else {
                    exts.add(Extent.copyOf(extent));
                }
                dims.add(extent.getType());
            }
            for (var extent : this.extents) {
                if (!dims.contains(extent.getType())) {
                    exts.add(Extent.copyOf(extent));
                }
            }
            return new ScaleImpl(exts);
        } else if (dimension instanceof TimeInstant timeInstant) {
            Time time = getTime();
            if (time != null) {
                return with(time.at(timeInstant));
            }
        }
        return this;
    }

    @Override
    public Scale collapse(Type... dimensions) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Extent<?> extent(Type extentType) {
        for (var e : extents) {
            if (e.getType() == extentType) {
                return e;
            }
        }
        return null;
    }

    @Override
    public boolean isGeneric() {
        // a scale only exists if it's NOT generic
        return false;
    }


    /*
     * Default base for locators, delegating anything not explicitly overridden to
     * the containing instance.
     *
     * @author Ferd
     *
     */
    class DelegatingScale implements Scale {

        private static final long serialVersionUID = -8416028789360949571L;

        @Override
        public String encode(Encoding... options) {
            return ScaleImpl.this.encode(options);
        }

        public Scale with(Extent dimension) {
            return ScaleImpl.this.with(dimension);
        }

        @Override
        public List<Dimension> getDimensions() {
            return ScaleImpl.this.getDimensions();
        }

        @Override
        public Dimension dimension(Type type) {
            return ScaleImpl.this.dimension(type);
        }

        @Override
        public Granularity getGranularity() {
            return ScaleImpl.this.getGranularity();
        }

        @Override
        public boolean isScalar() {
            return ScaleImpl.this.isScalar();
        }

        @Override
        public long size() {
            return ScaleImpl.this.size();
        }

        @Override
        public boolean infiniteTime() {
            return ScaleImpl.this.infiniteTime();
        }

        @Override
        public <T extends Locator> T as(Class<T> cls) {
            return ScaleImpl.this.as(cls);
        }

        @Override
        public boolean contains(Scale o) {
            return ScaleImpl.this.contains(o);
        }

        @Override
        public boolean overlaps(Scale o) {
            return ScaleImpl.this.overlaps(o);
        }

        @Override
        public boolean intersects(Scale o) {
            return ScaleImpl.this.intersects(o);
        }

        @Override
        public Iterator<Scale> iterator() {
            return ScaleImpl.this.iterator();
        }

        @Override
        public Space getSpace() {
            return ScaleImpl.this.getSpace();
        }

        @Override
        public Time getTime() {
            return ScaleImpl.this.getTime();
        }

        @Override
        public boolean isTemporallyDistributed() {
            return ScaleImpl.this.isTemporallyDistributed();
        }

        @Override
        public boolean isSpatiallyDistributed() {
            return ScaleImpl.this.isSpatiallyDistributed();
        }

        @Override
        public int getExtentCount() {
            return ScaleImpl.this.getExtentCount();
        }

        @Override
        public List<Extent<?>> getExtents() {
            return ScaleImpl.this.getExtents();
        }

        @Override
        public boolean isEmpty() {
            return ScaleImpl.this.isEmpty();
        }

        @Override
        public Scale merge(Scale other, LogicalConnector how) {
            return ScaleImpl.this.merge(other, how);
        }

        @Override
        public Scale initialization() {
            return ScaleImpl.this.initialization();
        }

        @Override
        public Scale termination() {
            return ScaleImpl.this.termination();
        }

        @Override
        public Scale without(Type dimension) {
            return ScaleImpl.this.without(dimension);
        }

        @Override
        public Scale at(Locator dimension) {
            return ScaleImpl.this.at(dimension);
        }

        @Override
        public Scale collapse(Type... dimensions) {
            return ScaleImpl.this.collapse(dimensions);
        }

        @Override
        public Extent<?> extent(Type extentType) {
            return ScaleImpl.this.extent(extentType);
        }

        @Override
        public boolean isGeneric() {
            // a generic scale cannot be iterated
            return false;
        }

        @Override
        public Scale mergeExtent(Extent<?> extent) {
            return ScaleImpl.this.mergeExtent(extent);
        }

    }

    @Override
    public Scale mergeExtent(Extent<?> extent) {
        // TODO Auto-generated method stub
        return null;
    }

    public NDCursor cursor() {
        var ret = new NDCursor();
        ret.defineDimensions(Arrays.stream(extents).map(e -> e.size()).toList());
        return ret;
    }

}
