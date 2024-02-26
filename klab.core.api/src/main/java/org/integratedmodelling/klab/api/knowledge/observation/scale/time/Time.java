/*
 * This file is part of k.LAB.
 *
 * k.LAB is free software: you can redistribute it and/or modify it under the terms of the Affero
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root directory of the k.LAB
 * distribution (LICENSE.txt). If this cannot be found see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned in author tags. All
 * rights reserved.
 */
package org.integratedmodelling.klab.api.knowledge.observation.scale.time;

import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.impl.ResolutionImpl;
import org.integratedmodelling.klab.api.lang.Encodeable;
import org.integratedmodelling.klab.api.lang.Quantity;

/**
 * Time, as seen by k.LAB when the default contextualizer time() is used.
 * <p>
 * Legal time extents are:
 *
 * <ul>
 * <li>Generic with resolution and no specific interval: dependencies will be matched only by
 * resolution and no temporal mediation will be possible.</li>
 * <li>Generic with resolution and interval: dependencies will be matched by resolution and
 * closeness to the interval; mediation will be possible; resources having time <i>closer</i> to the
 * interval will be chosen even if they are not within the interval. This will be the default time
 * extent for k.Explorer users, tuned in on the current year.</li>
 * <li>Specific with resolution and interval: dependencies will be matched by resolution and
 * interval; mediation will be possible; resources having time not within the interval will
 * <i>not</i> be chosen even if they are.</li>
 * <li>Regular grid must have interval and step: dependencies will be matched by resolution and
 * interval, choosing "moving" dependencies over initialization-only ones; mediation will be
 * possible. The end of period may remain unspecified for endless process simulations.</li>
 * <li>Irregular grid has steps that follow a resource and may have individually different duration.
 * Any use of this is likely to involve complex and potentially costly mediations.</li>
 * <li>Event time is an irregular grid aligned with a source of events. A transition is generated
 * per each event.</li>
 * <li>Real time is a grid aligned with the current time. Start time is now if not specified. End
 * time, if specified, must be in the actual future.</li>
 * </ul>
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface Time extends Extent<Time>, Encodeable {

    /**
     * Constant <code>MIN_SCALE_RANK=0</code>
     */
    int MIN_SCALE_RANK = 0;
    /**
     * Constant <code>MAX_SCALE_RANK=10</code>
     */
    int MAX_SCALE_RANK = 10;

    static public interface Resolution {

        public enum Type {

            MILLENNIUM(0), CENTURY(1), DECADE(2), YEAR(3), MONTH(4), WEEK(5), DAY(6), HOUR(7), MINUTE(8), SECOND(9),
            MILLISECOND(
                    10);

            int rank;

            Type(int rank) {
                this.rank = rank;
            }

            public int getRank() {
                return rank;
            }

            public boolean isRegular() {
                return switch (this) {
                    case DAY, HOUR, MILLISECOND, MINUTE, SECOND, WEEK -> true;
                    default -> false;
                };
            }

            public long getMilliseconds() {
                return switch (this) {
                    case MILLISECOND -> 1;
                    case SECOND -> 1000;
                    case MINUTE -> 1000L * 60L;
                    case HOUR -> 1000L * 60L * 60L;
                    case DAY -> 1000L * 60L * 60L * 24L;
                    case WEEK -> 1000L * 60L * 60L * 24L * 7L;
                    case MONTH -> 1000L * 60L * 60L * 24L * 30;
                    case YEAR -> 1000L * 60L * 60L * 24L * 365L;
                    case DECADE -> 1000L * 60L * 60L * 24L * 365L * 10L;
                    case CENTURY -> 1000L * 60L * 60L * 24L * 365L * 100L;
                    case MILLENNIUM -> 1000L * 60L * 60L * 24L * 365L * 1000L;
                };
            }

            public String getPredicate() {
                return switch (this) {
                    case MILLISECOND -> "millisecond";
                    case SECOND -> "second";
                    case MINUTE -> "minute";
                    case HOUR -> "hourly";
                    case DAY -> "daily";
                    case WEEK -> "weekly";
                    case MONTH -> "monthly";
                    case YEAR -> "yearly";
                    case DECADE -> "decadal";
                    case CENTURY -> "century";
                    case MILLENNIUM -> "millennial";
                };
            }

            public Resolution as(Type type) {
               return new ResolutionImpl(type, (double) type.getMilliseconds() / (double) this.getMilliseconds());
            }

            public static Type parse(String unit) {

                if (unit.length() > 1) {
                    // case insensitive beyond the single letter
                    unit = unit.toLowerCase();
                }

                switch (unit) {
                    case "M":
                    case "millennium":
                    case "millennia":
                        return Type.MILLENNIUM;
                    case "C":
                    case "century":
                    case "centuries":
                        return Type.CENTURY;
                    case "decades":
                    case "decade":
                        return Type.DECADE;
                    case "y":
                    case "yr":
                    case "year":
                        return Type.YEAR;
                    case "month":
                    case "months":
                    case "mon":
                        return Type.MONTH;
                    case "week":
                    case "weeks":
                    case "wk":
                    case "w":
                        return Type.WEEK;
                    case "d":
                    case "day":
                    case "days":
                        return Type.DAY;
                    case "h":
                    case "hr":
                    case "hour":
                    case "hours":
                        return Type.HOUR;
                    case "m":
                    case "min":
                    case "mins":
                    case "minute":
                    case "minutes":
                        return Type.MINUTE;
                    case "s":
                    case "sec":
                    case "secs":
                    case "second":
                    case "seconds":
                        return Type.SECOND;
                    case "ms":
                    case "milliseconds":
                    case "millisecond":
                        return Type.MILLISECOND;
                }
                throw new KlabValidationException("invalid time unit for resolution: " + unit);
            }

        }

        Type getType();

        double getMultiplier();

        /**
         * Get the number of units of this resolution between the two time points.
         *
         * @param start
         * @param end
         * @return
         */
        double getMultiplier(TimeInstant start, TimeInstant end);

        /**
         * Get the <em>indicative</em> span of one step in milliseconds. Spans may be under-estimates if the resolution
         * is in irregular steps, such as months, years and multiple thereof. In such cases, the smallest interval will
         * be reported and isRegular() will return false.
         *
         * @return
         */
        long getSpan();

        /**
         * @param multiplier
         * @param type
         * @return
         */
        static Resolution of(double multiplier, Type type) {
            return new ResolutionImpl(type, multiplier);
        }

        public static Resolution of(Quantity spec) {
            return new ResolutionImpl(Resolution.Type.parse(spec.getUnit().toString()), spec.getValue().doubleValue());
        }

        public static Resolution of(TimeInstant start, TimeInstant end) {
            return of(end.getMilliseconds() - start.getMilliseconds());
        }

        public static Resolution of(long ms) {

            Type type = Resolution.Type.MILLISECOND;
            // order of magnitude
            NumericRangeImpl order = NumericRangeImpl.create(1, 9.999, false);

            if (order.contains((double) ms / Resolution.Type.MILLENNIUM.getMilliseconds())) {
                type = Resolution.Type.MILLENNIUM;
            } else if (order.contains((double) ms / Resolution.Type.CENTURY.getMilliseconds())) {
                type = Resolution.Type.CENTURY;
            } else if (order.contains((double) ms / Resolution.Type.DECADE.getMilliseconds())) {
                type = Resolution.Type.DECADE;
            } else if (order.contains((double) ms / Resolution.Type.YEAR.getMilliseconds())) {
                type = Resolution.Type.YEAR;
            } else if (order.contains((double) ms / Resolution.Type.MONTH.getMilliseconds())) {
                type = Resolution.Type.MONTH;
            } else if (order.contains((double) ms / Resolution.Type.WEEK.getMilliseconds())) {
                type = Resolution.Type.WEEK;
            } else if (order.contains((double) ms / Resolution.Type.DAY.getMilliseconds())) {
                type = Resolution.Type.DAY;
            } else if (order.contains((double) ms / Resolution.Type.HOUR.getMilliseconds())) {
                type = Resolution.Type.HOUR;
            } else if (order.contains((double) ms / Resolution.Type.MINUTE.getMilliseconds())) {
                type = Resolution.Type.MINUTE;
            } else if (order.contains((double) ms / Resolution.Type.SECOND.getMilliseconds())) {
                type = Resolution.Type.SECOND;
            }

            return new ResolutionImpl(type, (double) ms / (double) type.getMilliseconds());
        }

        public static Resolution parse(String string) {
            return Resolution.of(Quantity.parse(string));
        }

    }

    static public enum Type {

        /**
         * Time before time exists: used internally in contextualizing perdurants.
         */
        INITIALIZATION,

        /**
         * Time after all transitions have happened. Also used to contexualize views and non-semantic artifacts that
         * must see the entire context.
         */
        TERMINATION,

        /**
         * Generic focus on a period without temporally locating it but specifying the length of the period of
         * interest.
         */
        LOGICAL,

        /**
         * Specific time period of any lenght, single multiplicity
         */
        PHYSICAL,

        /**
         * Time grid, multiplicity N.
         */
        GRID,

        /**
         * Real time, which is necessarily a grid, potentially irregular, multiplicity may be infinite if end is
         * undefined.
         */
        REAL
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overriding to require that the collapsed type is Time. This allows simpler coding against the API, and is the
     * most logical way to enforce that {@link #size()} == 1.
     */
    @Override
    Time collapsed();

    @Override
    Time getExtent(long stateIndex);

    /**
     * Locating a temporal extent includes the possibility of "focusing" it on a specific time instant. When a
     * {@link TimeInstant} is passed to locate an extent whose span includes it, the resulting extent will be the
     * narrowest span that includes it (a sub-extent if this time is multiple, or the whole extent if not) and will
     * include the specific instant as its focal time point, returned by {@link #getFocus()}.
     *
     * @param locator
     * @return
     */
    @Override
    Time at(Locator locator);

    /**
     * May be null in partially specified extents.
     *
     * @return start time
     */
    TimeInstant getStart();

    /**
     * May be null in partially specified extents. Returns the timestep BEYOND the end of computation - the one that the
     * time reaches, not the one it computes last. The latter is returned by getLast().
     *
     * @return end time
     */
    TimeInstant getEnd();

    /**
     * If multiplicity is 1, return the whole temporal extent.
     * <p>
     * FIXME this should only be defined if time is a grid - as done in ISpatialExtent (use a Grid
     * object).
     *
     * @return step if any
     */
    TimeDuration getStep();

    /**
     * Resolution of time observation according to this extent.
     *
     * @return
     */
    Resolution getResolution();

    /**
     * A logical time can have a resolution for its coverage, e.g. specifying "any month of january within a year" would
     * have resolution = year and coverageResolution = month, with a start coverage = 0 and end coverage = 1. If null,
     * there is no partial coverage.
     *
     * @return
     */
    Resolution getCoverageResolution();

    /**
     * Only for logical time: specifies the start offset of the covered portion within the overall span if the coverage
     * resolution is specified. The value is given in the coverage resolution unit.
     *
     * @return
     */
    long getCoverageLocatorStart();

    /**
     * Only for logical time: specifies the end offset of the covered portion within the overall span if the coverage
     * resolution is specified. The value is given in the coverage resolution unit.
     *
     * @return
     */
    long getCoverageLocatorEnd();

    /**
     * Check the type against the passed one.
     *
     * @param type
     * @return
     */
    boolean is(Type type);

    /**
     * Get the time type. Turns out there may be many, many ways to interpret time.
     *
     * @return
     */
    Type getTimeType();

    /**
     * Needed to check for intersection with resource geometry. Should probably redefine intersects etc. in
     * Geometry.Dimension and specialize, but for now keep the ad-hoc redundancy.
     *
     * @param dimension, guaranteed to have Type = TIME.
     * @return
     */
    boolean intersects(Dimension dimension);

    /**
     * Return the length of the period in the passed unit, which must be temporal.
     *
     * @param temporalUnit
     * @return
     */
    double getLength(Unit temporalUnit);

    /**
     * If this extent is a subdivision of a distributed extent, return the next in line after it, or null if it's the
     * last subdivision. Otherwise return null.
     *
     * @return
     */
    Time getNext();

    /**
     * If this extent is a subdivision of a distributed extent, return the first sub-extent time that is not
     * initialization. If not, return null.
     *
     * @return
     */
    Time earliest();

    /**
     * If this extent is a subdivision of a distributed extent, return the last sub-extent time that is not
     * initialization. If not, return null.
     *
     * @return
     */
    Time latest();

    /**
     * The initialization time, before time exists. Only defined for occurrent time extents.
     *
     * @return
     */
    Time initialization();

    /**
     * The termination time, after time has finished existing. Only defined for occurrent time extents.
     *
     * @return
     */
    Time termination();


    /**
     * A temporal extent always represents a period, but when created from a query may be simply focused on a particular
     * time point, used to locate the correct period in a scale that has (regular or irregular) time slices. These are
     * not produced during contextualization but can be used as locators. The focal instant is guaranteed to be within
     * the time span this represents.
     *
     * @return
     */
    TimeInstant getFocus();

//    /**
//     * Time extents record the actual changes in the observations they describe. This method checks if there have been
//     * one or more changes in the artifact's state during the time passed.
//     *
//     * @param time
//     * @return
//     */
//    boolean hasChangeDuring(Time time);

}
