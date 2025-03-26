package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
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
  /*
   * The event processor is a fully replayable, multicasting one with synchronized behavior.
   * Events don't end up in provenance, although the activities they engender do. The scheduler acts
   * as a provenance agent and is recorded as the agent for activities triggered by temporal events.
   */
  private final Sinks.Many<Event> processor = Sinks.many().replay().all();

  /*
   * Executors are loaded upon dataflow validation/compilation before registering the observations,
   * which triggers their usage. The cache loads actuator definitions from the knowledge graph on
   * demand and recompiles the executors if they are missing.
   */
  private LoadingCache<Long, BiFunction<Geometry, ContextScope, Boolean>> executors =
      CacheBuilder.newBuilder()
          .maximumSize(200)
          // .expireAfterAccess(10, TimeUnit.MINUTES)
          .build(
              new CacheLoader<Long, BiFunction<Geometry, ContextScope, Boolean>>() {
                public BiFunction<Geometry, ContextScope, Boolean> load(Long key) {
                  // TODO reconstruct the executor from actuator in the knowledge graph.
                  return (g, s) -> true;
                }
              });

  public SchedulerImpl(ServiceContextScope scope, DigitalTwinImpl digitalTwin) {
    this.rootScope = scope;
    this.knowledgeGraph = digitalTwin.getKnowledgeGraph();
    // The INIT event is created before anything happens and applies to every new observation
    // registered.
    post(new Event());
  }

  @Override
  public void submit(Observation observation) {

    var timeData = register(observation.getGeometry());
    var registration =
        new Registration(
            observation.getId(),
            SemanticType.fundamentalType(observation.getObservable().getSemantics().getType()),
            timeData.getFirst(),
            timeData.getSecond(),
            timeData.getThird());
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
      Observation observation, BiFunction<Geometry, ContextScope, Boolean> executor) {
    executors.put(observation.getId(), executor);
  }

  private Triple<Long, Long, Time.Resolution> register(Geometry geometry) {
    // TODO
    Time time = GeometryRepository.INSTANCE.scale(geometry).getTime();
    if (time != null) {
      return notifyTime(time);
    }
    return Triple.of(0L, 0L, null);
  }

  /**
   * This is called in response to the INIT event received by any root-level observation that was
   * successfully resolved. Successive executions of the same executors will happen by directly
   * calling {@link #contextualize(Observation, Geometry, ServiceContextScope)}
   *
   * @param observation
   */
  private void initialize(Observation observation, ServiceContextScope scope) {
    var scale = GeometryRepository.INSTANCE.scale(observation.getGeometry());
    var initializationGeometry = scale.initialization();
    if (contextualize(observation, scale, scope)) {}
  }

  private boolean contextualize(
      Observation observation, Geometry geometry, ServiceContextScope scope) {

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
        sequence = relationship.get().properties().get(/* TODO use formal property */"sequence", 0);
      }

      tasks
          .computeIfAbsent(sequence, n -> new ArrayList<>())
          .add(() -> contextualize(affected, geometry, scope));
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
      return executor.apply(geometry, scope);
//      //      scope.finalizeObservation(observation,/* contextualization,*/ ret);
//      return ret;
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
    // TODO compound resolutions, compile events

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
   */
  public record Registration(
      long id, SemanticType type, long start, long end, Time.Resolution resolution) {}

  /**
   * Event should have a type enum INITIALIZATION, TIME or EVENT (extendible: can have VISIT when a
   * new DT is connected for example).
   */
  public static class Event {

    private long start;
    private long end;

    enum Type {
      INITIALIZATION,
      TEMPORAL_TRANSITION,
      EVENT
    }

    final Type type;

    public Event() {
      type = Type.INITIALIZATION;
    }

    public Event(long start, long end, Time.Resolution resolution) {
      type = Type.TEMPORAL_TRANSITION;
      this.start = start;
      this.end = end;
    }

    @Override
    public String toString() {
      return type.toString();
    }
  }

  public void post(Event event) {
    processor.emitNext(
        event,
        new Sinks.EmitFailureHandler() {
          @Override
          public boolean onEmitFailure(SignalType signalType, Sinks.EmitResult emitResult) {
            return false;
          }
        });
  }

  private Boolean checkApplies(Registration observation, Event event) {
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
  private void handleEvent(Registration registration, Event event) {
    System.out.println(registration + " got event " + event);
    if (event.type == Event.Type.INITIALIZATION) {
      var observation = rootScope.getObservation(registration.id());
      if (observation != null
          && !observation.isResolved()) { // TODO check resolution condition and put in filter
        initialize(observation, rootScope.of(observation));
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
