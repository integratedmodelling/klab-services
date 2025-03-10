package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.utils.Utils;
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
public class SchedulerTest implements Scheduler {

  private long epochStart = 0L;
  private long epochEnd = 0L;
  private Time.Resolution resolution = null;

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
  private LoadingCache<Long, Function<Geometry, Boolean>> executors =
      CacheBuilder.newBuilder()
          .maximumSize(200)
          // .expireAfterAccess(10, TimeUnit.MINUTES)
          .build(
              new CacheLoader<Long, Function<Geometry, Boolean>>() {
                public Function<Geometry, Boolean> load(Long key) {
                  // TODO reconstruct the executor from actuator in the knowledge graph.
                  return g -> true;
                }
              });

  @Override
  public void submit(Observation observation) {
    var registration =
            // TODO analyze the geometry and the events affecting; record initiation conditions for the
            //  filter
        new Registration(
            observation.getName(),
            observation.getObservable().getSemantics().getUrn(),
            SemanticType.fundamentalType(observation.getObservable().getSemantics().getType()),
            // TODO!
            0,
            // TODO!
            0);
    register(registration);
  }

  public void registerExecutor(Observation observation, Function<Geometry, Boolean> executor) {
    executors.put(observation.getId(), executor);
  }

  private void register(Registration registration) {
    processor
        .asFlux()
        .filterWhen(event -> Mono.just(checkApplies(registration, event)))
        .subscribe(e -> handleEvent(registration, e));
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
   * TODO the registrations should be cached and reconstructed from the KG based on the resolution
   *  status and last time of update.
   *
   * <p>The observation should also know if it's a dependent or not, in which case only actual
   * observation events only affects it, given that contextualization actions are handled through
   * the influence diagram in the DT.
   *
   * @param name
   * @param concept
   * @param type
   * @param start
   * @param end
   */
  public record Registration(
      String name, String concept, SemanticType type, long start, long end) {}

  /**
   * Event should have a type enum INITIALIZATION, TIME or EVENT (extendible: can have VISIT when a
   * new DT is connected for example).
   */
  public static class Event {

    String type;

    public Event(Registration observation) {
      type = observation.toString();
    }

    public Event() {
      type = "INIT";
    }

    public Event(long start, long end) {
      type = "TIME " + start + "-" + end;
    }

    @Override
    public String toString() {
      return type;
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
    boolean ok = !observation.name.endsWith("2");
    System.out.println(
        "Checking " + observation + " against " + event + ": " + (ok ? "OK" : "NAAH"));
    return ok;
  }

  /**
   * These are guaranteed synchronous. Communication between actors will be synchronous at the actor
   * level, not at the scheduler level, so the actor system remains necessary.
   *
   * @param observation
   * @param e
   */
  private void handleEvent(Registration observation, Event e) {
    System.out.println(observation + " got event " + e);
    // TODO follow influence diagram, any multiple outgoing edges are
  }

  public static void main(String[] dio) {

    var scheduler = new SchedulerTest();
    AtomicInteger obsId = new AtomicInteger(1);

    Utils.Java.repl(
        "> ",
        s -> {
          switch (s) {
            // add a new observation and subscribe it to events
            case "+" ->
                scheduler.register(
                    new Registration(
                        "Obs" + obsId.getAndIncrement(),
                        "Concept",
                        SemanticType.AGENT,
                        System.currentTimeMillis(),
                        -1L));
            // send init event
            case "i" -> scheduler.post(new Event());
            // send time event between now and 1s after
            case "t" ->
                scheduler.post(
                    new Event(System.currentTimeMillis(), System.currentTimeMillis() + 1000));
          }
        });
  }
}
