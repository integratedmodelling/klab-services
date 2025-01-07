package org.integratedmodelling.klab.services.runtime;

import com.google.common.collect.ImmutableList;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.storage.BooleanStorage;
import org.integratedmodelling.klab.runtime.storage.DoubleStorage;
import org.integratedmodelling.klab.runtime.storage.FloatStorage;
import org.integratedmodelling.klab.runtime.storage.KeyedStorage;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.ojalgo.concurrent.Parallelism;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Object that follows the execution of the actuators. Each run produces a new context that is the
 * one for the next execution.
 */
public class ExecutionSequence {

  private final ServiceContextScope scope;
  private final DigitalTwin digitalTwin;
  private final ComponentRegistry componentRegistry;
  private final double resolvedCoverage;
  private final KnowledgeGraph.Operation contextualization;
  private final Dataflow<Observation> dataflow;
  private List<List<ExecutorOperation>> sequence = new ArrayList<>();
  private boolean empty;
  // the context for the next operation. Starts at the observation and doesn't normally change but
  // implementations
  // may change it when they return a non-null, non-POD object.
  // TODO check if this should be a RuntimeAsset or even an Observation.
  private Object currentExecutionContext;
  private Map<Actuator, KnowledgeGraph.Operation> operations = new HashMap<>();
  private Throwable cause;

  public ExecutionSequence(
      KnowledgeGraph.Operation contextualization,
      Dataflow<Observation> dataflow,
      ComponentRegistry componentRegistry,
      ServiceContextScope contextScope) {
    this.scope = contextScope;
    this.contextualization = contextualization;
    this.resolvedCoverage =
        dataflow instanceof DataflowImpl dataflow1 ? dataflow1.getResolvedCoverage() : 1.0;
    this.componentRegistry = componentRegistry;
    this.dataflow = dataflow;
    this.digitalTwin = contextScope.getDigitalTwin();
  }

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
      current.add(new ExecutorOperation(pair.getFirst()));
    }

    if (current != null) {
      sequence.add(current);
      return true;
    }

    return false;
  }

  public boolean run() {

    for (var operationGroup : sequence) {
      // groups are sequential; grouped items are parallel. Empty groups are currently possible
      // although
      // they should be filtered out, but we leave them for completeness for now as they don't
      // really
      // bother anyone.
      if (operationGroup.size() == 1) {
        if (!operationGroup.getFirst().run()) {
          return false;
        }
      }

      /*
       * Run also the empty operations because execution will update the observations
       */
      if (scope.getParallelism() == Parallelism.ONE) {
        for (var operation : operationGroup) {
          if (!operation.run()) {

            return false;
          }
        }
      } else {
        try (ExecutorService taskExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
          for (var operation : operationGroup) {
            taskExecutor.execute(operation::run);
          }
          taskExecutor.shutdown();
          if (!taskExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
            return false;
          }
        } catch (InterruptedException e) {
          this.cause = e;
          scope.error(e);
        }
      }
    }

    return true;
  }

  /** One operation per observation. Successful execution will update the observation in the DT. */
  class ExecutorOperation {

    private final long id;
    private final Observation observation;
    protected List<Supplier<Boolean>> executors = new ArrayList<>();
    private boolean scalar;
    private KnowledgeGraph.Operation operation;

    public ExecutorOperation(Actuator actuator) {
      this.id = actuator.getId();
      this.operation = operations.get(actuator);
      this.observation = scope.getObservation(this.id);
      compile(actuator);
    }

    private void compile(Actuator actuator) {

      // TODO compile info for provenance from actuator

      ScalarMapper scalarMapper = null;

      // TODO separate scalar calls into groups and compile them into one assembled functor
      for (var call : actuator.getComputation()) {

        var preset = RuntimeService.CoreFunctor.classify(call);
        if (preset != null) {
          switch (preset) {
            case URN_RESOLVER -> {}
            case URN_INSTANTIATOR -> {}
            case EXPRESSION_RESOLVER -> {}
            case LUT_RESOLVER -> {}
            case CONSTANT_RESOLVER -> {}
          }
        }

        // TODO this should return a list of candidates, to match based on the parameters. For
        // numeric there
        //  should be a float and double version.
        var descriptor = componentRegistry.getFunctionDescriptor(call);
        if (descriptor.serviceInfo.getGeometry().isScalar()) {

          if (scalarMapper == null) {
            scalarMapper = new ScalarMapper(observation, digitalTwin, scope);
          }

          /**
           * Executor is a class containing all consecutive steps in a single method and calling
           * whatever mapping strategy is configured in the scope, using a different class per
           * strategy.
           */
          scalarMapper.add(call, descriptor);

          System.out.println("SCALAR");
        } else {
          if (scalarMapper != null) {
            // offload the scalar mapping to the executors
            executors.add(scalarMapper::run);
            scalarMapper = null;
          }

          var scale = Scale.create(observation.getGeometry());

          // if we're a quality, we need storage at the discretion of the StorageManager.
          Storage storage =
              observation.getObservable().is(SemanticType.QUALITY)
                  ? digitalTwin.stateStorage().getOrCreateStorage(observation, Storage.class)
                  : null;
          /*
           * Create a runnable with matched parameters and have it set the context observation
           * TODO allow multiple methods with same annotation, taking different storage
           *  implementations, enabling the storage manager to be configured for the wanted precision
           *
           * Should match arguments, check if they all match, and if not move to the next until
           * no available implementations remain.
           */
          List<Object> runArguments = new ArrayList<>();
          if (componentRegistry.implementation(descriptor).method != null) {
            for (var argument :
                componentRegistry.implementation(descriptor).method.getParameterTypes()) {
              if (ContextScope.class.isAssignableFrom(argument)) {
                // TODO consider wrapping into read-only delegating wrappers
                runArguments.add(scope);
              } else if (Scope.class.isAssignableFrom(argument)) {
                runArguments.add(scope);
              } else if (Observation.class.isAssignableFrom(argument)) {
                runArguments.add(observation);
              } else if (ServiceCall.class.isAssignableFrom(argument)) {
                runArguments.add(call);
              } else if (Parameters.class.isAssignableFrom(argument)) {
                runArguments.add(call.getParameters());
              } else if (DoubleStorage.class.isAssignableFrom(argument)) {
                storage =
                    digitalTwin
                        .stateStorage()
                        .promoteStorage(observation, storage, DoubleStorage.class);
                runArguments.add(storage);
              } else if (FloatStorage.class.isAssignableFrom(argument)) {
                storage =
                    digitalTwin
                        .stateStorage()
                        .promoteStorage(observation, storage, DoubleStorage.class);
                runArguments.add(storage);
              } else if (BooleanStorage.class.isAssignableFrom(argument)) {
                storage =
                    digitalTwin
                        .stateStorage()
                        .promoteStorage(observation, storage, DoubleStorage.class);
                runArguments.add(storage);
              } else if (KeyedStorage.class.isAssignableFrom(argument)) {
                storage =
                    digitalTwin
                        .stateStorage()
                        .promoteStorage(observation, storage, DoubleStorage.class);
                runArguments.add(storage);
              } else if (Scale.class.isAssignableFrom(argument)) {
                runArguments.add(scale);
              } else if (Geometry.class.isAssignableFrom(argument)) {
                runArguments.add(scale);
              } else if (Observable.class.isAssignableFrom(argument)) {
                runArguments.add(observation.getObservable());
              } else if (Space.class.isAssignableFrom(argument)) {
                runArguments.add(scale.getSpace());
              } else if (Time.class.isAssignableFrom(argument)) {
                runArguments.add(scale.getTime());
              } else {
                scope.error(
                    "Cannot map argument of type "
                        + argument.getCanonicalName()
                        + " to known objects in call to "
                        + call.getUrn());
                runArguments.add(null);
              }
            }

            if (descriptor.staticMethod) {
              executors.add(
                  () -> {
                    try {
                      var context =
                          componentRegistry
                              .implementation(descriptor)
                              .method
                              .invoke(null, runArguments.toArray());
                      setExecutionContext(context == null ? observation : context);
                      return true;
                    } catch (Exception e) {
                      cause = e;
                      scope.error(e /* TODO tracing parameters */);
                    }
                    return true;
                  });
            } else if (componentRegistry.implementation(descriptor).mainClassInstance != null) {
              executors.add(
                  () -> {
                    try {
                      var context =
                          componentRegistry
                              .implementation(descriptor)
                              .method
                              .invoke(
                                  componentRegistry.implementation(descriptor).mainClassInstance,
                                  runArguments.toArray());
                      setExecutionContext(context == null ? observation : context);
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
      }

      if (scalarMapper != null) {
        executors.add(scalarMapper::run);
      }
    }

    public boolean run() {

      // TODO compile info for provenance, to be added to the KG at finalization
      long start = System.currentTimeMillis();
      for (var executor : executors) {
        if (!executor.get()) {
          if (operation != null) {
            operation.fail(scope, observation, cause);
          }
          return false;
        }
      }

      long time = System.currentTimeMillis() - start;

      if (operation != null) {
        operation.success(scope, observation, resolvedCoverage);
      }

      return true;
    }
  }

  private void setExecutionContext(Object returnedValue) {
    this.currentExecutionContext = returnedValue;
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

  public ExecutionSequence runActuator(Actuator actuator) {
    return this;
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
        dependencyGraph.addEdge(cache.get(child.getId()), rootActuator);
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
