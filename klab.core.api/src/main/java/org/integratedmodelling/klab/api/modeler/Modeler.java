package org.integratedmodelling.klab.api.modeler;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.scope.UserScope;

/**
 * The modeler is a singleton that starts the EngineManager, sets up and organizes the views and their
 * configuration, maintains any persistent UI status and preferences, and provides dispatching services for
 * all UIReactors. All UIReactors have access to the modeler so they can dispatch events if needed.
 * <p>
 * The modeler wraps a k.LAB {@link Engine} that handles authentication, scope management and all services,
 * including the management of the "current" engine in its
 * {@link org.integratedmodelling.klab.api.scope.UserScope scope}. The modeler is in charge of listening to
 * all message from the current services (and potentially from others) and turn them into UI events that are
 * dispatched to the UI reactors registered with it. Reactors can be of any type (their API is inspected for
 * reactor annotated methods) but if they implement UIReactor they get more functionality.
 */
public interface Modeler {

    /**
     * A modeler runs under one user at a time. The authentication view may be used to authenticate new users
     * or switch between available identities. This should never be null: in case of no authentication, the
     * anonymous user should be returned.
     *
     * @return the currently authenticated user or Anonymous.
     */
    UserScope getUser();

    /**
     * The modeler handles the engine, which will authenticate the user scope provided with all the services
     * and use it for its operation.
     *
     * @return the engine. Never null.
     */
    Engine getEngine();

    /**
     * Dispatch a UI event to all reactors, which will have been registered upon creation by the
     * implementation. This does not reach outside the modeler, therefore the payload can be a pointer to
     * something large unless the modeler is implemented in a distributed fashion.
     *
     * @param sender  the view that dispatched the event. It's possible that workbench-level event pass null
     *                here.
     * @param event   the event. Parameters must correspond to the event's payload class.
     * @param payload the payload. If multiple objects are sent, the payload is collected into a list.
     */
    void dispatch(UIReactor sender, UIReactor.UIEvent event, Object... payload);

    /**
     * Register a view instance (implementing {@link UIReactor}) so that it can be informed of all UI-relevant
     * events. The view interfaces specify default methods that turn UI actions into events which go back to
     * the modeler for wiring.
     * <p>
     * Registration should inspect the reactor for annotated event methods, which must take the parameters
     * specified in the event. If source analysis is enabled (e.g. when the view is specified through k.Actors
     * or JSON), any of the actions defined in the interface and not called in the implementation should be
     * flagged as warning.
     *
     * @param reactor
     */
    void register(UIReactor reactor);

    /**
     * Unregister the passed reactor.
     *
     * @param reactor
     */
    void unregister(UIReactor reactor);
}
