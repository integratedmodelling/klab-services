package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
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
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Reactive scheduler/event bus stub for testing, to evolve into the actual scheduler.
 *
 * <p>Executors should be registered with the observations at dataflow compilation - the insertion
 * in the DT should also compile them.They should have a flag that says when the implementations can
 * be removed (init only, recompute, event-specific etc). The DT should be able to reconstruct the
 * necessary ones from the recorded actuators without a need for the dataflow being there.
 */
public class SchedulerImpl implements Scheduler {

  private final ServiceContextScope rootScope;
  private long epochStart = 0L;
  private long epochEnd = 0L;
  private Time.Resolution resolution = null;
  private KnowledgeGraph knowledgeGraph;
  private TimeEmitter timeEmitter;
  private Event initializationEvent;

  /*
   * The event processor is a fully replayable multicast with synchronized behavior.
   * Events don't end up in provenance, although the activities they engender do. The scheduler acts
   * as a provenance agent and is recorded as the agent for activities triggered by temporal events.
   */
  private final Sinks.Many<Event> processor;

  /*
   * Executors are loaded upon dataflow validation/compilation before registering the observations,
   * which triggers their usage. The cache loads actuator definitions from the knowledge graph on
   * demand and recompiles the executors if they are missing.
   */
  private LoadingCache<Observation, TriFunction<Geometry, Event, ContextScope, Boolean>> executors =
      CacheBuilder.newBuilder()
          .maximumSize(200)
          // .expireAfterAccess(10, TimeUnit.MINUTES)
          .build(
              new CacheLoader<Observation, TriFunction<Geometry, Event, ContextScope, Boolean>>() {
                public TriFunction<Geometry, Event, ContextScope, Boolean> load(Observation key) {
                  // TODO reconstruct the executor from actuator in the knowledge graph.
                  return (g, e, s) -> true;
                }
              });

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
    // TODO read the existing context state from the knowledge graph and rebuild all relevant past
    //  events
  }

  @Override
  public boolean submit(Observation observation, ContextScope scope) {

    // TODO we should not register observations that are unaffected by others unless they're events
    if (observation.isEmpty()) {
      return false;
    }

    if (scope instanceof ServiceContextScope serviceContextScope) {

      var timeData = register(observation.getGeometry());
      var registration =
          new Registration(
              observation,
              SemanticType.fundamentalType(observation.getObservable().getSemantics().getType()),
              timeData.getFirst(),
              timeData.getSecond(),
              timeData.getThird(),
              serviceContextScope);
      if (observation.getObservable().is(SemanticType.EVENT)) {
        // TODO EVENT! Post it
      } else if (observation.getObservable().is(SemanticType.PROCESS)) {
        // TODO PROCESS! Time events will affect it
      }
      // TODO store the disposable that this returns so that we can remove it upon termination
      var subscription =
          processor
              .asFlux()
              .filter(event -> event.getType() != Event.Type.INITIALIZATION)
              .filterWhen(event -> Mono.just(checkApplies(registration, event)))
              .subscribe(e -> handleEvent(registration, e));
      var initialized = initialize(observation, serviceContextScope);
      if (!initialized) {
        subscription.dispose();
      }
      return initialized;
    }
    return false;
  }

  @Override
  public void registerExecutor(
      Observation observation, TriFunction<Geometry, Event, ContextScope, Boolean> executor) {
    executors.put(observation, executor);
  }

  @Override
  public boolean switchToRealTime(long until) {

    if (System.currentTimeMillis() < epochEnd) {
      return false;
    }

    if (until < 0) {
      this.timeEmitter.startRealtimeClock();
    } else {
      this.timeEmitter.startRealtimeClock(until);
    }

    return true;
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
   * @param scope
   * @param causingEvent
   * @return
   */
  private boolean contextualize(
      Observation observation, Geometry geometry, ServiceContextScope scope, Event causingEvent) {

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

      if (checkEvent((Observation) affecting.source(), causingEvent)) {
        continue;
      }

      var affectingRelationship =
          scope
              .getDigitalTwin()
              .getKnowledgeGraph()
              .getLinks(
                  affecting.source(),
                  GraphModel.Relationship.Direction.OUTGOING,
                  scope,
                  GraphModel.Relationship.AFFECTS)
              .stream()
              .findFirst()
              .orElseThrow(
                  () ->
                      new KlabIllegalStateException(
                          "Inconsistent AFFECT relationship in knowledge graph"));
      var sequence =
          affectingRelationship.properties().get(/* TODO use formal property */ "sequence", 0);

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
    var executor = executors.getIfPresent(observation);
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

  private boolean execute(
      TriFunction<Geometry, Event, ContextScope, Boolean> executor,
      Observation observation,
      Geometry geometry,
      Scheduler.Event event,
      ServiceContextScope scope) {
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

  private boolean checkEvent(Observation observation, Event event) {
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
    long tStart = time.getStart().getMilliseconds();
    long tEnd = time.getEnd().getMilliseconds();
    if (this.epochStart == 0 || this.epochStart > tStart) {
      this.epochStart = tStart;
    }
    if (this.epochEnd == 0 || this.epochEnd < tEnd) {
      this.epochEnd = tEnd;
    }
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
