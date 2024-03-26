package org.integratedmodelling.common.view;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.view.PanelController;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.ViewController;
import org.integratedmodelling.klab.api.view.annotations.UIActionHandler;
import org.integratedmodelling.klab.api.view.annotations.UIEventHandler;
import org.integratedmodelling.klab.api.view.annotations.UIView;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.springframework.core.annotation.AnnotationUtils;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Base abstract {@link UIController} class that implements annotation-driven (un)registration of
 * {@link UIReactor}s and event dispatching.
 */
public abstract class AbstractUIController implements UIController {

    private class EventReactor {

        List<Class<?>> parameterClasses = new ArrayList<>();
        Method method;
        UIReactor reactor;

        class EventReactionEdge extends DefaultEdge {
            UIReactor.UIEvent event;
        }

        /**
         * The interaction graph detailing who sends and receives what and from/to whom. Created after the
         * views before boot, gets updated when a panel is created or removed.
         */
        Graph<UIReactor, EventReactionEdge> interactionGraph =
                new DefaultDirectedGraph<>(EventReactionEdge.class);

        public EventReactor(UIReactor reactor, Method method) {
            this.reactor = reactor;
            this.method = method;
            this.parameterClasses.addAll(Arrays.stream(method.getParameterTypes()).toList());
        }

        public Object[] reorderArguments(@Nullable UIReactor sender, Object[] payload) {
            // TODO reorder and fill in the arguments as needed
            return payload;
        }

        public Object call(UIReactor sender, Object... payload) {
            try {
                return method.invoke(reactor, reorderArguments(sender, payload));
            } catch (Exception e) {
                scope().error(e);
            }
            return null;
        }
    }

    /**
     * Reactors to each event are registered here
     */
    Map<UIReactor.UIEvent, List<EventReactor>> reactors = new HashMap<>();
    private Map<UIReactor.Type, ViewController> views = new HashMap<>();
    private Engine engine;

    protected AbstractUIController() {

    }

    /**
     * Create the engine. Do not boot it! It will be booted when {@link #boot()} is called.
     *
     * @return
     */
    public abstract Engine createEngine();

    @Override
    public Engine engine() {
        return engine;
    }

    /**
     * Boot the engine asynchronously after installing the needed listeners. Must be called by implementors
     * after creation.
     */
    public void boot() {
        engine = createEngine();
        createView();
        createViewGraph();
        engine.addEventListener(this::processMessage);
        engine.boot();
    }

    /**
     * After all views were registered, process views and their links so that events can be properly routed.
     */
    private void createViewGraph() {
        // TODO build the event routing strategy based on the annotations
        for (var view : views.values()) {

        }
    }

    /**
     * All the registration of views should happen here. Panels are created on demand but views must pre-exist
     * before boot, potentially in a hidden state.
     */
    protected abstract void createView();

    /**
     * Translate k.LAB events into relevant UI events and dispatch them, routing through the view graph. If
     * overridden, most implementation should make sure that super is called.
     *
     * @param scope
     * @param message
     */
    protected void processMessage(Scope scope, Message message) {
        switch (message.getMessageClass()) {
            case Void -> {
            }
            case UserInterface -> {
                // shouldn't happen
            }
            case UserContextChange -> {
            }
            case UserContextDefinition -> {
            }
            case ServiceLifecycle -> {
                switch (message.getMessageType()) {
                    case ServiceUnavailable -> dispatch(null, UIReactor.UIEvent.ServiceUnavailable,
                            message.getPayload(Object.class));
                    case ServiceAvailable -> dispatch(null, UIReactor.UIEvent.ServiceAvailable,
                            message.getPayload(Object.class));
                    case ServiceInitializing -> dispatch(null, UIReactor.UIEvent.ServiceStarting,
                            message.getPayload(Object.class));
                    default -> {
                    }
                }
            }
            case EngineLifecycle -> {
                // TODO engine ready event and status
            }
            case KimLifecycle -> {
            }
            case ResourceLifecycle -> {
            }
            case ProjectLifecycle -> {
            }
            case Authorization -> {
            }
            case TaskLifecycle -> {
            }
            case ObservationLifecycle -> {
            }
            case SessionLifecycle -> {
            }
            case UnitTests -> {
            }
            case Notification -> {
            }
            case Search -> {
            }
            case Query -> {
            }
            case Run -> {
            }
            case ViewActor -> {
            }
            case ActorCommunication -> {
            }
        }
        ;
        System.out.println("AHA " + message);
    }

    @Override
    public void dispatch(UIReactor sender, UIReactor.UIEvent event, Object... payload) {
        var rs = reactors.get(event);
        if (rs != null) {
            for (var desc : rs) {
                // TODO debug?
                desc.call(sender, payload);
            }
        }
    }

    /**
     * Define the scope to use to report engine issues.
     *
     * @return
     */
    protected abstract Scope scope();

    @Override
    public void registerViewController(ViewController reactor) {

        var viewAnnotation = AnnotationUtils.findAnnotation(reactor.getClass(), UIView.class);
        if (viewAnnotation == null) {
            throw new KlabInternalErrorException("View class " + reactor.getClass().getCanonicalName() + " "
                    + "is not annotated with UIView");
        }
        if (views.containsKey(viewAnnotation.value())) {
            throw new KlabInternalErrorException("View class " + reactor.getClass().getCanonicalName() + " "
                    + " adds duplicated view type " + viewAnnotation.value());
        }

        views.put(viewAnnotation.value(), reactor);

        for (var method : reactor.getClass().getDeclaredMethods()) {
            var eventHandlerDefinition = AnnotationUtils.findAnnotation(method, UIEventHandler.class);
            if (eventHandlerDefinition != null) {
                var key = eventHandlerDefinition.value();
                var descriptor = new EventReactor(reactor, method);
                descriptor.method = method;
                descriptor.reactor = reactor;
                // TODO validate the argument list w.r.t. the event payload class!
                this.reactors.computeIfAbsent(key, k -> new ArrayList<>()).add(descriptor);

                // TODO update action graph

            }
            var actionHandlerDefinition = AnnotationUtils.findAnnotation(method, UIActionHandler.class);
            if (actionHandlerDefinition != null) {

                // TODO update action graph

            }
        }
    }

    @Override
    public <T extends ViewController<?>> T viewController(Class<T> controllerClass) {
        for (var view : views.values()) {
            if (controllerClass.isAssignableFrom(view.getClass())) {
                return (T) view;
            }
        }
        return null;
    }

    @Override
    public void unregister(UIReactor reactor) {
        for (var key : reactors.keySet()) {
            reactors.get(key).remove(reactor);
        }
    }

    @Override
    public <T> void open(PanelController<T, ?> panel, T payload) {

    }

    /**
     * Empty implementation of storeView. Override to implement/
     *
     * @param changedElements
     */
    @Override
    public void storeView(Object... changedElements) {
        scope().debug("Storing view");
    }
}
