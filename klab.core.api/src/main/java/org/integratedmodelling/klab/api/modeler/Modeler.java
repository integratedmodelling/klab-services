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
     * A modeler runs under one user, and its engine is locked to that scope.
     *
     * @return
     */
    UserScope getUser();

    /**
     * The modeler handles the engine, which should have a single, unchanging user scope.
     *
     * @return
     */
    Engine getEngine();

    /**
     * Dispatch a UI event to all reactors, which will have been registered upon creation by the
     * implementation. This does not reach outside the modeler, therefore the payload can be a pointer to
     * something large unless the modeler is implemented in a distributed fashion.
     *
     * @param event
     * @param payload
     */
    void dispatch(UIReactor.UIEvent event, Object payload);

    /**
     * Register an object (possibly a {@link UIReactor}) so that it can be informed of all UI-relevant events.
     * Registration should inspect the reactor for annotated event methods, which must take the parameters
     * specified in the event. If the object doesn't implement UIReactor, only the methods are analyzed and
     * wired to the event manager.
     *
     * @param reactor
     */
    void register(Object reactor);

    /**
     * Unregister the passed reactor.
     *
     * @param reactor
     */
    void unregister(Object reactor);
}
