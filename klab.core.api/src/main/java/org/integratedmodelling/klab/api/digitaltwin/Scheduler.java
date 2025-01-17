package org.integratedmodelling.klab.api.digitaltwin;

/**
 * Scheduler for arbitrary actions that can be registered to happen at given transitions, in either
 * real or mock time. Executor should enable different concurrency models (fully concurrent, fully
 * synchronous both overall and per registration)
 *
 * <p>When placed in the DT, it ingests:
 *
 * <ul>
 *   <li>Observer geometries
 *   <li>All root observation geometries
 *   <li>All process and "each event" successful observations
 * </ul>
 *
 * Gets notified of all events that enter the KG and dispatches them. When notified of an
 * observation, may reply with a "change in" event observable to resolve.
 *
 * <p>Manages time as an instantiator of time periods, only registering those that are consequential
 * within the KG
 *
 * <p>Can replay time events for new observations of substantials whose time starts before last
 * recorded
 *
 * @author ferdinando.villa
 */
public interface Scheduler {}
