package org.integratedmodelling.klab.modeler;

import org.integratedmodelling.engine.client.EngineClient;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.scope.UserScope;

public class ModelerImpl implements UIController {

    EngineClient engine;

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
