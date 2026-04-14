package org.integratedmodelling.klab.services.runtime;

import com.google.common.collect.ImmutableList;
import java.util.*;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.data.mediation.classification.LookupTable;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Notification;
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

  /**
   * One of these is created before an observation is contextualized and is available to all
   * executors to report their results. Upon completion of the contextualization, the result is
   * passed to the runtime to trigger any further resolutions (for collective observables) or to
   * clean up after failure.
   */
  public class ContextualizationScopeImpl
      implements org.integratedmodelling.klab.api.services.RuntimeService.ContextualizationScope {

    private final Observation target;
    private final Scheduler.Event event;

    public ContextualizationScopeImpl(
        Activity currentActivity, Observation observation, Scheduler.Event event) {
      this.target = observation;
      this.event = event;
    }

    @Override
    public Observation getTarget() {
      return target;
    }

    @Override
    public Scheduler.Event getEvent() {
      return event;
    }

    @Override
    public List<Observation> getOutcomes() {
      return List.of();
    }
  }

  public void createStorage() {

    for (var operation : operations.values()) {
      if (operation.observation.getObservable().is(SemanticType.QUALITY)) {
        digitalTwin.getStorageManager().createStorage(operation.observation);
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
    boolean execute(
        Scheduler.Event event,
        ServiceContextScope contextScope,
        RuntimeService.ContextualizationScope contextualizationScope);

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
      } else if (serviceInfo != null && serviceInfo.serviceInfo.getShardingStrategy() != null) {
        return serviceInfo.serviceInfo.getShardingStrategy();
      }
      return Data.ShardingStrategy.neutral();
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
  private CallDescriptors getCallInfo(ServiceCall call, Observation observation) {
    var ret = callInfo.get(call.getUrn());
    if (ret == null) {
      var preset = RuntimeService.CoreFunctor.classify(call);
      AdapterDescriptor adapterDescriptor = null;
      Extensions.FunctionDescriptor serviceInfo = null;
      Adapter embeddedAdapter = null;
      Resource resource = null;

      if (preset != null) {
        switch (preset) {
          case ADAPTER_RESOLVER -> {
            resource =
                Resource.builder(
                        call.getParameters().get("value", Observation.ContextualizationData.class))
                    .withGeometry(observation.getGeometry())
                    .build();

            if (resource != null) {

              // for now it's only embedded
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
          case URN_RESOLVER -> {

            // TODO use all services hostia
            resource =
                resolveResource(
                    call.getParameters().getList("urns", String.class), observation, scope);
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
        serviceInfo = choosePrototype(call, observation);
      }

      if (adapterDescriptor != null || serviceInfo != null) {
        ret = new CallDescriptors(adapterDescriptor, serviceInfo, resource, embeddedAdapter);
        callInfo.put(call.getUrn(), ret);
      }
    }

    return ret;
  }

  private Resource resolveResource(
      List<String> urns, Observation observation, ServiceContextScope scope) {

    if (urns.size() == 1 && scope.getData().containsKey(urns.getFirst())) {
      // namespace-local resource definition. TODO see if we need to add the observation's geometry
      // to the resource.
      return scope.getData().get(urns.getFirst(), Resource.class);
    }

    // TODO use all services hostia
    return scope.getService(ResourcesService.class).retrieveResource(urns, scope);
  }

  private Extensions.FunctionDescriptor choosePrototype(ServiceCall call, Observation observation) {
    List<Pair<Extensions.FunctionDescriptor, Integer>> candidates = new ArrayList<>();
    for (var prototype : componentRegistry.getFunctionDescriptor(call)) {
      // match parameters, types, fill curve w.r.t. observation geometry; choose best fit
      var implementation = componentRegistry.implementation(prototype);
      if (implementation != null) {
        var score =
            runtimeService.matchImplementation(
                prototype.serviceInfo, implementation, call, observation, scope);
        if (score >= 0) {
          candidates.add(Pair.of(prototype, score));
        }
      }
    }
    return candidates.isEmpty()
        ? null
        : candidates.stream()
            .sorted(Comparator.comparingInt(Pair::getSecond))
            .toList()
            .getFirst()
            .getFirst();
  }

  /**
   * Build the ordered dependency graph, the executors and the observations, using the sharding
   * strategies computed for each actuator, merged and mapped to the observation's native sharding.
   *
   * @param rootActuator
   * @return
   */
  public boolean compile(Actuator rootActuator) {

    // build the observations as required
    requireObservations(rootActuator);

    // harmonize the sharding strategies according to runtime configuration, native strategy and
    // model annotations
    harmonizeSharding(rootActuator);

    /**
     * Sort the actuator by run order and parallelism; establish links between observations and
     * local names for executors.
     */
    this.computation = sortComputation(rootActuator);
    this.rootActuator = rootActuator;

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

  /**
   * Harmonize the sharding strategy along quality dependency chains, compatibly with runtime
   * settings and pre-defined sharding.
   *
   * <p>TODO this should be implemented in a separate optimizer.
   *
   * @param rootActuator
   */
  private void harmonizeSharding(Actuator rootActuator) {
    harmonizeShardingInternal(rootActuator);
  }

  private Data.ShardingStrategy harmonizeShardingInternal(Actuator actuator) {

    Data.ShardingStrategy ret = null;

    List<Data.ShardingStrategy> priorityOrder = new ArrayList<>();
    for (var child : actuator.getChildren()) {
      priorityOrder.add(harmonizeShardingInternal(child));
    }

    if (actuator.getObservation().getObservable().is(SemanticType.QUALITY)) {

      var localDriven =
          actuator.getObservation().getContextualizationData() == null
              ? null
              : actuator.getObservation().getContextualizationData().getNativeShardingStrategy();

      if (actuator.getObservation().getId() > 0) {
        // what was done was done -- TODO assert that localDriven != null
        return localDriven;
      }

      var modelDriven = actuator.getShardingStrategy();
      var runtimeDriven =
          scope
              .getService(RuntimeService.class)
              .getDefaultShardingStrategy(actuator.getObservation(), scope);

      // add local and model strategies in increasing priority order. At least one strategy is
      // guaranteed non-null. At this stage the runtime settings only override what is NOT
      // specified.
      priorityOrder.addFirst(modelDriven); // second-tier
      priorityOrder.addFirst(localDriven); // first-tier
      priorityOrder.add(runtimeDriven); // highest priority
      var leastPriority =
          priorityOrder.stream()
              .filter(Objects::nonNull)
              .findFirst()
              .orElseThrow(
                  () ->
                      new KlabInternalErrorException(
                          "No sharding strategy could be determined for "
                              + actuator.getObservation().getObservable()));

      ret = leastPriority.mergeUndefined(priorityOrder.toArray(Data.ShardingStrategy[]::new));

      /*
       * Any definitions from downstream actuators are ignored if the runtime wants to avoid
       * parallelization. So we check out the service settings explicitly
       */
      var runtime = scope.getService(RuntimeService.class);

      // last remaining check: we have splits and don't need them (i.e., geometry w/o time
      //  has size 1). This must be done on the scale, as the geometry may be parametric and not
      //  know its actual size yet.
      // TODO space may eventually not be the only distributable extent.
      var scale = GeometryRepository.INSTANCE.scale(actuator.getObservation().getGeometry());
      var space = scale == null ? null : scale.getSpace();
      var splitsAreUnnecessary = space == null || !space.distributed();

      if (splitsAreUnnecessary
          || !runtime.settings().get(Setting.PARALLELIZE_OBSERVATIONS, Boolean.class)) {
        // force any splits to 0
        ret.setSuggestedSplits(1);
        ret.setMinSplitSize(0);
        ret.setMaxBufferSize(0);
      }

      if (ret.getDataType() == Storage.Type.DOUBLE
          && runtime.settings().get(Setting.USE_SHORT_FLOAT_REPRESENTATION, Boolean.class)) {
        // force to float
        ret.setDataType(Storage.Type.FLOAT);
      }

      if (actuator.getObservation().getContextualizationData()
          instanceof ObservationImpl.ContextualizationDataImpl cd) {
        cd.setNativeShardingStrategy(ret);
      }
    }

    return ret;
  }

  private synchronized void requireObservations(Actuator rootActuator) {
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
    if (actuator.getId() < 0) {
      var ret = actuator.getObservation();
      var transaction = scope.getCurrentTransaction();
      if (transaction != null) {
        transaction.add(ret);
      }
      var contextualizationData =
          (ObservationImpl.ContextualizationDataImpl) ret.getContextualizationData();
      if (contextualizationData == null) {
        contextualizationData = new ObservationImpl.ContextualizationDataImpl();
        ((ObservationImpl) ret).setContextualizationData(contextualizationData);
      }
      contextualizationData.setServiceUrl(runtimeService.getUrl());
      contextualizationData.setServiceId(runtimeService.serviceId());

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
    private final boolean operational;
    private final List<ServiceCall> serviceCalls = new ArrayList<>();
    private Map<String, Observation> localReferences = new HashMap<>();

    public ExecutorImpl(Actuator actuator) {
      this.observation = actuator.getObservation();
      defineLocalNames(actuator, this.localReferences);
      this.operational = compile(actuator);
    }

    private boolean compile(Actuator actuator) {

      this.serviceCalls.addAll(actuator.getComputation());

      // TODO compile info for provenance from actuator

      ScalarComputation.Builder scalarBuilder = null;

      /*
       * Now actually compile each computation, adapting the sharding strategy to whatever the
       * specific computation requires.
       *
       * <p>The calls refer to one shard at a time. Ingestion in the DT, when needed, happens AFTER
       * all shards have computed. If there are multiple shards, we must keep the executor functions
       * and wrap them into another one that runs the shard executors in parallel.
       */
      for (var call : actuator.getComputation()) {

        var callInfo = getCallInfo(call, observation);
        Expression expression = null;
        LookupTable lookupTable = null;
        var preset = RuntimeService.CoreFunctor.classify(call);

        // TODO the entire flow here is a bit backwards. Should be revised
        if (callInfo == null && preset != RuntimeService.CoreFunctor.EXPRESSION_RESOLVER) {
          scope.error("Cannot compile executor for " + actuator);
          // FIXME this doesn't get to the clients. Should add notifications to the (empty) dataflow
          //  instead.
          observation
              .getNotifications()
              .add(Notification.error("Cannot compile executor for " + actuator));
          return false;
        }

        if (preset != null) {
          switch (preset) {
            case URN_RESOLVER, ADAPTER_RESOLVER -> {
              if (scalarBuilder != null) {
                if (!getScalarOperator(scalarBuilder, localReferences)) {
                  return false;
                }
                scalarBuilder = null;
              }
              ContextualExecutor executor =
                  callInfo.embeddedAdapter() != null
                      ? new LocalAdapterExecutor(callInfo, observation, localReferences, scope)
                      : new RemoteAdapterExecutor(callInfo, observation, localReferences, scope);
              if (!executor.validate()) {
                var cause = executor.getCause();
                if (cause != null) {
                  observation.getNotifications().add(Notification.error(cause.getMessage(), cause));
                  scope.error(cause);
                } else {
                  observation
                      .getNotifications()
                      .add(Notification.error("Unknown error in adapter executor"));
                  scope.error("Unknown error in adapter executor");
                }
                return false;
              }
              executors.add(executor);
            }
            case EXPRESSION_RESOLVER, LUT_RESOLVER, CONSTANT_RESOLVER -> {
              (scalarBuilder == null
                      ? (scalarBuilder =
                          runtimeService.getComputationBuilder(
                              observation, scope, actuator, localReferences))
                      : scalarBuilder)
                  .add(call);
            }
          }
        } else {
          // TODO handle scalar geometry contextualizers! Must add to builder if not preset but
          //  geometry is scalar!!!
          if (scalarBuilder != null) {
            if (!getScalarOperator(scalarBuilder, localReferences)) {
              return false;
            }
            scalarBuilder = null;
          }
          executors.add(
              new ContextualizerExecutor(
                  componentRegistry, callInfo, observation, localReferences, call, scope));
        }
      }

      if (scalarBuilder != null) {
        if (!getScalarOperator(scalarBuilder, localReferences)) {
          return false;
        }
      }

      return true;
    }

    private void defineLocalNames(Actuator actuator, Map<String, Observation> localReferences) {
      localReferences.put(Dataflow.SELF_ID, observation);
      // only scan the direct dependents, references or not.
      for (var child : actuator.getChildren()) {
        localReferences.put(
            child.getName(),
            dependentObservations.values().stream()
                .filter(
                    observation ->
                        observation.getObservable().equals(child.getObservation().getObservable()))
                .findFirst()
                .orElseThrow(
                    () ->
                        new KlabInternalErrorException(
                            "Missing dependent observation for "
                                + child.getObservation().getObservable())));
      }
    }

    private boolean getScalarOperator(
        ScalarComputation.Builder scalarBuilder, Map<String, Observation> knownObservations) {
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
    public boolean run(Geometry geometry, Scheduler.Event event, ContextScope scope) {

      var contextScope = (ServiceContextScope) scope;

      if (observation.getObservable().is(SemanticType.QUALITY)) {
        createStorage();
      }

      var contextualization =
          Activity.of(
              Activity.Type.CONTEXTUALIZATION,
              observation,
              contextScope.getActivity(),
              "Contextualization of " + observation.getObservable());

      var executionScope = contextScope.executing(contextualization, observation);
      var contextualizationScope =
          new ContextualizationScopeImpl(contextualization, observation, event);

      Throwable failure = null;
      boolean ret = true;
      for (var executor : executors) {
        if (!executor.execute(event, executionScope, contextualizationScope)) {
          ret = false;
          failure = executor.getCause();
          break;
        }
      }

      if (ret) {
        executionScope
            .getService(RuntimeService.class)
            .submitContextualizationResult(
                contextualizationScope, executionScope, Activity.Outcome.SUCCESS);
        executionScope.commit();
      } else {
        executionScope.fail(failure);
        executionScope
            .getService(RuntimeService.class)
            .submitContextualizationResult(
                contextualizationScope, executionScope, Activity.Outcome.FAILURE);
      }

      return ret;
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
