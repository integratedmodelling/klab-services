package org.integratedmodelling.klab.modeler;

import org.integratedmodelling.engine.client.EngineClient;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.scope.UserScope;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Modeler implements UIController {

    EngineClient engine;


    private class EventReactor {
        List<Class<?>> parameterClasses = new ArrayList<>();
        Method method;
        UIReactor reactor;
    }

    /**
     * Reactors to each event are registered here
     */
    Map<UIReactor.UIEvent, List<EventReactor>> reactors = new HashMap<>();

    @Override
    public UserScope getUser() {
        return engine.getUser();
    }

    @Override
    public Engine getEngine() {
        return engine;
    }

    @Override
    public void dispatch(UIReactor sender, UIReactor.UIEvent event, Object... payload) {
        /*
        TODO the dispatcher uses the action graph to dispatch events according to the wiring detected
         */
    }

    @Override
    public void register(UIReactor reactor) {
        /*
        TODO update the action graph
         */
    }

    @Override
    public void unregister(UIReactor reactor) {
        /*
        TODO update the action graph
         */
    }
}
