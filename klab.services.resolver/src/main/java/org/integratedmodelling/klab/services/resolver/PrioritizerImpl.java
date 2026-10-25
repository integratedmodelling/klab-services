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
  private final Concept effectiveObserver;
  private final Map<Model,Integer> observerPreferences = new HashMap<>();
  private final Map<Model,Integer> jointChangePreferences = new IdentityHashMap<>();
  private final Map<Model,org.integratedmodelling.klab.runtime.language.ModelValidationGate.AdmissionReceipt> admissions = new java.util.IdentityHashMap<>();
  org.integratedmodelling.klab.runtime.language.ModelValidationGate.AdmissionReceipt admission(Model model) { return admissions.get(model); }
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
    var selectedObserver=scope.getObserver();
    this.effectiveObserver=selectedObserver==null||selectedObserver.getObservable()==null
        ? null : selectedObserver.getObservable().asConcept();
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
      var receipt = org.integratedmodelling.klab.runtime.language.ModelValidationGate.validatedPreparableAdmission(candidate,scope,observable);
      if (receipt == null) return Integer.MAX_VALUE;
      admissions.put(candidate,receipt);
      long validatedRevision=receipt.knowledgeRevision();
      var outputs = candidate.getObservables();
      if (outputs == null) return Integer.MAX_VALUE;
      if (observable.is(org.integratedmodelling.klab.api.knowledge.SemanticType.CHANGE)
          && !outputs.isEmpty()) {
        var qualities = new java.util.ArrayList<>(outputs.subList(1, outputs.size()));
        qualities.addAll(candidate.getDependencies());
        outputs = new java.util.ArrayList<>(outputs);
        outputs.addAll(org.integratedmodelling.klab.runtime.language.OccurrentSemantics.changes(
            outputs.getFirst(), qualities, scope));
      }
      int bestDistance=Integer.MAX_VALUE,bestPreference=Integer.MAX_VALUE;
      var reasoner=scope.getService(Reasoner.class);
      for(var output:outputs) {
        if(output==null)continue;
        int distance=reasoner.semanticDistance(output,observable,contextObservable,validatedRevision);
        if(distance<0)continue;
        int preference=0;
        if(observable.getObserverSemantics()==null&&effectiveObserver!=null) {
          if(output.getObserverSemantics()==null)preference=1;
          else {
            // Compare Agent meanings under the same admission receipt. This is an implicit
            // preference/compatibility test, never an authored observed-as query clause.
            if(!output.getObserverSemantics().is(org.integratedmodelling.klab.api.knowledge.SemanticType.AGENT)
                || !effectiveObserver.is(org.integratedmodelling.klab.api.knowledge.SemanticType.AGENT)
                || reasoner.semanticDistance(output.getObserverSemantics(),effectiveObserver,null,validatedRevision)<0)
              continue;
          }
        }
        if(preference<bestPreference) {bestPreference=preference;bestDistance=distance;}
        else if(preference==bestPreference)bestDistance=Math.min(bestDistance,distance);
      }
      observerPreferences.put(candidate,bestPreference);
      return bestDistance;
    });
  }

  /** Observer preference is a semantic partition, independent of configurable numeric weights. */
  int observerPreference(Model model) {
    if(observable.getObserverSemantics()!=null||effectiveObserver==null)return 0;
    semanticDistance(model);
    return observerPreferences.getOrDefault(model,Integer.MAX_VALUE);
  }

  @Override public int compare(Model first, Model second) {
    int perspective=Integer.compare(observerPreference(first),observerPreference(second));
    if(perspective!=0)return perspective;
    int joint = Integer.compare(jointChangePreference(second), jointChangePreference(first));
    if (joint != 0) return joint;
    var left = computeCriteria(first); var right = computeCriteria(second);
    for (var criterion : order) {
      int comparison = Double.compare(left.get(criterion), right.get(criterion));
      if (comparison != 0) return criterion == Criterion.SEMANTIC_DISTANCE ? comparison : -comparison;
    }
    // No identity-hash or arrival-time tie breaker: stable asset identities survive service boundaries.
    return Comparator.nullsLast(String::compareTo).compare(first.getUrn(), second.getUrn());
  }

  /** This partition is inside model selection; strategy tiers are still chosen by the compiler. */
  int jointChangePreference(Model model) {
    if (!observable.is(org.integratedmodelling.klab.api.knowledge.SemanticType.CHANGE)
        || contextObservable == null) return 0;
    var requested = ResolutionConstraint.getPayload(scope, ResolutionConstraint.Type.ChangeQualities,
        Observable.class);
    if (requested.size() < 2) return 0;
    // Never rank semantic claims from an unadmitted model.
    if (semanticDistance(model) == Integer.MAX_VALUE) return 0;
    return jointChangePreferences.computeIfAbsent(model, candidate -> jointChangeCoverage(
        candidate, requested, contextObservable, scope.getService(Reasoner.class),
        admissions.get(candidate).knowledgeRevision()));
  }

  static int jointChangeCoverage(Model model, List<Observable> requested, Concept bearer,
      Reasoner reasoner, long revision) {
    if (model.getObservables().isEmpty()) return 0;
    var process = model.getObservables().getFirst();
    if (!process.is(org.integratedmodelling.klab.api.knowledge.SemanticType.PROCESS)) return 0;
    var inherent = reasoner.inherent(process);
    // Exact/equivalent bearer specificity is stronger than merely being applicable to a superclass.
    if (inherent == null || reasoner.semanticDistance(inherent, bearer, null, revision) != 0
        || reasoner.semanticDistance(bearer, inherent, null, revision) != 0) return 0;
    var declared = new ArrayList<>(model.getObservables().subList(1, model.getObservables().size()));
    declared.addAll(model.getDependencies());
    var covered = new HashSet<String>();
    for (var quality : requested) {
      if (!quality.is(org.integratedmodelling.klab.api.knowledge.SemanticType.QUALITY)) continue;
      for (var output : declared) {
        if (output.is(org.integratedmodelling.klab.api.knowledge.SemanticType.QUALITY)
            && (reasoner.affectedBy(output, process) || reasoner.createdBy(output, process))
            && reasoner.semanticDistance(output, quality, bearer, revision) >= 0) {
          covered.add(quality.getUrn());
          break;
        }
      }
    }
    return covered.size() > 1 ? covered.size() : 0;
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
