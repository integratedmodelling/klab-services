/*
 * This file is part of k.LAB.
 * 
 * k.LAB is free software: you can redistribute it and/or modify
 * it under the terms of the Affero GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root
 * directory of the k.LAB distribution (LICENSE.txt). If this cannot be found 
 * see <http://www.gnu.org/licenses/>.
 * 
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned
 * in author tags. All rights reserved.
 */
package org.integratedmodelling.klab.api.data.mediation;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.mediation.impl.Range;

/**
 * The Interface INumericRange.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface NumericRange extends ValueMediator, Comparable<NumericRange> {
    
    /**
     * Any unbounded boundary will be the corresponding Double.(NEGATIVE_)INFINITE.
     *
     * @return the range of the scale.
     */
    double getLowerBound();

    /**
     * Any unbounded boundary will be the corresponding Double.(NEGATIVE_)INFINITE.
     *
     * @return the range of the scale.
     */
    double getUpperBound();

    /**
     * Both bounds are defined
     *
     * @return true if both boundaries are defined.
     */
    boolean isBounded();

    /**
     * If only integers should be used.
     *
     * @return true if the scale is not meant to have values in non-integer numbers.
     */
    boolean isInteger();

    boolean isInfinite();

    boolean isLowerExclusive();

    boolean isUpperOpen();

    boolean isRightInfinite();

    boolean isLeftInfinite();

    /**
     * true if the upper boundary is closed, i.e. includes the limit
     * 
     * @return true if upper boundary is closed
     */
    boolean isRightBounded();

    /**
     * true if the lower boundary is closed, i.e. includes the limit
     * 
     * @return true if lower bounday is closed
     */
    boolean isLeftBounded();

    boolean contains(double d);

    /**
     * Record the passed value as a part of the range, adjusting boundaries as
     * needed.
     * 
     * @param value
     */
    void adapt(double value);

    /**
     * Normalize the passed value to this range, which must include it.
     * 
     * @param value
     * @return the normalized value (0-1)
     */
    double normalize(double value);

    double getWidth();

    double getMidpoint();

    NumericRange intersection(NumericRange other);

    boolean overlaps(NumericRange other);

    /**
     * Returns the minimal range that {@linkplain #encloses encloses} both this range and {@code
     * other}. For example, the span of {@code [1..3]} and {@code (5..7)} is {@code [1..7)}.
     *
     * <p>
     * <i>If</i> the input ranges are {@linkplain #isConnected connected}, the returned range can
     * also be called their <i>union</i>. If they are not, note that the span might contain values
     * that are not contained in either input range.
     *
     * <p>
     * Like {@link #intersection(Range) intersection}, this operation is commutative, associative
     * and idempotent. Unlike it, it is always well-defined for any two input ranges.
     */
    NumericRange span(NumericRange other);

    /**
     * Stretch one of the ends so that the passed value is the midpoint. If the midpoint isn't in
     * the range, return self.
     * 
     * @param midpoint
     * @return
     */
    NumericRange stretchForMidpoint(double midpoint);

    boolean contains(NumericRange other);

    /**
     * Return a [0-1] double representing how much this interval excludes of the other. Will compute
     * the missing parts on each side, normalize to the extent of the range, and add them in the
     * output, dealing with infinity appropriately.
     * 
     * @param other
     * @return
     */
    double exclusionOf(NumericRange other);

    /**
     * Return another range that includes the passed one and aligns with this when divided by the
     * passed number of cells, which is expected to divide our width exactly. Also return a pair of
     * doubles representing the amount of coverage of the left and right cells in the original
     * range, in [0, 1) with 0,0 if they originally aligned exactly.
     * 
     * @param original the range to align
     * @param nCells the number of subdivisions in this range
     * @return 1) a new range that includes original and aligns with the grid we represent at the
     *         passed resolution. If original contains this on either side, cut it to align. 2) two
     *         doubles for the left and right percentage of original error (amount of cell covered
     *         in the original range).
     */
    Pair<NumericRange, Pair<Double, Double>> snap(NumericRange original, long nCells);

    boolean isWithin(double n);

    /**
     * A reference point in the interval, i.e. the midpoint if bounded, any boundary point that is
     * not infinity if not, and NaN if infinite.
     * 
     * @return
     */
    double getFocalPoint();

    boolean isUpperExclusive();
}
