package org.integratedmodelling.klab.services.resolver;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.collections.Pair;
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

import java.util.ArrayList;
import java.util.List;

/** Obviously a placeholder for the resolver 2.0 */
public class ResolutionCompiler {

  private final ResolverService resolver;
  private double MINIMUM_WORTHWHILE_CONTRIBUTION = 0.15;

  public ResolutionCompiler(ResolverService service) {
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

    var geometry = observation.getGeometry();
    if (geometry == null) {
      if (observation.getType().isDependent() && scope.getContextObservation() != null) {
        geometry = scope.getContextObservation().getGeometry();
      }
    }

    if (observation.getObservable().getSemantics().isCollective()) {
      if (scope.getObserver() != null && scope.getObserver().getGeometry() != null) {
        geometry =
            GeometryRepository.INSTANCE.getUnion(geometry, scope.getObserver().getGeometry());
      }
    }
    return geometry;
  }

  private ResolutionGraph resolve(
      Observation observation, ContextScope scope, ResolutionGraph parentGraph) {

    if (observation.isResolved()) {
      return parentGraph;
    }

    var resolutionGeometry = getObservationGeometry(observation, scope);
    if (resolutionGeometry == null || resolutionGeometry.isEmpty()) {
      return ResolutionGraph.empty();
    }

    var scale = Scale.create(resolutionGeometry, scope);
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

          if (contextualizedScope == null) {
            return ResolutionGraph.empty();
          }

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

          if (contextualizedScope == null) {
            return ResolutionGraph.empty();
          }

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

      if (!ret.isEmpty()) {

        // add any deferrals to the compiled strategy node and return it
        for (var deferral : operation.getContextualStrategies()) {}
      }
    }

    return ret;
  }

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

    if (requirements.isEmpty()) {
      return ResolutionGraph.empty();
    }
    ret.setDependencies(Utils.Resources.merge(requirements, ret.getDependencies()));

    /*
    resolve all dependencies
     */
    boolean complete = model.getDependencies().isEmpty();
    List<Pair<ResolutionGraph, String>> modelGraphs = new ArrayList<>();
    for (var dependency : model.getDependencies()) {

      var dependencyResolution = resolve(dependency, scaleToCover, ret, scope);

      // FIXME if the dep is on a collective, the geom of the obs will be the observer's and this
      //  will be irrelevant
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
        scale = Scale.create(scope.getObserver().getGeometry());
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
    } else if (observation.isResolved()) {
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

    var prioritizer = new PrioritizerImpl(scope, scale);

    System.out.println("QUERYING MODELS FOR " + observable);

    // FIXME use virtual threads & join() to obtain a synchronized list of ResourceSet, then
    //  use a merging strategy to get models one by one in their latest release

    var resources = scope.getService(ResourcesService.class);
    ResourceSet models = resources.resolveModels(observable, scope);
    var ret = new ArrayList<Model>(resolver.ingestResources(models, scope, Model.class));
    ret.sort(prioritizer);
    return ret;
  }

  /**
   * If the runtime contains the observation, return it (in resolved or unresolved status but with a
   * valid ID). Otherwise create one in the geometry that the scope implies, with the unresolved ID,
   * without submitting it to the runtime. The unresolved ID will tell us that it's an internally
   * created, provisional observation that the runtime does not have.
   *
   * @param observable
   * @param scope
   * @return a non-null observation
   */
  private Observation requireObservation(
      Observable observable, ContextScope scope, Geometry geometry) {
    var ret = scope.getObservation(observable.getSemantics());
    return (ret == null || ret.isEmpty())
        ? DigitalTwin.createObservation(scope, observable, geometry)
        : ret;
    //      return DigitalTwin.createObservation(scope, observable, geometry);
    //      var id = scope.getService(RuntimeService.class).submit(newObs, scope);
    //      if (id >= 0) {
    //        ret = scope.getObservation(observable.getSemantics());
    //      }
    //    } else if (!ret.isResolved()) {
    //      // unresolved and previously existing
    //      return Observation.empty();
    //    }
    //
    //    /* TODO this should also happen if the inherency is incompatible with the semantics for
    // dependent
    //    observables */
    //    if (ret == null || ret.isEmpty()) {
    //      scope.error(
    //          "Cannot instantiate observation of "
    //              + observable.getUrn()
    //              + " in context "
    //              + scope.getId());
    //      return Observation.empty();
    //    }
    //
    //    return ret;
  }
}
