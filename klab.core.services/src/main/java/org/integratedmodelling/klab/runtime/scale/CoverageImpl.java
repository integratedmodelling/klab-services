package org.integratedmodelling.klab.runtime.scale;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent.Constraint;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.api.utils.Utils.Numbers;

public class CoverageImpl extends ScaleImpl implements Coverage {

  private static final long serialVersionUID = 7952811602320618118L;

  /*
   * Default - do not accept a state model unless its coverage is greater than this. Instantiator
   * models make this 0.
   *
   * TODO make this configurable
   */
  private static double MIN_MODEL_COVERAGE = 0.01;

  /*
   * Default - we accept models if they cover at least an additional 20% of the whole context TODO
   * make this configurable
   */
  private static double MIN_TOTAL_COVERAGE = 0.20;

  /*
   * Default - we stop adding models when we cover at least 95% of the whole context. TODO make
   * this configurable
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
   * Keep all the (collapsed) merge history in subextents in their current situation. At each
   * merge, all the extents are combined again, any resulting empty extents eliminated.
   */
  private Map<Dimension.Type, List<Pair<LogicalConnector, Extent<?>>>> merged = new HashMap<>();

  /*
   * constraints specified for this coverage, if any.
   */
  private List<Constraint> constraints = new ArrayList<>();

  /**
   * Create a coverage with full coverage, which can be reduced by successive AND merges.
   *
   * @param original
   * @return a full coverage for the passed scale.
   */
  public static Coverage full(Scale original) {
    return new CoverageImpl(original, 1.0);
  }

  protected void setTo(CoverageImpl other) {
    define(Arrays.asList(other.extents));
    coverages.clear();
    for (Pair<Extent<?>, Double> pair : other.coverages) {
      coverages.add(Pair.of(pair.getFirst(), pair.getSecond()));
    }
    coverage = other.coverage;
  }

  /**
   * Create a coverage with full coverage, which can be increased by successive OR merges.
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

  public CoverageImpl(Scale original, double initialCoverage) {
    super(original.getExtents().stream().map(e -> e.collapsed()).collect(Collectors.toList()));
    this.coverage = initialCoverage;
    if (original.isUniversal()) {
      this.coverage = 1;
      this.extents = new Extent[0];
      super.setUniversal(true);
    } else if (original.isEmpty()) {
      this.coverage = 0;
      this.extents = new Extent[0];
    } else {
      for (Extent<?> extent : extents) {
        coverages.add(Pair.of(initialCoverage > 0 ? extent.collapsed() : null, initialCoverage));
      }
    }
  }

  public CoverageImpl(Scale original, double initialCoverage, double gain) {
    this(original, initialCoverage);
    this.gain = gain;
  }

  private CoverageImpl(
      CoverageImpl original,
      List<Pair<Extent<?>, Double>> newcoverages,
      double gain,
      boolean adopt) {
    super(original.getExtents());
    this.coverage = Double.NaN;
    this.gain = gain;
    List<Extent<?>> adopted = new ArrayList<>();
    for (Pair<Extent<?>, Double> cov : newcoverages) {
      double dimensionalCoverage = clampCoverage(cov.getSecond());
      coverages.add(Pair.of(cov.getFirst(), dimensionalCoverage));
      this.coverage =
          Double.isNaN(this.coverage)
              ? dimensionalCoverage
              : (this.coverage * dimensionalCoverage);
      if (adopt) {
        if (cov.getFirst() != null) {
          adopted.add(cov.getFirst());
        }
        this.adoptExtents(adopted);
      }
    }
    if (Double.isNaN(this.coverage)) {
      this.coverage = 0;
    }
    this.coverage = clampCoverage(this.coverage);
    assert (this.coverage >= 0 && this.coverage <= 1);
  }

  public CoverageImpl(CoverageImpl other) {
    this(other, other.coverages, other.gain, false);
  }

  public CoverageImpl withGain(double gain) {
    CoverageImpl ret = new CoverageImpl(this);
    ret.gain = gain;
    return ret;
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
    return Numbers.equal(coverage, 0);
  }

  @Override
  public double getCoverage() {
    return coverage;
  }

  @Override
  public double getCoverage(Dimension.Type dimension) {
    for (int i = 0; i < coverages.size(); i++) {
      if (coverageEntryType(this, i) == dimension) {
        return coverages.get(i).getSecond();
      }
    }
    throw new IllegalArgumentException("this coverage does not contain the dimension " + dimension);
  }

  @Override
  public Coverage merge(Geometry other, LogicalConnector how) {

    if (other == null) {
      return nullMerge(how);
    }
    var scale = GeometryRepository.INSTANCE.scale(other);
    if (scale == null) {
      return nullMerge(how);
    }

    /*
     * trivial cases first
     */
    if (isUniversal()) {
      return other instanceof CoverageImpl
          ? ((CoverageImpl) other).withGain(1.0)
          : new CoverageImpl(scale, 1.0, 1.0);
    } else if (scale.isUniversal()) {
      return this.withGain(1.0);
    } else if (other instanceof Coverage && ((Coverage) other).isEmpty()) {
      return how == LogicalConnector.INTERSECTION
          ? ((CoverageImpl) empty(this.asScale())).withGain(isEmpty() ? 0 : -1)
          : withGain(1.0);
    } else if (isEmpty() && other instanceof CoverageImpl) {
      return how == LogicalConnector.INTERSECTION
          ? empty(scale)
          : ((CoverageImpl) other).withGain(1.0);
    }

    return mergeScale(scale, how, true);
  }

  // @Override
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
    return mergeScale(coverage, how, false);
  }

  private Coverage mergeScale(Scale coverage, LogicalConnector how, boolean adopt) {
    if (coverage == null) {
      return nullMerge(how);
    }
    List<Pair<Extent<?>, Double>> newcoverages = new ArrayList<>();

    // flag gain for extents to recompute it; save previous and put it back after
    double pgain = this.gain;
    this.gain = Double.NaN;
    for (Extent<?> extent : extents) {

      Dimension.Type type = extent.getType();
      Pair<Extent<?>, Double> currentCoverage = coverageFor(type);

      if (coverage.extent(type) == null) {
        newcoverages.add(currentCoverage);
        continue;
      }

      Extent<?> currentExtent = getCurrentExtent(coverage, type);
      if (currentExtent == null) {
        newcoverages.add(
            how == LogicalConnector.INTERSECTION ? Pair.of(null, 0.0) : currentCoverage);
      } else {
        newcoverages.add(mergeExtent(type, currentExtent, how));
      }
    }

    double gain = this.gain;
    this.gain = pgain;

    // if nothing happened, reset gain to 0
    if (Double.isNaN(gain)) {
      gain = 0;
    }

    return new CoverageImpl(this, newcoverages, gain, adopt);
  }

  private Coverage nullMerge(LogicalConnector how) {
    return how == LogicalConnector.INTERSECTION
        ? ((CoverageImpl) empty(this.asScale())).withGain(isEmpty() ? 0 : -1)
        : withGain(0);
  }

  /*
   * Get the currently merged extent in the passed coverage
   */
  private static Extent<?> getCurrentExtent(Scale coverage, Dimension.Type type) {
    if (coverage instanceof CoverageImpl coverageImpl) {
      for (int i = 0; i < coverageImpl.coverages.size(); i++) {
        if (coverageEntryType(coverageImpl, i) == type) {
          Extent<?> current = coverageImpl.coverages.get(i).getFirst();
          return current == null ? null : current.collapsed();
        }
      }
    }
    Extent<?> extent = coverage.extent(type);
    return extent == null ? null : extent.collapsed();
  }

  @Override
  public double getGain() {
    return gain;
  }

  /**
   * Merging logics - not the simplest, so summarized here:
   *
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
   * @param type
   * @param other
   * @param how
   * @return
   */
  private Pair<Extent<?>, Double> mergeExtent(
      Dimension.Type type, Extent<?> other, LogicalConnector how) {

    Extent<?> orig = extent(type);

    if (orig instanceof Time time && time.is(Time.Type.INITIALIZATION)) {
      return Pair.of(orig, 1.0);
    } else if (other instanceof Time time && time.isGeneric()) {
      return Pair.of(orig, 1.0);
    }

    Pair<Extent<?>, Double> coverag = coverageFor(type);

    Extent<?> current = coverag.getFirst();
    double ccover = coverag.getSecond();
    double previouscoverage = current == null ? 0 : ccover;

    if (how == LogicalConnector.UNION) {

      double origcover = dimensionSize(orig);

      // guarantee that we don't union with anything larger. Use outer extent.
      Extent<?> x =
          orig.equals(other)
              ? other
              : mergeExtentUsingRepository(orig, other, LogicalConnector.INTERSECTION);
      if (x == null || x.isEmpty()) {
        return Pair.of(current, ccover);
      }

      Extent<?> mergedExtent = x;
      double newcover;
      if (current == null) {
        newcover = dimensionSize(x);
      } else {
        mergedExtent =
            x.equals(current) ? x : mergeExtentUsingRepository(x, current, LogicalConnector.UNION);
        if (mergedExtent == null) {
          return Pair.of(current, ccover);
        }
        newcover = dimensionSize(mergedExtent);
      }

      double proportionalCoverage = coverageRatio(newcover, origcover);
      boolean proceed = (proportionalCoverage - ccover) > minModelCoverage;
      if (proceed) {
        double gain = proportionalCoverage - previouscoverage;
        this.gain = Double.isNaN(this.gain) ? gain : this.gain * gain;
        return Pair.of(proportionalCoverage == 0 ? null : mergedExtent, proportionalCoverage);
      }

    } else if (how == LogicalConnector.INTERSECTION) {

      // if intersecting nothing with X, leave it at nothing
      if (current != null) {
        double origcover = dimensionSize(orig);
        Extent<?> x = mergeExtentUsingRepository(current, other, LogicalConnector.INTERSECTION);
        double proportionalCoverage =
            x == null || x.isEmpty() ? 0 : coverageRatio(dimensionSize(x), origcover);

        double gain = proportionalCoverage - previouscoverage;
        this.gain = Double.isNaN(this.gain) ? gain : this.gain * gain;
        return Pair.of(proportionalCoverage == 0 ? null : x, proportionalCoverage);
      }

    } else {
      // throw new IllegalArgumentException("cannot merge a coverage with another
      // using operation: " + how);
    }

    // return the original, let gain untouched
    return Pair.of(coverag.getFirst(), coverag.getSecond());
  }

  private Pair<Extent<?>, Double> coverageFor(Dimension.Type type) {
    for (int i = 0; i < coverages.size(); i++) {
      if (coverageEntryType(this, i) == type) {
        return coverages.get(i);
      }
    }
    return Pair.of(null, 0.0);
  }

  private static Dimension.Type coverageEntryType(CoverageImpl coverage, int index) {
    Extent<?> current = coverage.coverages.get(index).getFirst();
    if (current != null) {
      return current.getType();
    }
    return index < coverage.extents.length && coverage.extents[index] != null
        ? coverage.extents[index].getType()
        : null;
  }

  private static Extent<?> mergeExtentUsingRepository(
      Extent<?> left, Extent<?> right, LogicalConnector how) {
    try {
      Scale merged =
          GeometryRepository.INSTANCE.getMerged(
              new ScaleImpl(List.of(left)), new ScaleImpl(List.of(right)), how, Scale.class);
      return merged == null ? null : getCurrentExtent(merged, left.getType());
    } catch (RuntimeException e) {
      Logging.INSTANCE.warn(
          "coverage "
              + how.name().toLowerCase(Locale.ROOT)
              + " failed through geometry cache; retrying direct extent merge: "
              + e.getMessage());
    }
    try {
      return left.merge(right, how);
    } catch (RuntimeException e) {
      Logging.INSTANCE.warn(
          "coverage "
              + how.name().toLowerCase(Locale.ROOT)
              + " failed during direct extent merge; leaving coverage conservative: "
              + e.getMessage());
      return null;
    }
  }

  private static double dimensionSize(Extent<?> extent) {
    if (extent == null || extent.isEmpty()) {
      return 0;
    }
    try {
      double size = extent.getDimensionSize();
      return Double.isNaN(size) || size < 0 ? 0 : size;
    } catch (RuntimeException e) {
      Logging.INSTANCE.warn("coverage dimension size failed: " + e.getMessage());
      return 0;
    }
  }

  private static double coverageRatio(double covered, double total) {
    if (covered <= 0 || Double.isNaN(covered)) {
      return 0;
    }
    if (total <= 0 || Double.isNaN(total)) {
      return 1;
    }
    if (Double.isInfinite(total)) {
      return Double.isInfinite(covered) ? 1 : 0;
    }
    if (Double.isInfinite(covered)) {
      return 1;
    }
    return clampCoverage(covered / total);
  }

  private static double clampCoverage(double coverage) {
    if (Double.isNaN(coverage)) {
      return 0;
    }
    return Math.max(0, Math.min(1, coverage));
  }

  @Override
  public List<Geometry> split(int suggestedSplits) {
    return as(Geometry.class).split(suggestedSplits).stream()
        .map(geometry -> (Geometry) new CoverageImpl(new ScaleImpl(geometry), this.coverage))
        .toList();
  }

  @Override
  public Geometry dimensionsOnly() {
    return as(Geometry.class).dimensionsOnly();
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

  @Override
  public boolean isUniversal() {
    return this.coverage == 1 && extents.length == 0;
  }
}
