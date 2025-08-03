package org.integratedmodelling.klab.services.resolver;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.collections.BidiMap;
import org.apache.commons.collections.bidimap.DualHashBidiMap;
import org.apache.commons.collections4.comparators.ComparatorChain;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Prioritizer;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.utils.Utils;

public class PrioritizerImpl implements Prioritizer<Model> {

  private final ContextScope scope;
  private final Scale scale;
  private final Map<Model, Map<Criterion, Double>> ranks = new HashMap<>();
  private final Map<Criterion, Integer> defaultCriteria;

  public PrioritizerImpl(
      ContextScope scope, Scale scale, Map<String, Integer> defaultRankingCriteria) {
    this.scope = scope;
    this.scale = scale;
    this.defaultCriteria =
        defaultRankingCriteria.entrySet().stream()
            .collect(
                Collectors.toMap(
                    entry -> Criterion.forProperty(entry.getKey()), Map.Entry::getValue));
  }

  @Override
  public int compare(Model o1, Model o2) {

    var ranks1 = computeCriteria(o1);
    var ranks2 = computeCriteria(o2);
    var comparator = new ComparatorChain<Map<Criterion, Double>>();
    /*
     * For the final comparison we use the order for the model we are comparing against.
     */
    orderCriteria(o1)
        .forEach(
            criterion ->
                comparator.addComparator(Comparator.comparing(r -> r.get(criterion)), true));
    return comparator.compare(ranks1, ranks2);
  }

  @Override
  public Map<Criterion, Double> computeCriteria(Model model) {

    if (this.ranks.containsKey(model)) {
      return this.ranks.get(model);
    }

    Map<Criterion, Double> ret = new HashMap<>();

    for (var criterion : orderCriteria(model)) {
      //        if (cr.contains(",")) {
      //          ret.put(cr, computeCustomAggregation(cr, (ModelReference) model, context));
      //        } else {
      ret.put(criterion, computeStandardCriterion(criterion, model));
    }
    ranks.put(model, ret);

    return ret;
  }

  private List<Criterion> orderCriteria(Model model) {

    var criteriaMap = new HashMap<>(defaultCriteria);

    // override any custom order
    criteriaMap.putAll(
        model.getResolutionInfo().getResolutionCriteria().entrySet().stream()
            .collect(
                Collectors.toMap(
                    entry -> Criterion.forProperty(entry.getKey()), Map.Entry::getValue)));

    // sort by priority excluding the 0s or less
    return criteriaMap.entrySet().stream()
        .filter(e -> e.getValue() > 0)
        .sorted(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .toList();
  }

  @Override
  public List<String> listCriteria() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Map<Criterion, Double> getRanking(Model ranked) {
    return this.ranks.get(ranked);
  }

  //  class FieldComparator implements Comparator<Map<String, Object>> {
  //
  //    String _field;
  //
  //    FieldComparator(String field) {
  //      _field = field;
  //    }

  //    @SuppressWarnings({ "rawtypes" })
  //    @Override
  //    public int compare(Map<String, Object> o1, Map<String, Object> o2) {
  //
  //      Comparable n1 = (Comparable) o1.get(_field);
  //      Comparable n2 = (Comparable) o2.get(_field);
  //
  //      /*
  //       * comparator is icky if it gets an int and a double.
  //       */
  //      // double d1 = 0;
  //      // double d2 = 0;
  //
  //      if (n1 instanceof Number) {
  //        n1 = ((Number) n1).doubleValue();
  //        // d1 = ((Double) n1).doubleValue();
  //      }
  //      if (n2 instanceof Number) {
  //        n2 = ((Number) n2).doubleValue();
  //        // d2 = ((Double) n2).doubleValue();
  //      }
  //
  //      // if (d1 != d2 && o1.containsKey(NS.SUBJECTIVE_CONCORDANCE) &&
  //      // o2.containsKey(NS.SUBJECTIVE_CONCORDANCE)) {
  //      //
  //      // /*
  //      // * if the better one has a lower subjective concordance, the better one must
  //      // be
  //      // * better by at least the percent difference in subjective concordances.
  //      // */
  //      // double s1 = (double) o1.get(NS.SUBJECTIVE_CONCORDANCE);
  //      // double s2 = (double) o2.get(NS.SUBJECTIVE_CONCORDANCE);
  //      // double sd = Math.abs(s1 - s2);
  //      //
  //      // if (d1 > d2) {
  //      // if (d1 - sd < d2) {
  //      // return 0;
  //      // }
  //      // } else {
  //      // if (d2 - sd < d1) {
  //      // return 0;
  //      // }
  //      // }
  //      //
  //      // }
  //
  //      // TODO restore the null acceptance after testing. Should not get nulls.
  //      return Objects.compare(n1, n2, this);
  //    }
  //  }
  //
  //  public Prioritizer(IResolutionScope context) {
  //    scope = (ResolutionScope) context;
  //  }
  //
  //  @Override
  //  public int compare(ModelReference o1, ModelReference o2) {
  //    return comparator.compare(getRanks(o1), getRanks(o2));
  //  }
  //
  //  public Map<String, Double> getRanks(ModelReference md) {
  //
  //    if (ranks.get(md) == null) {
  //      ranks.put((ModelReference) md, computeCriteria(md, scope));
  //    }
  //    return ranks.get(md);
  //  }
  //

  //
  //  /**
  //   * Compute the standard criterion identified by cr. For aggregated criteria, use
  //   *
  //   * @param cr
  //   * @param model
  //   * @param context
  //   * @return criterion value
  //   */
  public double computeStandardCriterion(Criterion criterion, Model model) {

    return switch (criterion) {
      case LEXICAL_SCOPE -> computeLexicalScope(model);
      //      case TRAIT_CONCORDANCE -> computeLexicalScope(model);
      //      case SEMANTIC_DISTANCE -> computeLexicalScope(model);
      //      case INHERENCY -> computeLexicalScope(model);
      //      case EVIDENCE -> computeLexicalScope(model);
      //      case NETWORK_REMOTENESS -> computeLexicalScope(model);
      //      case SUBJECTIVE_CONCORDANCE -> computeLexicalScope(model);
      //      case SCALE_COVERAGE -> computeLexicalScope(model);
      //      case SCALE_SPECIFICITY -> computeLexicalScope(model);
      //      case SCALE_COHERENCY -> computeLexicalScope(model);
      //      case SPACE_COVERAGE -> computeLexicalScope(model);
      //      case SPACE_SPECIFICITY -> computeLexicalScope(model);
      //      case SPACE_COHERENCY -> computeLexicalScope(model);
      //      case TIME_COVERAGE -> computeLexicalScope(model);
      //      case TIME_SPECIFICITY -> computeLexicalScope(model);
      //      case TIME_COHERENCY -> computeLexicalScope(model);
      //      case RELIABILITY -> computeLexicalScope(model);
      default -> 0;
    };
  }

  //
  //  private double computeTimeSpecificity(ModelReference model, ResolutionScope context) {
  //    return computeTemporalCriteria(model, context)[1];
  //  }
  //
  //  private double computeTimeCoverage(ModelReference model, ResolutionScope context) {
  //    return computeTemporalCriteria(model, context)[0];
  //  }
  //
  //  private double computeTimeCoherency(ModelReference model, ResolutionScope context) {
  //    return computeTemporalCriteria(model, context)[2];
  //  }
  //
  //  private double computeSpaceSpecificity(ModelReference model, ResolutionScope context) {
  //    return computeSpatialCriteria(model, context)[1];
  //  }
  //
  //  private double computeSpaceCoverage(ModelReference model, ResolutionScope context) {
  //    return computeSpatialCriteria(model, context)[0];
  //  }
  //
  //  private double computeSpaceCoherency(ModelReference model, ResolutionScope context) {
  //    return computeSpatialCriteria(model, context)[2];
  //  }
  //
  //
  //  /*
  //   * Compute a customized aggregation of two or more standard criteria.
  //   * Aggregation is equally weighted. These criteria have been given the same
  //   * order in the strategy specifications.
  //   */
  //  private double computeCustomAggregation(String def, ModelReference model, IResolutionScope
  // context) {
  //    String[] ddef = def.split(",");
  //    ArrayList<Pair<Integer, Integer>> vals = new ArrayList<>();
  //    Map<String, Double> dt = getRanks(model);
  //    for (String cr : ddef) {
  //      vals.add(new Pair<>(dt.containsKey(cr) ? ((Number) (dt.get(cr))).intValue() : 50, 100));
  //    }
  //    return aggregate(vals);
  //  }
  //
  /*
   * lexical scope -> locality wrt context
   *
   * 100 = in observation scenario 75 = in same namespace as context 50-26 closer
   * to same namespace as context 25 = in same project as context 0 = non-private
   * in other visible namespace
   *
   * TODO #3 should use a hierarchy criterion: something in namespace xxxx.yyyy is
   * satisfied preferentially by something in namespace xxxx unless criteria 1 or
   * 2 are satisfied.
   *
   * From the docs (still not implemented as such):
   *
   * 1. Scenario 2. Private in same namespace 3. Public in same namespace 4.
   * Public in parent namespace (in order of distance) 5. Project private in same
   * project 6. Public local or cached 7. Public from the network
   */
  public double computeLexicalScope(Model model) {

    /*
     * Models without a namespace have been built for this resolution and win every match.
     */
    if (model.getNamespace() == null) {
      return 100;
    }

    Set<String> scenarios = new HashSet<>();
    scope.getResolutionConstraints().stream()
        .filter(r -> r.getType() == ResolutionConstraint.Type.Scenarios)
        .forEach(
            resolutionConstraint -> {
              scenarios.addAll(resolutionConstraint.payload(String.class));
            });

    /*
     * Scenarios always win.
     */
    if (scenarios.contains(model.getNamespace())) {
      return 100;
    }

    var resolutionNamespace =
        scope.getResolutionConstraints().stream()
            .filter(r -> r.getType() == ResolutionConstraint.Type.ResolutionNamespace)
            .findFirst()
            .flatMap(
                resolutionConstraint ->
                    resolutionConstraint.payload(String.class).stream().findFirst())
            .orElse(null);

    var resolutionProject =
        scope.getResolutionConstraints().stream()
            .filter(r -> r.getType() == ResolutionConstraint.Type.ResolutionProject)
            .findFirst()
            .flatMap(
                resolutionConstraint ->
                    resolutionConstraint.payload(String.class).stream().findFirst())
            .orElse(null);

    if (model.getNamespace().equals(resolutionNamespace)) {
      return 75;
    } else if (model.getProjectName().equals(resolutionProject)) {
      return 50;
    }

    //      IWorkspace wsc = (rns == null || rns.getProject() == null) ? null :
    //   rns.getProject().getWorkspace();
    //
    //      /*
    //       * between 25 and 50 is attributed to namespace being traceable in dependency
    //       * chain.
    //       */
    var ret = 0;
    //      int nsDistance = context.getNamespaceDistance(ns);
    //      if (nsDistance >= 0) {
    //        ret = 50 - (nsDistance > 24 ? 24 : nsDistance);
    //      }
    //
    //      /*
    //       * Workspace priority exists if the model comes from the local workspace or from
    //       * the same workspace as the resolution.
    //       */
    //      boolean workspaceHasPriority = ws != null
    //              && (ws.getName().equals(Resources.INSTANCE.getLocalWorkspace().getName())
    //              || (wsc != null && wsc.getName().equals(ws.getName())));
    //
    //      /*
    //       * between 1 and 25 is attributed to project being traceable.
    //       */
    //      int prDistance = context.getProjectDistance(ns == null ? null : ns.getProject());
    //      if (prDistance >= 0) {
    //        ret = 25 - (prDistance > 24 ? 24 : prDistance);
    //      }
    //
    //      /*
    //       * the criteria (now 0-50) above occupy 25 points on a partition decided by the
    //       * workspace criterion
    //       */
    //      if (!workspaceHasPriority) {
    //        ret = (int) ((double) ret / 2.0);
    //      }
    //
    return ret;
  }
  //
  //  /*
  //   * semantic distance. This makes sure that e.g. a matching abstract model is
  //   * chosen only after a concrete one is rejected.
  //   */
  //  public double computeSemanticDistance(ModelReference model, ResolutionScope context) {
  //
  //    /*
  //     * list of traits in common. Don't check the trait value - assumed the same
  //     * because of the search strategy.
  //     */
  //    try {
  //      IConcept provided = model.getObservableConcept();
  //      IConcept wanted = context.getObservable() == null ? null :
  // context.getObservable().getType();
  //      if (provided == null || wanted == null) {
  //        // TODO should not happen
  //        return 100;
  //      }
  //
  //      return getSemanticDistance(wanted, provided);
  //
  //    } catch (Exception e) {
  //    }
  //
  //    return 0;
  //  }
  //
  //  int getSemanticDistance(IConcept o1, IConcept o2) {
  //    // TODO
  //    if (o1.equals(o2)) {
  //      return 0;
  //    }
  //    return 50;
  //  }
  //
  //  /*
  //   * trait concordance wrt context n = # of traits shared / #. of traits possible,
  //   * normalized to 100 TODO REIMPLEMENT AS APPROPRIATE - a compatibility rank
  //   *
  //   * FIXME does nothing at the moment.
  //   */
  //  public double computeTraitConcordance(ModelReference model, IResolutionScope context) {
  //
  //    /*
  //     * list of traits in common. Don't check the trait value - assumed the same
  //     * because of the search strategy.
  //     */
  //    try {
  //      IConcept c = model.getObservableConcept(); // getWantedObservable(model,
  //      // context);
  //      if (c == null) {
  //        // TODO issues here - just a hack, should not happen
  //        return 0;
  //      }
  //      Collection<IConcept> attrs = Traits.INSTANCE.separateAttributes(c).getSecond();
  //      Collection<IConcept> wanted = new ArrayList<>(); // ((ResolutionScope)
  //      // context).getTraits();
  //      int common = 0;
  //
  //      if (attrs.size() == 0 && wanted.size() == 0)
  //        return 100.0;
  //
  //      for (IConcept zio : wanted) {
  //        if (attrs.contains(zio)) {
  //          common++;
  //        }
  //      }
  //      if (wanted.size() > 0) {
  //        return 100.0 * ((double) common / (double) wanted.size());
  //      }
  //
  //    } catch (Exception e) {
  //    }
  //
  //    return 0;
  //  }
  //
  //  /*
  //   * scale specificity -> total coverage of object wrt context (minimum of all
  //   * extents?) <n> = scale / (object coverage) * 100
  //   */
  //  public double computeScaleSpecificity(ModelReference model, ResolutionScope context) {
  //    return computeScaleCriteria(model, context)[1];
  //  }
  //
  //  /*
  //   * return the (possibly cached) array of coverage, specificity and resolution.
  //   */
  //  private double[] computeScaleCriteria(ModelReference model, ResolutionScope context) {
  //
  //    double specificityS = -1;
  //    double coverageS = -1;
  //    double resolutionS = -1;
  //    double specificityT = -1;
  //    double coverageT = -1;
  //    double resolutionT = -1;
  //
  //    if (!idxss.containsKey(model)) {
  //
  //      if (model.getShape() != null) {
  //        double[] sc = computeSpatialCriteria(model, context);
  //        coverageS = sc[0];
  //        specificityS = sc[1];
  //        resolutionS = sc[2];
  //      }
  //
  //      if (context.getCoverage().getTime() != null) {
  //        /*
  //         * TODO do the same with time and take the minimum - or should this be the
  //         * separate currency value ?
  //         */
  //        double[] tc = computeTemporalCriteria(model, context);
  //        coverageT = tc[0];
  //        specificityT = tc[1];
  //        resolutionT = tc[2];
  //      }
  //
  //      idxss.put(model, new double[] { getMin(coverageS, coverageT), getMin(specificityS,
  // specificityT),
  //              getMin(resolutionS, resolutionT) });
  //    }
  //
  //    return idxss.get(model);
  //
  //  }
  //
  //  @Override
  //  public String toString() {
  //    return asText();
  //  }
  //
  //  private double[] computeSpatialCriteria(ModelReference model, ResolutionScope context) {
  //    double[] ret = new double[] { -1, -1, -1 };
  //    if (model.getShape() != null) {
  //      /*
  //       * compute intersection if we're spatial
  //       */
  //      ISpace space = context.getCoverage().getSpace();
  //      if (space != null) {
  //        Geometry cspace = ((Shape) space.getShape()).getStandardizedGeometry();
  //        try {
  //          Geometry intersection = cspace.intersection(((Shape)
  // model.getShape()).getStandardizedGeometry());
  //          ret[1] = 100.0
  //                  * (intersection.getArea() / ((Shape)
  // model.getShape()).getStandardizedGeometry().getArea());
  //          ret[0] = 100.0 * (intersection.getArea() / cspace.getArea());
  //        } catch (Throwable t) {
  //          ret[1] = 10;
  //          context.getMonitor()
  //                  .warn("topology error in computing intersections: probable degenerate spatial
  // extent: "
  //                          + t.getMessage());
  //        }
  //      }
  //    }
  //    return ret;
  //  }
  //
  //  /**
  //   * Temporal criteria are 0: coverage; 1: specificity; 2: coherency. The latter
  //   * is not active at this time (will be -1).
  //   * <p>
  //   * Made static and public so that contextualizers can use it.
  //   *
  //   * @param modelStart
  //   * @param modelEnd
  //   * @param time
  //   * @return
  //   */
  //  public static double[] computeTemporalCriteria(long modelStart, long modelEnd, ITime time) {
  //
  //    double[] ret = new double[] { -1, -1, -1 };
  //
  //    if (time == null) {
  //      return ret;
  //    }
  //
  //    Range mrange = Range.create(modelStart == -1 ? null : modelStart, modelEnd == -1 ? null :
  // modelEnd);
  //    Range crange = Range.create(time.getStart(), time.getEnd());
  //
  //    /*
  //     * coverage: if non-grid, 100 for covered, 75 - [0-25] for partially covered, 50
  //     * - [0-25] distance if covered in infinite tail from or to a single-point
  //     * beginning or end. If grid, covered.
  //     */
  //    // if (time.size() > 1) {
  //    //
  //    // } else {
  //
  //    boolean compare = true;
  //    if (!mrange.contains(crange)) {
  //
  //      ret[0] = 25;
  //      if (mrange.isBounded() && crange.isBounded()) {
  //
  //        // this subtracts from 25 if the model is in the future, but that shouldn't even
  //        // get
  //        // here.
  //        double gap = crange.getLowerBound() - mrange.getUpperBound();
  //        double error = (crange.getWidth() - gap) / crange.getWidth();
  //        if (error > 1) {
  //          error = 1;
  //        }
  //        ret[0] += error * 25.0;
  //
  //      }
  //
  //      compare = false;
  //
  //    } else {
  //
  //      double d = mrange.exclusionOf(crange);
  //
  //      if (d == 1) {
  //        ret[0] = 1; // very least but we don't reject
  //      } else if (d == 0) {
  //        ret[0] = 100;
  //      } else if (mrange.isBounded()) {
  //        ret[0] = 75 - (d * 25);
  //      } else {
  //        ret[0] = 50 - (d * 49);
  //      }
  //
  //    }
  //
  //    /*
  //     * specificity differs by resolution type (even if generic) and is corrected by
  //     * the order of magnitude of the nearest multiplier. If the context is
  //     * universal, return 50.
  //     */
  //
  ////		System.out.println("MRANGE is " + new TimeInstant((long) mrange.getLowerBound()) + " to "
  ////				+ new TimeInstant((long) mrange.getUpperBound()));
  ////		System.out.println("CRANGE is " + new TimeInstant((long) crange.getLowerBound()) + " to "
  ////				+ new TimeInstant((long) crange.getUpperBound()));
  //
  //    if (mrange.contains(crange)) {
  //
  //      /*
  //       * TODO must compare model time resolution! We don't even have the info right
  //       * now.
  //       */
  //      ret[1] = 100;
  //
  //    } else if (compare && crange.isBounded()) {
  //
  //      double focalPointModel = mrange.isLeftBounded() ? mrange.getLowerBound() :
  // mrange.getFocalPoint();
  //      double focalPointContext = crange.getLowerBound();
  //
  //      if (time.size() > 1) {
  //
  //        if (Double.isNaN(focalPointModel)) {
  //          ret[1] = 0;
  //        } else {
  //          ret[1] = 100 * (mrange.getWidth() / crange.getWidth());
  //          if (ret[1] > 100) {
  //            ret[1] = 100;
  //          }
  //        }
  //
  //      } else if (Double.isNaN(focalPointModel)) {
  //        ret[1] = 25;
  //      } else {
  //
  //        double distanceFactor = Math.abs(focalPointModel - focalPointContext);
  //        distanceFactor /= (time.getResolution().getType().getMilliseconds()
  //                * time.getResolution().getMultiplier());
  //
  //        if (distanceFactor > 50) {
  //          distanceFactor = 50;
  //        }
  //
  //        ret[1] = 100 - 50 * (distanceFactor / 50);
  //      }
  //
  //    } else {
  //      ret[1] = 50;
  //    }
  //
  //    /*
  //     * resolution: if non-grid, 100 for identical, 75 - ([0-25] distance factor) for
  //     * overlapping. Else it's like space. Zero if grid goes with non-grid or the
  //     * other way around.
  //     */
  //
  //    return ret;
  //  }
  //
  //  private double[] computeTemporalCriteria(ModelReference model, ResolutionScope scope) {
  //    return computeTemporalCriteria(model.getTimeStart(), model.getTimeEnd(),
  // scope.getCoverage().getTime());
  //  }
  //
  //  private double getMin(double a, double b) {
  //    if (a < 0 && b < 0)
  //      return 0.0;
  //    if (a < 0)
  //      return b;
  //    if (b < 0)
  //      return a;
  //
  //    return Math.min(a, b);
  //  }
  //
  //  /*
  //   * network remoteness -> whether coming from remote KBox (added by kbox
  //   * implementation) 100 -> local 0 -> remote
  //   */
  //  public static double computeNetworkRemoteness(ModelReference model, IResolutionScope context)
  // {
  //    return model.getServerId() == null ? 100 : 0;
  //  }
  //
  //  /*
  //   * inherency -> level wrt observable: 100 = same thing-ness, specific inherency
  //   * (model and context are inherent to same thing) 66 = same thing-ness,
  //   * non-specific inherency 33 = different thing-ness, mediatable inherency
  //   *
  //   * TODO level of inherency at highest level should be modulated by semantic
  //   * distance between object and context, with 100 reserved for inherent to
  //   * exactly same object type.
  //   *
  //   */
  //  public double computeInherency(ModelReference model, IResolutionScope context) {
  //    return 0.0;
  //  }
  //
  //  /*
  //   * scale coherency -> coherency of domains adopted by context vs. the object n =
  //   * # of domains shared (based on the isSpatial/isTemporal fields) normalize to
  //   * 100 TODO reimplement this with the geometry
  //   */
  //  public double computeScaleCoherency(ModelReference model, IResolutionScope context) {
  //    // TODO Auto-generated method stub
  //    return 0.0;
  //  }
  //
  //  /*
  //   * scale coverage -> of scale in context (minimum of all extents? or one per
  //   * extent?) 0 = not scale-specific (outside scale will not be returned) (1, 100]
  //   * = (scale ^ object context) / scale
  //   */
  //  public double computeScaleCoverage(ModelReference model, ResolutionScope context) {
  //    return computeScaleCriteria(model, context)[0];
  //  }
  //
  //  /*
  //   * subjective concordance = multi-criteria ranking of user-defined metadata wrt
  //   * default or namespace priorities
  //   *
  //   * @returns chosen concordance metric normalized to 100
  //   */
  //  public double computeSubjectiveConcordance(ModelReference model, IResolutionScope context,
  //                                             List<String> subjectiveCriteria) {
  //
  //    if (context.getResolutionNamespace() == null) {
  //      // happens in non-semantic queries where the context is a data resource
  //      return 0;
  //    }
  //
  //    ArrayList<Pair<Integer, Integer>> vals = new ArrayList<>();
  //    IMetadata nm = context.getResolutionNamespace().getResolutionCriteria();
  //
  //    for (String s : subjectiveCriteria) {
  //
  //      int val = extractSubjectiveCriterion(s, model, 50);
  //
  //      int wei = 100;
  //      if (nm != null && nm.get(s) != null) {
  //        wei = context.getResolutionNamespace().getResolutionCriteria().get(s, Integer.class);
  //      } else if (defaultStrategy.get(s) != null) {
  //        wei = defaultStrategy.get(s, Integer.class);
  //      }
  //      vals.add(new Pair<>(val, wei));
  //    }
  //    return aggregate(vals) * 100;
  //  }
  //
  //  private int extractSubjectiveCriterion(String s, ModelReference model, int defaultValue) {
  //
  //    if (model.getMetadata() != null && model.getMetadata().containsKey(s)) {
  //      return Integer.parseInt(model.getMetadata().get(s));
  //    } else if (model.getName() != null /* happens with generated models */) {
  //      IKimObject m = Resources.INSTANCE.getModelObject(model.getName());
  //      if (m != null) {
  //        for (IAnnotation annotation : m.getAnnotations()) {
  //          if (s.endsWith(":" + annotation.getName()) && annotation.get("value") instanceof
  // Number) {
  //            return ((Number) annotation.get("value")).intValue();
  //          }
  //        }
  //      }
  //    }
  //
  //    return defaultValue;
  //  }
  //
  //  /*
  //   * evidence -> resolved/unresolved 100 = resolved from data or object source 75
  //   * = resolved but requires dereification 50 = computed, no dependencies 0 =
  //   * unresolved
  //   */
  //  public double computeEvidence(ModelReference model, IResolutionScope context) {
  //
  //    if ((model.isHasDirectData() || model.isHasDirectObjects()) &&
  // model.getDereifyingAttribute() == null) {
  //      return 100.0;
  //    }
  //    if ((model.isHasDirectData() || model.isHasDirectObjects()) &&
  // model.getDereifyingAttribute() != null) {
  //      return 75.0;
  //    }
  //    if (model.isResolved()) {
  //      return 50.0;
  //    }
  //    return 0.0;
  //  }
  //
  //  /**
  //   * Implement the weighted multiplicative aggregation for subjective criteria
  //   * expressed numerically with a 0-100 value. Each pair <v,w> of value and weight
  //   * is interpreted as a "benefit" criterion that contributes to the overall score
  //   * in a proportion that depends on its weight - the higher the weight, the
  //   * higher the contribution. All scores are multiplied when still in the [0 1]
  //   * range. The combination of low values is guaranteed to make the value lower.
  //   *
  //   * It is meant for "benefit" criteria that correlate directly to value, and the
  //   * weight of each criterion defines the amount of change it can produce in the
  //   * final index - higher weight, higher importance (the criterion value only
  //   * changes the (100 - w)/100 proportion of the [0 1] interval). For the index to
  //   * be meaningful, only indices that refer to exactly the same criteria can be
  //   * compared - the safe bet for missing values is to make them "average" by
  //   * giving them value 50.
  //   *
  //   * @param values collection of pairs <value, weight> for each criterion. Both
  //   *               value and weight must be integers in the 0-100 range.
  //   * @return aggregated criterion value
  //   */
  //  public double aggregate(Collection<Pair<Integer, Integer>> values) {
  //
  //    double ret = Double.NaN;
  //
  //    for (Pair<Integer, Integer> vp : values) {
  //
  //      double intProportion = 100.0 * ((double) vp.getSecond() / 100.0);
  //      double base = 100.0 - (double) vp.getSecond();
  //      double c = (base + intProportion * ((double) vp.getFirst() / 100.0)) / 100.0;
  //      ret = Double.isNaN(ret) ? c : ret * c;
  //    }
  //
  //    return ret;
  //  }
  //
  //  @Override
  //  public List<String> listCriteria() {
  //    return orderedCriteria;
  //  }
  //
  //  public Map<String, Double> getCriteria() {
  //    Map<String, Double> ret = new HashMap<>();
  //    IMetadata strategy = scope.getResolutionNamespace() == null ? null
  //            : scope.getResolutionNamespace().getResolutionCriteria();
  //    if (strategy == null) {
  //      strategy = getDefaultRankingStrategy();
  //    }
  //    for (String s : orderedCriteria) {
  //      ret.put(s, (strategy.get(s) == null ? 50.0 : ((Number) strategy.get(s)).doubleValue()));
  //    }
  //    for (String s : subjectiveCriteria) {
  //      ret.put(s, (strategy.get(s) == null ? 50.0 : ((Number) strategy.get(s)).doubleValue()));
  //    }
  //    return ret;
  //  }
  //
  //  public String asText() {
  //    String ret = "";
  //    IMetadata strategy = scope.getResolutionNamespace() == null ? null
  //            : scope.getResolutionNamespace().getResolutionCriteria();
  //    if (strategy == null) {
  //      strategy = getDefaultRankingStrategy();
  //    }
  //    for (String s : orderedCriteria) {
  //      if (!ret.isEmpty()) {
  //        ret += ",";
  //      }
  //      ret += s + "=" + ((strategy.get(s) == null ? 50 : strategy.get(s)));
  //    }
  //    for (String s : subjectiveCriteria) {
  //      if (!ret.isEmpty()) {
  //        ret += ",";
  //      }
  //      ret += s + "=" + ((strategy.get(s) == null ? 50 : strategy.get(s)));
  //    }
  //    return ret;
  //  }
  //
  //  /*
  //   * call to register ranks that were computed outside this object. Used for model
  //   * data coming from the remote search service.
  //   */
  //  public void registerRanks(ModelReference md) {
  //    ranks.put(md, md.getRanks());
  //  }
}
