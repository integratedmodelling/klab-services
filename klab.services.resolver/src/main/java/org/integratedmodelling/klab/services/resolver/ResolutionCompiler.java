package org.integratedmodelling.klab.services.resolver;

import java.util.ArrayList;
import java.util.List;
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
      Coverage coverage) {

    boolean hasCoverage() {
      return result != null && !result.isEmpty() && !coverage.isEmpty();
    }
  }

  private Graph<RuntimeAsset, DefaultEdge> resolutionCache =
      new DefaultDirectedGraph<>(DefaultEdge.class);
  private final ResolverService resolver;
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
    var contextGraph = ResolverService.getResolutionGraph(scope);
    if (contextGraph == null) {
      throw new KlabIllegalStateException(
          "Resolver context was not declared before starting observation resolution");
    }
    return resolve(observation, scope, contextGraph.createAttempt());
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

    return resolve(observation, scope, parentGraph, null);
  }

  private ResolutionGraph resolve(
      Observation observation,
      ContextScope scope,
      ResolutionGraph parentGraph,
      QueryMatch suppliedQuery) {

    if (observation.getId() > 0) {
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
      var ret = parentGraph.createChild(observation, scale);
      ret.addReference(query.reference(), query.coverage());
      return ret;
    }

    var scaleToResolve = scale;
    if (query.hasCoverage()) {
      scaleToResolve = missingScale(scale, query.coveredScale());
      if (scaleToResolve == null || scaleToResolve.isEmpty()) {
        var ret = parentGraph.createChild(observation, scale);
        ret.addReference(query.reference(), query.coverage());
        return ret;
      }
    }
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
    if (query.hasCoverage()) {
      ret.addReference(query.reference(), query.coverage());
    }
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

      // FIXME why? - this seems wrong, may be forgetting sth
      //      var cScope = scope;
      //      if (observation.getObservable().is(SemanticType.COUNTABLE)
      //          && !observation.getObservable().getSemantics().isCollective()) {
      //        cScope = cScope.within(observation);
      //      }

      var strategyResolution = resolve(strategy, scaleToResolve, ret, /* cScope */ scope);
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
          ret.merge(observableResolution, operation.getId());
        }
        case OBSERVE -> {
          boolean complete = false;
          List<ResolutionGraph> modelGraphs = new ArrayList<>();
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
              ret.merge(modelGraph, operation.getTransformationTarget());
            }
          } else {
            return ResolutionGraph.empty();
          }
        }
        case APPLY -> {
          if (operation.getType() == KimObservationStrategy.Operation.Type.APPLY
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

    updateServiceInfo(requirements, ret, scope);
    ret.setDependencies(Utils.Resources.merge(requirements, ret.getDependencies()));

    /*
    resolve all dependencies
     */
    //    boolean complete = model.getDependencies().isEmpty();
    List<Pair<ResolutionGraph, String>> modelGraphs = new ArrayList<>();
    for (var dependency : model.getDependencies()) {

      var dependencyResolution = resolve(dependency, scaleToCover, ret, scope);

      // FIXME if the dep is on a collective, the geom of the obs will be the observer's and this
      //  will be irrelevant 00 FIXME HERE - dependencyResolution.targetCoverage merges to
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

    var query = query(observable, contextualizedScope.getSecond(), contextualizedScope.getFirst());
    if (query.hasCoverage() && query.coverage().isComplete()) {
      return graph.createReference(observable, query.reference());
    }

    var geometry = contextualizedScope.getSecond().as(Geometry.class);
    if (query.hasCoverage()) {
      var missing = missingScale(contextualizedScope.getSecond(), query.coveredScale());
      if (missing == null || missing.isEmpty()) {
        return graph.createReference(observable, query.reference());
      }
      geometry = missing.as(Geometry.class);
    }

    // create the observation in unresolved state, restricted to the uncovered geometry
    var observation = requireObservation(observable, contextualizedScope.getFirst(), geometry);

    if (observation.isEmpty()) {
      return ResolutionGraph.empty();
    } else if (observation.getId() > 0) {
      return graph.createReference(observable, observation);
    }

    // resolve the observation in the scope
    return resolve(observation, contextualizedScope.getFirst(), graph, query);
  }

  /** Query the runtime without changing its state and normalize the result for resolution. */
  QueryMatch query(Observable observable, Scale requestedScale, ContextScope scope) {

    if (observable.is(SemanticType.QUALITY)) {
      var probe = new Observation.NaiveBuilder(observable, scope);
      probe.geometry(requestedScale.as(Geometry.class));
      var existing = scope.getObservation(probe.make());
      return existing == null || existing.isEmpty() || existing.getId() <= 0
          ? new QueryMatch(
              existing, null, requestedScale, null, Coverage.create(requestedScale, 0.0))
          : new QueryMatch(
              existing,
              existing,
              requestedScale,
              requestedScale,
              Coverage.create(requestedScale, 1.0));
    }

    if (!(SemanticType.isEnumerableSubstantial(observable.getSemantics().getType())
        && observable.getSemantics().isCollective())) {
      return new QueryMatch(null, null, requestedScale, null, Coverage.create(requestedScale, 0.0));
    }

    var result =
        scope
            .observation(observable)
            .geometry(requestedScale.as(Geometry.class))
            .query()
            .submit()
            .join();
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
        new PrioritizerImpl(scope, scale, resolver.getServiceConfiguration().getRankingStrategy());

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
                    scope.getResolutionConstraints()),
                KlabAsset.KnowledgeClass.MODEL,
                ResourceSet.class,
                scope)
            .stream()
            .reduce(ResourceSet.empty(), Utils.Resources::merge);
    // FIXME the notifications from the resource set must end up in the resolution output
    var ret = new ArrayList<>(resolver.ingestResources(models, scope, Model.class, true));
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
    resolutionCache.addEdge(parent, ret);

    return ret;
  }
}
