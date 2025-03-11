package org.integratedmodelling.klab.api.digitaltwin;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;

import java.util.Collection;
import java.util.function.Function;

/**
 * Scheduler for arbitrary actions that can be registered to happen at given transitions, in either
 * real or mock time. The scheduler is basically the event manager in the {@link DigitalTwin} and
 * operates by creating events after registering instantiators, including creating Time events when
 * they have consequences. The events are sent back to the scheduler after resolution, and the
 * scheduler causes consequences in order of execution. Executor should enable different concurrency
 * models (fully concurrent, fully synchronous both overall and per registration)
 *
 * <p>When placed in the DT, it gets access to the knowledge graph and reacts to committed
 * (successful) changes in it. It will ingest all observations and decide if other observations are
 * warranted. These may include time events, which are sent for resolution and affect
 *
 * <ul>
 *   <li>Observer geometries
 *   <li>All root observation geometries
 *   <li>All process and "each event" successful observations
 * </ul>
 *
 * When a geometry with occurrent time OR an occurrent observation is seen, it will activate and
 * register the relevant schedules, possibly changing the resolution to accommodate all time events
 * that concern it. Based on the time of expiration (if any) of the incoming time extent, it may
 * instruct the KnowledgeGraph to replay all the events that can affect the new observation. If the
 * final time is open and real time extents are seen, the clock will start moving and anything
 * situated in the past will be replayed as needed.
 *
 * <p>The KG notifies the scheduler of all singular events that enter the KG so that they can be
 * dispatched to them. When notified of a quality observation X, the scheduler may reply with a
 * "change in X" observable that is sent back to the runtime for resolution. The same may happen
 * based on the temporal reasoning and the changes in scheduling strategy implied by the new
 * observation.
 *
 * <p>Time is seen as the instantiation of time period events. The scheduler will send them to the
 * runtime for resolution only when they are consequential (either now or in the past). *
 *
 * @author ferdinando.villa
 */
public interface Scheduler {

  /**
   * Called when a new observation has been resolved. The scheduler will adapt its schedule to
   * include the new observation, possibly replaying events that concern it in the past. It will
   * determine if new observations are needed and send them for resolution through the scope, which
   * will send them back to submit(). After that has happened, the scheduler will determine the
   * consequences of any resulting events and call the scope to carry on any necessary computations
   * and update the knowledge graph.
   *
   * @param observation
   * @return
   */
  void submit(Observation observation);

  void registerExecutor(Observation observation, Function<Geometry, Boolean> executor);

  /**
   * The scheduler keeps the first time instant seen in the DT. This can change during the lifetime
   * of the {@link DigitalTwin}.
   *
   * @return
   */
  TimeInstant epochStart();

  /**
   * The scheduler keeps the last time instant seen in the DT. This can change during the lifetime
   * of the {@link DigitalTwin}.
   *
   * @return
   */
  TimeInstant epochEnd();

  /**
   * The scheduler keeps the resolution needed to represent events that involve all the observations
   * seen so far. This can change during the lifetime of the {@link DigitalTwin}.
   *
   * @return
   */
  Time.Resolution resolution();
}
