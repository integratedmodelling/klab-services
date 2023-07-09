package org.integratedmodelling.klab.runtime.scale;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent.Constraint;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.utils.Utils;

public class CoverageImpl extends ScaleImpl implements Coverage {

	private static final long serialVersionUID = 7952811602320618118L;

	/*
	 * Default - do not accept a state model unless its coverage is greater than
	 * this. Instantiator models make this 0.
	 * 
	 * TODO make this configurable
	 */
	private static double MIN_MODEL_COVERAGE = 0.01;

	/*
	 * Default - we accept models if they cover at least an additional 20% of the
	 * whole context TODO make this configurable
	 */
	private static double MIN_TOTAL_COVERAGE = 0.20;

	/*
	 * Default - we stop adding models when we cover at least 95% of the whole
	 * context. TODO make this configurable
	 */
	private static double MIN_REQUIRED_COVERAGE = 0.95;

	// make local copies that may be modified and are inherited by children
	private double minModelCoverage = MIN_MODEL_COVERAGE;
	private double minTotalCoverage = MIN_TOTAL_COVERAGE;
	private double minRequiredCoverage = MIN_REQUIRED_COVERAGE;

	List<Pair<Extent<?>, Double>> coverages = new ArrayList<>();
	private double coverage;
	private double gain = 0;

	/*
	 * Keep all the (collapsed) merge history in subextents in their current
	 * situation. At each merge, all the extents are combined again, any resulting
	 * empty extents eliminated.
	 */
	private Map<Dimension.Type, List<Pair<LogicalConnector, Extent<?>>>> merged = new HashMap<>();

	/*
	 * constraints specified for this coverage, if any.
	 */
	private List<Constraint> constraints = new ArrayList<>();
	
	/**
	 * Create a coverage with full coverage, which can be reduced by successive AND
	 * merges.
	 * 
	 * @param original
	 * @return a full coverage for the passed scale.
	 */
	public static Coverage full(Scale original) {
		return new CoverageImpl(original, 1.0);
	}

	protected void setTo(CoverageImpl other) {
		extents = Arrays.copyOf(other.extents, other.extents.length);
		sort();
		coverages.clear();
		for (Pair<Extent<?>, Double> pair : other.coverages) {
			coverages.add(Pair.of(pair.getFirst(), pair.getSecond()));
		}
		coverage = other.coverage;
	}

	/**
	 * Create a coverage with full coverage, which can be increased by successive OR
	 * merges.
	 * 
	 * @param original
	 * @return a new empty coverage of this scale
	 */
	public static Coverage empty(Scale original) {
		return new CoverageImpl(original, 0.0);
	}

	/**
	 * Use this when we need the IScale semantics on our same extents.
	 * 
	 * @return
	 */
	public Scale asScale() {
		return new ScaleImpl(Arrays.asList(extents));
	}

	protected CoverageImpl(Scale original, double initialCoverage) {
		super(original.getExtents().stream().map(e -> e.collapsed()).collect(Collectors.toList()));
		this.coverage = initialCoverage;
		for (Extent<?> extent : extents) {
			coverages.add(Pair.of(initialCoverage > 0 ? extent.collapsed() : null, initialCoverage));
		}
	}

	private CoverageImpl(CoverageImpl original, List<Pair<Extent<?>, Double>> newcoverages, double gain,
			boolean adopt) {
		super(original.getExtents());
		this.coverage = Double.NaN;
		this.gain = gain;
		List<Extent<?>> adopted = new ArrayList<>();
		for (Pair<Extent<?>, Double> cov : newcoverages) {
			coverages.add(Pair.of(cov.getFirst(), cov.getSecond()));
			this.coverage = Double.isNaN(this.coverage) ? cov.getSecond() : (this.coverage * cov.getSecond());
			if (adopt) {
				if (cov.getFirst() != null) {
					adopted.add(cov.getFirst());
				} else if (this.extent(cov.getFirst().getType()) != null) {
					adopted.add(this.extent(cov.getFirst().getType()));
				}
				this.adoptExtents(adopted);
			}
		}
		if (Double.isNaN(this.coverage)) {
			this.coverage = 0;
		}
		assert (this.coverage >= 0 && this.coverage <= 1);
	}

	public CoverageImpl(CoverageImpl other) {
		this(other, other.coverages, other.gain, false);
	}

	public void setCoverage(double c) {

		if (!(c == 0 || c == 1)) {
			throw new IllegalArgumentException("a coverage can only be explicitly set to 0 or 1");
		}
		this.coverage = c;
		List<Pair<Extent<?>, Double>> newCoverage = new ArrayList<>();
		for (int i = 0; i < coverages.size(); i++) {
			newCoverage.add(Pair.of(c == 0 ? null : extents[i], c));
		}
		this.coverages.clear();
		this.coverages.addAll(newCoverage);
	}

	@Override
	public boolean isEmpty() {
		return coverage == 0;
	}

	@Override
	public double getCoverage() {
		return coverage;
	}

	@Override
	public double getCoverage(Dimension.Type dimension) {
		for (Pair<Extent<?>, Double> cov : coverages) {
			if (cov.getFirst().getType() == dimension) {
				return cov.getSecond();
			}
		}
		throw new IllegalArgumentException("this coverage does not contain the dimension " + dimension);
	}

	@Override
	public Coverage merge(Scale other, LogicalConnector how) {

		// no need for suffering if either is 0 and we're intersecting
		if (how == LogicalConnector.INTERSECTION
				&& ((other instanceof Coverage && Utils.Numbers.equal(((Coverage) other).getCoverage(), 0))
						|| Utils.Numbers.equal(this.getCoverage(), 0))) {
			return empty(this.asScale());
		}

		Scale coverage = (Scale) other;
		List<Pair<Extent<?>, Double>> newcoverages = new ArrayList<>();

		// flag gain for extents to recompute it; save previous and put it back after
		double pgain = this.gain;
		this.gain = Double.NaN;
		for (int i = 0; i < coverage.getExtentCount(); i++) {

			Dimension.Type type = coverage.getExtents().get(i).getType();

			Extent<?> ex = this.extent(type);
			if (ex == null) {
				newcoverages.add(Pair.of(getCurrentExtent(coverage, type), 1.0));
			} else {
				// FIXME must use the MERGED extent - which are not kept. The extents array
				// contains the full area to cover.
				newcoverages.add(
						mergeExtent(coverage.getExtents().get(i).getType(), getCurrentExtent(coverage, type), how));
			}
		}

		double gain = this.gain;
		this.gain = pgain;

		// if nothing happened, reset gain to 0
		if (Double.isNaN(gain)) {
			gain = 0;
		}

		return new CoverageImpl(this, newcoverages, gain, true);
	}

//    @Override
	public Coverage mergeExtents(Coverage other, LogicalConnector how) {

		if (!(other instanceof Scale)) {
			throw new IllegalArgumentException("a coverage can only merge another scale");
		}

		// no need for suffering if either is 0 and we're intersecting
		if (how == LogicalConnector.INTERSECTION
				&& ((other instanceof Coverage && Utils.Numbers.equal(((Coverage) other).getCoverage(), 0))
						|| Utils.Numbers.equal(this.getCoverage(), 0))) {
			return empty(this.asScale());
		}

		Scale coverage = (Scale) other;
		List<Pair<Extent<?>, Double>> newcoverages = new ArrayList<>();

		// flag gain for extents to recompute it; save previous and put it back after
		double pgain = this.gain;
		this.gain = Double.NaN;
		for (int i = 0; i < coverage.getExtentCount(); i++) {

			Dimension.Type type = coverage.getExtents().get(i).getType();

			Extent<?> ex = this.extent(type);
			if (ex == null) {
				newcoverages.add(Pair.of(getCurrentExtent(coverage, type), 1.0));
			} else {
				// FIXME must use the MERGED extent - which are not kept. The extents array
				// contains the full area to cover.
				newcoverages.add(
						mergeExtent(coverage.getExtents().get(i).getType(), getCurrentExtent(coverage, type), how));
			}
		}

		double gain = this.gain;
		this.gain = pgain;

		// if nothing happened, reset gain to 0
		if (Double.isNaN(gain)) {
			gain = 0;
		}

		return new CoverageImpl(this, newcoverages, gain, false);
	}

	/*
	 * Get the currently merged extent in the passed coverage
	 */
	private static Extent<?> getCurrentExtent(Scale coverage, Dimension.Type type) {
		if (coverage instanceof CoverageImpl) {
			for (Pair<Extent<?>, Double> cov : ((CoverageImpl) coverage).coverages) {
				if (cov.getFirst() != null && cov.getFirst().getType() == type) {
					return cov.getFirst().collapsed();
				}
			}
		}
		return coverage.extent(type).collapsed();
	}

	@Override
	public double getGain() {
		return gain;
	}

	/**
	 * Merging logics - not the simplest, so summarized here:
	 * <p>
	 * 
	 * <pre>
	 * Given
	 * 
	 *    orig  = the original extent (extents.get(i))
	 *    other = the passed extent of same type
	 *    curr  = the current extent at coverages.get(i).getFirst() (possibly null)
	 *    
	 * if UNION:
	 *    set X to orig.equals(other) ? other : (orig INTERSECTION other);
	 *    determine benefit of swapping curr with X:
	 *       if   (curr == null)
	 *         ok = X.extent > relevant
	 *       else (
	 *        set U = X UNION curr
	 *        ok = (U.extent - curr.extent) > relevant
	 *        
	 *    if (ok)
	 *        set prev to curr == null ? 0 : coverages.get(i).second
	 *        set curr to curr == null ? X else (X UNION curr)
	 *        set gain to curr.extent - prev
	 *        set coverage to curr.extent
	 * 
	 * if INTERSECTION:
	 *    if (curr == null) return previous;
	 *    else 
	 *        set prev to curr == null ? 0 : coverages.get(i).second
	 *        set curr to curr INTERSECTION other
	 *        set gain to prev - curr.extent (negative)
	 *        set coverage to curr.extent
	 * </pre>
	 * 
	 * Assumes to get and operate only on already collapsed extents.
	 * 
	 * @param i
	 * @param other
	 * @param how
	 * @return
	 */
	private Pair<Extent<?>, Double> mergeExtent(Dimension.Type type, Extent<?> other, LogicalConnector how) {

		Extent<?> orig = extent(type);

		if (orig instanceof Time && ((Time) orig).is(Time.Type.INITIALIZATION)) {
			return Pair.of(orig, 1.0);
		}

		Pair<Extent<?>, Double> coverag = null;
		int i = 0;
		for (Extent<?> oc : extents) {
			if (oc.getType() == type) {
				coverag = coverages.get(i);
				break;
			}
			i++;
		}

		Extent<?> current = coverag.getFirst();
		double ccover = coverag.getSecond();
		double newcover = 0;
		double gain = 0;
		double previouscoverage = current == null ? 0 : ccover;

		if (how == LogicalConnector.UNION) {

			double origcover = orig.getDimensionSize();

			// guarantee that we don't union with anything larger. Use outer extent.
			Extent<?> x = orig.equals(other) ? other : orig.merge(other, LogicalConnector.INTERSECTION);

			Extent<?> union = null;
			if (current == null) {
				newcover = x.getDimensionSize();
			} else {
				union = x.equals(current) ? x : x.merge(current, LogicalConnector.UNION);
				newcover = union.getDimensionSize();
			}

			// happens with non-dimensional extents
			if (!x.isEmpty() && newcover == 0 && origcover == 0) {
				newcover = origcover = 1;
			}

			boolean proceed = ((newcover / origcover) - ccover) > minModelCoverage;
			if (proceed) {
				gain = (newcover / origcover) - previouscoverage;
				this.gain = Double.isNaN(this.gain) ? gain : this.gain * gain;
				return Pair.of(newcover == 0 ? null : (current == null ? x : union), newcover / origcover);
			}

		} else if (how == LogicalConnector.INTERSECTION) {

			// if intersecting nothing with X, leave it at nothing
			if (current != null) {
				double origcover = orig.getDimensionSize();
				Extent<?> x = current.merge(other, LogicalConnector.INTERSECTION);
				newcover = x.getDimensionSize();

				// happens with non-dimensional extents
				if (!x.isEmpty() && newcover == 0 && origcover == 0) {
					newcover = origcover = 1;
				}

				gain = (newcover / origcover) - previouscoverage;
				this.gain = Double.isNaN(this.gain) ? gain : this.gain * gain;
				return Pair.of(newcover == 0 ? null : x, newcover / origcover);
			}

		} else {
			// throw new IllegalArgumentException("cannot merge a coverage with another
			// using operation: " + how);
		}

		// return the original, let gain untouched
		return Pair.of(coverag.getFirst(), coverag.getSecond());
	}

	@Override
	public boolean isComplete() {
		return coverage >= minRequiredCoverage;
	}

	@Override
	public boolean isRelevant() {
		return coverage > minTotalCoverage;
	}

	public void setMinimumModelCoverage(double d) {
		this.minModelCoverage = d;
	}

	public void setMinimumTotalCoverage(double d) {
		this.minTotalCoverage = d;
	}

	public void setSufficientTotalCoverage(double d) {
		this.minRequiredCoverage = d;
	}

	@Override
	public boolean checkConstraints(Scale geometry) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Collection<Constraint> getConstraints() {
		// TODO Auto-generated method stub
		return this.constraints;
	}

}
