package org.integratedmodelling.common.view;

import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIActionHandler;
import org.integratedmodelling.klab.api.view.annotations.UIEventHandler;
import org.integratedmodelling.klab.api.view.annotations.UIView;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Base abstract {@link UIController} class that implements annotation-driven (un)registration of
 * {@link UIReactor}s and event dispatching.
 */
public abstract class AbstractUIController implements UIController {

    private class EventReactor {
        List<Class<?>> parameterClasses = new ArrayList<>();
        Method method;
        UIReactor reactor;

        public EventReactor(UIReactor reactor, Method method) {
            this.reactor = reactor;
            this.method = method;
            this.parameterClasses.addAll(Arrays.stream(method.getParameterTypes()).toList());
        }

        public Object[] reorderArguments(UIReactor sender, Object[] payload) {
            // TODO reorder and fill in the arguments as needed
            return payload;
        }
    }

    /**
     * Reactors to each event are registered here
     */
    Map<UIReactor.UIEvent, List<EventReactor>> reactors = new HashMap<>();
    private Engine engine;

    protected AbstractUIController() {

    }

    /**
     * Create the engine. Do not boot it! It will be booted when {@link #boot()} is called.
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
        engine.addEventListener((scope, message) -> {
            processMessage(scope, message);
        });
        engine.boot();
    }

    private void processMessage(Scope scope, Message message) {
        switch (message.getMessageClass()) {
            case Void -> {
            }
            case UserInterface -> {
            }
            case UserContextChange -> {
            }
            case UserContextDefinition -> {
            }
            case ServiceLifecycle -> {
            }
            case EngineLifecycle -> {
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
        };
        System.out.println("AHA " + message);
    }

    @Override
    public void dispatch(UIReactor sender, UIReactor.UIEvent event, Object... payload) {
        var rs = reactors.get(event);
        if (rs != null) {
            for (var desc : rs) {
                try {
                    desc.method.invoke(desc.reactor, desc.reorderArguments(sender,payload));
                } catch (Throwable e) {
                    scope().error(e);
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
    public void register(UIReactor reactor) {

        Reflections reflections = new Reflections(reactor.getClass());
        var viewDefinition = reactor.getClass().getAnnotation(UIEventHandler.class);
        // TODO compile record for the view declaration
        reflections.getMethodsAnnotatedWith(UIEventHandler.class).stream()
                   .forEach(method -> {
                       var eventHandlerDefinition = method.getAnnotation(UIEventHandler.class);
                       var key = eventHandlerDefinition.value();
                       // TODO validate the argument list w.r.t. the event payload class!
                       var descriptor = new EventReactor(reactor, method);
                       descriptor.method = method;
                       descriptor.reactor = reactor;
                       this.reactors.computeIfAbsent(key, k -> new ArrayList<>()).add(descriptor);
                   });
        reflections.getMethodsAnnotatedWith(UIActionHandler.class).stream()
                   // TODO Compile info about the actions available for the view
                   .forEach(method -> {
                       var actionHandlerDefinition = method.getAnnotation(UIActionHandler.class);
                   });

    }

    @Override
    public void unregister(UIReactor reactor) {
        for (var key : reactors.keySet()) {
            reactors.get(key).remove(reactor);
        }
    }
}
