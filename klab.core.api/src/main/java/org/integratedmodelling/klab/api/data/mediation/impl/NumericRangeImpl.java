package org.integratedmodelling.klab.api.data.mediation.impl;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.data.mediation.NumericRange;
import org.integratedmodelling.klab.api.data.mediation.ValueMediator;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;

import java.util.Date;
import java.util.List;

public class NumericRangeImpl implements NumericRange {

    private static final long serialVersionUID = 874216692405815586L;

    double lowerBound = Double.NEGATIVE_INFINITY;
    double upperBound = Double.POSITIVE_INFINITY;
    boolean lowerExclusive = false;
    boolean upperExclusive = false;
    boolean lowerInfinite = true;
    boolean upperInfinite = true;

    /**
     * Create a [-Inf, +Inf] range.
     */
    public NumericRangeImpl() {
    }

    /**
     * Parse a range from the string representation.
     * 
     * @param intvs
     */
    public NumericRangeImpl(String intvs) {
        parse(intvs);
    }

    @Override
    public boolean isBounded() {
        return !isLeftInfinite() && !isRightInfinite();
    }

    @Override
    public boolean isInfinite() {
        return lowerBound == Double.NEGATIVE_INFINITY || upperBound == Double.POSITIVE_INFINITY;
    }

    /**
     * Create a range.
     * 
     * @param left
     * @param right
     * @param leftExclusive
     * @param rightExclusive
     */
    public NumericRangeImpl(Double left, Double right, boolean leftExclusive, boolean rightExclusive) {

        if (!(lowerInfinite = (left == null)))
            lowerBound = left;

        if (!(upperInfinite = (right == null)))
            upperBound = right;

        if (lowerBound > upperBound) {
            double s = lowerBound;
            lowerBound = upperBound;
            upperBound = s;
        }

        lowerExclusive = leftExclusive;
        upperExclusive = rightExclusive;
    }

    public NumericRangeImpl(NumericRangeImpl range) {
        this.lowerBound = range.lowerBound;
        this.upperBound = range.upperBound;
        this.lowerExclusive = range.lowerExclusive;
        this.upperExclusive = range.upperExclusive;
        this.lowerInfinite = range.lowerInfinite;
        this.upperInfinite = range.upperInfinite;
    }

    @Override
    public boolean isLowerExclusive() {
        return lowerExclusive;
    }

    @Override
    public boolean isUpperExclusive() {
        return lowerExclusive;
    }

    public void setLowerExclusive(boolean b) {
        this.lowerExclusive = b;
    }

    public void parse(String s) {

        /*
         * OK, can't do it with StreamTokenizer as the silly thing does not read scientific
         * notation.
         */

        lowerInfinite = false;
        upperInfinite = false;

        s = s.trim();
        if (s.startsWith("(")) {
            lowerExclusive = true;
            s = s.substring(1);
        } else if (s.startsWith("[")) {
            lowerExclusive = false;
            s = s.substring(1);
        }

        if (s.endsWith(")")) {
            upperExclusive = true;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("]")) {
            upperExclusive = false;
            s = s.substring(0, s.length() - 1);
        }

        String upper = null;
        String lower = null;
        s = s.trim();
        if (s.startsWith(",")) {
            lowerInfinite = true;
            upper = s.substring(1).trim();
        }
        if (s.endsWith(",")) {
            upperInfinite = true;
            lower = s.substring(0, s.length() - 1).trim();
        }
        if (!s.startsWith(",") && !s.endsWith(",")) {

            if (!s.contains(",")) {
                throw new IllegalArgumentException("invalid interval syntax: " + s);
            }

            String[] ss = s.split(",");
            lowerBound = Double.valueOf(ss[0].trim());
            upperBound = Double.valueOf(ss[1].trim());
        } else {

            if (upper != null && !upper.isEmpty()) {
                upperBound = Double.valueOf(upper);
            }
            if (lower != null && !lower.isEmpty()) {
                lowerBound = Double.valueOf(lower);
            }
        }
    }

    @Override
    public int compareTo(NumericRange i) {

        if (lowerInfinite == i.isLeftInfinite() && lowerExclusive == i.isLowerExclusive() && upperInfinite == i.isRightInfinite()
                && upperExclusive == i.isUpperExclusive() && lowerBound == i.getLowerBound() && upperBound == i.getUpperBound())
            return 0;

        if (this.upperBound <= i.getLowerBound())
            return -1;

        if (this.lowerBound >= i.getUpperBound())
            return 1;

        throw new IllegalArgumentException("error: trying to sort overlapping numeric intervals");

    }

    @Override
    public boolean isEmpty() {
        return lowerBound == upperBound;
    }

    @Override
    public boolean isUpperOpen() {
        return upperExclusive;
    }

    public void setUpperOpen(boolean upperOpen) {
        this.upperExclusive = upperOpen;
    }

    public void setLowerBound(Double lowerBound) {
        lowerInfinite = lowerBound == null || lowerBound == Double.NEGATIVE_INFINITY;
        if (lowerBound != null) {
            this.lowerBound = lowerBound;
        }
    }

    public void setUpperBound(Double upperBound) {
        upperInfinite = upperBound == null || upperBound == Double.POSITIVE_INFINITY;
        if (upperBound != null) {
            this.upperBound = upperBound;
        }
    }

    public void setLeftInfinite(boolean leftInfinite) {
        this.lowerInfinite = leftInfinite;
        this.lowerBound = Double.NEGATIVE_INFINITY;
    }

    public void setRightInfinite(boolean rightInfinite) {
        this.upperInfinite = rightInfinite;
        this.upperBound = Double.POSITIVE_INFINITY;
    }

    @Override
    public boolean isRightInfinite() {
        return upperInfinite;
    }

    @Override
    public boolean isLeftInfinite() {
        return lowerInfinite;
    }

    /**
     * true if the upper boundary is closed, i.e. includes the limit
     * 
     * @return true if upper boundary is closed
     */
    @Override
    public boolean isRightBounded() {
        return !upperExclusive;
    }

    /**
     * true if the lower boundary is closed, i.e. includes the limit
     * 
     * @return true if lower bounday is closed
     */
    @Override
    public boolean isLeftBounded() {
        return !lowerExclusive;
    }

    @Override
    public double getLowerBound() {
        return lowerBound;
    }

    @Override
    public double getUpperBound() {
        return upperBound;
    }

    @Override
    public boolean contains(double d) {

        if (lowerInfinite)
            return (upperExclusive ? d < upperBound : d <= upperBound);
        else if (upperInfinite)
            return (lowerExclusive ? d > lowerBound : d >= lowerBound);
        else
            return (upperExclusive ? d < upperBound : d <= upperBound) && (lowerExclusive ? d > lowerBound : d >= lowerBound);
    }

    @Override
    public String toString() {

        String ret = "";

        if (!lowerInfinite) {
            ret += lowerExclusive ? "(" : "[";
            ret += lowerBound;
        }
        if (!upperInfinite) {
            if (!lowerInfinite)
                ret += " ";
            ret += upperBound;
            ret += upperExclusive ? ")" : "]";
        }

        return ret;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (lowerInfinite ? 1231 : 1237);
        long temp;
        temp = Double.doubleToLongBits(lowerBound);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + (lowerExclusive ? 1231 : 1237);
        result = prime * result + (upperInfinite ? 1231 : 1237);
        temp = Double.doubleToLongBits(upperBound);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + (upperExclusive ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        NumericRangeImpl other = (NumericRangeImpl) obj;
        if (lowerInfinite != other.lowerInfinite)
            return false;
        if (Double.doubleToLongBits(lowerBound) != Double.doubleToLongBits(other.lowerBound))
            return false;
        if (lowerExclusive != other.lowerExclusive)
            return false;
        if (upperInfinite != other.upperInfinite)
            return false;
        if (Double.doubleToLongBits(upperBound) != Double.doubleToLongBits(other.upperBound))
            return false;
        if (upperExclusive != other.upperExclusive)
            return false;
        return true;
    }

    /**
     * Record the passed value as a part of the range, adjusting boundaries as needed.
     * 
     * @param value
     */
    @Override
    public void adapt(double value) {

        if (Double.isNaN(value)) {
            return;
        }
        if (Double.isInfinite(lowerBound) || lowerBound > value) {
            setLowerBound(value);
        }
        if (Double.isInfinite(upperBound) || upperBound < value) {
            setUpperBound(value);
        }
    }

    /**
     * Normalize the passed value to this range, which must include it.
     * 
     * @param value
     * @return the normalized value (0-1)
     */
    @Override
    public double normalize(double value) {

        if (!isBounded()) {
            return value;
        }
        return (value - lowerBound) / (upperBound - lowerBound);
    }

    @Override
    public double getWidth() {
        return isInfinite() ? Double.POSITIVE_INFINITY : (isBounded() ? upperBound - lowerBound : Double.NaN);
    }

    @Override
    public double getMidpoint() {
        return isBounded() ? (lowerBound + (upperBound - lowerBound) / 2) : Double.NaN;
    }

    @Override
    public boolean isCompatible(ValueMediator other) {
        return other instanceof NumericRangeImpl && isBounded() && ((NumericRangeImpl) other).isBounded();
    }

    @Override
    public Number convert(Number d, ValueMediator other) {

        if (!isBounded()) {
            throw new IllegalArgumentException(
                    "range " + this + " cannot convert value " + d + " to " + other + " because it is unbound");
        }
        if (!(other instanceof NumericRangeImpl || ((NumericRangeImpl) other).isBounded())) {
            throw new IllegalArgumentException("range " + this + " cannot convert value " + d + " to " + other
                    + " because the target is not a range or is unbound");
        }
        if (!((NumericRangeImpl) other).contains(d.doubleValue())) {
            throw new IllegalArgumentException(
                    "range " + other + " cannot convert value " + d + " to range " + this + " because it does not contain it");
        }

        return this.lowerBound + (this.getWidth() * ((NumericRangeImpl) other).normalize(d.doubleValue()));
    }

    /**
     * Create a range from a list of doubles
     * 
     * @param range
     * @return
     */
    public static NumericRangeImpl create(List<Double> range) {
        return new NumericRangeImpl(range.get(0), range.get(1), false, false);
    }

    public static NumericRangeImpl create(double start, double end, boolean rightOpen) {
        return new NumericRangeImpl(start, end, false, rightOpen);
    }

    public static NumericRangeImpl create(double start, double end) {
        return new NumericRangeImpl(start, end, false, false);
    }

    public static NumericRangeImpl create(String string) {
        return new NumericRangeImpl(string);
    }

    /**
     * This form admits Number, ITimeInstant and Date. Also admits nulls to mean infinite in the
     * corresponding direction.
     * 
     * @param from
     * @param to
     * @return
     */
    public static NumericRangeImpl create(Object from, Object to) {

        boolean leftInfinite = from == null;
        boolean rightInfinite = to == null;
        double a = Double.NaN;
        double b = Double.NaN;

        if (!leftInfinite) {
            if (from instanceof Number) {
                a = ((Number) from).doubleValue();
            } else if (from instanceof TimeInstant) {
                a = ((TimeInstant) from).getMilliseconds();
            } else if (from instanceof Date) {
                a = ((Date) from).getTime();
            } else {
                throw new IllegalArgumentException("Cannot make a range: left limit unrecognized: " + from);
            }
        }
        if (!rightInfinite) {
            if (to instanceof Number) {
                b = ((Number) to).doubleValue();
            } else if (to instanceof TimeInstant) {
                b = ((TimeInstant) to).getMilliseconds();
            } else if (to instanceof Date) {
                b = ((Date) to).getTime();
            } else {
                throw new IllegalArgumentException("Cannot make a range: right limit unrecognized: " + to);
            }
        }

        return new NumericRangeImpl(leftInfinite ? null : a, rightInfinite ? null : b, false, true);
    }

    @Override
    public NumericRange intersection(NumericRange other) {
        int lowerCmp = Double.compare(lowerBound, other.getLowerBound());
        int upperCmp = Double.compare(upperBound, other.getUpperBound());
        if (lowerCmp >= 0 && upperCmp <= 0) {
            return this;
        } else if (lowerCmp <= 0 && upperCmp >= 0) {
            return other;
        } else {
            double newLower = (lowerCmp >= 0) ? lowerBound : other.getLowerBound();
            double newUpper = (upperCmp <= 0) ? upperBound : other.getUpperBound();
            return create(newLower, newUpper);
        }
    }

    @Override
    public boolean overlaps(NumericRange other) {
        return this.lowerBound <= other.getUpperBound() && other.getLowerBound() <= this.upperBound;
    }

    /**
     * Returns the minimal range that {@linkplain #contains(NumericRange)}} both this range and {@code
     * other}. For example, the span of {@code [1..3]} and {@code (5..7)} is {@code [1..7)}.
     *
     * <p>
     * <i>If</i> the input ranges are {@linkplain #isConnected connected}, the returned range can
     * also be called their <i>union</i>. If they are not, note that the span might contain values
     * that are not contained in either input range.
     *
     * <p>
     * Like {@link #intersection(NumericRangeImpl) intersection}, this operation is commutative, associative
     * and idempotent. Unlike it, it is always well-defined for any two input ranges.
     */
    @Override
    public NumericRange span(NumericRange other) {
        int lowerCmp = Double.compare(lowerBound, other.getLowerBound());
        int upperCmp = Double.compare(upperBound, other.getUpperBound());
        if (lowerCmp <= 0 && upperCmp >= 0) {
            return this;
        } else if (lowerCmp >= 0 && upperCmp <= 0) {
            return other;
        } else {
            double newLower = (lowerCmp <= 0) ? lowerBound : other.getLowerBound();
            double newUpper = (upperCmp >= 0) ? upperBound : other.getUpperBound();
            return create(newLower, newUpper);
        }
    }

    /**
     * Return a range that contains as much as possible of the span of the second argument
     * constrained to the span of this, changing the values so that the boundaries may change with
     * the least possible error, and keeping the span as much as possible. The output may be
     * different from both but will never be outside this, or span larger than the argument.
     * 
     * @param constraint
     * @param other
     * @return
     */
    public NumericRangeImpl match(NumericRangeImpl other) {
        return null;
    }

    /**
     * Stretch one of the ends so that the passed value is the midpoint. If the midpoint isn't in
     * the range, return self.
     * 
     * @param midpoint
     * @return
     */
    @Override
    public NumericRange stretchForMidpoint(double midpoint) {

        if (!isBounded()) {
            throw new IllegalArgumentException("range " + this + " cannot be stretched for midpoint because it is unbound");
        }

        if (!contains(midpoint)) {
            return this;
        }

        double left = midpoint - getLowerBound();
        double right = getUpperBound() - midpoint;
        NumericRangeImpl ret = new NumericRangeImpl(this);
        if (Math.abs(left) > Math.abs(right)) {
            ret.upperBound = midpoint + Math.abs(left);
        } else if (Math.abs(right) > Math.abs(left)) {
            ret.lowerBound = midpoint - Math.abs(right);
        }

        return ret;
    }

    @Override
    public boolean contains(NumericRange other) {

        if (this.equals(other)) {
            return true;
        }

        if (!lowerInfinite && !other.isLeftInfinite()
                && (lowerExclusive ? lowerBound >= other.getLowerBound() : lowerBound > other.getLowerBound())) {
            return false;
        }
        if (!upperInfinite && !other.isRightInfinite()
                && (upperExclusive ? upperBound <= other.getUpperBound() : upperBound < other.getUpperBound())) {
            return false;
        }
        if (!upperInfinite && other.isRightInfinite()) {
            return false;
        }
        if (!lowerInfinite && other.isLeftInfinite()) {
            return false;
        }
        return true;
    }

    /**
     * Return a [0-1] double representing how much this interval excludes of the other. Will compute
     * the missing parts on each side, normalize to the extent of the range, and add them in the
     * output, dealing with infinity appropriately.
     * 
     * @param other
     * @return
     */
    @Override
    public double exclusionOf(NumericRange other) {

        double leftExclusion = 0;
        if (lowerBound != Double.NEGATIVE_INFINITY && lowerBound > other.getLowerBound()) {
            leftExclusion = other.getLowerBound() - lowerBound;
        }
        double rightExclusion = 0;
        if (upperBound != Double.POSITIVE_INFINITY && upperBound < other.getUpperBound()) {
            rightExclusion = upperBound - other.getUpperBound();
        }

        double size = other.isBounded() ? other.getWidth() : (leftExclusion + rightExclusion);
        if (size == 0) {
            return 0;
        }

        return Math.abs(leftExclusion / size) + Math.abs(rightExclusion / size);

    }

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
    @Override
    public Pair<NumericRange, Pair<Double, Double>> snap(NumericRange original, long nCells) {

        if (!this.overlaps(original)) {
            return null;
        }

        double olower = original.getLowerBound() < getLowerBound() ? getLowerBound() : original.getLowerBound();
        double oupper = original.getUpperBound() > getUpperBound() ? getUpperBound() : original.getUpperBound();

        double cellWidth = getWidth() / nCells;
        double leftGap = olower - getLowerBound();
        double leftCells = (long) Math.floor(leftGap / cellWidth);
        double leftError = leftGap - (leftCells * cellWidth);
        double rightGap = getUpperBound() - oupper;
        double rightCells = (long) Math.floor(rightGap / cellWidth);
        double rightError = rightGap - (rightCells * cellWidth);

        return new PairImpl<>(
                create(this.getLowerBound() + (leftGap > 0 ? (leftCells * cellWidth) : 0),
                        this.getUpperBound() - (rightGap > 0 ? (rightCells * cellWidth) : 0)),
                new PairImpl<>(leftError, rightError));
    }

    @Override
    public boolean isWithin(double n) {
        boolean left = lowerExclusive ? n > lowerBound : n >= lowerBound;
        boolean right = upperExclusive ? n < upperBound : n <= upperBound;
        return left && right;
    }

    public static void main(String[] args) {

        NumericRangeImpl cock = create(-10, 0);
        Pair<NumericRange, Pair<Double, Double>> snapped = cock.snap(create(-8.7, -3.9), 10);

        System.out.println("FIXED RANGE: " + snapped.getFirst());
        System.out.println("ERRORS: " + snapped.getSecond());

        System.out.println("OVERLAP TRUE: " + cock.overlaps(create(1.7, 1.9)));
        System.out.println("OVERLAP FALSE: " + cock.overlaps(create(11, 19)));

        // System.out.println(Range.create("[1100000.0,7.148E7]").toString());
        // System.out.println(Range.create("[0,1]"));
        // System.out.println(Range.create("[12.33, 3222]"));
        // System.out.println(Range.create("[,1]"));
        // System.out.println(Range.create("[0,]"));
        // System.out.println(Range.create("[,]"));
    }

//    @Override
    public void include(double d) {

        if (lowerBound == Double.NEGATIVE_INFINITY || lowerBound > d) {
            lowerBound = d;
            lowerInfinite = false;
        }
        if (upperBound == Double.POSITIVE_INFINITY || upperBound < d) {
            upperBound = d;
            upperInfinite = false;
        }
    }

    /**
     * A reference point in the interval, i.e. the midpoint if bounded, any boundary point that is
     * not infinity if not, and NaN if infinite.
     * 
     * @return
     */
    @Override
    public double getFocalPoint() {

        return isBounded()
                ? getMidpoint()
                : lowerBound != Double.NEGATIVE_INFINITY
                        ? lowerBound
                        : (upperBound == Double.POSITIVE_INFINITY ? Double.NaN : upperBound);
    }

    public String getDisplayLabel() {
        if (!isBounded()) {
            if (lowerInfinite) {
                return "< " + upperBound;
            } else if (upperInfinite) {
                return "> " + lowerBound;
            }
        } else if (lowerBound == upperBound) {
            if (upperExclusive && lowerExclusive) {
                return "!= " + lowerBound;
            } else if (!upperExclusive && !lowerExclusive) {
                return "= " + lowerBound;
            }
        }
        return lowerBound + " - " + upperBound;
    }

    @Override
    public ValueMediator contextualize(Observable observable, Geometry scale) {
        return this;
    }

    public String getKimCode() {
        /*
         * for now k.IM does not allow much specification in observables
         */
        return getLowerBound() + " to " + getUpperBound();
    }

    @Override
    public Number convert(Number value, Locator locator) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isContextual() {
        return false;
    }

    @Override
    public boolean isInteger() {
        // TODO Auto-generated method stub
        return false;
    }

}
