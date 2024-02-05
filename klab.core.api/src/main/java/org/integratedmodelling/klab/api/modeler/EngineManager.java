package org.integratedmodelling.klab.api.modeler;

/**
 * The engine manager provides handles authentication, scope management and all services. It also maintains a
 * "current" service for each service type and keeps the list of the active services and the correspondent
 * permissions. Users may switch "current" service using the UI, and the engine will send UI events to inform
 * all the views.
 * <p>
 * The modeler's engine manager is in charge of listening to all message from the current services (and
 * potentially from others) and turn them into UI events that are dispatched to the UIReactors.
 */
public interface EngineManager extends UIReactor {
}
