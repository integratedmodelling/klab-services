package org.integratedmodelling.klab.api.knowledge.observation.scale.time.impl;

import java.time.Duration;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.mediation.impl.Range;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time.Resolution;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeDuration;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.utils.Utils;

public class TimeDurationImpl implements TimeDuration {

    private static final long serialVersionUID = 7766515470442027169L;

    // may be anchored to a start point or not
    private TimeInstant start = null;
    private Duration period = null;
    private Resolution.Type resolution = null;
    boolean regular = true;

    private TimeDurationImpl(Duration period, TimeInstant start) {
        this.period = period;
        this.start = start;
    }

    private TimeDurationImpl(Duration period, TimeInstant start, Resolution.Type resolution) {
        this.period = period;
        this.start = start;
        this.resolution = resolution;
    }

    private TimeDurationImpl() {
    }

    @Override
    public Resolution.Type getResolution() {

        if (this.resolution == null) {

            // order of magnitude
            Range order = Range.create(1, 9.999, false);

            if (order.contains(getMilliseconds() / Resolution.Type.MILLENNIUM.getMilliseconds())) {
                this.resolution = Resolution.Type.MILLENNIUM;
            } else if (order.contains(getMilliseconds() / Resolution.Type.CENTURY.getMilliseconds())) {
                this.resolution = Resolution.Type.CENTURY;
            } else if (order.contains(getMilliseconds() / Resolution.Type.DECADE.getMilliseconds())) {
                this.resolution = Resolution.Type.DECADE;
            } else if (order.contains(getMilliseconds() / Resolution.Type.YEAR.getMilliseconds())) {
                this.resolution = Resolution.Type.YEAR;
            } else if (order.contains(getMilliseconds() / Resolution.Type.MONTH.getMilliseconds())) {
                this.resolution = Resolution.Type.MONTH;
            } else if (order.contains(getMilliseconds() / Resolution.Type.WEEK.getMilliseconds())) {
                this.resolution = Resolution.Type.WEEK;
            } else if (order.contains(getMilliseconds() / Resolution.Type.DAY.getMilliseconds())) {
                this.resolution = Resolution.Type.DAY;
            } else if (order.contains(getMilliseconds() / Resolution.Type.HOUR.getMilliseconds())) {
                this.resolution = Resolution.Type.HOUR;
            } else if (order.contains(getMilliseconds() / Resolution.Type.MINUTE.getMilliseconds())) {
                this.resolution = Resolution.Type.MINUTE;
            } else if (order.contains(getMilliseconds() / Resolution.Type.SECOND.getMilliseconds())) {
                this.resolution = Resolution.Type.SECOND;
            } else {
                this.resolution = Resolution.Type.MILLISECOND;
            }
        }
        return this.resolution;
    }

    public static TimeDuration create(TimeInstant start, TimeInstant end, boolean anchor) {
        Duration period = Duration.ofMillis(end.getMilliseconds() - start.getMilliseconds());
        return new TimeDurationImpl(period, anchor ? start : null);
    }

    public static TimeDuration create(TimeInstant start, TimeInstant end, Resolution.Type resolution) {
        Duration period = Duration.ofMillis(end.getMilliseconds() - start.getMilliseconds());
        return new TimeDurationImpl(period, start, resolution);
    }

    public static TimeDuration create(long start, long end, Resolution.Type resolution) {
        Duration period = Duration.ofMillis(end - start);
        return new TimeDurationImpl(period, TimeInstant.create(start), resolution);
    }

    @Override
    public int compareTo(TimeDuration o) {
        return Long.compare(getMilliseconds(), o.getMilliseconds());
    }

    @Override
    public long getMilliseconds() {
        if (start == null) {
            return period.toMillis();
        }
        return ((TimeInstantImpl) start).asDate().plus(this.period).toInstant().toEpochMilli() - start.getMilliseconds();
    }

    @Override
    public Pair<TimeInstant, TimeInstant> localize() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isEmpty() {
        return getMilliseconds() == 0;
    }

    public Duration asDuration() {
        return period;
    }

    public String getDescription() {
        return Utils.Strings.capitalize(resolution.name().toLowerCase()) + " " + period.toString();
    }

    @Override
    public String toString() {
        return period.toString();
    }

    @Override
    public TimeDuration anchor(TimeInstant instant) {
        return new TimeDurationImpl(period, instant);
    }

    @Override
    public boolean isAnchored() {
        return start != null;
    }

    @Override
    public TimeInstant getStart() {
        return start;
    }

    public static TimeDuration create(long milliseconds, Resolution.Type type) {
        TimeDurationImpl ret = new TimeDurationImpl();
        ret.resolution = type;
        ret.period = Duration.ofMillis(milliseconds);
        return ret;
    }

    @Override
    public boolean isRegular() {
        return resolution.isRegular();
    }

    @Override
    public long getMaxMilliseconds() {
        return regular ? getMilliseconds() : 0;
    }

    @Override
    public long getCommonDivisorMilliseconds() {
        // TODO Auto-generated method stub
        return regular ? getMilliseconds() : 0;
    }

    @Override
    public String getSpecification() {
        // TODO Auto-generated method stub
        return "todo";
    }

}
