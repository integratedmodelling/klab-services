package org.integratedmodelling.klab.services.runtime;

import com.google.common.collect.ImmutableList;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.data.mediation.classification.LookupTable;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.ScalarComputation;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.data.ClientResourceContextualizer;
import org.integratedmodelling.klab.data.ServiceResourceContextualizer;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.ojalgo.concurrent.Parallelism;

/**
 * Object that follows the execution of the actuators. Each run produces a new context that is the
 * one for the next execution.
 */
public class ExecutionSequence {

  private final RuntimeService runtimeService;
  private final ServiceContextScope scope;
  private final DigitalTwin digitalTwin;
  private final ComponentRegistry componentRegistry;
  private final double resolvedCoverage;
  private final KnowledgeGraph.Operation contextualization;
  private final Dataflow dataflow;
  private List<List<ExecutorOperation>> sequence = new ArrayList<>();
  private boolean empty;
  // the context for the next operation. Starts at the observation and doesn't normally change but
  // implementations
  // may change it when they return a non-null, non-POD object.
  //  // TODO check if this should be a RuntimeAsset or even an Observation.
  //  private Object currentExecutionContext;
  private Map<Actuator, KnowledgeGraph.Operation> operations = new HashMap<>();
  private Throwable cause;

  public ExecutionSequence(
      RuntimeService runtimeService,
      KnowledgeGraph.Operation contextualization,
      Dataflow dataflow,
      ComponentRegistry componentRegistry,
      ServiceContextScope contextScope) {
    this.runtimeService = runtimeService;
    this.scope = contextScope;
    this.contextualization = contextualization;
    this.resolvedCoverage =
        dataflow instanceof DataflowImpl dataflow1 ? dataflow1.getResolvedCoverage() : 1.0;
    this.componentRegistry = componentRegistry;
    this.dataflow = dataflow;
    this.digitalTwin = contextScope.getDigitalTwin();
  }

  /**
   * HERE - instead of compiling the whole thing for execution, should just build the order of
   * contextualization and insert it in the DT as triggers relationships between either actuators or
   * observations. As that is done, an actutor -> ExecutorOperation map can be filled. After that,
   * an initialization event should be sent to the scheduler - which should lookup (lazily as we
   * could be resuming after a crash) the operation and run them in triggering order, adding any
   * other dependency to the KG as operations are triggered and building the relevant schedules to
   * account for temporal events and any others. Obs should subscribe to the "fluxes" they will
   * react to, using a Replay sink so that any new obs will receive the replayed events when they
   * intercept them.
   *
   * @param rootActuator
   * @return
   */
  public boolean compile(Actuator rootActuator) {

    var pairs = sortComputation(rootActuator);
    List<ExecutorOperation> current = null;
    int currentGroup = -1;
    for (var pair : pairs) {
      if (currentGroup != pair.getSecond()) {
        if (current != null) {
          sequence.add(current);
        }
        current = new ArrayList<>();
      }
      currentGroup = pair.getSecond();
      var operation = new ExecutorOperation(pair.getFirst());
      if (!operation.isOperational()) {
        return false;
      }
      current.add(operation);
    }

    if (current != null) {
      sequence.add(current);
      return true;
    }

    return false;
  }

  /**
   * Submit the operations to the scheduler, then submit the geometry so that the relevant temporal
   * events can be generated if necessary.
   *
   * @return
   */
  public void submit() {
    for (var operationGroup : sequence) {
      for (var operation : operationGroup) {
        scope
            .getDigitalTwin()
            .getScheduler()
            .registerExecutor(operation.observation, operation::run);
      }
    }
  }

  /** One operation per observation. Successful execution will update the observation in the DT. */
  class ExecutorOperation {

    private final long id;
    private final Observation observation;
    // TODO executors get cached within the DT, here we should just ensure they can be produced. A
    //  list should be indexed by observation URN, empty for empty dataflows.
    protected List<Function<Geometry, Boolean>> executors = new ArrayList<>();
    private boolean scalar;
    private boolean operational;
    private KnowledgeGraph.Operation operation;

    public ExecutorOperation(Actuator actuator) {
      this.id = actuator.getId();
      this.operation = operations.get(actuator);
      this.observation = scope.getObservation(this.id);
      instrumentObservation(this.observation, actuator);
      this.operational = compile(actuator);
    }

    /**
     * Determine fill curve and split strategy for quality observations. The actuator may contain
     * contextualizers whose prototype mandates the strategy. Otherwise, there may be @fillcurve
     * and @split annotations in the actuator (taken from the model or observable), Failing these,
     * locally configured defaults take precedence. The inferred instructions will be passed on to
     * the storage when asking for buffers.
     *
     * @param actuator
     */
    private void instrumentObservation(Observation observation, Actuator actuator) {
      //      Utils.Annotations.getAnnotation()
      if (observation.getObservable().is(SemanticType.QUALITY)) {}
    }

    private boolean compile(Actuator actuator) {

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
                executors.add(geometry -> contextualizer.contextualize(observation, scope));
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
                executors.add(geometry -> contextualizer.contextualize(observation, scope));
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

          var runArguments =
              ComponentRegistry.matchArguments(
                  componentRegistry.implementation(currentDescriptor).method,
                  resource,
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
                  scope);

          if (runArguments == null) {
            return false;
          }

          if (currentDescriptor.staticMethod) {
            Extensions.FunctionDescriptor finalDescriptor1 = currentDescriptor;
            executors.add(
                geometry -> {
                  try {
                    var context =
                        componentRegistry
                            .implementation(finalDescriptor1)
                            .method
                            .invoke(null, runArguments.toArray());
                    //                      setExecutionContext(context == null ? observation :
                    // context);
                    return true;
                  } catch (Exception e) {
                    cause = e;
                    scope.error(e /* TODO tracing parameters */);
                  }
                  return true;
                });
          } else if (componentRegistry.implementation(currentDescriptor).mainClassInstance
              != null) {
            Extensions.FunctionDescriptor finalDescriptor = currentDescriptor;
            executors.add(
                geometry -> {
                  try {
                    var context =
                        componentRegistry
                            .implementation(finalDescriptor)
                            .method
                            .invoke(
                                componentRegistry.implementation(finalDescriptor).mainClassInstance,
                                runArguments.toArray());
                    //                      setExecutionContext(context == null ? observation :
                    // context);
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
            geometry -> {
              try {
                return scalarMapper.execute(geometry);
              } catch (Throwable e) {
                cause = e;
                scope.error(e /* TODO tracing parameters */);
                throw e;
              }
            });
      }

      return true;
    }

    public boolean run(Geometry geometry) {

      // TODO compile info for provenance, to be added to the KG at finalization
      long start = System.currentTimeMillis();
      for (var executor : executors) {
        if (!executor.apply(geometry)) {
          if (operation != null) {
            operation.fail(scope, observation, cause);
          }
          return false;
        }
      }

      long time = System.currentTimeMillis() - start;

      if (operation != null) {
        operation.success(scope, observation, resolvedCoverage);
        // FIXME move this to the scheduler. The operation in here is STILL resolution
        scope.finalizeObservation(observation, operation, true);
      }

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
    var dependencyGraph = computeActuatorOrder(rootActuator);
    for (var nextActuator : ImmutableList.copyOf(new TopologicalOrderIterator<>(dependencyGraph))) {
      if (nextActuator.getActuatorType() != Actuator.Type.REFERENCE) {
        ret.add(
            Pair.of(
                nextActuator,
                (executionOrder =
                    checkExecutionOrder(executionOrder, nextActuator, dependencyGraph, group))));
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
      Graph<Actuator, DefaultEdge> dependencyGraph,
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

  private Graph<Actuator, DefaultEdge> computeActuatorOrder(Actuator rootActuator) {
    Graph<Actuator, DefaultEdge> dependencyGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
    Map<Long, Actuator> cache = new HashMap<>();
    loadGraph(rootActuator, dependencyGraph, cache, this.contextualization);
    // keep the actuators that do nothing so we can tag their observation as resolved
    return dependencyGraph;
  }

  private void loadGraph(
      Actuator rootActuator,
      Graph<Actuator, DefaultEdge> dependencyGraph,
      Map<Long, Actuator> cache,
      KnowledgeGraph.Operation contextualization) {

    var childContextualization =
        contextualization.createChild(
            rootActuator, "Contextualization of " + rootActuator, Activity.Type.CONTEXTUALIZATION);
    operations.put(rootActuator, childContextualization);

    cache.put(rootActuator.getId(), rootActuator);
    dependencyGraph.addVertex(rootActuator);
    for (Actuator child : rootActuator.getChildren()) {
      if (child.getActuatorType() == Actuator.Type.REFERENCE) {
        // may be satisfied by a previous resolution
        if (cache.containsKey(child.getId())) {
          dependencyGraph.addEdge(cache.get(child.getId()), rootActuator);
        }
      } else {
        loadGraph(child, dependencyGraph, cache, childContextualization);
        dependencyGraph.addEdge(child, rootActuator);
      }
    }
  }

  public Throwable getCause() {
    return cause;
  }
}
