package org.integratedmodelling.klab.runtime.scale.time;

import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.exceptions.KUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.geometry.impl.GeometryImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.TopologicallyComparable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TemporalExtension;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeDuration;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.impl.ServiceCallImpl;
import org.integratedmodelling.klab.api.services.UnitService;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.runtime.scale.ExtentImpl;

import java.io.Serial;
import java.util.Collection;
import java.util.Iterator;

public class TimeImpl extends ExtentImpl<Time> implements Time {

    @Serial
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
    private TimeInstant focus;

    private TemporalExtension extension;

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
        this.focus = time.focus;
        this.extension = time.extension;
    }

    public Time.Type getExtentType() {
        return extentType;
    }


    public TemporalExtension getExtension() {
        return extension;
    }

    public void setExtension(TemporalExtension extension) {
        this.extension = extension;
    }

    public void setExtentType(Time.Type extentType) {
        this.extentType = extentType;
    }

    @Override
    public Time at(Locator locator) {

        if (locator == this || locator.equals(this)) {
            return this;
        }

        // TODO Auto-generated method stub
//        if (locators != null && locators.length == 1) {
//            if (locators[0] instanceof String && "INITIALIZATION".equals(locators[0])) {
//                return initialization(this);
//            } else if (locators[0] instanceof String && "TERMINATION".equals(locators[0])) {
//                return termination(this);
//            } else if (locators[0] instanceof Number) {
//                long ofs = ((Number) locators[0]).longValue();
//                if (this.size() == 1 && ofs == 0) {
//                    return this;
//                } else if (this.size() > ofs) {
//                    return getExtent(ofs);
//                }
//            } else if (locators[0] instanceof Time) {
//                if (((Time) locators[0]).is(ITime.Type.INITIALIZATION)) {
//                    // initialization but with our scaleId
//                    return new Time((Time) locators[0]).withScaleId(getScaleId()).withLocatedOffset(0);
//                } else if (((Time) locators[0]).__id == this.__id) {
//                    return (IExtent) locators[0];
//                } else if (locators[0] instanceof TimesliceLocator) {
//                    return /* this.focus((ITimeInstant) */((TimesliceLocator) locators[0])/*
//                     * .getStart ())
//                     */;
//                } else if (((Time) locators[0]).getLocatedOffsets() != null) {
//                    return (IExtent) locators[0];
//                } else if (((Time) locators[0]).focus != null) {
//                    return focus(((Time) locators[0]).focus);
//                } else {
//                    /*
//                     * Mediation situation. Because of the irregular extents, not doing the coverage
//                     * thing. TODO: do the coverage thing.
//                     */
//                    Time other = (Time) locators[0];
//                    IExtent start = at(other.getStart());
//                    IExtent end = at(other.getEnd());
//                    // TODO compute how much other.getStart() leaves out of start() and add it
//                    // somehow to the coverage for the first and last steps
//                    if (start.equals(end)) {
//                        // works for initialization, too
//                        return other;
//                    }
//                    return new TimeGrid(other);
//                }
//            } else if (locators[0] instanceof ITimeInstant) {
//
//                /*
//                 * Pick the sub-extent containing the instant or return the entire scale if we
//                 * have none. In all cases focalize on the specific instant requested.
//                 */
//                if (end.equals((ITimeInstant) locators[0])) {
//                    return termination(this);
//                }
//                if (!(start == null || start.isAfter((ITimeInstant) locators[0])
//                        || (end != null && end.isBefore((ITimeInstant) locators[0])))) {
//
//                    if (size() <= 1 || extension != null) {
//                        return this.focus((ITimeInstant) locators[0]);
//                    }
//                    Time last = null;
//                    for (int i = 1; i < size(); i++) {
//                        last = (Time) getExtent(i);
//                        if (last.getStart().getMilliseconds() >= ((ITimeInstant) locators[0]).getMilliseconds()
//                                || last.getEnd().getMilliseconds() > ((ITimeInstant) locators[0]).getMilliseconds()) {
//                            return last.focus((ITimeInstant) locators[0]);
//                        }
//                        if (last != null
//                                && last.getEnd().getMilliseconds() == ((ITimeInstant) locators[0]).getMilliseconds
//                                ()) {
//                            // admit a locator focused on the immediate after
//                            return last.focus((ITimeInstant) locators[0]);
//                        }
//                        // long target = ((ITimeInstant) locators[0]).getMilliseconds();
//                        // long tleft = target - start.getMilliseconds();
//                        // long n = tleft / resolution.getSpan() + 1;
//                        // if (target == end.getMilliseconds()) {
//                        // /*
//                        // * last extent, located to get the point before the beginning of the
//                        // * next period
//                        // */
//                        // Time ret = (Time) getExtent(size() - 1);
//                        // return ret.focus((ITimeInstant) locators[0]);
//                        //
//                        // } else if (n >= 0 && n < size()) {
//                        // Time ret = (Time) getExtent(n);
//                        // long nn = n;
//                        // // previous was approximate due to potential irregularity; correct as
//                        // // needed
//                        // while(nn > 0 && ret.getEnd().isBefore(((ITimeInstant) locators[0]))) {
//                        // ret = (Time) getExtent(++nn);
//                        // }
//                        // nn = n;
//                        // while(nn < size() && ret.getStart().isAfter(((ITimeInstant)
//                        // locators[0]))) {
//                        // ret = (Time) getExtent(--nn);
//                        // }
//                        // return ret.focus((ITimeInstant) locators[0]);
//                        // }
//                    }
//                }
//            }
//        }

        return null;
    }

    @Override
    public int getRank() {
        return resolution.getType().getRank();
    }

    @Override
    public double getDimensionSize() {
        return start != null && end != null ? end.getMilliseconds() - start.getMilliseconds() :
                Double.POSITIVE_INFINITY;
    }

    @Override
    public double getStandardizedDimension(Locator locator) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public <T extends TopologicallyComparable<T>> Extent<T> merge(Extent<T> other, LogicalConnector how) {

        if (how == LogicalConnector.UNION) {
            start = TimeInstant
                    .create(Long.min(this.start.getMilliseconds(), ((Time) other).getStart().getMilliseconds()));
            end = TimeInstant
                    .create(Long.max(this.end.getMilliseconds(), ((Time) other).getEnd().getMilliseconds()));
        } else if (how == LogicalConnector.INTERSECTION) {
            start = TimeInstant
                    .create(Long.max(this.start.getMilliseconds(), ((Time) other).getStart().getMilliseconds()));
            end = TimeInstant
                    .create(Long.min(this.end.getMilliseconds(), ((Time) other).getEnd().getMilliseconds()));
        }

        // TODO this removes the extension and the type, and may change the resolution
        return (Extent<T>) create(start.getMilliseconds(), end.getMilliseconds());
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
        if (extentType == Time.Type.INITIALIZATION) {
            return this;
        }
        if (this.distributed()) {
            // TODO rewind until 1 res unit before start
            // FIXME maybe not - probably we should just have the runtime decide what to do

        }
        return new TimeImpl(this).withType(Time.Type.INITIALIZATION);
    }

    @Override
    public TimeImpl copy() {
        return new TimeImpl(this);
    }

    private Time withType(Time.Type type) {
        this.extentType = type;
        return this;
    }

    public boolean distributed() {
        return getTimeType() == Time.Type.GRID || getTimeType() == Time.Type.REAL || (this.extension != null && this.extension.size() > 1);
    }

    @Override
    public Time termination() {
        if (extentType == Time.Type.TERMINATION) {
            return this;
        }
        if (this.distributed()) {
            // TODO wind up until 1 res unit after end unless we're realtime
            // FIXME maybe not
        }
        return new TimeImpl(this).withType(Time.Type.TERMINATION);
    }

    @Override
    public TimeInstant getFocus() {
        return this.focus;
    }

    @Override
    public Unit getDimensionUnit() {
        return Configuration.INSTANCE.getService(UnitService.class).milliseconds();
    }

    @Override
    public boolean isEmpty() {
        // TODO Auto-generated method stub
        return false;
    }


    @Override
    public boolean matches(Collection<Constraint> constraints) {
        // TODO Auto-generated method stub
        return false;
    }

    protected ServiceCall encodeCall() {
        ServiceCallImpl ret = new ServiceCallImpl();
        // TODO
        return ret;
    }

    @Override
    public String encode(String language) {
        return encodeCall().encode(language);
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

        return create(type, resType, (scope == null ? null : 1.0), start, end, null, coverage, (cstart == null ? -1 :
                        cstart),
                (cend == null ? -1 : cend));
    }

    private static Time initialization(Scale scale) {
        TimeImpl ret = new TimeImpl((TimeImpl) scale.getTime());
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
        TimeImpl ret = new TimeImpl((TimeImpl) scale.getTime());
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
        TimeImpl ret = new TimeImpl((TimeImpl) time);
        ret.extentType = Time.Type.INITIALIZATION;
        ret.size = 1;
        // ret.locatedOffsets = new long[] { 0 };
        // ret.locatedLinearOffset = 0;
        // ret.parentExtent = (Time) time;
        return ret;
    }

    public static Time termination(Time time) {
        TimeImpl ret = new TimeImpl((TimeImpl) time);
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

    public static TimeImpl create(long startMillis, long endMillis) {
        TimeImpl ret = new TimeImpl();
        ret.extentType = Time.Type.PHYSICAL;
        ret.start = TimeInstant.create(startMillis);
        ret.end = TimeInstant.create(endMillis);
        ret.resolution = Resolution.of(ret.start, ret.end);
        return ret;
    }

    public static Time create(Time.Type type, Resolution.Type resolutionType, Double resolutionMultiplier,
                              TimeInstant start,
                              TimeInstant end, TimeDuration period, Resolution.Type coverageUnit, Long coverageStart,
                              Long coverageEnd) {

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

    public static Time create(Time.Type type, Resolution.Type resolutionType, double resolutionMultiplier,
                              TimeInstant start,
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
