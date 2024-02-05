package org.integratedmodelling.klab.api.modeler;

/**
 * The modeler is a singleton that starts the EngineManager, sets up and organizes the views and their
 * configuration, maintains any persistent UI status and preferences, and provides dispatching services for
 * all UIReactors. All UIReactors have access to the modeler.
 */
public interface Modeler {

    /**
     * Dispatch a UI event to all reactors, which will have been registered upon creation by the
     * implementation. This does not reach outside the modeler, therefore the payload can be a pointer to
     * something large unless the modeler is implemented in a distributed fashion.
     *
     * @param event
     * @param payload
     */
    void dispatch(UIReactor.UIEvent event, Object payload);

}
