package org.integratedmodelling.klab.services.resolver;

import org.integratedmodelling.common.knowledge.KnowledgeRepository;
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

  private ResolutionGraph resolve(
      Observation observation, ContextScope scope, ResolutionGraph parentGraph) {

    var resolutionGeometry = scope.getObservationGeometry(observation);
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

      var strategyResolution = resolve(strategy, scale, ret, scope);
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
    if (observable.isCollective()) {
      /*
       * Use the observer's scale if there is an observer
       */
      if (scope.getObserver() != null) {
        scale = Scale.create(scope.getObserver().getGeometry());
      }
    } else if (!SemanticType.isSubstantial(observable.getSemantics().getType())) {
      /*
       * must have a context in the scope (and it must be compatible for the inherency)
       */
      Observation context = resolutionSoFar.getContextObservation();
      if (context == null) {
        scope.error(
            "Cannot resolve a dependent without a context substantial observation: "
                + observable.getUrn());
        return null;
      }
      scope = scope.within(context);
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

    if (observation == null) {
      return ResolutionGraph.empty();
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
    ResourceSet models = resources.queryModels(observable, scope);
    var ret = new ArrayList<Model>(resolver.ingestResources(models, scope, Model.class));
    ret.sort(prioritizer);
    return ret;
  }

  /**
   * If the runtime contains the observation, return it (in resolved or unresolved status but with a
   * valid ID). Otherwise create one in the geometry that the scope implies, with the unresolved ID,
   * and return it for submission to the knowledge graph.
   *
   * @param observable
   * @param scope
   * @return a non-null observation
   */
  private Observation requireObservation(
      Observable observable, ContextScope scope, Geometry geometry) {
    var ret = scope.query(Observation.class, observable);
    if (ret.isEmpty()) {

      var newObs = DigitalTwin.createObservation(scope, observable, geometry);
      var id = scope.getService(RuntimeService.class).submit(newObs, scope);
      if (id >= 0) {
        ret = scope.query(Observation.class, observable);
      }
    }

    /* TODO this should also happen if the inherency is incompatible with the semantics for dependent
    observables */
    if (ret.isEmpty()) {
      scope.error(
          "Cannot instantiate observation of "
              + observable.getUrn()
              + " in context "
              + scope.getId());
      return null;
    }

    return ret.getFirst();
  }
}
