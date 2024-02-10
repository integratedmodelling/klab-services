package org.integratedmodelling.klab.modeler;

import org.integratedmodelling.engine.client.EngineClient;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.modeler.Modeler;
import org.integratedmodelling.klab.api.modeler.UIReactor;
import org.integratedmodelling.klab.api.scope.UserScope;

public class ModelerImpl implements Modeler {

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
    public void dispatch(UIReactor.UIEvent event, Object payload) {

    }

    @Override
    public void register(Object reactor) {

    }

    @Override
    public void unregister(Object reactor) {

    }
}
