package org.integratedmodelling.common.view;

import org.integratedmodelling.common.services.client.engine.EngineClient;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.view.*;
import org.integratedmodelling.klab.api.view.annotations.UIActionHandler;
import org.integratedmodelling.klab.api.view.annotations.UIEventHandler;
import org.integratedmodelling.klab.api.view.annotations.UIPanelController;
import org.integratedmodelling.klab.api.view.annotations.UIViewController;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Base abstract {@link UIController} class that implements annotation-driven (un)registration of
 * {@link UIReactor}s and event dispatching.
 */
public abstract class AbstractUIController implements UIController {

    /**
     * All events that the UI reacts to. Used to filter the engine events so they are not dispatched unless
     * something is listening.
     */
    private Set<UIReactor.UIEvent> relevantEvents = EnumSet.noneOf(UIReactor.UIEvent.class);

    private class EventReactor {

        List<Class<?>> parameterClasses = new ArrayList<>();
        Method method;
        UIReactor reactor;
        Queue<Pair<UIReactor, Object[]>> messageQueue = new LinkedBlockingDeque<>(128);

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

        public void dispatchPendingTasks() {
            while (!messageQueue.isEmpty()) {
                var message = messageQueue.remove();
                callMessage(message.getFirst(), message.getSecond());
            }
        }


        public Object call(UIReactor sender, Object... payload) {

            if (reactor instanceof AbstractUIViewController<?> viewController) {
                if (viewController.view() == null) {
                    // put away the messages in the synchronous queue
                    // FIXME it takes another message (with a non-null view) to empty a queue; messages
                    //  pre-view creation get lost if there are no messages after
                    messageQueue.add(Pair.of(sender, payload));
                    return null;
                } else {
                    while (!messageQueue.isEmpty()) {
                        var message = messageQueue.remove();
                        callMessage(message.getFirst(), message.getSecond());
                    }
                }
            }

            return callMessage(sender, payload);
        }

        private Object callMessage(UIReactor sender, Object... payload) {
            var args = Utils.Collections.reorderArguments(method.getParameterTypes(), payload);
            if (args == null && payload != null) {
                return null;
            }
            try {
                return method.invoke(reactor, args);
            } catch (Exception e) {
                scope().error(e);
            }
            return null;
        }
    }

    /**
     * Reactors to each event are registered here
     */
    Map<UIReactor.UIEvent, List<EventReactor>> reactors = Collections.synchronizedMap(new HashMap<>());
    Map<UIReactor.Type, Class<? extends PanelController<?, ?>>> panelControllerClasses =
            Collections.synchronizedMap(new HashMap<>());
    private Map<UIReactor.Type, ViewController<?>> views = new HashMap<>();
    private Engine engine;

    protected AbstractUIController() {
        createView();
        createViewGraph();
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
        if (engine instanceof EngineClient engineClient) {
            engineClient.addScopeListener(this::processMessage);
        } else {
            engine.serviceScope().warn("Engine is not default: will not communicate engine messages to UI");
        }
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
    protected void processMessage(Channel scope, Message message) {

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
                    case ServiceUnavailable -> dispatch(this, UIReactor.UIEvent.ServiceUnavailable,
                            message.getPayload(Object.class));
                    case ServiceAvailable -> dispatch(this, UIReactor.UIEvent.ServiceAvailable,
                            message.getPayload(Object.class));
                    case ServiceInitializing -> dispatch(this, UIReactor.UIEvent.ServiceStarting,
                            message.getPayload(Object.class));
                    case ServiceStatus -> dispatch(this, UIReactor.UIEvent.ServiceStatus,
                            message.getPayload(KlabService.ServiceStatus.class));
                    default -> {
                    }
                }
            }
            case EngineLifecycle -> {
                // TODO engine ready event and status
                switch (message.getMessageType()) {
                    case ServiceUnavailable -> {
                        dispatch(this, UIReactor.UIEvent.EngineUnavailable,
                                message.getPayload(Object.class));
                    }
                    case ServiceAvailable -> {
                        dispatch(this, UIReactor.UIEvent.EngineAvailable,
                                message.getPayload(Object.class));
                    }
                    case ServiceInitializing -> {
                        dispatch(this, UIReactor.UIEvent.EngineStarting,
                                message.getPayload(Object.class));
                    }
                    case ServiceStatus -> {
                        dispatch(this, UIReactor.UIEvent.ServiceStatus,
                                message.getPayload(KlabService.ServiceStatus.class));
                    }
                    case UsingDistribution -> {
                        dispatch(this, UIReactor.UIEvent.DistributionSelected,
                                message.getPayload(Distribution.class));
                    }
                    default -> {
                    }
                }
            }
            case KimLifecycle -> {
            }
            case ResourceLifecycle -> {
            }
            case ProjectLifecycle -> {
            }
            case Authorization -> {
                if (message.is(Message.MessageType.UserAuthorized)) {
                    dispatch(this, UIReactor.UIEvent.UserAuthenticated,
                            message.getPayload(UserIdentity.class));
                }
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

    }

    @Override
    public void dispatch(UIReactor sender, UIReactor.UIEvent event, Object... payload) {
        if (relevantEvents.contains(event)) {
            var rs = reactors.get(event);
            if (rs != null) {
                for (var desc : rs) {
                    desc.call(sender, payload);
                }
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
    public void registerPanelControllerClass(Class<? extends PanelController<?, ?>> cls) {
        var panelAnnotation = AnnotationUtils.findAnnotation(cls, UIPanelController.class);
        if (panelAnnotation == null) {
            throw new KlabInternalErrorException("Panel class " + cls.getCanonicalName() + " "
                    + "is not annotated with UIPanel");
        }
        if (panelControllerClasses.containsKey(panelAnnotation.value())) {
            throw new KlabInternalErrorException("Panel class " + cls.getCanonicalName() + " "
                    + " adds duplicated panel type " + panelAnnotation.value());
        }
        panelControllerClasses.put(panelAnnotation.value(), cls);
    }

    @Override
    public void registerViewController(ViewController<?> reactor) {

        var viewAnnotation = AnnotationUtils.findAnnotation(reactor.getClass(), UIViewController.class);
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
                relevantEvents.add(eventHandlerDefinition.value());
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
    public <P, T extends PanelView<P>> T openPanel(Class<T> panelType, P payload) {
        // create and register the panel controller, which must unregister itself when the panel is closed.
        // This must be hooked into a view-side controller somehow, as we cannot create
        // the panel view itself.
        Class<?> controllerClass = null;
        for (Class<?> cls : panelControllerClasses.values()) {


            var panelViewClass =
                    ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
            if (cls.isAssignableFrom(panelType)) {
                try {
                    controllerClass = Class.forName(panelViewClass.getTypeName());
                } catch (ClassNotFoundException e) {
                    // screw it
                }
            }
        }

        if (controllerClass != null) {
            System.out.println("ME SPUPAZZO ER " + panelType + " CON " + controllerClass);
        }


        System.out.println("ME SPUPAZZO ER PAYLOAD " + panelType + ": " + payload);
        return null;
    }

    /**
     * Convenience method used by inheritors
     *
     * @param serviceId
     * @param serviceClass
     * @param <S>
     * @return
     */
    public <S extends KlabService> S serviceById(String serviceId, Class<S> serviceClass) {
        for (var service : engine.serviceScope().getServices(serviceClass)) {
            if (serviceId.equals(service.serviceId())) {
                return service;
            }
        }
        return null;
    }


    public <T extends View> void dispatchPendingTasks(AbstractUIViewController<T> viewController) {

        for (var reactorList : reactors.values()) {
            for (var reactor : reactorList) {
                if (reactor.reactor == viewController && !reactor.messageQueue.isEmpty()) {
                    reactor.dispatchPendingTasks();
                }
            }
        }
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
