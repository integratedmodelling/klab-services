package org.integratedmodelling.klab.runtime.scale.time;

import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.exceptions.KUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.geometry.impl.GeometryImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeDuration;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.runtime.scale.ExtentImpl;

import java.util.Collection;
import java.util.Iterator;

public class TimeImpl extends ExtentImpl<Time> implements Time {

    private static final long serialVersionUID = 5936628543110335175L;

    Time.Type extentType;
    TimeInstant start;
    TimeInstant end;
    TimeDuration step;
    boolean realtime = false;
    Resolution resolution;
    Resolution coverageResolution;
    long coverageStart;
    long coverageEnd;
    long size = 1;
    // flag that irregular intervals must be computed to obtain size
    boolean regular = true;
    int timeSlice = -1;

    public TimeImpl() {
        super(Dimension.Type.TIME);
    }

    private TimeImpl(TimeImpl time) {
        super(Dimension.Type.TIME);
        this.end = time.getEnd();
        this.extentType = time.getTimeType();
        this.size = time.size();
        this.realtime = time.realtime;
        this.resolution = time.resolution == null
                ? null
                : Resolution.of(time.resolution.getMultiplier(), time.resolution.getType());
        this.start = time.start;
        this.step = time.step;
        this.timeSlice = time.timeSlice;
    }

    @Override
    public Time at(Locator locator) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int getRank() {
        return resolution.getType().getRank();
    }

    @Override
    public double getDimensionSize() {
        return 1;
    }

    @Override
    public double getStandardizedDimension(Locator locator) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public <T extends Locator> T as(Class<T> cls) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long size() {
        return size;
    }

    public String encode() {
//        Set<Encoding> opts = EnumSet.noneOf(Encoding.class);
//        if (options != null) {
//            for (Encoding e : options) {
//                opts.add(e);
//            }
//        }
//
//        String prefix = "T";
//        if (getTimeType() == ITime.Type.LOGICAL) {
//            prefix = "\u03c4";
//        } /*else if (partial && step == null) {
//            prefix = "t";
//        }*/
//
//        String ret = prefix + getDimensionality() + "(" + size + ")";
//        String args = GeometryImpl.PARAMETER_TIME_REPRESENTATION + "=" + getTimeType();
//
//        Time target = this;
//
//        if (this.is(Time.Type.INITIALIZATION) && opts.contains(IGeometry.Encoding.CONCRETE_TIME_INTERVALS)) {
//            args = GeometryImpl.PARAMETER_TIME_REPRESENTATION + "=" + ITime.Type.PHYSICAL;
//            if (this.size() > 1) {
//                target = getPreviousExtent(this);
//            }
//        }
//
//        if (target.getStart() != null) {
//            if (target.getEnd() != null) {
//                args += "," + GeometryImpl.PARAMETER_TIME_PERIOD + "=[" + target.getStart().getMilliseconds() + " "
//                        + target.end.getMilliseconds() + "]";
//            } else {
//                args += "," + GeometryImpl.PARAMETER_TIME_LOCATOR + "=" + target.getEnd().getMilliseconds();
//            }
//        }
//        if (target.getStart() != null) {
//            args += "," + GeometryImpl.PARAMETER_TIME_GRIDRESOLUTION + "=" + target.step.getMilliseconds();
//        }
//        if (target.resolution != null) {
//            args += "," + GeometryImpl.PARAMETER_TIME_SCOPE + "=" + target.resolution.getMultiplier();
//            args += "," + GeometryImpl.PARAMETER_TIME_SCOPE_UNIT + "=" + target.resolution.getType();
//        }
//        if (target.coverageResolution != null) {
//            args += "," + GeometryImpl.PARAMETER_TIME_COVERAGE_UNIT + "=" + target.coverageResolution.getType();
//            args += "," + GeometryImpl.PARAMETER_TIME_COVERAGE_START + "=" + target.coverageStart;
//            args += "," + GeometryImpl.PARAMETER_TIME_COVERAGE_END + "=" + target.coverageEnd;
//        }
//
//        return ret + "{" + args + "}";
        return null;
    }

    @Override
    public boolean contains(Time o) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean overlaps(Time o) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean intersects(Time o) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Iterator<Time> iterator() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Time collapsed() {
        return create(start.getMilliseconds(), end.getMilliseconds());
    }

    @Override
    public Time getExtent(long stateIndex) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TimeInstant getStart() {
        return start;
    }

    @Override
    public TimeInstant getEnd() {
        return end;
    }

    @Override
    public TimeDuration getStep() {
        return step;
    }

    @Override
    public Resolution getResolution() {
        return resolution;
    }

    @Override
    public Resolution getCoverageResolution() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long getCoverageLocatorStart() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long getCoverageLocatorEnd() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean is(Time.Type type) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Time.Type getTimeType() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean intersects(Dimension dimension) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public double getLength(Unit temporalUnit) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Time getNext() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Time earliest() {
        // TODO Auto-generated method stub
        throw new KUnimplementedException("Time::earliest");
    }

    @Override
    public Time latest() {
        throw new KUnimplementedException("Time::latest");
    }

    @Override
    public Time initialization() {
        throw new KUnimplementedException("Time::initialization");
    }

    @Override
    public Time termination() {
        throw new KUnimplementedException("Time::termination");
    }

    @Override
    public TimeInstant getFocus() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean hasChangeDuring(Time time) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Unit getDimensionUnit() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isEmpty() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Extent<?> merge(Extent<?> other, LogicalConnector how) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean matches(Collection<Constraint> constraints) {
        // TODO Auto-generated method stub
        return false;
    }

    public static Time create(Dimension dimension) {

        long[] period = dimension.getParameters().get(GeometryImpl.PARAMETER_TIME_PERIOD, long[].class);
        String representation = dimension.getParameters().get(GeometryImpl.PARAMETER_TIME_REPRESENTATION, String.class);
        Double scope = dimension.getParameters().get(GeometryImpl.PARAMETER_TIME_SCOPE, Double.class);
        String unit = dimension.getParameters().get(GeometryImpl.PARAMETER_TIME_SCOPE_UNIT, String.class);
        Long locator = dimension.getParameters().get(GeometryImpl.PARAMETER_TIME_LOCATOR, Long.class);
        String res = dimension.getParameters().get(GeometryImpl.PARAMETER_TIME_GRIDRESOLUTION, String.class);
        Long tstart = dimension.getParameters().get(GeometryImpl.PARAMETER_TIME_START, Long.class);
        Long tend = dimension.getParameters().get(GeometryImpl.PARAMETER_TIME_END, Long.class);
        String cunit = dimension.getParameters().get(GeometryImpl.PARAMETER_TIME_COVERAGE_UNIT, String.class);
        Long cstart = dimension.getParameters().get(GeometryImpl.PARAMETER_TIME_COVERAGE_START, Long.class);
        Long cend = dimension.getParameters().get(GeometryImpl.PARAMETER_TIME_COVERAGE_END, Long.class);
        Time.Type type = representation == null ? null : Time.Type.valueOf(representation.toUpperCase());

        if (type == Time.Type.INITIALIZATION) {
            return initialization((Scale) null);
        }

        if (type == Time.Type.TERMINATION) {
            return termination((Scale) null);
        }

        if (dimension.isGeneric()) {
            type = Time.Type.LOGICAL;
        }

        TimeInstant start = tstart == null ? null : TimeInstant.create(tstart);
        TimeInstant end = tend == null ? null : TimeInstant.create(tend);
        if (period != null) {
            start = TimeInstant.create(period[0]);
            end = TimeInstant.create(period[1]);
        } else if (locator != null) {
            start = TimeInstant.create(locator);
        }

        Resolution.Type coverage = null;
        if (cunit != null) {
            coverage = Resolution.Type.parse(cunit);
        }

        Resolution.Type resType = unit == null ? null : Resolution.Type.parse(unit);
        if (resType == null && res != null) {
            Resolution rres = Resolution.of(Quantity.parse(res));
            resType = rres.getType();
            scope = rres.getMultiplier();
        }

        if (type == null) {
            if (start != null && end != null) {
                type = Time.Type.PHYSICAL;
                if (resType == null) {
                    Resolution rres = Resolution.of(start, end);
                    resType = rres.getType();
                    scope = rres.getMultiplier();
                }
            }
        }

        return create(type, resType, (scope == null ? null : 1.0), start, end, null, coverage, (cstart == null ? -1 : cstart),
                (cend == null ? -1 : cend));
    }

    private static Time initialization(Scale scale) {
        TimeImpl ret = new TimeImpl((TimeImpl)scale.getTime());
        ret.extentType = Time.Type.INITIALIZATION;
        ret.start = TimeInstant.create(0);
        ret.end = TimeInstant.create(0);
        ret.size = 1;
        // ret.locatedLinearOffset = 0;
        // ret.locatedOffsets = new long[] { 0 };
        ret.resolution = Resolution.of(0, Resolution.Type.YEAR);
        return ret;
    }

    private static Time termination(Scale scale) {
        TimeImpl ret = new TimeImpl((TimeImpl)scale.getTime());
        ret.extentType = Time.Type.TERMINATION;
        ret.start = TimeInstant.create(0);
        ret.end = TimeInstant.create(0);
        ret.size = 1;
        // ret.locatedLinearOffset = 0;
        // ret.locatedOffsets = new long[] { 0 };
        ret.resolution = Resolution.of(0, ret.resolution.getType());
        // if (scale != null) {
        // ret.setScaleId(scale.getScaleId());
        // }
        return ret;
    }

    public static Time initialization(Time time) {
        TimeImpl ret = new TimeImpl((TimeImpl)time);
        ret.extentType = Time.Type.INITIALIZATION;
        ret.size = 1;
        // ret.locatedOffsets = new long[] { 0 };
        // ret.locatedLinearOffset = 0;
        // ret.parentExtent = (Time) time;
        return ret;
    }

    public static Time termination(Time time) {
        TimeImpl ret = new TimeImpl((TimeImpl)time);
        ret.extentType = Time.Type.TERMINATION;
        ret.size = 1;
        // ret.locatedOffsets = new long[] { 0 };
        // ret.locatedLinearOffset = 0;
        // ret.parentExtent = (Time) time;
        return ret;
    }

    public static Time create(int year) {
        TimeImpl ret = new TimeImpl();
        ret.extentType = Time.Type.PHYSICAL;
        ret.start = TimeInstant.create(year);
        ret.resolution = Resolution.of(1, Resolution.Type.YEAR);
        ret.end = ret.start.plus(1, ret.resolution);
        return ret;
    }
    
    public static Time create(int startYear, int endYear) {
        TimeImpl ret = new TimeImpl();
        ret.extentType = Time.Type.PHYSICAL;
        ret.start = TimeInstant.create(startYear);
        ret.end = TimeInstant.create(endYear);
        ret.resolution = Resolution.of(endYear - startYear, Resolution.Type.YEAR);
        return ret;
    }

    public static Time create(long startMillis, long endMillis) {
        TimeImpl ret = new TimeImpl();
        ret.extentType = Time.Type.PHYSICAL;
        ret.start = TimeInstant.create(startMillis);
        ret.end = TimeInstant.create(endMillis);
        ret.resolution = Resolution.of(ret.start, ret.end);
        return ret;
    }

    public static Time create(Time.Type type, Resolution.Type resolutionType, Double resolutionMultiplier, TimeInstant start,
            TimeInstant end, TimeDuration period, Resolution.Type coverageUnit, Long coverageStart, Long coverageEnd) {

        TimeImpl ret = new TimeImpl();
        ret.extentType = type;
        ret.start = start;
        ret.end = end;
        if (resolutionType != null) {
            ret.resolution = Resolution.of(resolutionMultiplier, resolutionType);
        }
        ret.step = period;
        if (ret.step != null) {
            if (type == Time.Type.REAL && ret.end == null) {
                ret.size = Geometry.INFINITE_SIZE;
            } else if (start != null && end != null) {
                ret.size = (long) (ret.getCoverage() / ret.step.getMilliseconds()) + 1;
            } else {
                ret.size = 0;
            }
        } else if (ret.extentType == Time.Type.GRID) {
            ret.setupExtents();
        }

        if (coverageUnit != null) {
            ret.coverageResolution = Resolution.of(1, coverageUnit);
            ret.coverageStart = coverageStart;
            ret.coverageEnd = coverageEnd;
        }

        return ret;
    }

    public static Time create(Time.Type type, Resolution.Type resolutionType, double resolutionMultiplier, TimeInstant start,
            TimeInstant end, TimeDuration period) {
        return create(type, resolutionType, resolutionMultiplier, start, end, period, null, null, null);
    }

    private void setupExtents() {
        if (step == null) {
            if (resolution != null) {
                if (resolution.getType().isRegular()) {
                    this.size = (long) ((end.getMilliseconds() - start.getMilliseconds())
                            / (resolution.getType().getMilliseconds() * resolution.getMultiplier())) + 1;
                } else {
                    // compute on request
                    this.regular = false;
                }
            }
        }
    }
}
