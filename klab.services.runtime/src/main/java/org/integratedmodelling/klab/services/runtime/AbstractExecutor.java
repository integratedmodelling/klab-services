package org.integratedmodelling.klab.services.runtime;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.data.mediation.classification.LookupTable;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

public abstract class AbstractExecutor implements CompiledDataflow.ContextualExecutor {

  protected final ContextScope scope;
  protected final CompiledDataflow.CallDescriptors callInfo;
  protected final Observation observation;
  protected Throwable cause;
  protected Storage storage;
  protected Map<String, Observation> dependencies = new HashMap<>();

  public AbstractExecutor(
      CompiledDataflow.CallDescriptors callInfo,
      Observation observation,
      ContextScope scope,
      Map<String, Observation> dependencies) {
    this.callInfo = callInfo;
    this.observation = observation;
    this.scope = scope;
    this.dependencies = dependencies;
  }

  @Override
  public boolean execute(Scheduler.Event event, ServiceContextScope contextScope) {

    List<Callable<Object>> tasks = new ArrayList<>();

    if (observation.getObservable().is(SemanticType.QUALITY)) {

      /*
       * Guaranteed to be there by the dataflow compilation process.
       */
      var shardingStrategy = observation.getContextualizationData().getNativeShardingStrategy();

      Map<String, List<Storage.Scanner>> scanners = new HashMap<>();
      scanners.put(
          Dataflow.SELF_ID,
          new ArrayList<>(
              storage.scan(event, shardingStrategy, shardingStrategy.getScannerClass(), false)));

      var nScanners = scanners.get(Dataflow.SELF_ID).size();

      /*
       * All dependencies must be coerced into a scanner structure that
       * is compatible with the local sharding strategy.
       */
      for (var dependency : dependencies.keySet()) {

        if (dependency.equals(Dataflow.SELF_ID)
            || !dependencies.get(dependency).getObservable().is(SemanticType.QUALITY)) {
          continue;
        }

        var store =
            contextScope
                .getDigitalTwin()
                .getStorageManager()
                .getStorage(dependencies.get(dependency));
        scanners.put(
            dependency,
            new ArrayList<>(
                store.scan(event, shardingStrategy, shardingStrategy.getScannerClass(), true)));

        if (scanners.get(dependency).size() != nScanners) {
          cause =
              new KlabIllegalStateException(
                  "Incompatible sharding strategies for " + dependency + " or mediation failed");
          return false;
        }
      }

      List<Map<String, Storage.Scanner>> allScanners = new ArrayList<>();
      for (int n = 0; n < nScanners; n++) {
        var map = new HashMap<String, Storage.Scanner>();
        for (var scanner : scanners.keySet()) {
          map.put(scanner, scanners.get(scanner).get(n));
        }
        allScanners.add(map);
      }

      try {
        for (var scannerMap : allScanners) {
          tasks.add(
              () -> {
                var ok = run(event, scannerMap, contextScope);
                if (ok) {
                  storage.finalizeRun(scannerMap.get(Dataflow.SELF_ID));
                } else {
                  observation
                      .getNotifications()
                      .add(Notification.error("Contextualization of " + observation + " failed"));
                }
                return ok;
              });
        }
      } catch (Throwable t) {
        observation
            .getNotifications()
            .add(Notification.error("Error running dataflow task: " + t.getMessage(), t));
        cause = t;
        return false;
      }

    } else {
      // non-quality
      try {
        tasks.add(() -> run(event, null, contextScope));
      } catch (Throwable t) {
        observation
            .getNotifications()
            .add(Notification.error("Error running dataflow task: " + t.getMessage(), t));
        cause = t;
        return false;
      }
    }

    try (var executorService = Executors.newVirtualThreadPerTaskExecutor()) {
      var results = executorService.invokeAll(tasks);
      var ret =
          results.stream()
              .allMatch(
                  f -> f.state() == Future.State.SUCCESS && Boolean.TRUE.equals(f.resultNow()));

      if (!ret) {

        List<Throwable> exceptions = new ArrayList<>();
        for (var future : results) {
          if (future.state() == Future.State.FAILED) {
            exceptions.add(future.exceptionNow());
          }
        }
        cause =
            exceptions.isEmpty()
                ? new KlabIllegalStateException("Execution failed")
                : exceptions.getFirst();
      }
      return ret;
    } catch (Throwable t) {
      cause = t;
      return false;
    }
  }

  /**
   * Implement for the actual contextualization.
   *
   * @param event
   * @param scanners the scanners for the output (keyed by Dataflow.SELF_ID) and any quality
   *     dependencies, mapped to the same shard geometry and keyed by their local name.
   * @param scope
   * @return
   */
  protected abstract boolean run(
      Scheduler.Event event, Map<String, Storage.Scanner> scanners, ContextScope scope);

  @Override
  public Throwable getCause() {
    return cause;
  }

  /**
   * Specialized argument matcher for method using or inferring all possible arguments, aware of the
   * input/output structure of the contextualizer and capable of matching observations, scanners and
   * storage to the context by name and type.
   *
   * @param method
   * @param resource
   * @param geometry
   * @param builder
   * @param observation
   * @param observable
   * @param urn
   * @param urnParameters
   * @param serviceCall // * @param storage
   * @param expression
   * @param lookupTable
   * @param schedulerEvent
   * @param scope
   * @return
   */
  public List<Object> matchArguments(
      ServiceInfo serviceInfo,
      Method method,
      Resource resource,
      Geometry geometry,
      Data.Builder builder,
      Map<String, Storage.Scanner> scanners,
      Observation observation,
      Observable observable,
      Urn urn,
      Parameters<String> urnParameters,
      ServiceCall serviceCall,
      Expression expression,
      LookupTable lookupTable,
      Data inputData,
      Scheduler.Event schedulerEvent,
      Scope scope) {
    List<Object> runArguments = new ArrayList<>();
    DigitalTwin digitalTwin = null;
    if (scope instanceof ContextScope contextScope) {
      digitalTwin = contextScope.getDigitalTwin();
    }
    Scale scale = geometry instanceof Scale scale1 ? scale1 : null;

    // TODO HERE match inputs to scanners through the data builder

    var observationReferences = getObservationReferences();

    if (method != null) {
      for (var argument : method.getParameters()) {
        if (ContextScope.class.isAssignableFrom(argument.getType())) {
          // TODO consider wrapping into read-only delegating wrappers
          runArguments.add(scope);
        } else if (Scope.class.isAssignableFrom(argument.getType())) {
          runArguments.add(scope);
        } else if (Data.Builder.class.isAssignableFrom(argument.getType())) {
          runArguments.add(builder);
        } else if (Data.class.isAssignableFrom(argument.getType())) {
          runArguments.add(inputData);
        } else if (ServiceCall.class.isAssignableFrom(argument.getType())) {
          runArguments.add(serviceCall);
        } else if (Parameters.class.isAssignableFrom(argument.getType())) {
          runArguments.add(urnParameters);
        } else if (Storage.Shard.class.isAssignableFrom(argument.getType())
            || Storage.Scanner.class.isAssignableFrom(argument.getType())
            || Observation.class.isAssignableFrom(argument.getType())) {
          runArguments.add(
              bindObservationParameter(
                  serviceInfo,
                  argument,
                  observationReferences,
                  digitalTwin,
                  observation,
                  scanners));
        } else if (Scale.class.isAssignableFrom(argument.getType())) {
          if (scale == null && geometry != null) {
            scale = GeometryRepository.INSTANCE.scale(geometry);
          }
          runArguments.add(scale);
        } else if (Geometry.class.isAssignableFrom(argument.getType())) {
          runArguments.add(geometry);
        } else if (Observable.class.isAssignableFrom(argument.getType())) {
          runArguments.add(observable);
        } else if (Space.class.isAssignableFrom(argument.getType())) {
          if (scale == null && geometry != null) {
            scale = GeometryRepository.INSTANCE.scale(geometry);
          }
          runArguments.add(scale == null ? null : scale.getSpace());
        } else if (Time.class.isAssignableFrom(argument.getType())) {
          if (schedulerEvent != null) {
            runArguments.add(schedulerEvent.getTime());
          } else if (scale == null && geometry != null) {
            scale = GeometryRepository.INSTANCE.scale(geometry);
            runArguments.add(scale == null ? null : scale.getTime());
          } else {
            runArguments.add(null);
          }
        } else if (Scheduler.Event.class.isAssignableFrom(argument.getType())) {
          runArguments.add(schedulerEvent);
        } else if (Resource.class.isAssignableFrom(argument.getType()) && resource != null) {
          runArguments.add(resource);
        } else if (Expression.class.isAssignableFrom(argument.getType()) && expression != null) {
          runArguments.add(expression);
        } else if (Urn.class.isAssignableFrom(argument.getType()) && urn != null) {
          runArguments.add(urn);
        } else if (LookupTable.class.isAssignableFrom(argument.getType()) && lookupTable != null) {
          runArguments.add(lookupTable);
        } else {
          scope.error(
              "Cannot map argument "
                  + argument.getName()
                  + " of type "
                  + argument.getType().getCanonicalName()
                  + " to known objects in call to "
                  + method.getName());
          runArguments.add(null);
        }
      }
      return runArguments;
    }

    return null;
  }

  private Object bindObservationParameter(
      ServiceInfo serviceInfo,
      Parameter argument,
      Map<String, Boolean> observations,
      DigitalTwin digitalTwin,
      Observation observation,
      Map<String, Storage.Scanner> scanners) { // FIXME must be the map of scanners

    var self = scanners.get(Dataflow.SELF_ID);

    /*
    if self is null, we bind observation to the first observation, and we let the calling function bind others or the same again.
     */
    var input =
        serviceInfo.listInputs().stream()
            .filter(i -> i.getName().equals(argument.getName()))
            .findFirst()
            .orElse(null);

    var output =
        serviceInfo.listOutputs().stream()
            .filter(o -> o.getName().equals(argument.getName()))
            .findFirst()
            .orElse(null);

    if (input != null) {
      // observations, scanners and storages must be bound as inputs or outputs, not as normal
      // parameters.
      if (dependencies.containsKey(input.getName())) {
        return adaptObservationArgument(
            input, argument, dependencies.get(input.getName()), scanners.get(input.getName()));
      } else {
        // single input? Bind to that anyway
        if (dependencies.keySet().stream().filter(k -> !Dataflow.SELF_ID.equals(k)).count() == 1) {
          var singleInputKey =
              dependencies.keySet().stream()
                  .filter(k -> !Dataflow.SELF_ID.equals(k))
                  .findFirst()
                  .orElse(null);
          if (singleInputKey != null) {
            return adaptObservationArgument(
                input, argument, dependencies.get(singleInputKey), scanners.get(singleInputKey));
          }
        }
      }
    } else if (output != null) {

      /*
      TODO the situation where the output is not self is not possible yet.
       */
      if (dependencies.containsKey(output.getName())) {
        return adaptObservationArgument(
            output, argument, dependencies.get(output.getName()), scanners.get(output.getName()));
      }

      return adaptObservationArgument(output, argument, observation, self);
    }

    /* either an input or an output must be mapped. Otherwise this is just null. */

    return null;
  }

  /**
   * Once established that the argument should be bound to an observation or its helper objects,
   * return the adapted argument that the function argument wants.
   *
   * @param input
   * @param argument
   * @param observation
   * @param scanner
   * @return
   */
  private Object adaptObservationArgument(
      ServiceInfo.Argument input,
      Parameter argument,
      Observation observation,
      Storage.Scanner scanner) {

    if (Observation.class.isAssignableFrom(argument.getType())) {
      return observation;
    } else if (Storage.Scanner.class.isAssignableFrom(argument.getType())) {
      // TODO adapt the scanner type and UNIT if adaptable
      return scanner;
    } else if (Storage.Shard.class.isAssignableFrom(argument.getType())) {
      return scanner == null ? null : scanner.shard();
    }

    return null;
  }

  /**
   * Returns the known names of observations from the context with a flag that indicates "read only"
   * for the connected observation.
   *
   * @return
   */
  private Map<String, Boolean> getObservationReferences() {

    var ret = new HashMap<String, Boolean>();

    if (callInfo.resource() != null) {
      this.callInfo.resource().getInputs().stream()
          .map(Resource.Attribute::getName)
          .forEach(name -> ret.put(name, true));
      this.callInfo.resource().getOutputs().stream()
          .map(Resource.Attribute::getName)
          .forEach(name -> ret.put(name, false));
    } else if (callInfo.serviceInfo() != null) {
      this.callInfo.serviceInfo().serviceInfo.listInputs().stream()
          .map(ServiceInfo.Argument::getName)
          .toList()
          .forEach(name -> ret.put(name, true));
      this.callInfo.serviceInfo().serviceInfo.listOutputs().stream()
          .map(ServiceInfo.Argument::getName)
          .toList()
          .forEach(name -> ret.put(name, false));
    }

    return ret;
  }
}
