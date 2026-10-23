package org.integratedmodelling.klab.services.resolver;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.impl.AgentImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.common.data.Notification;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/** Resolution compiler for k.LAB 1.0. Contains the majority of the resolution logics. */
public class ResolutionCompiler {

  record QueryMatch(
      Observation result,
      Observation reference,
      Scale requestedScale,
      Scale coveredScale,
      Coverage coverage,
      boolean deferredAbsolute) {

    QueryMatch(Observation result, Observation reference, Scale requestedScale,
        Scale coveredScale, Coverage coverage) {
      this(result, reference, requestedScale, coveredScale, coverage, false);
    }

    boolean hasCoverage() {
      return !deferredAbsolute && result != null && !result.isEmpty() && !coverage.isEmpty();
    }
  }

  private Graph<RuntimeAsset, DefaultEdge> resolutionCache =
      new DefaultDirectedGraph<>(DefaultEdge.class);
  private final ResolverService resolver;
  private final java.util.Set<String> activeContextBindings = new java.util.HashSet<>();
  private final java.util.Set<String> activeClassifierDependencies = new java.util.HashSet<>();
  private boolean automaticClassifiersAllowed = true;
  private boolean strictTierZeroOnly;
  /** Identity keys prevent mutable Model equality from moving cached grants between instances. */
  private record ModelIdentity(Model model) {
    @Override public boolean equals(Object other) {return other instanceof ModelIdentity key && key.model==model;}
    @Override public int hashCode() {return System.identityHashCode(model);}
  }
  private final Map<ModelIdentity,org.integratedmodelling.klab.runtime.language.ModelValidationGate.AdmissionReceipt> modelAdmissions = new java.util.concurrent.ConcurrentHashMap<>();
  org.integratedmodelling.klab.runtime.language.ModelValidationGate.AdmissionReceipt admissionReceipt(Model model) {
    return modelAdmissions.get(new ModelIdentity(model));
  }
  /** Prerequisites are private to this compiler and exact invocation, never catalog grants. */
  private record PreparationKey(Model model,ContextScope scope,String witness) {
    @Override public boolean equals(Object other) {return other instanceof PreparationKey key
        && key.model==model && key.scope==scope && key.witness.equals(witness);}
    @Override public int hashCode() {return 31*(31*System.identityHashCode(model)+System.identityHashCode(scope))+witness.hashCode();}
  }
  private record PreparedCandidate(
      org.integratedmodelling.klab.runtime.language.ModelValidationGate.PreparationReceipt receipt,
      ResourceSet requirements) {}
  private final java.util.concurrent.ConcurrentMap<PreparationKey,PreparedCandidate> preparedCandidates = new java.util.concurrent.ConcurrentHashMap<>();

  private static String observationWitness(Observation observation) {
    if (observation==null)return "null";
    return observation.getId()+"|"+observation.getUrn()+"|"
        +(observation.getObservable()==null?null:observation.getObservable().getUrn())+"|"
        +(observation.getGeometry()==null?null:observation.getGeometry().encode());
  }
  private static String preparationScopeWitness(ContextScope scope) {
    return observationWitness(scope.getContextObservation())+"#"+observationWitness(scope.getObserver())+"#"
        +org.integratedmodelling.klab.utilities.Utils.Json.asString(scope.getResolutionConstraints());
  }
  private PreparedCandidate preparedCandidate(Model model,ContextScope scope) {
    return preparedCandidates.get(new PreparationKey(model,scope,preparationScopeWitness(scope)));
  }
  private boolean hasPreparedModel(Model model) {
    return preparedCandidates.keySet().stream().anyMatch(key->key.model()==model);
  }
  /** Source proof precedes authorized prerequisite preparation; full installed admission follows it. */
  boolean prepareCandidate(Model model,Observable requested,ContextScope scope) {
    var key=new PreparationKey(model,scope,preparationScopeWitness(scope));
    // Per-key atomic preparation; unrelated contexts/models never share an invocation lock.
    var prepared=preparedCandidates.computeIfAbsent(key,ignored-> {
      var receipt=org.integratedmodelling.klab.runtime.language.ModelValidationGate.validatedSourcePreparation(model,scope,requested);
      if(receipt==null)return null;
      var lexical=scope.withResolutionConstraints(
          ResolutionConstraint.of(ResolutionConstraint.Type.ResolutionNamespace,model.getNamespace()),
          ResolutionConstraint.of(ResolutionConstraint.Type.ResolutionProject,model.getProjectName()));
      var runtime=lexical==null?null:lexical.getService(RuntimeService.class);
      if(runtime==null)return null;
      ResourceSet requirements;
      try { requirements=runtime.resolveContextualizables(model.getComputation(),lexical); }
      catch(RuntimeException failure) {scope.warn("Contextualizer prerequisites unavailable for "+model.getUrn()+": "+failure.getMessage());return null;}
      if(requirements==null || requirements.isEmpty()
          || org.integratedmodelling.common.utils.Utils.Notifications.hasErrors(requirements.getNotifications())
          || !key.witness().equals(preparationScopeWitness(scope))
          || !org.integratedmodelling.klab.runtime.language.ModelValidationGate.matches(receipt,model,scope))return null;
      return new PreparedCandidate(receipt,requirements);
    });
    return prepared!=null && key.witness().equals(preparationScopeWitness(scope))
        && org.integratedmodelling.klab.runtime.language.ModelValidationGate.matches(prepared.receipt(),model,scope);
  }
  private org.integratedmodelling.klab.api.knowledge.CollectiveReductionBinding activeCollectiveReduction;
  private static final class ClassifierPhaseDeferred extends RuntimeException {
    ClassifierPhaseDeferred() { super(null,null,false,false); }
  }
  private int modelQueries;
  private int matchedModels;

  boolean hasNoExplanatoryModel() {
    return modelQueries > 0 && matchedModels == 0;
  }

  private double MINIMUM_WORTHWHILE_CONTRIBUTION = 0.15;
  private List<Notification> notifications = new ArrayList<>();

  public ResolutionCompiler(ResolverService service) {
    this.resolutionCache.addVertex(RuntimeAsset.CONTEXT_ASSET);
    this.resolver = service;
  }

  /**
   * Entry point for observations at root level.
   *
   * @param observation
   * @param scope
   * @return
   */
  public ResolutionGraph resolve(Observation observation, ContextScope scope) {
    if (scope.isInterrupted()) return ResolutionGraph.empty();
    var contextGraph = ResolverService.getResolutionGraph(scope);
    if (contextGraph == null) {
      throw new KlabIllegalStateException(
          "Resolver context was not declared before starting observation resolution");
    }
    var result = resolve(observation, scope, contextGraph.createAttempt());
    if (result.isEmpty()) {
      for (var diagnostic : notifications) result.getNotifications().add(
          org.integratedmodelling.klab.api.services.runtime.Notification.error(diagnostic.getMessage().toString()));
    }
    return result;
  }

  public List<Notification> getNotifications() {
    return notifications;
  }

  private Geometry getObservationGeometry(Observation observation, ContextScope scope) {

    var parent =
        scope.getContextObservation() == null
            ? RuntimeAsset.CONTEXT_ASSET
            : scope.getContextObservation();

    resolutionCache.addVertex(observation);
    resolutionCache.addVertex(parent);
    resolutionCache.addEdge(parent, observation);

    var geometry = observation.getGeometry();
    if (geometry == null) {
      if (SemanticType.isDependent(observation.getObservable().getSemantics().getType())
          && scope.getContextObservation() != null) {
        geometry = scope.getContextObservation().getGeometry();
      }
    }

    return geometry;
  }

  private ResolutionGraph resolve(
      Observation observation, ContextScope scope, ResolutionGraph parentGraph) {

    if (observation.getObservable().getContextualization() != null
        && observation.getObservable().getContextualization().modifiesExistingObservations()) {
      var geometry =
          observation.getGeometry() == null && scope.getContextObservation() != null
              ? scope.getContextObservation().getGeometry()
              : observation.getGeometry();
      if (geometry == null || geometry.isEmpty()) return ResolutionGraph.empty();
      return resolveOperation(
          observation.getObservable(),
          GeometryRepository.INSTANCE.scale(geometry, scope),
          parentGraph,
          scope);
    }
    return resolve(observation, scope, parentGraph, null);
  }

  /** Resolve a directive without querying or registering it as an observation. */
  private ResolutionGraph resolveOperation(
      Observable observable, Scale scale, ResolutionGraph parent, ContextScope scope) {
    return resolveOperation(observable, scale, parent, scope, null);
  }

  private ResolutionGraph resolveOperation(
      Observable observable,
      Scale scale,
      ResolutionGraph parent,
      ContextScope scope,
      Observable modelDependency) {
    if (scope.getContextObservation() == null) return ResolutionGraph.empty();
    var target = new OperationTarget(observable, scope.getContextObservation(), modelDependency);
    var result = parent.createChild(target, scale);
    // The existing Reasoner API accepts an observation-shaped request, not a runtime identity.
    var builder = new Observation.NaiveBuilder(observable, scope);
    builder.geometry(scale.as(Geometry.class));
    var probe = builder.make();
    for (var strategy :
        scope.getService(Reasoner.class).computeObservationStrategies(probe, scope)) {
      if (observable.getContextualization() == Contextualization.CLASSIFICATION) {
        var terminal =
            strategy.getOperations().isEmpty() ? null : strategy.getOperations().getLast();
        if (terminal == null
            || terminal.getType() != ObservationStrategy.Operation.Type.OBSERVE
            || !terminal.getInputs().containsKey("members")) continue;
      }
      var candidate = resolve(strategy, scale, result, scope, null);
      if (candidate.isEmpty()) continue;
      result.merge(candidate);
      if (result.getCoverage().isComplete()) return result;
    }
    return ResolutionGraph.empty();
  }

  private ResolutionGraph resolve(
      Observation observation,
      ContextScope scope,
      ResolutionGraph parentGraph,
      QueryMatch suppliedQuery) {

    if (observation.getId() > 0) {
      ScheduleNegotiationSupport.checkReuse(observation, parentGraph.scheduleRequest,
          GeometryRepository.INSTANCE.scale(observation.getGeometry()).getTime(), scope);
      return parentGraph;
    }

    var resolutionGeometry = getObservationGeometry(observation, scope);
    if (resolutionGeometry == null || resolutionGeometry.isEmpty()) {
      return ResolutionGraph.empty();
    }
    var scale =
        suppliedQuery == null
            ? GeometryRepository.INSTANCE.scale(resolutionGeometry, scope)
            : suppliedQuery.requestedScale();
    var query =
        suppliedQuery == null ? query(observation.getObservable(), scale, scope) : suppliedQuery;
    if (query.hasCoverage() && query.coverage().isComplete()) {
      ScheduleNegotiationSupport.checkReuse(query.reference(), parentGraph.scheduleRequest, scale.getTime(), scope);
      var ret = parentGraph.createChild(observation, scale);
      ret.addReference(query.reference(), query.coverage(), query.coveredScale());
      return ret;
    }

    var scaleToResolve = scale;
    if (query.hasCoverage()) {
      scaleToResolve = missingScale(scale, query.coveredScale());
      if (scaleToResolve == null || scaleToResolve.isEmpty()) {
        // An unrepresentable complement cannot turn reported partial coverage into success.
        scaleToResolve = scale;
      }
    }
    Coverage coverage = Coverage.create(scale, 0.0);
    for (var resolvable : parentGraph.getResolving(observation.getObservable(), scale)) {
      if (parentGraph.scheduleRequest != null || observation.getObservable().is(SemanticType.PROCESS)
          || observation.getObservable().is(SemanticType.EVENT)) {
        // A semantic/coverage cache entry alone cannot prove occurrence compatibility.
        if (!(resolvable.getFirst() instanceof Observation cached)) continue;
        ScheduleNegotiationSupport.checkReuse(cached, parentGraph.scheduleRequest, scale.getTime(), scope);
      }
      if (resolvable.getSecond().getGain() < MINIMUM_WORTHWHILE_CONTRIBUTION) {
        continue;
      }
      parentGraph.accept(resolvable.getFirst(), resolvable.getSecond());
      coverage.merge(resolvable.getSecond(), LogicalConnector.UNION);
      if (coverage.isComplete()) {
        break;
      }
    }

    if (coverage.isComplete()) {
      return parentGraph;
    }

    ResolutionGraph ret = parentGraph.createChild(observation, scale);
    if (query.hasCoverage()) {
      ret.addReference(query.reference(), query.coverage(), query.coveredScale());
    }
    scope =
        scope.withResolutionConstraints(
            ResolutionConstraint.of(
                ResolutionConstraint.Type.Provenance, Agent.create(AgentImpl.KLAB_AGENT_NAME)));

    if (observation.getContextualizationData() != null) {
      if (observation.getContextualizationData().getData() != null) {
        // TODO compile data in (inline contextualizer?), return
      } else if (observation.getContextualizationData().getAdapterId() != null) {
        // TODO validate and compile adapter call in (resource?), return
      }
    }

    List<ResolutionGraph> strategyGraphs = new ArrayList<>();
    var strategies = scope.getService(Reasoner.class).computeObservationStrategies(observation, scope)
        .stream().sorted(java.util.Comparator.comparingInt(ObservationStrategy::getRank)).toList();
    if (strategies.isEmpty() && suppliedQuery != null && !observation.getObservable().isOptional()) {
      scope.warn("No observation strategy for dependency " + observation.getObservable().getUrn()
          + " (abstract=" + observation.getObservable().isAbstract() + ")");
    }
    boolean inheritedBudget = automaticClassifiersAllowed;
    boolean inheritedStrictTier = strictTierZeroOnly;
    try {
      // Exhaust all precomputed rank-0 alternatives before any automatic classifier plan or
      // rank-1 native binding can be launched. A nested target cannot expand its ancestor's budget.
      for(int phase=0;phase<(inheritedStrictTier?1:2);phase++) {
        strictTierZeroOnly = phase==0 || inheritedStrictTier;
        // This singleton capability needs whole native support; it does not compose a deferred
        // parent with already accepted partial precomputed parent results.
        automaticClassifiersAllowed = !strictTierZeroOnly && inheritedBudget && ret.getResolvedCoverage()==0;
        for (ObservationStrategy strategy : strategies) {
          if(phase==0 && strategy.getRank()>0)continue;
          var strategyResolution = resolve(strategy, scaleToResolve, ret, scope, observation,
              query.deferredAbsolute() && phase == 0 ? query : null);
          var cov = ret.checkCoverage(strategyResolution);
          if (!cov.isRelevant()) continue;
          strategyGraphs.add(strategyResolution);
          ret.merge(strategyResolution);
          if (cov.isComplete()) return ret;
        }
        // A committed absolute answer remains legal for an implicit request. Prefer reusable
        // knowledge to recursive discovery, after all Tier-0 observer-specific attempts fail.
        if (phase == 0 && query.deferredAbsolute()) {
          ScheduleNegotiationSupport.checkReuse(query.reference(), parentGraph.scheduleRequest, scale.getTime(), scope);
          ret.addReference(query.reference(), query.coverage(), query.coveredScale());
          if (ret.getCoverage().isComplete()) return ret;
          scaleToResolve = missingScale(scale, query.coveredScale());
        }
      }
    } finally {
      automaticClassifiersAllowed = inheritedBudget;
      strictTierZeroOnly = inheritedStrictTier;
    }

    return ResolutionGraph.empty();
  }

  ResolutionGraph resolve(
      ObservationStrategy observationStrategy,
      Scale scaleToCover,
      ResolutionGraph graph,
      ContextScope scope) {
    return resolve(observationStrategy, scaleToCover, graph, scope, null);
  }

  private ResolutionGraph resolve(
      ObservationStrategy observationStrategy,
      Scale scaleToCover,
      ResolutionGraph graph,
      ContextScope scope,
      Observation explainedObservation) {
    return resolve(observationStrategy, scaleToCover, graph, scope, explainedObservation, null);
  }

  private ResolutionGraph resolve(ObservationStrategy observationStrategy, Scale scaleToCover,
      ResolutionGraph graph, ContextScope scope, Observation explainedObservation, QueryMatch fallback) {
    var binding = observationStrategy.getContextBinding();
    if(strictTierZeroOnly && observationStrategy.getRank()>0)return ResolutionGraph.empty();
    if (scope.isInterrupted()) return ResolutionGraph.empty();
    if (observationStrategy.getCollectiveReduction() != null)
      return resolveCollectiveReduction(observationStrategy, scaleToCover, graph, scope, explainedObservation);
    if (binding == null) return resolveStrategyBody(observationStrategy, scaleToCover, graph, scope, explainedObservation, fallback);
    if (observationStrategy.getRank() < 1 || observationStrategy.getOperations().size() != 1
        || observationStrategy.getOperations().getFirst().getType() != ObservationStrategy.Operation.Type.OBSERVE
        || explainedObservation == null
        || !java.util.Objects.equals(binding.childObservable(), explainedObservation.getObservable().getUrn()))
      return ResolutionGraph.empty();
    String key = binding.contextId() + "|" + binding.childObservable() + "|" + observationStrategy.getUrn();
    if (activeContextBindings.size() >= 32 || !activeContextBindings.add(key)) {
      scope.warn("Recursive singular binding made no progress: " + key);
      return ResolutionGraph.empty();
    }
    try {
      binding.require(scope, observationStrategy.getOperations().getFirst().getObservable());
      if (!java.util.Objects.equals(binding.contextSupport(), Geometry.forTransport(scaleToCover).encode())
          || explainedObservation.getParentId() > 0 && explainedObservation.getParentId() != binding.contextId())
        return ResolutionGraph.empty();
      var result = resolveStrategyBody(observationStrategy, scaleToCover, graph, scope, explainedObservation, fallback);
      binding.require(scope, observationStrategy.getOperations().getFirst().getObservable());
      return scope.isInterrupted() ? ResolutionGraph.empty() : result;
    } catch (IllegalStateException invalidBinding) {
      scope.warn(invalidBinding.getMessage());
      return ResolutionGraph.empty();
    } finally {
      activeContextBindings.remove(key);
    }
  }

  private ResolutionGraph resolveCollectiveReduction(ObservationStrategy strategy, Scale scale,
      ResolutionGraph graph, ContextScope scope, Observation request) {
    var binding = strategy.getCollectiveReduction();
    if (strategy.getRank() < 1 || strategy.getContextBinding() != null || request == null
        || strategy.getOperations().size() != 1
        || strategy.getOperations().getFirst().getType() != ObservationStrategy.Operation.Type.RESOLVE
        || !java.util.Objects.equals(binding.operand(), strategy.getOperations().getFirst().getObservable().getUrn()))
      return ResolutionGraph.empty();
    String key = "reduce|" + binding.context().contextId() + "|" + binding.context().childObservable();
    if (activeContextBindings.size() >= 32 || !activeContextBindings.add(key)) return ResolutionGraph.empty();
    var previousReduction = activeCollectiveReduction;
    try {
      binding.require(scope, request.getObservable(), scale);
      activeCollectiveReduction = binding;
      var result = graph.createChild(strategy, scale);
      var members = resolve(strategy.getOperations().getFirst().getObservable(), scale, result.withScheduleRequest(null), scope);
      if (members.isEmpty() || !members.getCoverage().isComplete()) return ResolutionGraph.empty();
      var operand = strategy.getOperations().getFirst().getObservable();
      if (members.graph().vertexSet().stream().filter(Model.class::isInstance).map(Model.class::cast)
          .flatMap(model -> model.getObservables().stream()).anyMatch(output -> output.getObserverSemantics() != null
              && java.util.Objects.equals(output.asConcept().getUrn(), operand.asConcept().getUrn()))) {
        scope.warn("Collective reduction does not yet support observer-specific substantial discovery outputs");
        return ResolutionGraph.empty();
      }
      binding.require(scope, request.getObservable(), scale);
      result.merge(members, "members");
      return result;
    } catch (IllegalStateException failure) {
      scope.warn(failure.getMessage());
      return ResolutionGraph.empty();
    } finally { activeCollectiveReduction = previousReduction; activeContextBindings.remove(key); }
  }

  private ResolutionGraph resolveStrategyBody(ObservationStrategy observationStrategy,
      Scale scaleToCover, ResolutionGraph graph, ContextScope scope, Observation explainedObservation, QueryMatch fallback) {
    var ret = graph.createChild(observationStrategy, scaleToCover);
    Map<String, ResolutionGraph> produced = new LinkedHashMap<>();
    // Named ports may request the same collective (e.g. both ends of a connection).
    // Share the producer within this strategy, before it exists in the runtime catalog.
    Map<String, ResolutionGraph> collectives = new LinkedHashMap<>();
    boolean namedPlan =
        observationStrategy.getOperations().stream().allMatch(o -> o.getId() != null);

    for (var operation : observationStrategy.getOperations()) {
      if (scope.isInterrupted()) return ResolutionGraph.empty();

      switch (operation.getType()) {
        case RESOLVE -> {
          var contextualizedScope =
              contextualizeScope(scope, operation.getObservable(), scaleToCover, graph);

          var observable = operation.getObservable();
          boolean share =
              observable.getSemantics().isCollective()
                  && (observable.getContextualization() == null
                      || !observable.getContextualization().modifiesExistingObservations());
          String key = observable.getUrn() + "|" + observable.getContextualization();
          var observableResolution = share ? collectives.get(key) : null;
          if (observableResolution == null)
            observableResolution =
                resolve(
                    operation.getObservable(),
                    contextualizedScope.getSecond(),
                    ret.withScheduleRequest(null),
                    contextualizedScope.getFirst());
          if (observableResolution.isEmpty() || !observableResolution.getCoverage().isComplete()) {
            return ResolutionGraph.empty();
          }
          if (share) collectives.put(key, observableResolution);
          if (namedPlan) {
            produced.put(operation.getId(), observableResolution);
            if (operation == observationStrategy.getOperations().getLast())
              ret.merge(observableResolution);
          } else ret.merge(observableResolution, operation.getId());
        }
        case OBSERVE -> {
          boolean acceptedModel = false;
          boolean preferSpecific = fallback != null && java.util.Objects.equals(
              operation.getObservable().asConcept().getUrn(), fallback.reference().getObservable().asConcept().getUrn());
          // Each producer accumulates its own output coverage, independently of prerequisites.
          var contextualizedScope =
              contextualizeScope(scope, operation.getObservable(), scaleToCover, graph);
          var contextObservable =
              contextualizedScope.getFirst().getContextObservation() == null
                  ? null
                  : contextualizedScope
                      .getFirst()
                      .getContextObservation()
                      .getObservable()
                      .getSemantics();

          for (Model model :
              queryModels(
                  operation.getObservable(),
                  contextObservable,
                  contextualizedScope.getFirst(),
                  contextualizedScope.getSecond())) {

            if (preferSpecific && model.getObservables().stream().noneMatch(output ->
                output.getObserverSemantics() != null
                    && org.integratedmodelling.klab.runtime.ObserverResolution.preference(
                        output, operation.getObservable(), contextualizedScope.getFirst()) == 0)) continue;
            // Select strategies and models in the requesting context. Only the model's
            // computation/dependencies enter the substantial it explains; otherwise a
            // root substantial would incorrectly be its own context during model lookup.
            var modelScope = contextualizedScope.getFirst();
            if (explainedObservation != null
                && !explainedObservation.getObservable().is(SemanticType.EVENT)
                && SemanticType.isEnumerableSubstantial(
                    explainedObservation.getObservable().getSemantics().getType())
                && java.util.Objects.equals(
                    operation.getObservable().getUrn(),
                    explainedObservation.getObservable().getUrn())) {
              modelScope = modelScope.within(explainedObservation);
            }
            var modelResolution = resolve(model, contextualizedScope.getSecond(), ret, modelScope,
                explainedObservation, contextualizedScope.getFirst(), operation.getObservable());
            if (modelResolution.isEmpty()) continue;
            if (operation.getTransformationTarget() != null) {
              // Until composed contextualizer pipelines expose their graph ports, accept only
              // one contextualizer with exactly one declared base-input port.
              if (model.getComputation().size() != 1
                  || model.getComputation().getFirst().getServiceCall() == null) continue;
              var prototype =
                  modelResolution.getServiceInfo(
                      model.getComputation().getFirst().getServiceCall().getUrn());
              if (prototype == null
                  || prototype.listInputs().stream()
                          .filter(
                              input ->
                                  input
                                          .getTags()
                                          .contains(
                                              org.integratedmodelling.klab.api.lang.ServiceInfo.Tag
                                                  .INPUT)
                                      || input
                                          .getName()
                                          .equals(operation.getTransformationTarget()))
                          .count()
                      != 1) {
                scope.warn(
                    "Transformer model requires exactly one declared base-input port: "
                        + model.getUrn());
                continue;
              }
            }
            for (var input : operation.getInputs().entrySet()) {
              var prerequisite = produced.get(input.getValue());
              if (prerequisite == null || prerequisite.isEmpty()) {
                scope.error("Unknown strategy graph input " + input.getValue());
                return ResolutionGraph.empty();
              }
              modelResolution.merge(prerequisite, input.getKey());
            }
            var cov = ret.checkCoverage(modelResolution);
            if (!cov.isRelevant()) {
              continue;
            }
            ret.merge(modelResolution, operation.getTransformationTarget());
            acceptedModel = true;
            if (cov.isComplete()) {
              break;
            }
          }

          // A REFERENCE is an input, not an output-value fill operator. Partial specific
          // computation cannot be declared complete by unioning absolute reference coverage.
          // Keep trying complete models; the outer phase can return the full absolute source.
          if (!acceptedModel || !ret.getCoverage().isComplete()) {
            return ResolutionGraph.empty();
          }
          if (operation.getTransformationTarget() != null) {
            var base = produced.get(operation.getTransformationTarget());
            if (base == null
                || base.isEmpty()
                || !base.getCoverage().isComplete()
                || operation.getObservable().getContextualization()
                    != org.integratedmodelling.klab.api.knowledge.Contextualization
                        .TRANSFORMATION) {
              scope.error(
                  "Invalid transformation input graph " + operation.getTransformationTarget());
              return ResolutionGraph.empty();
            }
            // Attach the base only after the transformer has independently resolved. Its graph
            // name binds the transformer's INPUT parameter during dataflow compilation.
            ret.merge(base, operation.getTransformationTarget());
          }
          if (namedPlan && operation != observationStrategy.getOperations().getLast()) {
            scope.error("Intermediate observe producers require graph composition support");
            return ResolutionGraph.empty();
          }
        }
        case APPLY -> {
          if (operation.getType()
                  == org.integratedmodelling.klab.api.knowledge.ObservationStrategy.Operation.Type
                      .APPLY
              && !operation.getContextualizables().isEmpty()) {
            /**
             * We ask the runtime to resolve all the contextualizables as a single operation. This
             * will enable using anything that's supported natively in the runtime as well as using
             * the resources service to locate and install any needed components or resources.
             *
             * <p>The strategy goes in the graph so there is no need for further storage of the
             * contextualizers.
             */
            var runtime = scope.getService(RuntimeService.class);
            ResourceSet requirements =
                runtime.resolveContextualizables(operation.getContextualizables(), scope);

            if (requirements == null) {
              scope.error(
                  "Runtime returned no contextualizer resolution; check that the Runtime and "
                      + "Resolver services use compatible API versions");
              return ResolutionGraph.empty();
            }
            if (requirements.isEmpty()) {
              return ResolutionGraph.empty();
            }

            updateServiceInfo(requirements, ret, scope);
            ret.setDependencies(Utils.Resources.merge(ret.getDependencies(), requirements));
          }
        }
      }
    }

    return ret;
  }

  /*
   * Retrieve any missing service info from the runtime so that the dataflow compiler
   * can analyze the prototype and link arguments as required.
   */
  private void updateServiceInfo(
      ResourceSet requirements, ResolutionGraph ret, ContextScope scope) {

    for (var resource : requirements.getResults()) {
      if (resource.getKnowledgeClass() == KlabAsset.KnowledgeClass.SERVICE_IMPLEMENTATION) {
        var serviceInfo = ret.getServiceInfo(resource.getResourceUrn());
        if (serviceInfo == null) {
          serviceInfo =
              scope
                  .getService(RuntimeService.class)
                  .getServiceInfo(resource.getResourceUrn(), scope);
          if (serviceInfo != null) {
            ret.addServiceInfo(resource.getResourceUrn(), serviceInfo);
          } // null should never happen
        }
      }
    }
  }

  /**
   * TODO the resolution must also check any geometry constraints that come with the
   * contextualizables, taken from function and adapter specs. These probably should come along with
   * the ResourceSet results.
   *
   * @param model
   * @param scaleToCover
   * @param graph
   * @param scope
   * @return
   */
  private ResolutionGraph resolve(
      Model model, Scale scaleToCover, ResolutionGraph graph, ContextScope scope) {
    return resolve(model, scaleToCover, graph, scope, null, scope,
        model.getObservables().isEmpty()?null:model.getObservables().getFirst());
  }

  private ResolutionGraph resolve(
      Model model, Scale scaleToCover, ResolutionGraph graph, ContextScope scope,
      Observation occurrentObservation, ContextScope selectionScope, Observable selectedRequest) {

    var admitted=admissionReceipt(model);
    if (model.getNamespace()!=null && !model.getNamespace().isBlank()) {
      if (admitted==null || !java.util.Objects.equals(admitted.executableHash(),org.integratedmodelling.klab.runtime.language.ModelValidationGate.executableHash(model))
          || admitted.knowledgeRevision()!=graph.semanticRevision() || !java.util.Objects.equals(admitted.reasonerId(),graph.semanticReasonerId())) {
        scope.warn("Named model lacks its original stable source admission: "+model.getUrn());return ResolutionGraph.empty();
      }
      graph.admitModel(model,admitted);
    }
    var ret = graph.createChild(model, scaleToCover);
    org.integratedmodelling.klab.api.digitaltwin.ProcessPlan processPlan;
    try {
      processPlan = ProcessModelBindings.analyze(model, scope);
    } catch (org.integratedmodelling.klab.api.exceptions.KlabValidationException rejected) {
      scope.warn("Rejected occurrence bindings in " + model.getUrn() + ": " + rejected.getMessage());
      return ResolutionGraph.empty();
    }
    ret.processPlan = processPlan;

    // Model lookup is performed in the requesting scope. For a root substantial,
    // only computation/dependencies enter the explained observation; preserve that
    // explicit internal transition without treating arbitrary contexts as equivalent.
    var originalScope=selectionScope;
    var prepared=preparedCandidate(model,originalScope);
    if(prepared==null && hasPreparedModel(model)) {
      scope.warn("Context changed after model prerequisite preparation: "+model.getUrn());
      return ResolutionGraph.empty();
    }
    if(prepared!=null && (!org.integratedmodelling.klab.runtime.language.ModelValidationGate.matches(prepared.receipt(),model,originalScope)
        || !org.integratedmodelling.klab.runtime.language.ModelValidationGate.sameSnapshot(prepared.receipt(),admitted))) {
      scope.warn("Model changed after contextualizer prerequisite preparation: "+model.getUrn());
      return ResolutionGraph.empty();
    }


    if(prepared!=null && (scope!=selectionScope
        || !preparationScopeWitness(scope).equals(preparationScopeWitness(selectionScope)))) {
      // The existing root-substantial path deliberately enters the explained subject.
      // Resource prerequisites may depend on this scope: prepare there independently,
      // never transfer the requesting context's ResourceSet or contextual eligibility.
      var selectionPreparation=prepared;
      if(!prepareCandidate(model,selectedRequest,scope))return ResolutionGraph.empty();
      prepared=preparedCandidate(model,scope);
      var computationAdmission=org.integratedmodelling.klab.runtime.language.ModelValidationGate.validatedPreparableAdmission(model,scope,selectedRequest);
      if(prepared==null || !org.integratedmodelling.klab.runtime.language.ModelValidationGate.sameSnapshot(selectionPreparation.receipt(),computationAdmission)
          || !org.integratedmodelling.klab.runtime.language.ModelValidationGate.sameSnapshot(prepared.receipt(),computationAdmission))return ResolutionGraph.empty();
    }

    scope =
        scope.withResolutionConstraints(
            ResolutionConstraint.of(
                ResolutionConstraint.Type.ResolutionNamespace, model.getNamespace()),
            ResolutionConstraint.of(
                ResolutionConstraint.Type.ResolutionProject, model.getProjectName()));

    // check that all contextualizers are supported
    var runtime = scope.getService(RuntimeService.class);
    ResourceSet requirements = prepared==null
        ? runtime.resolveContextualizables(model.getComputation(), scope) : prepared.requirements();

    if (requirements == null) {
      scope.error(
          "Runtime returned no contextualizer resolution for model ",
          model.getUrn(),
          "; check that the Runtime and Resolver services use compatible API versions");
      return ResolutionGraph.empty();
    }
    // TODO filter the results to accommodate constraints w.r.t. the geometry and (possibly) the
    // semantics.

    if (requirements.isEmpty()) {
      return ResolutionGraph.empty();
    }

    updateServiceInfo(requirements, ret, scope);
    try {
      ret.scheduleNegotiation = ScheduleNegotiationSupport.negotiate(model, ret, scope, scaleToCover.getTime());
    } catch (org.integratedmodelling.klab.api.exceptions.KlabValidationException rejected) {
      scheduleRejected(model, ret.scheduleRequest, rejected, scope);
      return ResolutionGraph.empty();
    }
    ret.setDependencies(Utils.Resources.merge(requirements, ret.getDependencies()));

    /*
    resolve all dependencies
     */
    //    boolean complete = model.getDependencies().isEmpty();
    List<Pair<ResolutionGraph, String>> modelGraphs = new ArrayList<>();
    var inputs = new ArrayList<>(model.getDependencies());
    if (processPlan != null) {
      for (var binding : processPlan.bindings()) {
        if (!binding.dependency()
            && binding.effect()
                != org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.Effect.CREATED)
          inputs.add(binding.observable());
      }
    }
    for (int dependencyIndex=0;dependencyIndex<inputs.size();dependencyIndex++) {
      final int sourceDependencyIndex=dependencyIndex;
      var dependency=inputs.get(dependencyIndex);
      var binding =
          processPlan == null ? null : processPlan.binding(ProcessModelBindings.name(dependency));
      // An epiphenomenon is a transition output, never an observation/INIT prerequisite.
      if (binding != null
          && binding.effect()
              == org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.Effect.CREATED) continue;

      ResolutionGraph dependencyResolution;
      try {
        var pendingMember=ret.admission(model)==null?null:ret.admission(model).pendingMemberQualities().stream()
            .filter(p->p.dependencyIndex()==sourceDependencyIndex).findFirst().orElse(null);
        if(pendingMember!=null) {
          // Expected result kind is a complete native collection, not an ordinary Observation.
          // No direct-view Tier-0 provider exists yet; Tier-1 discovery reuses native per-member Tier-0 normally.
          if(!planMemberQualityInput(model,dependency,sourceDependencyIndex,ret,scope))return ResolutionGraph.empty();
          continue;
        }
        if (planClassifiedDependency(model, dependency, sourceDependencyIndex, scaleToCover, ret, scope))
          continue;
        var dependencyScope = scope;
        var primary = model.getObservables().getFirst();
        if (dependency.is(SemanticType.QUALITY)
            && (primary.is(SemanticType.PROCESS)
                || primary.is(SemanticType.EVENT) && !primary.getSemantics().isCollective())) {
          var context = scope.getContextObservation();
          var bearer = org.integratedmodelling.klab.runtime.language.OccurrentSemantics.bearer(
              dependency, primary, context == null ? null : context.getObservable(),
              scope.getService(Reasoner.class));
          if (bearer == org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.Bearer.OCCURRENT) {
            if (occurrentObservation == null)
              throw new org.integratedmodelling.klab.api.exceptions.KlabValidationException(
                  "Missing occurrence observation for " + dependency.getUrn());
            dependencyScope = scope.within(occurrentObservation);
          }
        }
        var requestedSchedule = dependency.is(SemanticType.PROCESS)
            ? org.integratedmodelling.klab.api.digitaltwin.OccurrenceSchedule.fromDependency(dependency.getAnnotations()) : null;
        var request = requestedSchedule == null ? null
            : new org.integratedmodelling.klab.api.digitaltwin.OccurrenceNegotiation.Request(
                model.getUrn(), ProcessModelBindings.name(dependency), dependency.getUrn(), requestedSchedule);
        var dependencyGraph = ret.withScheduleRequest(request);
        dependencyResolution =
            dependency.getContextualization() != null
                    && dependency.getContextualization().modifiesExistingObservations()
                ? resolveOperation(dependency, scaleToCover, dependencyGraph, dependencyScope, dependency)
                : resolve(dependency, scaleToCover, dependencyGraph, dependencyScope);
      } catch (ClassifierPhaseDeferred deferred) {
        return ResolutionGraph.empty();
      } catch (org.integratedmodelling.klab.api.exceptions.KlabValidationException rejected) {
        scheduleRejected(model, ret.scheduleRequest, rejected, scope);
        return ResolutionGraph.empty();
      }

      // FIXME if the dep is on a collective, the geom of the obs will be the observer's and this
      //  will be irrelevant 00 FIXME HERE - dependencyResolution.targetCoverage merges to
      // insufficient the FIRST time only
      var cov = ret.checkCoverage(dependencyResolution);
      if (!cov.isRelevant()) {
        if (dependency.isOptional()) {
          continue;
        } else {
          scope.warn("Model " + model.getUrn() + " rejected: mandatory dependency "
              + dependency.getUrn() + " has no relevant resolution coverage");
          return ResolutionGraph.empty();
        }
      }
      modelGraphs.add(Pair.of(dependencyResolution, dependency.getStatedName()));
    }

    for (var modelGraph : modelGraphs) {
      ret.merge(modelGraph.getFirst(), modelGraph.getSecond());
    }

    return ret;
  }

  private boolean planMemberQualityInput(Model model,Observable dependency,int index,ResolutionGraph graph,ContextScope scope) {
    var receipt=graph.admission(model);
    if(receipt==null || !org.integratedmodelling.klab.runtime.language.ModelValidationGate.matches(receipt,model,scope))return false;
    var pending=receipt.pendingMemberQualities().stream().filter(p->p.dependencyIndex()==index).findFirst().orElse(null);
    if(pending==null || !Objects.equals(pending.quality(),dependency.getUrn())
        || !Objects.equals(pending.port(),dependency.getStatedName()) || model.getComputation().size()!=1
        || model.getComputation().getFirst().getServiceCall()==null)return false;
    var resources=scope.getService(org.integratedmodelling.klab.api.services.ResourcesService.class);
    var saved=resources==null?null:resources.retrieve(model.getNamespace(),org.integratedmodelling.klab.api.lang.kim.KimNamespace.class,scope);
    var source=saved==null?null:saved.getStatements().stream().filter(s->s instanceof org.integratedmodelling.klab.api.lang.kim.KimModel
        && model.getUrn().equals(s.getUrn())).map(org.integratedmodelling.klab.api.lang.kim.KimModel.class::cast).findFirst().orElse(null);
    if(source==null || index>=source.getDependencies().size())return false;
    var occurrence=source.getDependencies().get(index);
    if(org.integratedmodelling.klab.runtime.language.ModelValidationGate.explicitAtomicInherent(occurrence)==null
        || !Objects.equals(occurrence.getUrn(),pending.sourceObservable()) || !Objects.equals(occurrence.getFormalName(),pending.port()))return false;
    var runtime=scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
    if(runtime==null || !runtime.supportsMemberQualityInput(model.getComputation().getFirst().getServiceCall(),pending.port(),scope))return false;
    var context=scope.getContextObservation();var reasoner=scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);
    if(!org.integratedmodelling.klab.api.knowledge.SingularContextBinding.eligible(context))return false;
    graph.requireSemanticSnapshot(scope);
    var nativeBearer=reasoner.resolveConcept(pending.nativeBearer());
    var members=nativeBearer==null?null:reasoner.resolveObservable(nativeBearer.collective().getUrn());
    if(members==null || members.isAbstract() || !members.getSemantics().isCollective())return false;
    var container=new org.integratedmodelling.klab.api.knowledge.SingularContextBinding(context.getId(),context.getUrn(),
        context.getObservable().getUrn(),Geometry.forTransport(context.getGeometry()).encode(),pending.quality(),
        graph.semanticReasonerId(),graph.semanticRevision());
    var request=new org.integratedmodelling.klab.api.services.runtime.objects.MemberQualityRequest(container,members.getUrn(),
        pending.quality(),null,scope.getResolutionConstraints()==null?List.of():List.copyOf(scope.getResolutionConstraints()));
    var plan=new org.integratedmodelling.klab.api.services.runtime.MemberQualityInputPlan(model.getUrn(),model.getNamespace(),
        receipt.sourceVersion(),receipt.sourceHash(),index,pending.port(),pending.sourceObservable(),request);
    var prior=graph.memberQualityInputs.computeIfAbsent(model,ignored->new java.util.LinkedHashMap<>()).putIfAbsent(pending.port(),plan);
    if(prior!=null && !prior.equals(plan))return false;
    graph.requireSemanticSnapshot(scope);
    return org.integratedmodelling.klab.runtime.language.ModelValidationGate.matches(receipt,model,scope);
  }

  /** Freeze and plan classifier work; this method performs no Runtime mutation or execution. */
  private boolean planClassifiedDependency(Model model, Observable dependency, int index,
      Scale scale, ResolutionGraph graph, ContextScope scope) {
    var resources=scope.getService(org.integratedmodelling.klab.api.services.ResourcesService.class);
    if(resources==null)return false;
    String namespace=model.getNamespace();long version=-1;String fingerprint=null;
    org.integratedmodelling.klab.api.lang.kim.KimObservable parsed;
    if(namespace!=null && !namespace.isBlank()) {
      var saved=resources.retrieve(namespace,org.integratedmodelling.klab.api.lang.kim.KimNamespace.class,scope);
      if(saved==null)throw new org.integratedmodelling.klab.api.exceptions.KlabValidationException("Missing selected model source");
      var authored=saved.getStatements().stream().filter(statement->statement instanceof org.integratedmodelling.klab.api.lang.kim.KimModel
          && Objects.equals(statement.getUrn(),model.getUrn())).map(org.integratedmodelling.klab.api.lang.kim.KimModel.class::cast).findFirst().orElseThrow();
      if(index>=authored.getDependencies().size())return false;
      parsed=authored.getDependencies().get(index);
      if(!Objects.equals(parsed.getUrn(),dependency.getUrn()) || parsed.isOptional()!=dependency.isOptional())
        throw new org.integratedmodelling.klab.api.exceptions.KlabValidationException("Selected dependency differs from saved authored occurrence");
      version=saved.getLastUpdateTimestamp();fingerprint=org.integratedmodelling.common.utils.Utils.Json.asString(saved);
    } else parsed=resources.declareObservable(dependency.getUrn());
    if(!(parsed instanceof org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl implementation))return false;
    var source=new org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl(implementation);
    var prepared=org.integratedmodelling.klab.api.lang.kim.DependencyExpansion.prepare(source,resources::declareConcept);
    if(prepared.requests().isEmpty())return false;
    if(!automaticClassifiersAllowed || graph.hasPartialOutputAncestor())throw new ClassifierPhaseDeferred();
    if(namespace==null || namespace.isBlank())throw new org.integratedmodelling.klab.api.exceptions.KlabValidationException("Classifier dependency continuation requires exact saved model source");
    if(scope.getContextObservation()==null || scope.getContextObservation().getId()<=0
        || scope.getContextObservation().getObservable().getSemantics().isCollective())
      throw new org.integratedmodelling.klab.api.exceptions.KlabValidationException("Classifier dependency requires an already observed singular native context");
    var reasoner=scope.getService(Reasoner.class);
    var caps=reasoner.capabilities(scope);
    if(!(caps instanceof Reasoner.Capabilities commitment) || commitment.getKnowledgeRevision()<0)
      throw new org.integratedmodelling.klab.api.exceptions.KlabValidationException("Classifier dependency requires an accepted semantic commitment");
    var operations=new LinkedHashMap<List<String>,OperationTarget>();
    var plans=new ArrayList<ResolutionGraph>();
    String recursionKey=model.getUrn()+"/"+index+"/"+scope.getContextObservation().getId()+"/"+scale.encode();
    if(activeClassifierDependencies.size()>=64 || !activeClassifierDependencies.add(recursionKey))
      throw new org.integratedmodelling.klab.api.exceptions.KlabValidationException("Recursive classifier producer dependency: "+recursionKey);
    try {
    for(var request:prepared.requests()) {
      var path=request.identity().path();
      boolean directAttribute=dependency.is(org.integratedmodelling.klab.api.knowledge.SemanticType.QUALITY) && path.size()==3 && path.get(0).equals("semantics") && path.get(1).equals("traits")
          && request.declaration().is(org.integratedmodelling.klab.api.knowledge.SemanticType.ATTRIBUTE);
      boolean bearerAttribute=path.size()==4 && path.get(0).equals("semantics") && path.get(1).equals("inherent")
          && (path.get(2).equals("traits") || path.get(2).equals("roles"));
      if(!directAttribute && !bearerAttribute)
        throw new org.integratedmodelling.klab.api.exceptions.KlabValidationException("Unsupported classifier dependency occurrence "+path);
      var bearer=request.originalObservable().getSemantics().getInherent();
      if(bearer==null || bearer.getName()==null || bearer.getObservable()!=null)
        throw new org.integratedmodelling.klab.api.exceptions.KlabValidationException("Classifier dependency requires a named native bearer");
      var member=resources.declareConcept(bearer.getName());
      if(!(member instanceof org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl memberSyntax))
        throw new org.integratedmodelling.klab.api.exceptions.KlabValidationException("Missing native member syntax");
      var collective=memberSyntax.mapSyntax(node->{});collective.setCollective(true);collective.finalizeDefinition();
      var predicate=(org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl)request.declaration();
      var directive=predicate.mapSyntax(node->{});directive.setInherent(collective);directive.finalizeDefinition();
      var syntax=new org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl();syntax.setSemantics(directive);
      var declared=reasoner.declareObservable(syntax);
      var plan=resolveOperation(declared,scale,graph,scope,dependency);
      if(plan.isEmpty() || !(plan.target() instanceof OperationTarget target))
        throw new org.integratedmodelling.klab.api.exceptions.KlabValidationException("Required concretizing classifier has no plan");
      operations.put(path,target);plans.add(plan);
    }
    for(var plan:plans)graph.merge(plan);
    graph.classifierDependencies.computeIfAbsent(model,ignored->new ArrayList<>()).add(new DeferredDependency(
        source,index,dependency.getStatedName(),namespace,version,fingerprint,commitment.getKnowledgeRevision(),
        commitment.getServiceId(),Map.copyOf(operations)));
    return true;
    } finally { activeClassifierDependencies.remove(recursionKey); }
  }

  private void scheduleRejected(Model model,
      org.integratedmodelling.klab.api.digitaltwin.OccurrenceNegotiation.Request request,
      RuntimeException failure, ContextScope scope) {
    var message = "Rejected occurrence schedule in model " + model.getUrn()
        + (request == null ? "" : " for " + request.model() + "/" + request.dependency())
        + ": " + failure.getMessage();
    var diagnostic = new Notification();
    diagnostic.setMessage(message);
    diagnostic.setLevel(org.integratedmodelling.klab.common.data.Level.WARNING);
    notifications.add(diagnostic);
    scope.warn(message);
  }

  private Pair<ContextScope, Scale> contextualizeScope(
      ContextScope originalScope,
      Observable observable,
      Scale originalScale,
      ResolutionGraph resolutionSoFar) {
    Scale scale = originalScale;
    ContextScope scope = originalScope;

    Observation context = scope.getContextObservation();
    if (context == null && !SemanticType.isSubstantial(observable.getSemantics().getType())) {
      scope.error(
          "Cannot resolve a dependent without a context substantial observation: "
              + observable.getUrn());
    }

    return Pair.of(
        scope.withResolutionConstraints(
            ResolutionConstraint.of(ResolutionConstraint.Type.Geometry, scale.as(Geometry.class))),
        scale);
  }

  private ResolutionGraph resolve(
      Observable observable, Scale scaleToCover, ResolutionGraph graph, ContextScope scope) {

    var contextualizedScope = contextualizeScope(scope, observable, scaleToCover, graph);

    if (observable.getContextualization() != null
        && observable.getContextualization().modifiesExistingObservations()) {
      return resolveOperation(
          observable, contextualizedScope.getSecond(), graph, contextualizedScope.getFirst());
    }
    var query = query(observable, contextualizedScope.getSecond(), contextualizedScope.getFirst());
    if (query.hasCoverage() && query.coverage().isComplete()) {
      ScheduleNegotiationSupport.checkReuse(query.reference(), graph.scheduleRequest, contextualizedScope.getSecond().getTime(), contextualizedScope.getFirst());
      return graph.createReference(observable, query.reference());
    }

    var geometry = contextualizedScope.getSecond().as(Geometry.class);
    if (query.hasCoverage()) {
      var missing = missingScale(contextualizedScope.getSecond(), query.coveredScale());
      if (missing != null && !missing.isEmpty()) geometry = missing.as(Geometry.class);
    }

    // create the observation in unresolved state, restricted to the uncovered geometry
    var observation = requireObservation(observable, contextualizedScope.getFirst(), geometry);

    if (observation.isEmpty()) {
      return ResolutionGraph.empty();
    } else if (observation.getId() > 0) {
      ScheduleNegotiationSupport.checkReuse(observation, graph.scheduleRequest, contextualizedScope.getSecond().getTime(), contextualizedScope.getFirst());
      return graph.createReference(observable, observation);
    }

    // resolve the observation in the scope
    return resolve(observation, contextualizedScope.getFirst(), graph, query);
  }

  /** Query the runtime without changing its state and normalize the result for resolution. */
  QueryMatch query(Observable observable, Scale requestedScale, ContextScope scope) {

    if (observable.is(SemanticType.QUALITY) || observable.is(SemanticType.PROCESS)) {
      var probe = new Observation.NaiveBuilder(observable, scope);
      probe.geometry(requestedScale.as(Geometry.class));
      var existing = scope.getObservation(probe.make());
      var coverage = org.integratedmodelling.klab.runtime.ObserverReuseSupport.coverage(existing, requestedScale);
      if (existing == null || existing.isEmpty() || existing.getId() <= 0 || coverage == null || coverage.isEmpty())
        return new QueryMatch(existing, null, requestedScale, null, Coverage.create(requestedScale, 0));
      var covered = existing.getGeometry() == requestedScale ? requestedScale
          : GeometryRepository.INSTANCE.getMerged(requestedScale,
              GeometryRepository.INSTANCE.scale(existing.getGeometry(), scope), LogicalConnector.INTERSECTION, Scale.class);
      if (covered == null || covered.isEmpty())
        return new QueryMatch(existing, null, requestedScale, null, Coverage.create(requestedScale, 0));
      org.integratedmodelling.klab.runtime.storage.StorageReads.validateSpatialReuse(existing, requestedScale, scope);
      return new QueryMatch(existing, existing, requestedScale, covered, coverage,
          org.integratedmodelling.klab.runtime.ObserverResolution.deferred(existing, observable, scope));
    }

    if (!(SemanticType.isEnumerableSubstantial(observable.getSemantics().getType())
        && observable.getSemantics().isCollective())) {
      return new QueryMatch(null, null, requestedScale, null, Coverage.create(requestedScale, 0.0));
    }

    var query = scope.observation(observable).geometry(requestedScale.as(Geometry.class)).query();
    if (activeCollectiveReduction != null && java.util.Objects.equals(activeCollectiveReduction.operand(), observable.getUrn()))
      query.metadata(org.integratedmodelling.klab.api.knowledge.CollectiveReductionBinding.DATA_KEY,
          org.integratedmodelling.klab.utilities.Utils.Json.asString(activeCollectiveReduction));
    var result = query.submit().join();
    if (result == null || result.isEmpty() || result.getGeometry() == null) {
      return new QueryMatch(
          result, null, requestedScale, null, Coverage.create(requestedScale, 0.0));
    }

    var coveredScale = GeometryRepository.INSTANCE.scale(result.getGeometry(), scope);
    var coverage = Coverage.create(requestedScale, 0.0).merge(coveredScale, LogicalConnector.UNION);
    return new QueryMatch(result, result, requestedScale, coveredScale, coverage);
  }

  private Scale missingScale(Scale requested, Scale covered) {
    var missing =
        GeometryRepository.INSTANCE.getMerged(
            requested, covered, LogicalConnector.EXCLUSION, Scale.class);
    if (missing == null) {
      return requested;
    }

    /*
     * A Scale is a Cartesian product of extents, so some complements (and extents that do not yet
     * implement EXCLUSION) cannot be represented by one Scale. Never under-resolve in that case:
     * retain the reference but let the new actuator cover the full request.
     */
    var coveredProportion =
        Coverage.create(requested, 0.0).merge(covered, LogicalConnector.UNION).getCoverage();
    var missingProportion =
        Coverage.create(requested, 0.0).merge(missing, LogicalConnector.UNION).getCoverage();
    return Math.abs((1.0 - coveredProportion) - missingProportion) <= 1.0e-6 ? missing : requested;
  }

  /**
   * Query all the resource servers available in the scope to find the models that can observe the
   * passed observable. The result should be ranked in decreasing order of fit to the context and
   * the RESOLUTION_SCORE ranking should be in their metadata.
   *
   * @param observable
   * @param scope
   * @return
   */
  public List<Model> queryModels(
      Observable observable, Concept contextObservable, ContextScope scope, Scale scale) {

    var prioritizer =
        new PrioritizerImpl(
            scope,
            scale,
            resolver.getServiceConfiguration().getRankingStrategy(),
            observable,
            contextObservable);

    var resources = scope.getService(ResourcesService.class);
    ResourceSet models =
        resources
            .query(
                Parameters.create(
                    "observable",
                    observable,
                    "geometry",
                    GeometryRepository.INSTANCE.geometry(scale),
                    "contextObservable",
                    contextObservable,
                    "resolutionConstraints",
                    scope.getResolutionConstraints(),
                    org.integratedmodelling.klab.api.services.resources.ModelDiscoveryMode.PARAMETER,
                    org.integratedmodelling.klab.api.services.resources.ModelDiscoveryMode.PREPARABLE.name()),
                KlabAsset.KnowledgeClass.MODEL,
                ResourceSet.class,
                scope)
            .stream()
            .reduce(ResourceSet.empty(), Utils.Resources::merge);
    // FIXME the notifications from the resource set must end up in the resolution output
    var ret = new ArrayList<>(resolver.ingestResources(models, scope, Model.class, true));
    if (org.integratedmodelling.common.utils.Utils.Notifications.hasErrors(
        models.getNotifications())) throw new KlabIllegalStateException("Model discovery failed");
    modelQueries++;
    matchedModels += ret.size();
    // Catalog PREPARABLE results have no executable/ranking grant. Preserve the original
    // source snapshot while the existing Runtime path prepares components/resources under
    // its normal load policy, then require installed admission BEFORE semantic distance.
    ret.removeIf(model -> !prepareCandidate(model,observable,scope));
    ret.removeIf(model -> {
      var prepared=preparedCandidate(model,scope);
      if(prepared==null || !org.integratedmodelling.klab.runtime.language.ModelValidationGate.matches(prepared.receipt(),model,scope))return true;
      if(prioritizer.semanticDistance(model)==Integer.MAX_VALUE)return true;
      return !org.integratedmodelling.klab.runtime.language.ModelValidationGate.sameSnapshot(prepared.receipt(),prioritizer.admission(model));
    });
    for (var model : ret) {
      var receipt=prioritizer.admission(model);
      if (receipt!=null) modelAdmissions.put(new ModelIdentity(model),receipt);
    }
    prioritizer.prepare(ret);
    ret.sort(prioritizer);
    return ret;
  }

  /**
   * Register a provisional observation for the geometry that remains after the runtime query. The
   * query itself is performed before this method so registration cannot hide partial coverage by
   * returning a semantic match whose geometry is insufficient.
   *
   * @param observable
   * @param scope
   * @return a non-null observation
   */
  private Observation requireObservation(
      Observable observable, ContextScope scope, Geometry geometry) {

    /**
     * Validate and register an observation with a unique ID with the digital twin. If the ID is
     * negative, the observation will be resolved.
     */
    var candidateScope = observable.getSemantics().isCollective() ? scope.within(null) : scope;
    var ret = candidateScope.observation(observable).geometry(geometry).register();
    if (ret.getId() > 0 || ret.isEmpty()) {
      return ret;
    }

    resolutionCache.addVertex(ret);
    var parent =
        candidateScope.getContextObservation() == null
            ? RuntimeAsset.CONTEXT_ASSET
            : scope.getContextObservation();
    resolutionCache.addVertex(parent);
    resolutionCache.addEdge(parent, ret);

    return ret;
  }
}
