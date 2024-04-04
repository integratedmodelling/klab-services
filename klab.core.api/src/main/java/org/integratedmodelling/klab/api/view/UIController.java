package org.integratedmodelling.klab.api.view;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.scope.UserScope;

/**
 * The UI controller is a singleton that represents the engine when it's associated to a UI. It starts the
 * EngineManager, sets up and organizes the views and their configuration, maintains any persistent UI status
 * and preferences, and provides dispatching services for all UIReactors. All UIReactors have access to the
 * controller, so they can dispatch events.
 * <p>
 * The controller wraps a k.LAB {@link Engine} that handles authentication, scope management and all services,
 * including the management of the "current" engine in its
 * {@link org.integratedmodelling.klab.api.scope.UserScope scope}. The controller is in charge of listening to
 * all message from the current services (and potentially from others) and turn them into UI events that are
 * dispatched to the UI reactors registered with it. Reactors can be of any type (their API is inspected for
 * reactor annotated methods) but if they implement UIReactor they get more functionality.
 * <p>
 * The view implementation should: 1. create a UIController as a singleton and initialize it properly
 * (asynchronously). 2. setup the UI for the workbench
 * <p>
 * Each UIView and UIPanel in the view implementation must: 0. if a Panel, provide a construction that uses
 * the target object(s) 1. implement the event reactors 2. call the action functions.
 */
public interface UIController extends UIReactor {

    /**
     * A controller runs under one user at a time. The authentication view may be used to authenticate new
     * users or switch between available identities. This should never be null: in case of no authentication,
     * the anonymous user should be returned.
     *
     * @return the currently authenticated user or Anonymous.
     */
    UserScope user();

    /**
     * The controller handles the engine, which will authenticate the user scope provided with all the
     * services and use it for its operation.
     *
     * @return the engine. Never null.
     */
    Engine engine();

    /**
     * Boot the engine if needed, create the view if needed, and start processing events.
     */
    void boot();

    /**
     * Dispatch a UI event to all reactors, which will have been registered upon creation by the
     * implementation. This does not reach outside the controller, therefore the payload can be a pointer to
     * something large unless the controller is implemented in a distributed fashion.
     *
     * @param sender  the view that dispatched the event. It's possible that workbench-level event pass null
     *                here.
     * @param event   the event. Parameters must correspond to the event's payload class.
     * @param payload the payload. If multiple objects are sent, the payload is collected into a list.
     */
    void dispatch(UIReactor sender, UIReactor.UIEvent event, Object... payload);

    /**
     * Register a view controller (normally implementing {@link UI} or {@link ViewController}) so that it can
     * be informed of all UI-relevant events. The view interfaces specify default methods that turn UI actions
     * into events which go back to the modeler for wiring. This must be done for all persistent reactive
     * objects, including all views, prior to booting the UI: views pre-exist although they may be invisible
     * or inactive. Panels are opened but not registered.
     * <p>
     * There can only be one view per view annotation declared. only {@link PanelController}s can be present
     * in multiple instances. If the
     * {@link org.integratedmodelling.klab.api.view.annotations.UIViewController} annotation isn't present, no
     * check for duplication is done.
     * <p>
     * Registration inspects the reactor for annotated event methods, which must take the parameters
     * specified in the event. If source analysis is enabled (e.g. when the view is specified through k.Actors
     * or JSON), any of the actions defined in the interface and not called in the implementation should be
     * flagged as warning.
     *
     * @param reactor the reactor to register. Its methods will be scanned and connected to event handling.
     *                The main UI object passed to the controller's constructor will be automatically
     *                registered.
     */
    void registerViewController(Object reactor);

    /**
     * Register a panel controller class. Differently from views, panels are created and opened on demand, so
     * the class needs to have a recognizable constructor - typically one taking the panel view (created at
     * the view side before the panel is opened) and the payload if any. Argument reordering and injection of
     * obvious other arguments will be done automatically.
     *
     * @param cls
     */
    void registerPanelControllerClass(Class<? extends PanelController<?, ?>> cls);

    void closePanel(PanelController<?, ?> controller);

    /**
     * Return the registered view controller for the passed class. Used in view implementations to register
     * themselves.
     *
     * @param controllerClass
     * @return the view controller or null
     */
    <T extends ViewController<?>> T viewController(Class<T> controllerClass);

    /**
     * Open a panel to display an object. This will create both the view and the controller, injecting
     * arguments in their constructors, and return the view.
     *
     * @param panelType a panel view class. The constructor will be handled intelligently to inject the
     *                  controller or other arguments.
     * @param payload   the object displayed in the panel. It will be passed after creation using
     *                  {@link PanelView#load(P)}.
     * @param <T>       the actual type of the panel view.
     * @param <P>       the type of the object handled by the panel. Must be assignable from the class set for
     *                  the panel.
     */
    <P, T extends PanelView<P>> T openPanel(Class<T> panelType, P payload);

    /**
     * Unregister the passed reactor.
     *
     * @param reactor
     */
    void unregister(UIReactor reactor);

    /**
     * Called whenever something has changed that has the potential of changing the entire layout of the view.
     * Pass the objects whose contents are likely to be changed for reference (could be services, assets,
     * settings etc.)
     *
     * @param changedElements
     */
    void storeView(Object... changedElements);
}
