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
import org.integratedmodelling.klab.api.data.StorageScan;
import org.integratedmodelling.klab.runtime.storage.StorageReads;
import org.integratedmodelling.klab.runtime.language.ScanResources;
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
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.runtime.language.ScannerAdapters;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

public abstract class AbstractExecutor implements CompiledDataflow.ContextualExecutor {

  protected final ContextScope scope;
  protected final CompiledDataflow.CallDescriptors callInfo;
  protected final Observation observation;
  protected Throwable cause;
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
  public boolean execute(
      Scheduler.Event event,
      ServiceContextScope contextScope,
      RuntimeService.ContextualizationScope contextualizationScope) {

    if (event != null && event.getType()!=Scheduler.Event.Type.INITIALIZATION
        && observation.getObservable().is(SemanticType.QUALITY)) {
      // Scalar operations use TemporalScalarExecution. Other output paths must join the write-set
      // protocol explicitly instead of mutating legacy buffers or treating missing deltas as no-op.
      throw new UnsupportedOperationException("This contextualizer has no transactional temporal output protocol");
    }

    cause = null;
    List<Callable<Object>> tasks = new ArrayList<>();
    var threadNotifications = Collections.synchronizedList(new ArrayList<Notification>());

    try (var resources = new ScanResources()) {
      if (observation.getObservable().is(SemanticType.QUALITY)) {

        /*
         * Created by the main execution sequence before calling execute()
         */
        var storage = contextScope.getDigitalTwin().getStorageManager().getStorage(observation);

        /*
         * Guaranteed to be there by the dataflow compilation process.
         */
        var shardingStrategy = observation.getContextualizationData().getNativeShardingStrategy();

        var partitions = storage.writeLayout(event);
        Map<String, List<Storage.Scanner>> scanners = new HashMap<>();
        var plans = new LinkedHashMap<String, StorageScan.Plan<? extends Storage.Scanner>>();
        var stores = new HashMap<String, Storage>();
        // Complete metadata validation for every input before acquiring writable output cursors.
        for (var entry : dependencies.entrySet()) {
          var name = entry.getKey();
          var input = entry.getValue();
          if (name.equals(Dataflow.SELF_ID) || !input.getObservable().is(SemanticType.QUALITY)) continue;
          try {
            var source = StorageReads.source(input, contextScope);
            var store = contextScope.getDigitalTwin().getStorageManager().getStorage(source);
            var layout = new Data.ShardingStrategy(shardingStrategy.getCurve(), partitions.size(), 0, 0, null);
            var plan = store.plan(StorageReads.request(input, event, layout, partitions, inputScannerClass(name), StorageReads.spatialSupport(observation)));
            if (!plan.description().partitions().equals(partitions))
              throw new IllegalStateException("Planner changed the explicit output partition identities or locations");
            plans.put(name, plan);
            stores.put(name, store);
          } catch (RuntimeException e) {
            throw new IllegalStateException("Cannot bind input " + name + " from " + input.getUrn()
                + " to output " + observation.getUrn() + " with " + shardingStrategy + ": " + e.getMessage(), e);
          }
        }
        for (var entry : plans.entrySet()) {
          var session = resources.add(stores.get(entry.getKey()).open(entry.getValue()));
          scanners.put(entry.getKey(), new ArrayList<>(session.scanners()));
        }
        validateInputBindings(scanners);
        plans.forEach((name, plan) -> StorageReads.record(contextScope, observation.getId() + ":" + name, plan.description()));
        scanners.put(Dataflow.SELF_ID, new ArrayList<>(
            storage.scan(event, shardingStrategy, shardingStrategy.getScannerClass(), false)));
        var nScanners = scanners.get(Dataflow.SELF_ID).size();
        if (nScanners != partitions.size()) throw new IllegalStateException("Native write layout changed after planning");
        for (int i = 0; i < nScanners; i++) {
          var output = scanners.get(Dataflow.SELF_ID).get(i);
          var expected = partitions.get(i);
          if (output.size() != expected.size()
              || !StorageScan.parseGeometry(output.shard().getGeometry().encode()).encode().equals(expected.geometry()))
            throw new IllegalStateException("Native write partition changed after planning: " + expected.id());
        }

        List<Map<String, Storage.Scanner>> allScanners = new ArrayList<>();
        for (int n = 0; n < nScanners; n++) {
          var map = new HashMap<String, Storage.Scanner>();
          for (var scanner : scanners.keySet()) {
            map.put(scanner, scanners.get(scanner).get(n));
          }
          allScanners.add(map);
        }

        for (var scannerMap : allScanners) {
          tasks.add(
              () -> {
                try {
                  var ok = run(event, scannerMap, contextScope, contextualizationScope);
                  if (ok) {
                    storage.finalizeRun(scannerMap.get(Dataflow.SELF_ID));
                  } else {
                    threadNotifications.add(
                        Notification.error("Contextualization of " + observation + " failed"));
                  }
                  return ok;
                } catch (Throwable t) {
                  threadNotifications.add(
                      Notification.error("Error running dataflow task: " + t.getMessage(), t));
                  cause = t;
                  return false;
                }
              });
        }

      } else if (event != null && event.getType() != Scheduler.Event.Type.INITIALIZATION
          && observation.getObservable().is(SemanticType.EVENT)) {
        var writes = contextScope.getCurrentTransaction().getTemporalWrites();
        if (writes == null) throw new IllegalStateException("Events require transactional storage");
        var scanners = new LinkedHashMap<String, List<Storage.Scanner>>();
        var requests = new LinkedHashMap<String, StorageScan.Request<Storage.Scanner>>();
        var eventSpace = StorageReads.spatialSupport(observation);
        for (var entry : dependencies.entrySet()) {
          var input = entry.getValue();
          if (entry.getKey().equals(Dataflow.SELF_ID) || !input.getObservable().is(SemanticType.QUALITY)) continue;
          var layout = eventSpace == null
              ? contextScope.getDigitalTwin().getStorageManager().createStorage(StorageReads.source(input, contextScope)).getNativeShardingStrategy()
              : new Data.ShardingStrategy(Data.FillCurve.D1_LINEAR, 1, 0, 0, null);
          requests.put(entry.getKey(), StorageReads.request(input, event, layout,
              eventSpace == null ? writes.writeLayout(StorageReads.source(input, contextScope)) : List.of(),
              Storage.Scanner.class, eventSpace));
        }
        // Open every causal snapshot before granting any writable binding.
        for (var entry : requests.entrySet()) {
          var input = dependencies.get(entry.getKey());
          var source = StorageReads.source(input, contextScope);
          var session = resources.add(writes.read(source, entry.getValue(), org.integratedmodelling.klab.api.data.TemporalWriteSet.Access.PRIOR));
          StorageReads.record(contextScope, observation.getId() + ":" + entry.getKey(), session.description());
          scanners.put(entry.getKey(), new ArrayList<>(session.scanners()));
        }
        validateInputBindings(scanners);
        if (event.getBoundary() != Scheduler.Event.Boundary.NONE) {
          var reasoner = contextScope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);
          for (var entry : requests.entrySet()) {
            var input = dependencies.get(entry.getKey());
            if (reasoner.affectedBy(input.getObservable(), observation.getObservable())) {
              var session = resources.add(writes.write(StorageReads.source(input, contextScope), entry.getValue()));
              scanners.put(entry.getKey(), new ArrayList<>(session.scanners()));
            }
          }
        }
        int count = scanners.isEmpty() ? 1 : scanners.values().iterator().next().size();
        if (scanners.values().stream().anyMatch(list -> list.size() != count))
          throw new IllegalStateException("Event bindings have incompatible partitions");
        for (int p = 0; p < count; p++) {
          var bindings = new LinkedHashMap<String, Storage.Scanner>();
          for (var entry : scanners.entrySet()) bindings.put(entry.getKey(), entry.getValue().get(p));
          tasks.add(() -> run(event, bindings, contextScope, contextualizationScope));
        }
      } else {
        // non-quality
        tasks.add(
            () -> {
              try {
                return run(event, Map.of(), contextScope, contextualizationScope);
              } catch (Throwable t) {
                threadNotifications.add(
                    Notification.error("Error running dataflow task: " + t.getMessage(), t));
                cause = t;
                return false;
              }
            });
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
          if (!exceptions.isEmpty()) {
            cause = exceptions.getFirst();
          } else if (cause == null) {
            cause =
                new KlabIllegalStateException(
                    "Execution failed: one or more executors returned false");
          }
        }

        if (!ret) {
          // A failed transaction may never persist its activity. Publish the actual cause now.
          contextScope.error(cause);
          if (threadNotifications.isEmpty()) {
            threadNotifications.add(Notification.error(
                "Contextualization of " + observation.getObservable().getUrn()
                    + " failed: " + cause.getMessage(), cause));
          }
        }
        observation.getNotifications().addAll(threadNotifications);

        return ret;
      } catch (Throwable t) {
        cause = t;
        contextScope.error(t);
        observation.getNotifications().add(Notification.error(t.getMessage(), t));
        return false;
      }
    } catch (RuntimeException e) {
      cause = e;
      contextScope.error(e);
      observation.getNotifications().add(Notification.error(e.getMessage(), e));
      return false;
    }

  }

  /** Reflection executors override this per binding; generic inputs retain their own native type. */
  protected Class<? extends Storage.Scanner> inputScannerClass(String name) { return Storage.Scanner.class; }

  /** Validate reflection-only requirements before writable outputs are opened. */
  protected void validateInputBindings(Map<String, List<Storage.Scanner>> scanners) {}

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
      Scheduler.Event event,
      Map<String, Storage.Scanner> scanners,
      ContextScope scope,
      RuntimeService.ContextualizationScope contextualizationScope);

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
        } else if (StorageScan.View.class.isAssignableFrom(argument.getType())
            || Storage.Shard.class.isAssignableFrom(argument.getType())
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
        } else if (org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant.class.isAssignableFrom(argument.getType())) {
          runArguments.add(schedulerEvent == null ? null : schedulerEvent.getInstant());
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
            argument, dependencies.get(input.getName()), scanners.get(input.getName()));
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
                argument, dependencies.get(singleInputKey), scanners.get(singleInputKey));
          }
        }
      }
    } else if (output != null) {

      /*
      TODO the situation where the output is not self is not possible yet.
       */
      if (dependencies.containsKey(output.getName())) {
        return adaptObservationArgument(
            argument, dependencies.get(output.getName()), scanners.get(output.getName()));
      }

      return adaptObservationArgument(argument, observation, self);
    }

    /* either an input or an output must be mapped. Otherwise this is just null. */

    return null;
  }

  /**
   * Once established that the argument should be bound to an observation or its helper objects,
   * return the adapted argument that the function argument wants.
   *
   * @param argument
   * @param observation
   * @param scanner
   * @return
   */
  static Object adaptObservationArgument(
      Parameter argument, Observation observation, Storage.Scanner scanner) {

    if (Observation.class.isAssignableFrom(argument.getType())) {
      return observation;
    } else if (Storage.Scanner.class.isAssignableFrom(argument.getType())) {
      if (scanner == null) {
        return null;
      }
      return ScannerAdapters.adaptType(
          scanner, argument.getType().asSubclass(Storage.Scanner.class));
    } else if (StorageScan.View.class.isAssignableFrom(argument.getType())) {
      if (scanner == null) return null;
      try { return scanner.view(); }
      catch (UnsupportedOperationException nativeOutput) {
        var shard = scanner.shard();
        return new StorageScan.View(new StorageScan.Partition("task-" + shard.getShardIndex(),
            shard.getGeometry().encode(), scanner.size()), shard.getShardingStrategy().getCurve(),
            shard.getNativeType(), StorageScan.semantics(observation.getObservable()),
            StorageScan.Slice.of(Scheduler.Event.initialization()), List.of(), StorageScan.HistogramPolicy.UNAVAILABLE);
      }
    } else if (Storage.Shard.class.isAssignableFrom(argument.getType())) {
      if (scanner == null) return null;
      var shard = scanner.shard();
      StorageScan.View view = null;
      try { view = scanner.view(); } catch (UnsupportedOperationException legacy) { /* native output */ }
      if (view != null && (!view.partition().geometry().equals(StorageScan.parseGeometry(shard.getGeometry().encode()).encode())
          || view.curve() != shard.getShardingStrategy().getCurve() || view.valueType() != shard.getNativeType()))
        throw new IllegalArgumentException("Mediated input requires StorageScan.View instead of a physical Shard parameter");
      return shard;
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
