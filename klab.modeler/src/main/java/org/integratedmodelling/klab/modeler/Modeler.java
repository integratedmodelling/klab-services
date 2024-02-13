package org.integratedmodelling.klab.modeler;

import org.integratedmodelling.common.view.AbstractUIController;
import org.integratedmodelling.engine.client.EngineClient;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.scope.UserScope;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link UIController} specialized to provide and orchestrate the views and panels that compose the
 * k.Modeler application. Uses an {@link EngineClient} which will connect to local services if available.
 */
public class Modeler extends AbstractUIController {


    public Modeler() {
    }

    @Override
    public Engine createEngine() {
        return new EngineClient();
    }

    @Override
    public UserScope getUser() {
        return ((EngineClient)getEngine()).getUser();
    }

    @Override
    protected Scope getScope() {
        return getUser();
    }
}
