package org.integratedmodelling.klab.services.runtime;

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
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public abstract class AbstractExecutor implements CompiledDataflow.ContextualExecutor {

  protected final ContextScope scope;
  protected final CompiledDataflow.CallDescriptors callInfo;
  protected final Observation observation;
  protected Throwable cause;
  protected Storage storage;
  protected Map<String, Observable> localNames;

  public AbstractExecutor(
      CompiledDataflow.CallDescriptors callInfo,
      Observation observation,
      ContextScope scope,
      Map<String, Observable> localNames) {
    this.callInfo = callInfo;
    this.observation = observation;
    this.scope = scope;
    this.localNames = localNames;
  }

  @Override
  public boolean execute(Scheduler.Event event, ServiceContextScope contextScope) {

    List<Callable<Object>> tasks = new ArrayList<>();

    if (observation.getObservable().is(SemanticType.QUALITY)) {
      if (storage == null) {
        storage = contextScope.getDigitalTwin().getStorageManager().getStorage(observation);
      }
      if (storage == null) {
        cause = new KlabIllegalStateException("No storage available for " + observation);
        return false;
      }
      var localShardingStrategy =
          callInfo == null ? storage.getNativeShardingStrategy() : callInfo.shardingStrategy();
      if (localShardingStrategy == null) {
        cause = new KlabIllegalStateException("No sharding strategy available for " + observation);
        return false;
      }

      try {
        for (var scanner :
            storage.scan(
                event, localShardingStrategy, localShardingStrategy.getScannerClass(), false)) {
          tasks.add(
              () -> {
                // HERE TODO FIXME catch any exceptions
                var ok = run(event, scanner, contextScope);
                if (ok) {
                  storage.finalizeRun(scanner);
                }
                return ok;
              });
        }
      } catch (Throwable t) {
        cause = t;
        return false;
      }

    } else {
      // non-quality
      tasks.add(() -> run(event, null, contextScope));
    }

    try (var executorService = Executors.newVirtualThreadPerTaskExecutor()) {
      var results = executorService.invokeAll(tasks);
      return results.stream()
          .noneMatch(objectFuture -> objectFuture.state() == Future.State.FAILED);
    } catch (Throwable t) {
      cause = t;
      return false;
    }
  }

  /**
   * Implement for the actual contextualization. NOTE: must also link the storage or
   * sub-observations to the current transaction in the scope.
   *
   * @param event
   * @param scanner
   * @param scope
   * @return
   */
  protected abstract boolean run(
      Scheduler.Event event, Storage.Scanner scanner, ContextScope scope);

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
      Method method,
      Resource resource,
      Geometry geometry,
      Data.Builder builder,
      Storage.Scanner scanner,
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
                  argument, observationReferences, digitalTwin, observation, scanner));
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
          }
          runArguments.add(scale == null ? null : scale.getTime());
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
      Parameter argument,
      Map<String, Boolean> observations,
      DigitalTwin digitalTwin,
      Observation observation,
      Storage.Scanner scanner) {

    if (observations.containsKey(argument.getName())) {

    } else {

      if (Observation.class.isAssignableFrom(argument.getType())) {
        return observation;
      } else if (Storage.Scanner.class.isAssignableFrom(argument.getType())) {
        return scanner;
      } else if (Storage.Shard.class.isAssignableFrom(argument.getType())) {
        return scanner == null ? null : scanner.shard();
      }
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
