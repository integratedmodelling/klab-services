package org.integratedmodelling.klab.services.runtime;

import com.google.common.collect.ImmutableList;
import java.util.*;

import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.data.mediation.classification.LookupTable;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.ScalarComputation;
import org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;

public class CompiledDataflow {

  private final RuntimeService runtimeService;
  private final ServiceContextScope scope;
  private final DigitalTwin digitalTwin;
  private final ComponentRegistry componentRegistry;
  private boolean empty;
  private Throwable cause;
  private List<Pair<Actuator, Integer>> computation = new ArrayList<>();
  private final Map<Long, ExecutorImpl> operations = new HashMap<>();
  private final Map<Long, Observation> dependentObservations = new HashMap<>();
  private Graph<Actuator, DependencyEdge> dependencyGraph;
  private Observation rootObservation;
  private Actuator rootActuator;
  private final Map<String, CallDescriptors> callInfo = new HashMap<>();

  public void createStorage() {

    for (var operation : operations.values()) {
      if (operation.observation.getObservable().is(SemanticType.QUALITY)) {
        digitalTwin
            .getStorageManager()
            .createStorage(operation.observation, operation.nativeShardingStrategy);
      }
    }
  }

  /// The executor for each step in a contextualization, each negotiating the various execution
  /// strategies and any sharding logic. These may be executed in parallel or sequentially. Sharding
  /// is always parallel and implemented inside each individual executor.
  ///
  ///  Four possible execution strategies corresponding to different subclasses:
  ///  - Call a function from a prototype
  ///  - Call a local adapter
  ///  - Invoke a remote adapter and ingest the outputs into local storage
  ///  - Distribute a scalar operation like an expression computation or a table lookup over the
  ///    geometry
  ///
  /// As the only parameter for the execution is the event, the executors must store the
  /// observation, the scope, and any target sharding strategy
  ///
  public interface ContextualExecutor {

    /**
     * Called before insertion in the compiled dataflow to ensure everything is OK and online with
     * the resources being contextualized.
     *
     * @return
     */
    boolean validate();

    ///  Main executor method
    /// @return true if successful. A `false` return value will stop contextualization.
    boolean execute(Scheduler.Event event);

    ///  If [#execute] has returned false, the cause should be here.
    Throwable getCause();
  }

  // this is used to keep info around during compilation without calling services too many times
  public record CallDescriptors(
      AdapterDescriptor adapterDescriptor,
      Extensions.FunctionDescriptor serviceInfo,
      Resource resource,
      Adapter embeddedAdapter) {

    public Data.ShardingStrategy shardingStrategy() {
      if (adapterDescriptor != null) {
        return adapterDescriptor.shardingStrategy();
      } else if (serviceInfo != null) {
        return serviceInfo.serviceInfo.getShardingStrategy();
      }
      return null;
    }
  }

  public CompiledDataflow(
      RuntimeService runtimeService,
      Observation rootObservation,
      ServiceContextScope contextScope) {
    this.runtimeService = runtimeService;
    this.scope = contextScope;
    this.rootObservation = rootObservation;
    this.componentRegistry = runtimeService.getComponentRegistry();
    this.digitalTwin = contextScope.getDigitalTwin();
  }

  /* STILL UNUSED - for independent dataflow execution */
  public CompiledDataflow(
      RuntimeService runtimeService,
      Dataflow dataflow,
      ComponentRegistry componentRegistry,
      ServiceContextScope contextScope) {
    this.runtimeService = runtimeService;
    this.scope = contextScope;
    this.componentRegistry = componentRegistry;
    this.digitalTwin = contextScope.getDigitalTwin();
  }

  // use the cache to return the call info
  private CallDescriptors getCallInfo(ServiceCall call) {
    var ret = callInfo.get(call.getUrn());
    if (ret == null) {
      var preset = RuntimeService.CoreFunctor.classify(call);
      AdapterDescriptor adapterDescriptor = null;
      Extensions.FunctionDescriptor serviceInfo = null;
      Adapter embeddedAdapter = null;
      Resource resource = null;

      if (preset != null) {
        switch (preset) {
          case URN_RESOLVER -> {
            // TODO use all services hostia
            resource =
                scope
                    .getService(ResourcesService.class)
                    .retrieveResource(call.getParameters().getList("urns", String.class), scope);
            if (resource != null) {

              embeddedAdapter =
                  componentRegistry.getAdapter(
                      resource.getAdapterType(), /* TODO adapter version! */
                      Version.ANY_VERSION,
                      scope);

              adapterDescriptor =
                  embeddedAdapter == null
                      ? scope
                          .getService(ResourcesService.class)
                          .retrieveAdapterInfo(resource.getAdapterType(), scope)
                      : embeddedAdapter.getAdapterInfo();
            }
          }
          default -> {}
        }
      } else {
        // TODO this should return a list of candidates, to match based on the parameters. For
        //  numeric there may be a float and double version.
        serviceInfo = componentRegistry.getFunctionDescriptor(call);
      }

      if (adapterDescriptor != null || serviceInfo != null) {
        ret = new CallDescriptors(adapterDescriptor, serviceInfo, resource, embeddedAdapter);
        callInfo.put(call.getUrn(), ret);
      }
    }

    return ret;
  }

  /**
   * Build the ordered dependency graph, the executors and the observations, using the sharding
   * strategies computed for each actuator, merged and mapped to the observation's native sharding.
   *
   * @param rootActuator
   * @return
   */
  public boolean compile(Actuator rootActuator) {

    this.computation = sortComputation(rootActuator);
    this.rootActuator = rootActuator;

    // build the observations as required
    requireObservations(rootActuator);

    for (var pair : this.computation) {
      // todo change this with a loop over
      var operation = new ExecutorImpl(pair.getFirst());
      if (!operation.isOperational()) {
        return false;
      }
      operations.put(pair.getFirst().getId(), operation);
    }
    return true;
  }

  private void requireObservations(Actuator rootActuator) {
    Map<Long, Observation> observationMap = new HashMap<>();
    requireObservation(rootActuator, observationMap);
    dependentObservations.putAll(observationMap);
  }

  private void requireObservation(Actuator actuator, Map<Long, Observation> observationMap) {
    // we don't add the root observation because it's added externally
    if (rootObservation.getId() != actuator.getId()
        && !observationMap.containsKey(actuator.getId())) {
      observationMap.put(actuator.getId(), requireObservation(actuator));
    }
    for (var child : actuator.getChildren()) {
      requireObservation(child, observationMap);
    }
  }

  private Observation requireObservation(Actuator actuator) {
    if (actuator.getId() < 0 && actuator instanceof ActuatorImpl actuator1) {
      var ret =
          DigitalTwin.createObservation(
              scope, actuator.getObservable(), actuator1.getResolvedGeometry(), actuator.getName());
      //      scope.registerObservation(ret);
      return ret;
    }
    return scope.getObservation(actuator.getId());
  }

  /**
   * Called after successful compilation and insertion of the root observation to add the actuators
   * and sibling observations in the execution sequence and to submit all the compiled executors to
   * the scheduler. The knowledge graph will be modified when the passed transaction is committed.
   *
   * @param transaction
   */
  public boolean store(DigitalTwinImpl.TransactionImpl transaction) {

    var knowledgeGraph = scope.getDigitalTwin().getKnowledgeGraph();

    /*
    The links to be made depend on the reciprocal nature of the root and its dependents
     */
    var rootRole = Observation.classifyRole(rootObservation);

    // TODO do we need a collective if the root observation is an individual and we're not in a
    //  collective scope? Probably - which means we have a manually instantiated collective and
    //  resolution is user-driven (activity and plan should be stored as such). If the collective
    //  is already there, we may need criteria for collision - probably based on identities - and
    //  resolution of new collectives for that observable will return the reference to the existing
    //  collective.

    /* Add all missing and unresolved observations. The unresolved ones will be automatically added. */
    dependentObservations
        .values()
        .forEach(
            dependent -> {
              transaction.add(dependent);

              if (dependent.getId() > 0) {
                // only do this for new observations
                return;
              }

              var dependentRole = Observation.classifyRole(dependent);

              if (rootRole == Observation.Role.DEPENDENT) {
                switch (dependentRole) {
                  case DEPENDENT -> {
                    transaction.link(
                        scope.getContextObservation(),
                        dependent,
                        GraphModel.Relationship.HAS_CHILD);
                    transaction.link(dependent, rootObservation, GraphModel.Relationship.AFFECTS);
                  }
                  case COLLECTIVE_SUBSTANTIAL -> {
                    // dependency to observe a quality = link to scope, add AFFECTS
                    transaction.link(
                        knowledgeGraph.scope(), dependent, GraphModel.Relationship.HAS_CHILD);
                    transaction.link(dependent, rootObservation, GraphModel.Relationship.AFFECTS);
                  }
                  default ->
                      throw new KlabInternalErrorException(
                          "unexpected relationship between dependent and dependency observation");
                }
              } else if (rootRole == Observation.Role.INDIVIDUAL_SUBSTANTIAL
                  || rootRole == Observation.Role.RELATIONAL
                  || rootRole == Observation.Role.COLLECTIVE_SUBSTANTIAL) {

                if (rootRole == Observation.Role.RELATIONAL) {
                  // TODO! Add source and target nodes
                }

                switch (dependentRole) {
                  case COLLECTIVE_SUBSTANTIAL -> {
                    // link to the scope but add the AFFECTS relationship to the root substantial
                    transaction.link(
                        knowledgeGraph.scope(), dependent, GraphModel.Relationship.HAS_CHILD);
                    transaction.link(dependent, rootObservation, GraphModel.Relationship.AFFECTS);
                  }
                  case INDIVIDUAL_SUBSTANTIAL, RELATIONAL -> {
                    if (dependentRole == Observation.Role.RELATIONAL) {
                      // TODO source and target
                    }

                    if (scope
                        .getContextObservation()
                        .getObservable()
                        .getSemantics()
                        .isCollective()) {
                      transaction.link(
                          scope.getContextObservation(),
                          dependent,
                          GraphModel.Relationship.HAS_CHILD);
                    } else {
                      // TODO - a single individual substantial outside its collective scope should
                      //  probably be added to a (possibly ad-hoc) collective upstream as discussed
                      // above.
                      transaction.link(
                          knowledgeGraph.scope(), dependent, GraphModel.Relationship.HAS_CHILD);
                    }
                  }
                  case DEPENDENT -> {
                    if (dependent.getObservable().is(SemanticType.QUALITY)
                        && dependent instanceof ObservationImpl obs) {
                      obs.setSubstantialQuality(true);
                    }

                    // AFFECTS and HAS_CHILD
                    transaction.link(rootObservation, dependent, GraphModel.Relationship.HAS_CHILD);
                    transaction.link(dependent, rootObservation, GraphModel.Relationship.AFFECTS);
                  }
                  default ->
                      throw new KlabInternalErrorException(
                          "unexpected relationship between dependent and dependency observation");
                }
              }
            });

    // now add the root to a temporary map so that we can properly set up the links
    var allObservations = new HashMap<>(dependentObservations);
    allObservations.put(rootObservation.getId(), rootObservation);

    /*
     * Establish the computation rank for the scheduler
     */
    int current = -1;
    Set<Actuator> set = null;
    List<Pair<Integer, Set<Actuator>>> order = new ArrayList<>();
    for (var ac : computation) {
      if (ac.getSecond() != current) {
        if (set != null) {
          order.add(Pair.of(current, set));
        }
        set = new HashSet<>();
        current = ac.getSecond();
      }
      set.add(ac.getFirst());
    }
    if (set != null) {
      order.add(Pair.of(current, set));
    }

    for (int i = 1; i < order.size(); i++) {
      var cGroup = order.get(i);
      var pGroup = order.get(i - 1);
      for (var act : cGroup.getSecond()) {
        for (var prv : pGroup.getSecond()) {
          var edge = dependencyGraph.getEdge(prv, act);
          if (edge != null) {
            edge.order = pGroup.getFirst();
          }
        }
      }
    }

    for (var actuator : dependencyGraph.vertexSet()) {
      if (!actuator.getComputation().isEmpty()) {
        transaction.add(actuator);
        transaction.link(
            allObservations.get(actuator.getId()),
            actuator,
            GraphModel.Relationship.CONTEXTUALIZED_BY,
            // TODO the geometry key or something else must be in the link.
            "geometry",
            ((ActuatorImpl) actuator).getResolvedGeometry());
        if (operations.containsKey(actuator.getId())) {
          transaction.resolveWith(
              allObservations.get(actuator.getId()), operations.get(actuator.getId()));
        }
      }
    }

    transaction.link(transaction.getActivity(), rootActuator, GraphModel.Relationship.HAS_PLAN);
    transaction.link(knowledgeGraph.dataflow(), rootActuator, GraphModel.Relationship.HAS_CHILD);
    transaction.link(transaction.getActivity(), rootObservation, GraphModel.Relationship.RESOLVED);

    /* Record and link actuators */
    for (var edge : dependencyGraph.edgeSet()) {
      var aSource = dependencyGraph.getEdgeSource(edge);
      var aTarget = dependencyGraph.getEdgeTarget(edge);
      var source = allObservations.get(aSource.getId());
      var target = allObservations.get(aTarget.getId());
      // TODO geometry?
      transaction.link(source, target, GraphModel.Relationship.AFFECTS, "rank", edge.order);
      // TODO the geometry should probably be here if coverage is not full
      transaction.link(aTarget, aSource, GraphModel.Relationship.HAS_CHILD);
    }

    return true;
  }

  /** One operation per observation. Successful execution will update the observation in the DT. */
  class ExecutorImpl implements DigitalTwin.Executor {

    private final Observation observation;
    protected List<ContextualExecutor> executors = new ArrayList<>();
    private boolean scalar;
    private final boolean operational;
    private final List<ServiceCall> serviceCalls = new ArrayList<>();
    private Data.ShardingStrategy nativeShardingStrategy;

    public ExecutorImpl(Actuator actuator) {
      this.observation =
          actuator.getId() == rootObservation.getId()
              ? rootObservation
              : dependentObservations.get(actuator.getId());
      this.operational = compile(actuator);
      // TODO if this is restoring an existing observation from the KG, the sharding strategy MUST
      //  be restored too
    }

    private boolean compile(Actuator actuator) {

      this.serviceCalls.addAll(actuator.getComputation());

      // TODO compile info for provenance from actuator

      ScalarComputation.Builder scalarBuilder = null;

      if (observation.getObservable().is(SemanticType.QUALITY) && nativeShardingStrategy == null) {
        for (var call : actuator.getComputation()) {
          // set to the strategy for the computation adjusted by the actuator's
          var callInfo = getCallInfo(call);
          var computationStrategy = callInfo == null ? null : callInfo.shardingStrategy();
          nativeShardingStrategy =
              nativeShardingStrategy == null
                  ? computationStrategy
                  : nativeShardingStrategy.override(computationStrategy);
        }

        if (nativeShardingStrategy != null) {
          // apply any forcings to the merged sharding strategy obtained so far. The actuator's
          // strategy (coming from model annotations) is first to override; scope is next, and
          // service
          // is last, overriding scope settings if needed. This may be revised.
          nativeShardingStrategy =
              nativeShardingStrategy.override(
                  actuator.getShardingStrategy(),
                  scope.getShardingStrategy(observation),
                  runtimeService.getDefaultShardingStrategy(observation));
        }
      }

      Map<String, Observable> knownObservations = new HashMap<>();
      // TODO fill in the observables

      /**
       * Now actually compile each computation, adapting the sharding strategy to whatever the
       * specific computation requires.
       *
       * <p>TODO the calls should be for one shard at a time. Ingestion in the DT, when needed,
       * should happen AFTER all shards have computed. If there is >1 shard, we should keep the
       * executor functions and wrap them into another one that runs the shard executors in
       * parallel.
       */
      for (var call : actuator.getComputation()) {

        var callInfo = getCallInfo(call);
        Expression expression = null;
        LookupTable lookupTable = null;

        if (callInfo == null) {
          scope.error("Cannot compile executor for " + actuator);
          return false;
        }

        var preset = RuntimeService.CoreFunctor.classify(call);
        if (preset != null) {
          switch (preset) {
            case URN_RESOLVER -> {
              if (scalarBuilder != null) {
                if (!getScalarOperator(scalarBuilder, knownObservations)) {
                  return false;
                }
                scalarBuilder = null;
              }
              ContextualExecutor executor =
                  callInfo.embeddedAdapter() != null
                      ? new LocalAdapterExecutor(callInfo, observation, knownObservations, scope)
                      : new RemoteAdapterExecutor(callInfo, observation, knownObservations, scope);
              if (!executor.validate()) {
                var cause = executor.getCause();
                if (cause != null) {
                  scope.error(cause);
                }
                return false;
              }
              executors.add(executor);
            }
            case EXPRESSION_RESOLVER, LUT_RESOLVER, CONSTANT_RESOLVER -> {
              (scalarBuilder == null
                      ? (scalarBuilder =
                          runtimeService.getComputationBuilder(observation, scope, actuator))
                      : scalarBuilder)
                  .add(call);
            }
            case DEFER_RESOLUTION -> {
              if (scalarBuilder != null) {
                if (!getScalarOperator(scalarBuilder, knownObservations)) {
                  return false;
                }
                scalarBuilder = null;
              }
              throw new KlabUnimplementedException("Deferral execution not yet implemented");
            }
          }
        } else {
          // TODO handle scalar geometry contextualizers! Must add to builder if not preset but
          //  geometry is scalar!!!
          if (scalarBuilder != null) {
            if (!getScalarOperator(scalarBuilder, knownObservations)) {
              return false;
            }
            scalarBuilder = null;
          }
          executors.add(
              new ContextualizerExecutor(
                  componentRegistry, callInfo, observation, knownObservations, call, scope));
        }
      }

      if (scalarBuilder != null) {
        if (!getScalarOperator(scalarBuilder, knownObservations)) {
          return false;
        }
      }

      return true;
    }

    private boolean getScalarOperator(
        ScalarComputation.Builder scalarBuilder, Map<String, Observable> knownObservations) {
      var executor =
          new ScalarOperationExecutor(scalarBuilder, observation, knownObservations, scope);
      if (!executor.validate()) {
        var cause = executor.getCause();
        if (cause != null) {
          scope.error(cause);
        }
        return false;
      }
      executors.add(executor);
      return true;
    }

    @Override
    public List<ServiceCall> serialized() {
      return serviceCalls;
    }

    @Override
    public boolean run(
        Geometry geometry,
        Scheduler.Event event,
        ContextScope scope) {

      createStorage();

      scope.send(
          Message.create(
              scope,
              Message.MessageType.ContextualizationStarted,
              Message.MessageClass.DigitalTwin,
              observation));

      for (var executor : executors) {
        if (!executor.execute(event)) {
          scope.send(
              Message.create(
                  scope,
                  Message.MessageType.ContextualizationAborted,
                  Message.MessageClass.DigitalTwin,
                  observation));
          return false;
        }
      }

      scope.send(
          Message.create(
              scope,
              Message.MessageType.ContextualizationSuccessful,
              Message.MessageClass.DigitalTwin,
              observation));

      return true;
    }

    public boolean isOperational() {
      return operational;
    }
  }

  public String statusLine() {
    return "Execution terminated";
  }

  public Klab.ErrorCode errorCode() {
    return Klab.ErrorCode.NO_ERROR;
  }

  public Klab.ErrorContext errorContext() {
    return Klab.ErrorContext.RUNTIME;
  }

  public boolean isEmpty() {
    return this.empty;
  }

  /**
   * Establish the order of execution and the possible parallelism. Each root actuator should be
   * sorted by dependency and appended in order to the result list along with its order of
   * execution. Successive roots can refer to the previous roots but they must be executed
   * sequentially.
   *
   * <p>The DigitalTwin is asked to register the actuator in the scope and prepare the environment
   * and state for its execution, including defining its contextualization scale in context.
   *
   * @return
   */
  private List<Pair<Actuator, Integer>> sortComputation(Actuator rootActuator) {
    List<Pair<Actuator, Integer>> ret = new ArrayList<>();
    int executionOrder = 0;
    Map<Long, Actuator> branch = new HashMap<>();
    Set<Actuator> group = new HashSet<>();
    this.dependencyGraph = computeActuatorOrder(rootActuator);
    for (var nextActuator : ImmutableList.copyOf(new TopologicalOrderIterator<>(dependencyGraph))) {
      if (nextActuator.getActuatorType() != Actuator.Type.REFERENCE) {
        var order = checkExecutionOrder(executionOrder, nextActuator, dependencyGraph, group);
        ret.add(Pair.of(nextActuator, (executionOrder = order)));
      }
    }
    return ret;
  }

  /**
   * If the actuator depends on any in the currentGroup, empty the group and increment the order;
   * otherwise, add it to the group and return the same order.
   *
   * @param executionOrder
   * @param current
   * @param dependencyGraph
   * @param currentGroup
   * @return
   */
  private int checkExecutionOrder(
      int executionOrder,
      Actuator current,
      Graph<Actuator, DependencyEdge> dependencyGraph,
      Set<Actuator> currentGroup) {
    boolean dependency = false;
    for (Actuator previous : currentGroup) {
      for (var edge : dependencyGraph.incomingEdgesOf(current)) {
        if (currentGroup.contains(dependencyGraph.getEdgeSource(edge))) {
          dependency = true;
          break;
        }
      }
    }

    if (dependency) {
      currentGroup.clear();
      return executionOrder + 1;
    }

    currentGroup.add(current);

    return executionOrder;
  }

  private static class DependencyEdge extends DefaultEdge {
    public int order;
  }

  private Graph<Actuator, DependencyEdge> computeActuatorOrder(Actuator rootActuator) {
    Graph<Actuator, DependencyEdge> dependencyGraph =
        new DefaultDirectedGraph<>(DependencyEdge.class);
    Map<Long, Actuator> cache = new HashMap<>();
    loadGraph(rootActuator, dependencyGraph, cache /*, this.contextualization*/);
    // keep the actuators that do nothing so we can tag their observation as resolved
    return dependencyGraph;
  }

  private void loadGraph(
      Actuator rootActuator,
      Graph<Actuator, DependencyEdge> dependencyGraph,
      Map<Long, Actuator> cache) {

    cache.put(rootActuator.getId(), rootActuator);
    dependencyGraph.addVertex(rootActuator);
    for (Actuator child : rootActuator.getChildren()) {
      if (child.getActuatorType() == Actuator.Type.REFERENCE) {
        // may be satisfied by a previous resolution
        if (cache.containsKey(child.getId())) {
          dependencyGraph.addEdge(cache.get(child.getId()), rootActuator);
        }
      } else {
        loadGraph(child, dependencyGraph, cache /*, childContextualization*/);
        dependencyGraph.addEdge(child, rootActuator);
      }
    }
  }

  public Throwable getCause() {
    return cause;
  }
}
