package org.integratedmodelling.klab.services.resolver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
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
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/** Resolution compiler for k.LAB 1.0. Contains the majority of the resolution logics. */
public class ResolutionCompiler {

  private Graph<RuntimeAsset, DefaultEdge> resolutionCache =
      new DefaultDirectedGraph<>(DefaultEdge.class);
  private final ResolverService resolver;
  private double MINIMUM_WORTHWHILE_CONTRIBUTION = 0.15;
  // FIXME this weak strategy can probably be removed, just using the objects from the graph as keys
  private AtomicLong nextResolutionId = new AtomicLong(-1);

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
    return resolve(observation, scope, ResolverService.getResolutionGraph(scope));
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
      if (observation.getType().isDependent() && scope.getContextObservation() != null) {
        geometry = scope.getContextObservation().getGeometry();
      }
    }

    if (observation.getObservable().getSemantics().isCollective()) {
      if (scope.getObserver() != null && scope.getObserver().getGeometry() != null) {
        geometry =
            GeometryRepository.INSTANCE.getUnion(
                geometry, scope.getObserver().getGeometry(), Scale.class);
      }
    }
    return geometry;
  }

  private ResolutionGraph resolve(
      Observation observation, ContextScope scope, ResolutionGraph parentGraph) {

    if (observation.getId() > 0) {
      return parentGraph;
    }

    var resolutionGeometry = getObservationGeometry(observation, scope);
    if (resolutionGeometry == null || resolutionGeometry.isEmpty()) {
      return ResolutionGraph.empty();
    }
    var scale = GeometryRepository.INSTANCE.scale(resolutionGeometry, scope);
    Coverage coverage = Coverage.create(scale, 0.0);
    for (var resolvable : parentGraph.getResolving(observation.getObservable(), scale)) {
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
    boolean complete = false;

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
    for (ObservationStrategy strategy :
        scope.getService(Reasoner.class).computeObservationStrategies(observation, scope)) {

      var cScope = scope;
      if (observation.getObservable().is(SemanticType.COUNTABLE)
          && !observation.getObservable().getSemantics().isCollective()) {
        cScope = cScope.within(observation);
      }

      var strategyResolution = resolve(strategy, scale, ret, cScope);
      var cov = strategyResolution.checkCoverage(strategyResolution);
      if (!cov.isRelevant()) {
        continue;
      }
      strategyGraphs.add(strategyResolution);
      if (cov.isComplete()) {
        complete = true;
        break;
      }
    }

    if (complete) {
      for (var strategyGraph : strategyGraphs) {
        ret.merge(strategyGraph);
      }
      return ret;
    }

    return ResolutionGraph.empty();
  }

  private ResolutionGraph resolve(
      ObservationStrategy observationStrategy,
      Scale scaleToCover,
      ResolutionGraph graph,
      ContextScope scope) {

    var ret = graph.createChild(observationStrategy, scaleToCover);

    for (var operation : observationStrategy.getOperations()) {

      switch (operation.getType()) {
        case RESOLVE -> {
          var contextualizedScope =
              contextualizeScope(scope, operation.getObservable(), scaleToCover, graph);

          var observableResolution =
              resolve(
                  operation.getObservable(),
                  contextualizedScope.getSecond(),
                  ret,
                  contextualizedScope.getFirst());
          var cov = ret.checkCoverage(observableResolution);
          if (!cov.isRelevant()) {
            return ResolutionGraph.empty();
          }
          ret.merge(observableResolution);
        }
        case OBSERVE -> {
          boolean complete = false;
          List<ResolutionGraph> modelGraphs = new ArrayList<>();
          var contextualizedScope =
              contextualizeScope(scope, operation.getObservable(), scaleToCover, graph);

          for (Model model :
              queryModels(
                  operation.getObservable(),
                  contextualizedScope.getFirst(),
                  contextualizedScope.getSecond())) {

            var modelResolution = resolve(model, scaleToCover, ret, scope);
            var cov = ret.checkCoverage(modelResolution);
            if (!cov.isRelevant()) {
              continue;
            }
            modelGraphs.add(modelResolution);
            if (cov.isComplete()) {
              complete = true;
              break;
            }
          }

          if (complete) {
            for (var modelGraph : modelGraphs) {
              ret.merge(modelGraph);
            }
          } else {
            return ResolutionGraph.empty();
          }
        }
        case APPLY -> {

          // FIXME this shouldn't happen - apply what?
          if (!operation.getContextualizables().isEmpty()) {
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

            if (requirements.isEmpty()) {
              return ResolutionGraph.empty();
            }
            ret.setDependencies(Utils.Resources.merge(ret.getDependencies(), requirements));
          }
        }
      }
    }

    return ret;
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

    var ret = graph.createChild(model, scaleToCover);

    scope =
        scope.withResolutionConstraints(
            ResolutionConstraint.of(
                ResolutionConstraint.Type.ResolutionNamespace, model.getNamespace()),
            ResolutionConstraint.of(
                ResolutionConstraint.Type.ResolutionProject, model.getProjectName()));

    // check that all contextualizers are supported
    var runtime = scope.getService(RuntimeService.class);
    ResourceSet requirements = runtime.resolveContextualizables(model.getComputation(), scope);

    // TODO filter the results to accommodate constraints w.r.t. the geometry and (possibly) the
    // semantics.

    if (requirements.isEmpty()) {
      return ResolutionGraph.empty();
    }
    ret.setDependencies(Utils.Resources.merge(requirements, ret.getDependencies()));

    /*
    resolve all dependencies
     */
    //    boolean complete = model.getDependencies().isEmpty();
    List<Pair<ResolutionGraph, String>> modelGraphs = new ArrayList<>();
    for (var dependency : model.getDependencies()) {

      var dependencyResolution = resolve(dependency, scaleToCover, ret, scope);

      // FIXME if the dep is on a collective, the geom of the obs will be the observer's and this
      //  will be irrelevant 00 FIXME HERE - dependencyResolution.targetCOverage merges to
      // insufficient the FIRST time only
      var cov = ret.checkCoverage(dependencyResolution);
      if (!cov.isRelevant()) {
        if (dependency.isOptional()) {
          continue;
        } else {
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

  private Pair<ContextScope, Scale> contextualizeScope(
      ContextScope originalScope,
      Observable observable,
      Scale originalScale,
      ResolutionGraph resolutionSoFar) {
    Scale scale = originalScale;
    ContextScope scope = originalScope;

    if (observable.getSemantics().isCollective()) {
      /*
       * Use the observer's scale if there is an observer with a significant geometry
       */
      if (scope.getObserver() != null
          && !(scope.getObserver().getGeometry().isScalar()
              || !scope.getObserver().getGeometry().isEmpty())) {
        scale = GeometryRepository.INSTANCE.scale(scope.getObserver().getGeometry());
      }
    }
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

    //  create the observation in unresolved state
    var observation =
        requireObservation(
            observable,
            contextualizedScope.getFirst(),
            contextualizedScope.getSecond().as(Geometry.class));

    if (observation.isEmpty()) {
      return ResolutionGraph.empty();
    } else if (observation.getId() > 0) {
      return graph.createReference(observable, observation);
    }

    // resolve the observation in the scope
    return resolve(observation, contextualizedScope.getFirst(), graph);
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
  public List<Model> queryModels(Observable observable, ContextScope scope, Scale scale) {

    var prioritizer =
        new PrioritizerImpl(scope, scale, resolver.getServiceConfiguration().getRankingStrategy());

    var resources = scope.getService(ResourcesService.class);
    ResourceSet models = resources.resolveModels(observable, scope);
    var ret = new ArrayList<>(resolver.ingestResources(models, scope, Model.class, true));
    ret.sort(prioritizer);
    return ret;
  }

  //  private ContextScope contextualizeScope(ContextScope scope, Observable observable, Scale
  // scale) {
  //
  //  }

  /**
   * If the runtime contains the observation, return it (in resolved or unresolved status but with a
   * valid ID). Otherwise create one in the geometry that the scope implies, with the unresolved ID,
   * without submitting it to the runtime. The unresolved ID will tell us that it's an internally
   * created, provisional observation that the runtime does not have.
   *
   * <p>TODO/FIXME: the most challenging situation isn't handled yet: the observer's context has
   * shifted and a previous collective observation no longer covers it entirely or at all, so
   * existing obs will CONTRIBUTE to the resolution and the one to be resolved needs to cover the
   * remaining geometry. The issue of instance identity is very hard to address here, and we may
   * need a strategy to swap objects (repeating any resolution that involved them) or recognize them
   * as candidates for the same instance.
   *
   * @param observable
   * @param scope
   * @return a non-null observation
   */
  private Observation requireObservation(
      Observable observable, ContextScope scope, Geometry geometry) {

    /*
     * We must check for existing observations in the local or remote knowledge graph iif:
     *
     * <p>1. the observable is a dependent and the context observation is resolved, OR 2. the
     * observation is a collective (check in root scope).
     *
     * <p>Otherwise we just keep the one we created and resolve it locally.
     */
    var candidateScope = observable.getSemantics().isCollective() ? scope.within(null) : scope;
    var ret = DigitalTwin.createObservation(scope, observable, geometry);
    var mayAlreadyExist =
        (!SemanticType.isSubstantial(observable.getSemantics().getType())
                && candidateScope.getContextObservation() != null
                && candidateScope.getContextObservation().getId() > 0)
            || observable.getSemantics().isCollective();

    if (mayAlreadyExist) {

      for (var obs :
          resolutionCache.outgoingEdgesOf(
              candidateScope.getContextObservation() == null
                  ? RuntimeAsset.CONTEXT_ASSET
                  : candidateScope.getContextObservation())) {
        if (obs instanceof Observation observation
            && observation.getObservable().getSemantics().equals(observable.getSemantics())) {
          // TODO also compare the URN of provenance in case it's an independent subject
          return observation;
        }
      }

      var existing = candidateScope.getObservation(ret);
      if (existing != null && !existing.isEmpty()) {
        return existing;
      }
    }

    resolutionCache.addVertex(ret);
    var parent =
        candidateScope.getContextObservation() == null
            ? RuntimeAsset.CONTEXT_ASSET
            : scope.getContextObservation();
    resolutionCache.addEdge(parent, ret);

    ret.setId(nextResolutionId.decrementAndGet());

    return ret;
  }
}
