package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Cache;
import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.TriFunction;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.runtime.CompiledDataflow;
import org.integratedmodelling.klab.services.runtime.RuntimeService;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.Disposables;

/** INIT registration and synchronous bounded temporal dispatch with durable per-plan progress. */
public class SchedulerImpl implements Scheduler, AutoCloseable {

  private final ServiceContextScope rootScope;
  private long epochStart = 0L;
  private long epochEnd = 0L;
  private Time.Resolution resolution = null;
  private KnowledgeGraph knowledgeGraph;
  private TimeEmitter timeEmitter;
  private Event initializationEvent;
  private final reactor.core.Disposable.Composite subscriptions = Disposables.composite();
  private boolean hasTimeBounds;
  private final Map<Long, OccurrenceRegistration> occurrences = new ConcurrentHashMap<>();
  private final Map<Long, Observation> occurrenceObservations = new ConcurrentHashMap<>();
  private final java.util.concurrent.atomic.AtomicLong requestedThrough =
      new java.util.concurrent.atomic.AtomicLong(Long.MIN_VALUE);
  private final Map<DigitalTwin.Transaction, Set<Observation>> pending = new IdentityHashMap<>();
  private final InitializationRuns initializationRuns = new InitializationRuns();
  private boolean draining;
  private boolean advancePending;

  public Map<Long, OccurrenceRegistration> getOccurrenceRegistrations() {
    return Map.copyOf(occurrences);
  }

  /*
   * The event processor is a fully replayable multicast with synchronized behavior.
   * Events don't end up in provenance, although the activities they engender do. The scheduler acts
   * as a provenance agent and is recorded as the agent for activities triggered by temporal events.
   */
  private final Sinks.Many<Event> processor;

  /*¶
   * Executors are loaded upon dataflow validation/compilation before registering the observations,
   * which triggers their usage. The cache loads actuator definitions from the knowledge graph on
   * demand and recompiles the executors if they are missing.
   */
  private final Cache<Observation, TriFunction<Geometry, Event, ContextScope, Boolean>> executors =
      CacheBuilder.newBuilder().maximumSize(200).build();

  public SchedulerImpl(ServiceContextScope scope, DigitalTwinImpl digitalTwin) {
    this.rootScope = scope;
    this.knowledgeGraph = digitalTwin.getKnowledgeGraph();
    this.timeEmitter = new TimeEmitter(/*this*/ );
    this.processor = Sinks.many().replay().all();
    initializeScheduler();
  }

  private void initializeScheduler() {
    // The INIT event is created before anything happens and applies to every new observation
    // registered.
    post(this.initializationEvent = Event.initialization(), rootScope);
    // Restore committed registrations without rerunning initialization or changing timestamps.
    // Resumption is explicitly driven by advanceTo; construction must never execute user code.
    for (var observation : knowledgeGraph.getScheduledObservations(rootScope)) {
      if (observation.getMetadata().containsKey(OccurrenceRegistration.METADATA_KEY)) {
        activateOccurrence(observation);
        continue;
      }
      var time = register(observation.getGeometry());
      subscriptions.add(
          subscribe(
              new Registration(
                  observation,
                  SemanticType.fundamentalType(
                      observation.getObservable().getSemantics().getType()),
                  time.getFirst(),
                  time.getSecond(),
                  time.getThird(),
                  rootScope)));
    }
  }

  @Override
  public boolean submit(Observation observation, ContextScope scope) {

    // TODO we should not register observations that are unaffected by others unless they're events
    if (observation.isEmpty()) {
      return false;
    }

    if (scope instanceof ServiceContextScope serviceContextScope) {

      var initialized = initialize(observation, serviceContextScope);
      if (initialized) {
        var previous = observation.getMetadata().get(Scheduler.REGISTRATION_METADATA_KEY);
        observation.getMetadata().put(Scheduler.REGISTRATION_METADATA_KEY, true);
        var transaction = scope.getCurrentTransaction();
        transaction.update(observation);
        transaction.afterRollback(
            () -> restoreMetadata(observation, Scheduler.REGISTRATION_METADATA_KEY, previous));
        transaction.afterCommit(
            () -> {
              if (observation.getMetadata().containsKey(OccurrenceRegistration.METADATA_KEY))
                return;
              var time = register(observation.getGeometry());
              subscriptions.add(
                  subscribe(
                      new Registration(
                          observation,
                          SemanticType.fundamentalType(
                              observation.getObservable().getSemantics().getType()),
                          time.getFirst(),
                          time.getSecond(),
                          time.getThird(),
                          rootScope)));
            });
      }
      return initialized;
    }
    return false;
  }

  private reactor.core.Disposable subscribe(Registration registration) {
    return processor
        .asFlux()
        .filter(event -> event.getType() != Event.Type.INITIALIZATION)
        .filterWhen(event -> Mono.just(checkApplies(registration, event)))
        .subscribe(event -> handleEvent(registration, event));
  }

  @Override
  public void registerExecutor(
      Observation observation, TriFunction<Geometry, Event, ContextScope, Boolean> executor) {
    executors.put(observation, executor);
    observation.getMetadata().put(Scheduler.EXECUTION_METADATA_KEY, true);
  }

  @Override
  public boolean switchToRealTime(long until) {
    throw new UnsupportedOperationException(
        "Only bounded simulated temporal dispatch is supported");
  }

  /** Explicit bounded clock driver. Restart and newly introduced cadences use their own cursors. */
  @Override
  public synchronized boolean advanceTo(long until) {
    if (until == Long.MAX_VALUE)
      throw new IllegalArgumentException("A finite simulation horizon is required");
    until = requestedThrough.accumulateAndGet(until, Math::max);
    advancePending = true;
    if (draining) return true;
    draining = true;
    try {
      do {
        advancePending = false;
        if (!drainThrough(requestedThrough.get())) return false;
      } while (advancePending);
      return true;
    } finally {
      draining = false;
    }
  }

  private boolean drainThrough(long until) {
    var registrations = new ArrayList<SimulatedDispatch.Registration>();
    for (var entry : occurrences.entrySet()) {
      var registration = entry.getValue();
      var schedule = registration.schedules().getFirst().bound();
      if (registration.schedules().stream().anyMatch(s -> !s.bound().equals(schedule)))
        throw new UnsupportedOperationException(
            "An actuator chain must have one accepted schedule");
      var observation = occurrenceObservations.get(entry.getKey());
      registrations.add(
          new SimulatedDispatch.Registration(
              entry.getKey(),
              registration.id(),
              registration.planRevision(),
              schedule,
              () -> completedThrough(observation, registration)));
    }
    return timeEmitter.emitSimulated(
        registrations,
        until,
        tick -> {
          var observation = occurrenceObservations.get(tick.registration().observationId());
          var registration = occurrences.get(observation.getId());
          return dispatch(observation, registration, tick.event(), true);
        });
  }

  private long completedThrough(Observation observation, OccurrenceRegistration registration) {
    var encoded = observation.getMetadata().get(DispatchProgress.KEY);
    if (encoded == null) return Long.MIN_VALUE;
    var progress = Utils.Json.parseObject(encoded.toString(), DispatchProgress.class);
    if (!progress.registration().equals(registration.id())
        || !progress.revision().equals(registration.planRevision()))
      throw new IllegalStateException(
          "Temporal cursor belongs to another accepted plan; re-resolution required");
    return progress.through();
  }

  @Override
  public synchronized boolean dispatchObserved(Observation observation) {
    if (observation.getId() <= 0
        || !observation.getObservable().is(SemanticType.EVENT)
        || observation.getObservable().getSemantics().isCollective())
      throw new IllegalArgumentException("A durable individual event observation is required");
    var time = GeometryRepository.INSTANCE.scale(observation.getGeometry()).getTime();
    if (time == null || time.getStart() == null || time.getEnd() == null)
      throw new IllegalArgumentException("Observed events require bounded temporal support");
    if (time.getStart().getMilliseconds() >= time.getEnd().getMilliseconds())
      throw new UnsupportedOperationException(
          "Point events require an explicit temporal support policy");
    var id = "observed:" + observation.getId();
    var previous = observation.getMetadata().get(DispatchProgress.KEY);
    if (previous != null) {
      var receipt = Utils.Json.parseObject(previous.toString(), DispatchProgress.class);
      if (!receipt.eventId().equals(id)
          || !receipt.revision().equals("observed-v1:" + observation.getGeometry().encode()))
        throw new IllegalStateException(
            "Observed event identity or support changed after dispatch");
      return true;
    }
    return dispatch(
        observation,
        null,
        new TransitionEvent(
            id, time.getStart().getMilliseconds(), time.getEnd().getMilliseconds(), observation),
        false);
  }

  private boolean dispatch(
      Observation observation,
      OccurrenceRegistration registration,
      TransitionEvent event,
      boolean runOccurrence) {
    var geometry =
        org.integratedmodelling.klab.services.runtime.TemporalGeometry.localize(
            observation.getGeometry(), event);
    var scope =
        rootScope.executingFresh(
            org.integratedmodelling.klab.api.provenance.Activity.of(
                org.integratedmodelling.klab.api.provenance.Activity.Type.SIMULATION),
            observation);
    try {
      var bearer = registration == null ? observation.getParentId() : registration.bearerId();
      var consequences =
          ComputationalClosure.ordered(observation, bearer, geometry, knowledgeGraph, scope);
      var writes =
          new org.integratedmodelling.klab.runtime.storage.LocalTemporalWriteSet(event, scope);
      if (runOccurrence) {
        // Compile against this attempt's scope, never a cached/committed transaction.
        var executor = restoreExecutor(observation, scope);
        if (executor == null || !execute(executor, observation, geometry, event, scope))
          throw new IllegalStateException(
              "Temporal contextualization failed for " + observation.getUrn());
      }
      for (var consequence : consequences) {
        boolean triggered =
            knowledgeGraph
                .getLinks(
                    consequence,
                    GraphModel.Relationship.Direction.INCOMING,
                    scope,
                    GraphModel.Relationship.AFFECTS)
                .stream()
                .anyMatch(
                    link ->
                        org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.PREREQUISITE
                                .equals(
                                    link.properties()
                                        .get(
                                            org.integratedmodelling.klab.api.digitaltwin.ProcessPlan
                                                .EDGE_ROLE))
                            && link.source() instanceof Observation input
                            && (writes.changed(input) || input.getId() == observation.getId()));
        if (!triggered) continue;
        var executor = restoreExecutor(consequence, scope);
        var support =
            org.integratedmodelling.klab.services.runtime.TemporalGeometry.localize(
                consequence.getGeometry(), event);
        if (executor == null || !execute(executor, consequence, support, event, scope))
          throw new IllegalStateException(
              "Causal contextualization failed for " + consequence.getUrn());
      }
      var changed = writes.changedObservations();
      writes.prepare();
      var id = registration == null ? event.id() : registration.id();
      var revision =
          registration == null
              ? "observed-v1:" + observation.getGeometry().encode()
              : registration.planRevision();
      var previous = observation.getMetadata().get(DispatchProgress.KEY);
      var transaction = scope.getCurrentTransaction();
      transaction.afterRollback(() -> restoreMetadata(observation, DispatchProgress.KEY, previous));
      observation
          .getMetadata()
          .put(
              DispatchProgress.KEY,
              Utils.Json.asString(
                  new DispatchProgress(
                      1,
                      id,
                      revision,
                      event.end(),
                      event.id(),
                      registration == null ? event.end() : requestedThrough.get())));
      transaction.update(observation);
      transaction.beforeCommit(
          () -> {
            var deltas =
                changed.stream()
                    .sorted(java.util.Comparator.comparingLong(Observation::getId))
                    .map(
                        quality -> {
                          var history =
                              Utils.Json.parseObject(
                                  quality
                                      .getMetadata()
                                      .get(
                                          org.integratedmodelling.klab.runtime.storage
                                              .TemporalHistory.KEY)
                                      .toString(),
                                  org.integratedmodelling.klab.runtime.storage.TemporalHistory
                                      .class);
                          var state =
                              history.revisions().stream()
                                  .filter(r -> r.event().equals(event.toKey()))
                                  .findFirst()
                                  .orElseThrow();
                          return Utils.Json.asString(
                              new org.integratedmodelling.klab.api.digitaltwin.TemporalStateDelta(
                                  1,
                                  quality.getId(),
                                  event.toKey(),
                                  state.support(),
                                  state.shards()));
                        })
                    .toList();
            if (!deltas.isEmpty())
              transaction
                  .getActivity()
                  .getMetadata()
                  .put(
                      org.integratedmodelling.klab.api.digitaltwin.TemporalStateDelta.METADATA_KEY,
                      deltas);
          });
      transaction.stageSchedulerJournal(
          () ->
              new org.integratedmodelling.klab.api.digitaltwin.SchedulerJournal(
                  1,
                  event.id(),
                  null,
                  event.getType(),
                  event.start(),
                  event.end(),
                  id,
                  revision,
                  geometry.encode(),
                  0,
                  changed.stream().map(Observation::getId).sorted().toList(),
                  !changed.isEmpty()));
      if (scope.commit() < 0)
        throw new IllegalStateException("Temporal transaction did not commit");
      return true;
    } catch (Throwable failure) {
      scope.fail(failure);
      rootScope.error(failure);
      return false;
    }
  }

  private Triple<Long, Long, Time.Resolution> register(Geometry geometry) {
    // TODO record frequency @ starting point and determine which events to send
    Time time = GeometryRepository.INSTANCE.scale(geometry).getTime();
    if (time != null && !time.isEmpty()) {
      return notifyTime(time);
    }
    return Triple.of(0L, 0L, null);
  }

  /**
   * This is called in response to the INIT event received by any root-level observation that was
   * successfully resolved. Successive executions of the same executors will happen by directly
   * calling {@link #contextualize(Observation, Geometry, ServiceContextScope, Event)}
   *
   * @param observation
   */
  @Override
  public boolean executeDependency(
      Observation observation, Geometry geometry, Event event, ContextScope scope) {
    if (event.getType() != Event.Type.INITIALIZATION)
      throw new UnsupportedOperationException(
          "Temporal prerequisites are selected by causal dispatch, not INIT recursion");
    return checkEvent(observation, event)
        || contextualize(observation, geometry, (ServiceContextScope) scope, event);
  }

  private boolean initialize(Observation observation, ServiceContextScope scope) {
    var scale = GeometryRepository.INSTANCE.scale(observation.getGeometry());
    try {
      return contextualize(observation, scale, scope, this.initializationEvent);
    } catch (Throwable t) {
      Logging.INSTANCE.error(t);
      scope.fail(t);
      observation.getNotifications().add(Notification.error(t.getMessage(), t));
      return false;
    }
  }

  /**
   * Returns true if anything was done. By returning false we don't add activities when they don't
   * do any operations.
   *
   * @param observation
   * @param geometry
   * @param requestedScope
   * @param causingEvent
   * @return
   */
  private boolean contextualize(
      Observation observation,
      Geometry geometry,
      ServiceContextScope requestedScope,
      Event causingEvent) {

    if(causingEvent.getType()==Event.Type.INITIALIZATION) {
      return initializationRuns.execute(requestedScope.getCurrentTransaction(), observation.getId(),
          () -> checkEvent(observation,causingEvent)
              || contextualizeOnce(observation,geometry,requestedScope,causingEvent));
    }
    return contextualizeOnce(observation,geometry,requestedScope,causingEvent);
  }

  private boolean contextualizeOnce(
      Observation observation, Geometry geometry, ServiceContextScope requestedScope, Event causingEvent) {

    var transactionExecutor =
        requestedScope.getCurrentTransaction() == null
            ? null
            : requestedScope.getCurrentTransaction().getExecutor(observation);
    var scope =
        (ServiceContextScope)
            (transactionExecutor == null
                ? requestedScope
                : transactionExecutor.executionScope(requestedScope));

    // follow the dependency chain first, then execute self
    Map<Integer, List<Callable<Boolean>>> tasks = new HashMap<>();
    for (var affecting :
        scope
            .getDigitalTwin()
            .getKnowledgeGraph()
            .getLinks(
                observation,
                GraphModel.Relationship.Direction.INCOMING,
                scope,
                GraphModel.Relationship.AFFECTS)) {

      var role =
          affecting
              .properties()
              .get(org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.EDGE_ROLE);
      if (org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.INFLUENCE.equals(role)
          || org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.DESCRIPTIVE.equals(role))
        continue;

      if (checkEvent((Observation) affecting.source(), causingEvent)) {
        continue;
      }

      // Sequence belongs to this dependency edge, not an arbitrary edge from its source.
      var sequence = affecting.properties().get(/* TODO use formal property */ "sequence", 0);

      tasks
          .computeIfAbsent(sequence, n -> new ArrayList<>())
          .add(
              () -> contextualize((Observation) affecting.source(), geometry, scope, causingEvent));
    }

    var sortedTasks =
        tasks.entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .map(Map.Entry::getValue)
            .toList();

    for (var group : sortedTasks) {
      if (!group.isEmpty())
        try (var executorService = Executors.newVirtualThreadPerTaskExecutor()) {
          var ret = executorService.invokeAll(group);
          if (ret.stream().anyMatch(objectFuture -> objectFuture.state() == Future.State.FAILED)) {
            // TODO collect the exceptions and pass them along
            return false;
          }
          // check if anything has returned false
          if (ret.stream()
              .anyMatch(
                  future -> {
                    try {
                      return !future.get();
                    } catch (Exception e) {
                      return false;
                    }
                  })) {
            observation
                .getNotifications()
                .add(
                    Notification.error(
                        "Some concurrent tasks failed during contextualization of " + observation));
            return false;
          }
        } catch (Throwable t) {
          observation.getNotifications().add(Notification.error(t.getMessage(), t));
          scope.error(t);
          return false;
        }
    }

    /*
     * The actual execution for self
     */
    TriFunction<Geometry, Event, ContextScope, Boolean> executor =
        transactionExecutor == null
            ? executors.getIfPresent(observation)
            : transactionExecutor::run;
    if (transactionExecutor != null
        && transactionExecutor.getActuator() != null
        && transactionExecutor.getActuator().getExecutionRole()
            != org.integratedmodelling.klab.api.services.runtime.Actuator.ExecutionRole
                .INITIALIZATION) {
      if (causingEvent.getType() != Event.Type.INITIALIZATION)
        throw new UnsupportedOperationException(
            "Temporal execution must enter the transactional dispatcher");
      if (!transactionExecutor.run(geometry, causingEvent, scope)) return false;
      stageOccurrence(observation, transactionExecutor.getActuator(), scope);
      return true;
    }
    if (observation.getMetadata().containsKey(OccurrenceRegistration.METADATA_KEY)) {
      if (causingEvent.getType() != Event.Type.INITIALIZATION)
        throw new UnsupportedOperationException(
            "Temporal execution must enter the transactional dispatcher");
      return true;
    }
    if (executor == null && observation.getId() > 0) {
      executor = restoreExecutor(observation, scope);
      if (executor != null) {
        executors.put(observation, executor);
      }
    }
    if (executor != null) {
      var ret = execute(executor, observation, geometry, causingEvent, scope);

      /**
       * At this point if ret == true there have been no errors within the runtime, but the
       * observation may still contain non-fatal error notifications coming from a plug-in adapter
       * or external service. Those must be handled separately.
       */
      return ret && !Utils.Notifications.hasErrors(observation.getNotifications());
    }

    return true;
  }

  TriFunction<Geometry, Event, ContextScope, Boolean> restoreExecutor(
      Observation observation, ServiceContextScope scope) {
    if (observation.getMetadata().containsKey(OccurrenceRegistration.METADATA_KEY)) {
      var registration = readOccurrence(observation);
      var plan =
          Utils.Json.parseObject(
              registration.plan(),
              org.integratedmodelling.klab.api.services.runtime.Actuator.class);
      var boundScope = scope;
      if (registration.bearerId() > 0) {
        var bearer = scope.getObservation(registration.bearerId());
        if (bearer == null)
          throw new IllegalStateException("Missing occurrence bearer " + registration.bearerId());
        boundScope = scope.within(bearer);
      }
      var compiled =
          new CompiledDataflow(scope.getService(RuntimeService.class), observation, boundScope);
      return compiled.restoreOccurrenceExecutor(plan)::run;
    }
    var snapshot = observation.getMetadata().get(Scheduler.PLAN_METADATA_KEY);
    if (snapshot != null) {
      var plan =
          Utils.Json.parseObject(
              snapshot.toString(),
              org.integratedmodelling.klab.api.services.runtime.Actuator.class);
      return new CompiledDataflow(scope.getService(RuntimeService.class), observation, scope)
              .restoreOccurrenceExecutor(plan)
          ::run;
    }
    var implementations =
        knowledgeGraph.getLinks(
            observation,
            GraphModel.Relationship.Direction.OUTGOING,
            scope,
            GraphModel.Relationship.CONTEXTUALIZED_BY);
    if (implementations.isEmpty()) {
      if (Boolean.TRUE.equals(observation.getMetadata().get(Scheduler.EXECUTION_METADATA_KEY))) {
        throw new KlabIllegalStateException(
            "Missing persisted execution plan for " + observation.getUrn());
      }
      return null; // An acknowledged/input observation need not have executable computation.
    }
    if (implementations.size() != 1) {
      throw new KlabUnimplementedException(
          "Restoring multiple actuators requires coverage selection for " + observation.getUrn());
    }
    var link = implementations.iterator().next();
    if (!(link.target() instanceof ActuatorImpl actuator)
        || actuator.getComputation().isEmpty()
        || actuator.getActuatorType() == null
        || actuator.getType() == null) {
      throw new KlabIllegalStateException(
          "No restorable actuator definition for " + observation.getUrn());
    }
    if (actuator.getChildrenCount() > 0
        || !knowledgeGraph
            .getLinks(
                actuator,
                GraphModel.Relationship.Direction.OUTGOING,
                scope,
                GraphModel.Relationship.HAS_CHILD)
            .isEmpty()) {
      throw new KlabUnimplementedException(
          "Restoring actuator input bindings is not implemented for " + observation.getUrn());
    }
    var coverage = actuator.getCoverage();
    if (coverage != null
        && !coverage.isUniversal()
        && !GeometryRepository.INSTANCE
            .scale(coverage)
            .encode()
            .equals(GeometryRepository.INSTANCE.scale(observation.getGeometry()).encode())) {
      throw new KlabUnimplementedException(
          "Restoring partial-coverage actuators is not implemented for " + observation.getUrn());
    }
    actuator.setObservation(observation);
    var compiled = new CompiledDataflow(scope.getService(RuntimeService.class), observation, scope);
    var restored = compiled.restoreLeafExecutor(actuator);
    return restored::run;
  }

  @Override
  public void close() {
    timeEmitter.close();
    subscriptions.dispose();
    processor.tryEmitComplete();
    executors.invalidateAll();
    occurrences.clear();
    occurrenceObservations.clear();
    synchronized (pending) {
      pending.clear();
    }
  }

  private boolean execute(
      TriFunction<Geometry, Event, ContextScope, Boolean> executor,
      Observation observation,
      Geometry geometry,
      Scheduler.Event event,
      ServiceContextScope scope) {
    if (event.getType() != Event.Type.INITIALIZATION) return executor.apply(geometry, event, scope);
    if (observation instanceof ObservationImpl concrete && scope.getCurrentTransaction() != null) {
      var previousTimestamps = new ArrayList<>(observation.getEventTimestamps());
      scope
          .getCurrentTransaction()
          .afterRollback(() -> concrete.setEventTimestamps(previousTimestamps));
    }
    if (executor.apply(geometry, event, scope)) {
      if (observation.getObservable().is(SemanticType.QUALITY)) {
        var storage = scope.getDigitalTwin().getStorageManager().getStorage(observation);
        if (storage != null) {
          // A shard descriptor must never be committed before its durable data file is complete.
          storage.flush();
          if (observation instanceof ObservationImpl observationImpl) {
            observationImpl.setHistograms(storage.getHistograms());
            scope.getCurrentTransaction().update(observationImpl);
          }
          for (var buffer : storage.getNativeShards(event)) {
            if (buffer.getId() < 0) {
              scope
                  .getCurrentTransaction()
                  .link(observation, buffer, GraphModel.Relationship.HAS_DATA);
            } else {
              // Re-contextualizing an existing time slice updates its histogram and descriptor;
              // creating another Data node would orphan the old shard and duplicate its counts.
              scope.getCurrentTransaction().update(buffer);
            }
          }
        }
      }
      var geometryTime = GeometryRepository.INSTANCE.scale(geometry).getTime();
      recordEvent(observation, event, geometryTime, scope.getCurrentTransaction());
      return true;
    }
    return false;
  }

  private static void restoreMetadata(Observation observation, String key, Object previous) {
    if (previous == null) observation.getMetadata().remove(key);
    else observation.getMetadata().put(key, previous);
  }

  private OccurrenceRegistration readOccurrence(Observation observation) {
    return Utils.Json.parseObject(
        observation.getMetadata().get(OccurrenceRegistration.METADATA_KEY, String.class),
        OccurrenceRegistration.class);
  }

  private void activateOccurrence(Observation observation) {
    occurrenceObservations.put(observation.getId(), observation);
    occurrences.put(observation.getId(), readOccurrence(observation));
    var encoded = observation.getMetadata().get(DispatchProgress.KEY);
    if (encoded != null) {
      var progress = Utils.Json.parseObject(encoded.toString(), DispatchProgress.class);
      requestedThrough.accumulateAndGet(progress.requestedThrough(), Math::max);
    }
  }

  private void stageOccurrence(
      Observation observation,
      org.integratedmodelling.klab.api.services.runtime.Actuator plan,
      ServiceContextScope scope) {
    var transaction = scope.getCurrentTransaction();
    while (transaction.getParent() != null) transaction = transaction.getParent();
    final var root = transaction;
    var schedules =
        plan.getOccurrenceSchedules().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(
                entry ->
                    new OccurrenceRegistration.Schedule(
                        entry.getKey(),
                        entry.getValue(),
                        entry
                            .getValue()
                            .bind(
                                GeometryRepository.INSTANCE
                                    .scale(observation.getGeometry())
                                    .getTime())))
            .toList();
    synchronized (pending) {
      if (!pending
          .computeIfAbsent(root, ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
          .add(observation)) return;
    }
    var id = UUID.randomUUID().toString();
    var previous = observation.getMetadata().get(OccurrenceRegistration.METADATA_KEY);
    var previousRegistered = observation.getMetadata().get(Scheduler.REGISTRATION_METADATA_KEY);
    var negotiationKey =
        org.integratedmodelling.klab.api.digitaltwin.OccurrenceNegotiation.DATA_KEY;
    var previousNegotiation = observation.getMetadata().get(negotiationKey);
    Runnable release =
        () -> {
          synchronized (pending) {
            var values = pending.get(root);
            if (values != null && values.remove(observation) && values.isEmpty())
              pending.remove(root);
          }
        };
    root.afterRollback(
        () -> {
          restoreMetadata(observation, OccurrenceRegistration.METADATA_KEY, previous);
          restoreMetadata(observation, Scheduler.REGISTRATION_METADATA_KEY, previousRegistered);
          restoreMetadata(observation, negotiationKey, previousNegotiation);
          release.run();
        });
    root.update(observation);
    root.beforeCommit(
        () -> {
          if (observation.getId() <= 0)
            throw new KlabIllegalStateException("Occurrence has no durable identity");
          var encodedProcess =
              plan.getData().get(org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.DATA_KEY);
          var bearerId =
              encodedProcess == null
                  ? (scope.getContextObservation() == null
                      ? 0
                      : scope.getContextObservation().getId())
                  : Utils.Json.parseObject(
                          encodedProcess.toString(),
                          org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.class)
                      .bearerId();
          if (encodedProcess != null && bearerId <= 0)
            throw new KlabIllegalStateException("Process bearer has no durable identity");
          var registration =
              new OccurrenceRegistration(
                  1,
                  id,
                  id,
                  plan.getExecutionRole(),
                  bearerId,
                  schedules,
                  Utils.Json.asString(CompiledDataflow.portableOccurrencePlan(plan)));
          observation
              .getMetadata()
              .put(OccurrenceRegistration.METADATA_KEY, Utils.Json.asString(registration));
          observation.getMetadata().put(Scheduler.REGISTRATION_METADATA_KEY, true);
          if (plan.getData().containsKey(negotiationKey))
            observation.getMetadata().put(negotiationKey, plan.getData().get(negotiationKey));
        });
    root.afterCommit(
        () -> {
          // The first callback activates the entire root commit before any temporal execution.
          Set<Observation> committed;
          synchronized (pending) {
            committed = pending.remove(root);
          }
          if (committed == null) return;
          committed.forEach(this::activateOccurrence);
          advanceCommittedOccurrences();
        });
  }

  /** Run committed bounded registrations; the accepted bounds are also durable retry intent. */
  void advanceCommittedOccurrences() {
    long horizon = occurrences.values().stream()
        .flatMap(registration -> registration.schedules().stream())
        .mapToLong(schedule -> schedule.bound().end()).max().orElse(Long.MIN_VALUE);
    if (horizon != Long.MIN_VALUE && !advanceTo(horizon))
      rootScope.error("Temporal dispatch stopped after registration commit; retry from durable progress is required");
  }

  private boolean checkEvent(Observation observation, Event event) {
    if (event.getType() == Event.Type.INITIALIZATION
        && (observation.getObservable().is(SemanticType.PROCESS)
            || (observation.getObservable().is(SemanticType.EVENT)
                && observation.getObservable().getSemantics().isCollective()))) return false;
    var timestamps = observation.getEventTimestamps();
    if (event.getType() == Event.Type.INITIALIZATION
        && observation instanceof ObservationImpl observation1
        && observation1.isSubstantialQuality()) {
      return !timestamps.isEmpty() && timestamps.getFirst() == 0;
    }
    return !timestamps.isEmpty()
        && timestamps.getLast() >= event.getTime().getEnd().getMilliseconds();
  }

  private void recordEvent(
      Observation observation,
      Event event,
      Time geometryTime,
      DigitalTwin.Transaction transaction) {
    if (observation instanceof ObservationImpl observation1) {
      var timestamps = new ArrayList<>(observation.getEventTimestamps());
      if (event.getType() == Event.Type.INITIALIZATION && observation1.isSubstantialQuality()) {
        timestamps.add(0L);
        if (geometryTime != null && geometryTime.getStart() != null) {
          timestamps.add(geometryTime.getStart().getMilliseconds());
        }
      } else {
        timestamps.add(event.getTime().getEnd().getMilliseconds());
      }
      observation1.setEventTimestamps(timestamps);
      transaction.update(observation);
    }
  }

  /**
   * Adjust the internal parameters to reflect the time seen and post any events this extent
   * implies.
   *
   * @param time
   */
  private Triple<Long, Long, Time.Resolution> notifyTime(Time time) {
    if (time.getStart() == null || time.getEnd() == null) {
      // Open extents need an explicit clock/catch-up policy; don't turn missing bounds into zero.
      return Triple.of(0L, 0L, time.getResolution());
    }
    long tStart = time.getStart().getMilliseconds();
    long tEnd = time.getEnd().getMilliseconds();
    if (!hasTimeBounds || this.epochStart > tStart) {
      this.epochStart = tStart;
    }
    if (!hasTimeBounds || this.epochEnd < tEnd) {
      this.epochEnd = tEnd;
    }
    hasTimeBounds = true;
    /* ensure that all events are there */
    //    if (timeEmitter.updateEvents(tStart, tEnd, time.getResolution())) {
    //      // if anything has changed, notify the scope listeners
    //      rootScope.send(
    //          Message.MessageClass.DigitalTwin,
    //          Message.MessageType.ScheduleModified,
    //          TimeEmitter.getSchedule());
    //    }
    return Triple.of(tStart, tEnd, time.getResolution());
  }

  @Override
  public TimeInstant epochStart() {
    return TimeInstant.create(epochStart);
  }

  @Override
  public TimeInstant epochEnd() {
    return TimeInstant.create(epochEnd);
  }

  @Override
  public Time.Resolution resolution() {
    return resolution;
  }

  /**
   * TODO instead of observation, just store a pair of longs (observation ID in DT + last time of
   * update, -1, 0 or N). A third long is a key to a map of event checkers which are reused on
   * demand. A fourth could be the ID of a linked DT when the event is external. We can also keep
   * the IDs of the affected and maybe affecting observations as a Set of longs.
   *
   * <p>TODO the registrations should be cached and reconstructed from the KG based on the
   * resolution status and last time of update.
   *
   * <p>TODO add info for filtering, e.g. a <em>substantial</em> flag to filter initialization
   *
   * <p>The observation should also know if it's a dependent or not, in which case only actual
   * observation events affect it, given that contextualization actions are handled through the
   * influence diagram in the DT.
   *
   * @param observation
   * @param type
   * @param start
   * @param end
   * @param scope the scope executing the activity that made the registration
   */
  public record Registration(
      Observation observation,
      SemanticType type,
      long start,
      long end,
      Time.Resolution resolution,
      ServiceContextScope scope) {}

  private void post(Event event, Scope scope) {
    processor.emitNext(
        event,
        (signalType, emitResult) -> {
          scope.error(
              "Scheduler: internal: failed to emit event " + event + ": result is " + emitResult);
          return false;
        });
  }

  private Boolean checkApplies(Registration observation, Event event) {
    // TODO filter INITIALIZATION for substantials and their qualities
    // TODO check observed event based on 'affects' semantics
    return true;
  }

  /**
   * These are guaranteed synchronous. Communication between actors will be synchronous at the actor
   * level, not at the scheduler level, so the actor system remains necessary.
   *
   * @param registration
   * @param event
   */
  private void handleEvent(Registration registration, Event event) {
    //    System.out.println(registration + " got event " + event);
    if (event.getType() == Event.Type.INITIALIZATION) {
      // FIXME this should not be necessary when the filter works
      initialize(registration.observation(), registration.scope);
    }
  }
}
