package org.integratedmodelling.klab.services.runtime;

import com.google.common.collect.ImmutableList;
import java.util.*;

import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Pair;
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
import org.integratedmodelling.klab.api.lang.TriFunction;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.ScalarComputation;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.data.ClientResourceContextualizer;
import org.integratedmodelling.klab.data.ServiceResourceContextualizer;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;

/**
 * Object that follows the execution of the actuators. Each run produces a new context that is the
 * one for the next execution.
 */
public class CompiledDataflow {

  private final RuntimeService runtimeService;
  private final ServiceContextScope scope;
  private final DigitalTwin digitalTwin;
  private final ComponentRegistry componentRegistry;
  private boolean empty;
  private Throwable cause;
  private List<Pair<Actuator, Integer>> computation = new ArrayList<>();
  private Map<Long, ExecutorImpl> operations = new HashMap<>();
  private Map<Long, Observation> dependentObservations = new HashMap<>();
  private Graph<Actuator, DependencyEdge> dependencyGraph;
  private Observation rootObservation;
  private Actuator rootActuator;

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

  /**
   * Build the ordered dependency graph, the executors and the observations
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
    protected List<TriFunction<Geometry, Scheduler.Event, ContextScope, Boolean>> executors =
        new ArrayList<>();
    private boolean scalar;
    private final boolean operational;
    private final List<ServiceCall> serviceCalls = new ArrayList<>();

    public ExecutorImpl(Actuator actuator) {
      this.observation =
          actuator.getId() == rootObservation.getId()
              ? rootObservation
              : dependentObservations.get(actuator.getId());
      this.operational = compile(actuator);
    }

    private boolean compile(Actuator actuator) {

      this.serviceCalls.addAll(actuator.getComputation());

      // TODO compile info for provenance from actuator

      ScalarComputation.Builder scalarBuilder = null;

      // each service call may produce one or more function descriptors
      // separate scalar calls into groups and compile them into one assembled functor
      for (var call : actuator.getComputation()) {

        Extensions.FunctionDescriptor currentDescriptor = null;

        /*
         * These will accumulate arguments that may be required by the invoked method
         */
        Resource resource = null;
        Urn urn = null;
        Expression expression = null;
        LookupTable lookupTable = null;

        var preset = RuntimeService.CoreFunctor.classify(call);
        if (preset != null) {

          /* Turn the call into the appropriate function descriptor for the actual call, provided by
          the adapter or by the runtime. */

          switch (preset) {
            case URN_RESOLVER -> {
              var urns = call.getParameters().getList("urns", String.class);
              // TODO use all services
              resource = scope.getService(ResourcesService.class).retrieveResource(urns, scope);
              final Resource finalResource = resource;

              /*
              1. check if we have the adapter locally. If so we can use it directly.
               */
              var adapter =
                  componentRegistry.getAdapter(
                      resource.getAdapterType(), /* TODO adapter version! */
                      Version.ANY_VERSION,
                      scope);
              if (adapter != null) {

                if (adapter.hasContextualizer()) {
                  // FIXME move this within the URN_RESOLVER. Also shouldn't happen unless the
                  // adapter is local.
                  resource = adapter.contextualize(resource, observation.getGeometry(), scope);
                }

                // enqueue data extraction from adapter method
                final var contextualizer =
                    new ServiceResourceContextualizer(adapter, resource, observation);
                executors.add(
                    (geometry, event, scope) ->
                        contextualizer.contextualize(
                            // pass the operation for provenance recording
                            observation, event, scope));
                continue;

              } else {

                /*
                2. Use the adapter from the service that provides it.
                 */

                var service =
                    scope.getServices(ResourcesService.class).stream()
                        .filter(r -> r.serviceId().equals(finalResource.getServiceId()))
                        .findFirst();

                if (service.isEmpty()) {
                  throw new KlabInternalErrorException(
                      "Illegal service ID in resource " + resource.getUrn());
                }

                var adapterInfo =
                    componentRegistry.findAdapter(
                        resource.getAdapterType(), /* TODO need the version in the resource */
                        Version.ANY_VERSION);

                // TODO validate type chain
                if (adapterInfo.contextualizing()) {
                  resource =
                      service
                          .get()
                          .contextualizeResource(resource, observation.getGeometry(), scope);
                }

                // enqueue data extraction from service method
                final var contextualizer =
                    new ClientResourceContextualizer(service.get(), resource, observation);
                executors.add(
                    (geometry, event, scope) ->
                        contextualizer.contextualize(observation, event, scope));
                continue;
              }
            }
            case EXPRESSION_RESOLVER, LUT_RESOLVER, CONSTANT_RESOLVER -> {
              (scalarBuilder == null
                      ? (scalarBuilder =
                          runtimeService.getComputationBuilder(observation, scope, actuator))
                      : scalarBuilder)
                  .add(call);
              continue;
            }
            case DEFER_RESOLUTION -> {
              System.out.println("DEFER ZIOCAN");
            }
          }
        } else {
          // TODO this should return a list of candidates, to match based on the parameters. For
          //  numeric there should be a float and double version.
          currentDescriptor = componentRegistry.getFunctionDescriptor(call);
        }

        if (currentDescriptor == null) {
          scope.error("Cannot compile executor for " + actuator);
          return false;
        }

        if (scalarBuilder != null) {
          var scalarMapper = scalarBuilder.build();
          executors.add(scalarMapper::execute);
        }

        var storageAnnotation =
            Utils.Annotations.mergeAnnotations(
                "storage", currentDescriptor.serviceInfo, actuator, observation.getObservable());

        // if we're a quality, we need storage at the discretion of the StorageManager.
        Storage storage =
            observation.getObservable().is(SemanticType.QUALITY)
                ? digitalTwin.getStorageManager().getStorage(observation, storageAnnotation)
                : null;
        /*
         * Create a runnable with matched parameters and have it set the context observation
         * TODO allow multiple methods with same annotation, taking different storage
         *  implementations, enabling the storage manager to be configured for the wanted precision
         *
         * Should match arguments, check if they all match, and if not move to the next until
         * no available implementations remain.
         */
        if (componentRegistry.implementation(currentDescriptor).method != null) {

          //          var runArguments =
          //              ;
          //
          //          if (runArguments == null) {
          //            return false;
          //          }

          if (currentDescriptor.staticMethod) {
            Extensions.FunctionDescriptor finalDescriptor1 = currentDescriptor;
            var implementation = componentRegistry.implementation(currentDescriptor);
            var resource1 = resource;
            if (implementation == null) {
              return false;
            }
            executors.add(
                (geometry, event, scope) -> {
                  try {
                    var arguments =
                        ComponentRegistry.matchArguments(
                            implementation.method,
                            resource1,
                            observation.getGeometry(),
                            null,
                            observation,
                            observation.getObservable(),
                            urn,
                            call.getParameters(),
                            call,
                            storage,
                            expression,
                            lookupTable,
                            null,
                            storageAnnotation,
                            event,
                            scope);

                    if (arguments == null) {
                      return false;
                    }

                    var context =
                        componentRegistry
                            .implementation(finalDescriptor1)
                            .method
                            .invoke(null, arguments.toArray());

                    return true;
                  } catch (Exception e) {
                    cause = e;
                    scope.error(e /* TODO tracing parameters */);
                  }
                  return true;
                });
          } else if (componentRegistry.implementation(currentDescriptor).mainClassInstance
              != null) {
            var implementation = componentRegistry.implementation(currentDescriptor);
            var resource1 = resource;
            Extensions.FunctionDescriptor finalDescriptor = currentDescriptor;
            executors.add(
                (geometry, event, scope) -> {
                  try {
                    var arguments =
                        ComponentRegistry.matchArguments(
                            implementation.method,
                            resource1,
                            observation.getGeometry(),
                            null,
                            observation,
                            observation.getObservable(),
                            urn,
                            call.getParameters(),
                            call,
                            storage,
                            expression,
                            lookupTable,
                            null,
                            storageAnnotation,
                            event,
                            scope);

                    if (arguments == null) {
                      return false;
                    }

                    var context =
                        componentRegistry
                            .implementation(finalDescriptor)
                            .method
                            .invoke(
                                componentRegistry.implementation(finalDescriptor).mainClassInstance,
                                arguments.toArray());
                    return true;
                  } catch (Exception e) {
                    cause = e;
                    scope.error(e /* TODO tracing parameters */);
                  }
                  return true;
                });
          }
        }
      }

      if (scalarBuilder != null) {
        var scalarMapper = scalarBuilder.build();
        executors.add(
            (geometry, event, scope) -> {
              try {
                return scalarMapper.execute(geometry, event, scope);
              } catch (Throwable e) {
                cause = e;
                scope.error(e /* TODO tracing parameters */);
                throw e;
              }
            });
      }

      return true;
    }

    @Override
    public List<ServiceCall> serialized() {
      return serviceCalls;
    }

    @Override
    public boolean run(Geometry geometry, Scheduler.Event event, ContextScope scope) {

      scope.send(
          Message.create(
              scope,
              Message.MessageType.ContextualizationStarted,
              Message.MessageClass.DigitalTwin,
              observation));

      for (var executor : executors) {
        if (!executor.apply(geometry, event, scope)) {
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

  //  private void setExecutionContext(Object returnedValue) {
  //    this.currentExecutionContext = returnedValue;
  //  }

  public String statusLine() {
    return "Execution terminated";
  }

  public Klab.ErrorCode errorCode() {
    return Klab.ErrorCode.NO_ERROR;
  }

  public Klab.ErrorContext errorContext() {
    return Klab.ErrorContext.RUNTIME;
  }

  /**
   * TODO this should be something recognized by the notification to fully describe the context of
   * execution.
   *
   * @return
   */
  public Object statusInfo() {
    return null;
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
