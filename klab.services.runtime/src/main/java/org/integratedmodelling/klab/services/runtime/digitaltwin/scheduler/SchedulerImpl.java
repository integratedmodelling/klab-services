package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.TriFunction;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
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
  private EventImpl initializationEvent;

  /*
   * The event processor is a fully replayable, multicasting one with synchronized behavior.
   * Events don't end up in provenance, although the activities they engender do. The scheduler acts
   * as a provenance agent and is recorded as the agent for activities triggered by temporal events.
   */
  private final Sinks.Many<EventImpl> processor = Sinks.many().replay().all();

  /*
   * Executors are loaded upon dataflow validation/compilation before registering the observations,
   * which triggers their usage. The cache loads actuator definitions from the knowledge graph on
   * demand and recompiles the executors if they are missing.
   */
  private LoadingCache<Long, TriFunction<Geometry, Scheduler.Event, ContextScope, Boolean>>
      executors =
          CacheBuilder.newBuilder()
              .maximumSize(200)
              // .expireAfterAccess(10, TimeUnit.MINUTES)
              .build(
                  new CacheLoader<
                      Long, TriFunction<Geometry, Scheduler.Event, ContextScope, Boolean>>() {
                    public TriFunction<Geometry, Scheduler.Event, ContextScope, Boolean> load(
                        Long key) {
                      // TODO reconstruct the executor from actuator in the knowledge graph.
                      return (g, e, s) -> true;
                    }
                  });

  public SchedulerImpl(ServiceContextScope scope, DigitalTwinImpl digitalTwin) {
    this.rootScope = scope;
    this.knowledgeGraph = digitalTwin.getKnowledgeGraph();
    this.timeEmitter = new TimeEmitter(this);
    initializeScheduler();
  }

  private void initializeScheduler() {
    // The INIT event is created before anything happens and applies to every new observation
    // registered.
    post(this.initializationEvent = new EventImpl());
    // TODO read the existing context state from the knowledge graph and rebuild all relevant past
    //  events
  }

  @Override
  public void submit(Observation observation, Activity triggeringActivity) {

    if (observation.isEmpty()) {
      return;
    }

    var timeData = register(observation.getGeometry());
    var registration =
        new Registration(
            observation.getId(),
            SemanticType.fundamentalType(observation.getObservable().getSemantics().getType()),
            timeData.getFirst(),
            timeData.getSecond(),
            timeData.getThird(),
            triggeringActivity);
    if (observation.getObservable().is(SemanticType.EVENT)) {
      // EVENT! Post iy
    } else if (observation.getObservable().is(SemanticType.PROCESS)) {
      // PROCESS! Time events will affect it
    }
    processor
        .asFlux()
        .filterWhen(event -> Mono.just(checkApplies(registration, event)))
        .subscribe(e -> handleEvent(registration, e));
  }

  @Override
  public void registerExecutor(
      Observation observation,
      TriFunction<Geometry, Scheduler.Event, ContextScope, Boolean> executor) {
    executors.put(observation.getId(), executor);
  }

  private Triple<Long, Long, Time.Resolution> register(Geometry geometry) {
    // TODO record frequency @ starting point and determine which events to send
    Time time = GeometryRepository.INSTANCE.scale(geometry).getTime();
    if (time != null) {
      return notifyTime(time);
    }
    return Triple.of(0L, 0L, null);
  }

  /**
   * This is called in response to the INIT event received by any root-level observation that was
   * successfully resolved. Successive executions of the same executors will happen by directly
   * calling {@link #contextualize(Observation, Geometry, ServiceContextScope, EventImpl,
   * DigitalTwin.Transaction)}}}
   *
   * @param observation
   */
  private void initialize(
      Observation observation, ServiceContextScope scope, Activity triggeringResolution) {
    var scale = GeometryRepository.INSTANCE.scale(observation.getGeometry());
    var transaction =
        scope
            .getDigitalTwin()
            .transaction(
                Activity.of(
                    Activity.Type.INITIALIZATION, observation, "Initialization of " + observation),
                scope,
                triggeringResolution);
    try {
      if (contextualize(observation, scale, scope, this.initializationEvent, transaction)) {
        transaction.commit();
      }
    } catch (Throwable t) {
      transaction.fail(t);
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
   * @param transaction
   * @return
   */
  private boolean contextualize(
      Observation observation,
      Geometry geometry,
      ServiceContextScope scope,
      EventImpl causingEvent,
      DigitalTwin.Transaction transaction) {

    var knowledgeGraph = scope.getDigitalTwin().getKnowledgeGraph();

    // follow the dependency chain first, then execute self
    Map<Integer, List<Callable<Boolean>>> tasks = new HashMap<>();
    for (var affected :
        knowledgeGraph
            .query(Observation.class, scope)
            .target(observation)
            .along(GraphModel.Relationship.AFFECTS)
            .run(scope)) {

      var relationship =
          knowledgeGraph
              .query(KnowledgeGraph.Link.class, scope)
              .between(affected, observation, GraphModel.Relationship.AFFECTS)
              .peek(scope);

      var sequence = 0;
      if (relationship.isPresent()) {
        sequence =
            relationship.get().properties().get(/* TODO use formal property */ "sequence", 0);
      }

      tasks
          .computeIfAbsent(sequence, n -> new ArrayList<>())
          .add(() -> contextualize(affected, geometry, scope, causingEvent, transaction));
    }

    var sortedTasks =
        tasks.entrySet().stream()
            .sorted((i1, i2) -> i1.getKey().compareTo(i2.getKey()))
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
                  })) {}
        } catch (Throwable t) {
          scope.error(t);
          return false;
        }
    }

    /*
     * The actual execution for self
     */
    var executor = executors.getIfPresent(observation.getId());
    if (executor != null) {
      executor.apply(geometry, causingEvent, scope);
    }

    return true;
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
    timeEmitter.updateEvents(tStart, tEnd, time.getResolution());
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
   * <p>The observation should also know if it's a dependent or not, in which case only actual
   * observation events only affects it, given that contextualization actions are handled through
   * the influence diagram in the DT.
   *
   * @param id
   * @param type
   * @param start
   * @param end
   * @param activity the activity that made the registration
   */
  public record Registration(
      long id,
      SemanticType type,
      long start,
      long end,
      Time.Resolution resolution,
      Activity activity) {}

  /**
   * Event should have a type enum INITIALIZATION, TIME or EVENT (extendible: can have VISIT when a
   * new DT is connected for example).
   */
  public static class EventImpl implements Event {

    private long start;
    private long end;
    private final Type type;
    private final Observation event;

    private EventImpl() {
      type = Type.INITIALIZATION;
      event = null;
    }

    public EventImpl(long start, long end, Time.Resolution resolution) {
      type = Type.TEMPORAL_TRANSITION;
      this.start = start;
      this.end = end;
      event = null;
    }

    @Override
    public long getStart() {
      return start;
    }

    public void setStart(long start) {
      this.start = start;
    }

    @Override
    public long getEnd() {
      return end;
    }

    public void setEnd(long end) {
      this.end = end;
    }

    @Override
    public Type getType() {
      return type;
    }

    @Override
    public Observation getEvent() {
      return event;
    }

    @Override
    public String toString() {
      return type.toString();
    }
  }

  public void post(EventImpl event) {
    processor.emitNext(
        event,
        new Sinks.EmitFailureHandler() {
          @Override
          public boolean onEmitFailure(SignalType signalType, Sinks.EmitResult emitResult) {
            return false;
          }
        });
  }

  private Boolean checkApplies(Registration observation, EventImpl event) {
    //    boolean ok = !observation.name.endsWith("2");
    System.out.println("Checking " + observation + " against " + event);
    // TODO
    return true;
  }

  /**
   * These are guaranteed synchronous. Communication between actors will be synchronous at the actor
   * level, not at the scheduler level, so the actor system remains necessary.
   *
   * @param registration
   * @param event
   */
  private void handleEvent(Registration registration, EventImpl event) {
    System.out.println(registration + " got event " + event);
    if (event.type == EventImpl.Type.INITIALIZATION) {
      var observation = rootScope.getObservation(registration.id());
      if (observation != null) {
        initialize(observation, rootScope.of(observation), registration.activity());
      }
    }
  }

  public static void main(String[] dio) {

    var scheduler = new SchedulerImpl(null, null);
    AtomicInteger obsId = new AtomicInteger(1);

    Utils.Java.repl(
        "> ",
        s -> {
          //          switch (s) {
          //            // add a new observation and subscribe it to events
          //            case "+" ->
          //                scheduler.register(
          //                    new Registration(
          //                        "Obs" + obsId.getAndIncrement(),
          //                        "Concept",
          //                        SemanticType.AGENT,
          //                        System.currentTimeMillis(),
          //                        -1L));
          //            // send init event
          //            case "i" -> scheduler.post(new Event());
          //            // send time event between now and 1s after
          //            case "t" ->
          //                scheduler.post(
          //                    new Event(System.currentTimeMillis(), System.currentTimeMillis() +
          // 1000));
          //          }
        });
  }
}
