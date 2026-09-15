package org.integratedmodelling.klab.services.resolver;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.services.UnitService;
import org.integratedmodelling.klab.api.services.resolver.Prioritizer;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;

/** One request's ranking session. Instances and cached model/scale inputs are not shared across requests. */
public class PrioritizerImpl implements Prioritizer<Model> {
  private static final Set<Criterion> SUPPORTED = Set.of(Criterion.LEXICAL_SCOPE,
      Criterion.SEMANTIC_DISTANCE, Criterion.SPACE_COVERAGE, Criterion.SPACE_SPECIFICITY,
      Criterion.TIME_COVERAGE, Criterion.TIME_SPECIFICITY);
  private final ContextScope scope;
  private final Scale scale;
  private final Observable observable;
  private final Concept contextObservable;
  private final Map<Criterion, Integer> defaults;
  private final List<Criterion> order;
  private final Map<Model, Integer> distances = new HashMap<>();
  private final Map<Model, Map<Criterion, Double>> ranks = new HashMap<>();
  private final Map<Model, double[]> spatial = new HashMap<>(), temporal = new HashMap<>();
  private final Set<String> scenarios = new HashSet<>();
  private final String namespace, project;

  public PrioritizerImpl(ContextScope scope, Scale scale, Map<String, Integer> criteria,
      Observable observable, Concept contextObservable) {
    this.scope = Objects.requireNonNull(scope);
    this.scale = scale;
    this.observable = Objects.requireNonNull(observable);
    this.contextObservable = contextObservable;
    this.defaults = parse(Objects.requireNonNull(criteria));
    String namespace = null, project = null;
    for (var constraint : scope.getResolutionConstraints()) {
      switch (constraint.getType()) {
        case Scenarios -> scenarios.addAll(constraint.payload(String.class));
        case ResolutionNamespace -> { var values = constraint.payload(String.class); if (namespace == null && !values.isEmpty()) namespace = values.getFirst(); }
        case ResolutionProject -> { var values = constraint.payload(String.class); if (project == null && !values.isEmpty()) project = values.getFirst(); }
        default -> {}
      }
    }
    this.namespace = namespace;
    this.project = project;
    // The lexical resolution namespace chooses the policy, not the candidate's namespace.
    if (namespace != null) {
      var resources = scope.getService(ResourcesService.class);
      var document = resources == null ? null : resources.retrieve(namespace, KimNamespace.class, scope);
      if (document == null) {
        scope.warn("Ranking namespace " + namespace + " is unavailable; using service ranking defaults");
      } else if (document.getResolutionCriteria() != null) {
        defaults.putAll(parse(document.getResolutionCriteria()));
      }
    }
    this.order = order(defaults);
  }

  private static Map<Criterion, Integer> parse(Map<String, Integer> criteria) {
    var result = new EnumMap<Criterion, Integer>(Criterion.class);
    criteria.forEach((key, priority) -> result.put(Criterion.forProperty(Objects.requireNonNull(key)),
        Objects.requireNonNull(priority, "Null priority for " + key)));
    return result;
  }

  private static List<Criterion> order(Map<Criterion, Integer> criteria) {
    return criteria.entrySet().stream().filter(e -> e.getValue() > 0)
        .sorted(Map.Entry.<Criterion, Integer>comparingByValue()
            .thenComparing(e -> e.getKey().property))
        .map(Map.Entry::getKey).toList();
  }

  /** Compute diagnostics for every candidate, including single-candidate queries. */
  public void prepare(Collection<Model> candidates) {
    candidates.forEach(this::computeCriteria);
  }

  public int semanticDistance(Model model) {
    return distances.computeIfAbsent(model, candidate -> {
      var outputs = candidate.getObservables();
      if (outputs == null) return Integer.MAX_VALUE;
      return outputs.stream().filter(Objects::nonNull)
          .mapToInt(output -> scope.getService(Reasoner.class)
              .semanticDistance(output, observable, contextObservable))
          .filter(distance -> distance >= 0).min().orElse(Integer.MAX_VALUE);
    });
  }

  @Override public int compare(Model first, Model second) {
    var left = computeCriteria(first); var right = computeCriteria(second);
    for (var criterion : order) {
      int comparison = Double.compare(left.get(criterion), right.get(criterion));
      if (comparison != 0) return criterion == Criterion.SEMANTIC_DISTANCE ? comparison : -comparison;
    }
    // No identity-hash or arrival-time tie breaker: stable asset identities survive service boundaries.
    return Comparator.nullsLast(String::compareTo).compare(first.getUrn(), second.getUrn());
  }

  @Override public Map<Criterion, Double> computeCriteria(Model model) {
    return ranks.computeIfAbsent(model, candidate -> {
      var result = new EnumMap<Criterion, Double>(Criterion.class);
      for (var criterion : order) result.put(criterion, computeStandardCriterion(criterion, candidate));
      return Collections.unmodifiableMap(result);
    });
  }

  @Override public List<String> listCriteria() { return order.stream().map(c -> c.property).toList(); }
  @Override public Set<Criterion> unsupportedCriteria() {
    var ret = EnumSet.noneOf(Criterion.class);
    for (var criterion : order) if (!SUPPORTED.contains(criterion)) ret.add(criterion);
    return Collections.unmodifiableSet(ret);
  }
  @Override public Map<Criterion, Double> getRanking(Model model) { return computeCriteria(model); }

  public double computeStandardCriterion(Criterion criterion, Model model) {
    return switch (criterion) {
      case LEXICAL_SCOPE -> computeLexicalScope(model);
      case SEMANTIC_DISTANCE -> semanticDistance(model);
      case SPACE_COVERAGE -> spatial.computeIfAbsent(model, this::computeSpatialCriteria)[0];
      case SPACE_SPECIFICITY -> spatial.computeIfAbsent(model, this::computeSpatialCriteria)[1];
      case TIME_COVERAGE -> temporal.computeIfAbsent(model, this::computeTemporalCriteria)[0];
      case TIME_SPECIFICITY -> temporal.computeIfAbsent(model, this::computeTemporalCriteria)[1];
      default -> -1; // Explicitly unavailable, never a fabricated measurement.
    };
  }

  public double computeLexicalScope(Model model) {
    if (model.getNamespace() == null || scenarios.contains(model.getNamespace())) return 100;
    if (model.getNamespace().equals(namespace)) return 75;
    if (project != null && project.equals(model.getProjectName())) return 50;
    return 0;
  }

  private double[] computeSpatialCriteria(Model model) {
    if (scale == null || scale.getSpace() == null) return unavailable();
    if (model.getCoverage() == null) return unavailable();
    if (model.getCoverage().isUniversal()) return new double[]{100, 0, -1};
    var modelScale = org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(model.getCoverage());
    if (modelScale == null || modelScale.getSpace() == null) return unavailable();
    var requested = scale.getSpace().getGeometricShape();
    var supplied = modelScale.getSpace().getGeometricShape();
    if (requested == null || supplied == null) return unavailable();
    if (requested.isEmpty() || supplied.isEmpty()) return new double[]{0, 0, -1};
    var intersection = requested.intersection(supplied);
    if (intersection.isEmpty()) return new double[]{0, 0, -1};
    var sqm = ServiceConfiguration.INSTANCE.getService(UnitService.class).squareMeters();
    double common = intersection.getArea(sqm);
    return new double[]{percent(common, requested.getArea(sqm)),
        percent(common, supplied.getArea(sqm)), -1};
  }

  private static double percent(double numerator, double denominator) {
    if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || denominator <= 0) return -1;
    return Math.max(0, Math.min(100, 100 * numerator / denominator));
  }
  private static double[] unavailable() { return new double[]{-1, -1, -1}; }

  /** Coverage and specificity use interval overlap; -1 endpoints denote open model bounds. */
  public static double[] computeTemporalCriteria(long modelStart, long modelEnd, Time time) {
    return temporal(modelStart == -1 ? null : modelStart, modelEnd == -1 ? null : modelEnd, time);
  }

  private static double[] temporal(Long start, Long end, Time requested) {
    if (requested == null || requested.getStart() == null || requested.getEnd() == null) return unavailable();
    double requestStart = requested.getStart().getMilliseconds(), requestEnd = requested.getEnd().getMilliseconds();
    double modelStart = start == null ? Double.NEGATIVE_INFINITY : start;
    double modelEnd = end == null ? Double.POSITIVE_INFINITY : end;
    if (requestEnd < requestStart || modelEnd < modelStart)
      throw new IllegalArgumentException("Reversed temporal bounds in ranking");
    if (requestStart == requestEnd) return new double[]{
        requestStart >= modelStart && requestStart <= modelEnd ? 100 : 0,
        modelStart == requestStart && modelEnd == requestEnd ? 100 : 0, -1};
    double overlap = Math.max(0, Math.min(requestEnd, modelEnd) - Math.max(requestStart, modelStart));
    return new double[]{percent(overlap, requestEnd - requestStart),
        Double.isInfinite(modelEnd - modelStart) ? 0 : percent(overlap, modelEnd - modelStart), -1};
  }

  private double[] computeTemporalCriteria(Model model) {
    if (scale == null || scale.getTime() == null || model.getCoverage() == null) return unavailable();
    if (scale.getTime().getStart() == null || scale.getTime().getEnd() == null) return unavailable();
    if (model.getCoverage().isUniversal()) return new double[]{100, 0, -1};
    var modelScale = org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(model.getCoverage());
    var time = modelScale == null ? null : modelScale.getTime();
    if (time == null) return unavailable();
    return temporal(time.getStart() == null ? null : time.getStart().getMilliseconds(),
        time.getEnd() == null ? null : time.getEnd().getMilliseconds(), scale.getTime());
  }
}
