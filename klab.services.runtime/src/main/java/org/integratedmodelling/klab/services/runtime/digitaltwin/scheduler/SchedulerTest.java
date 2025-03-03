package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.utils.Utils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;

/**
 * Executors should be registered with the observations at dataflow compilation - the insertion in
 * the DT should also compile them.They should have a flag that says when the implementations can be
 * removed (init only, recompute, event-specific etc). The DT should be able to reconstruct the
 * necessary ones from the recorded actuators without a need for the dataflow being there.
 */
public class SchedulerTest {

  /**
   * TODO instead of observation, just store a pair of longs (observation ID in DT + last time of
   * update, -1, 0 or N). A third long is a key to a map of event checkers which are reused on
   * demand. A fourth could be the ID of a linked DT when the event is external.
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
  public record Observation(String name, String concept, SemanticType type, long start, long end) {}

  private static Event init = new Event();

  /**
   * Event should have a type enum INITIALIZATION, TIME or EVENT (extendible: can have VISIT when a
   * new DT is connected for example).
   */
  public static class Event {

    String type;

    public Event(Observation observation) {
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

  // TODO processor (sink) for events. We want a fully replayable, multicasting one. Events don't
  //  end up in provenance although the activities they engender do.
  private Sinks.Many<Event> processor = Sinks.many().replay().all();

  /**
   * Only subscribe the root observations - those that are not affected by anything - and those that
   * are directly affected by events. Use flags and activation for the filter so that they can be
   * removed from the chain of events when time events pass.
   *
   * @param observation
   */
  public void insert(Observation observation /*, Consumer<Observation> onEvent*/) {
    processor
        .asFlux()
        .filterWhen(event -> Mono.just(checkApplies(observation, event)))
        .subscribe(e -> handleEvent(observation, e));
  }

  private Boolean checkApplies(Observation observation, Event event) {
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
  private void handleEvent(Observation observation, Event e) {
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
            case "+" -> {
              scheduler.insert(
                  new Observation(
                      "Obs" + obsId.getAndIncrement(),
                      "Concept",
                      SemanticType.AGENT,
                      System.currentTimeMillis(),
                      -1L));
            }
            // send init event
            case "i" -> {
              scheduler.processor.emitNext(
                  init,
                  new Sinks.EmitFailureHandler() {
                    @Override
                    public boolean onEmitFailure(
                        SignalType signalType, Sinks.EmitResult emitResult) {
                      return false;
                    }
                  });
            }
            // send time event between now and 1s after
            case "t" -> {
              scheduler.processor.emitNext(
                  new Event(System.currentTimeMillis(), System.currentTimeMillis() + 1000),
                  new Sinks.EmitFailureHandler() {
                    @Override
                    public boolean onEmitFailure(
                        SignalType signalType, Sinks.EmitResult emitResult) {
                      return false;
                    }
                  });
            }
          }
        });
  }
}
