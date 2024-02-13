package org.integratedmodelling.klab.modeler;

import org.integratedmodelling.common.view.AbstractUIController;
import org.integratedmodelling.engine.client.EngineClient;
import org.integratedmodelling.klab.api.configuration.PropertyHolder;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.modeler.configuration.EngineConfiguration;

import java.lang.reflect.Method;
import java.util.*;

/**
 * A {@link UIController} specialized to provide and orchestrate the views and panels that compose the
 * k.Modeler application. Uses an {@link EngineClient} which will connect to local services if available.
 * <p>
 * Call {@link #boot()} in a separate thread when the view is initialized and let the UI events do the rest.
 */
public class Modeler extends AbstractUIController implements PropertyHolder {

    EngineConfiguration workbench;

    public Modeler() {
        // TODO read the workbench config
    }

    @Override
    public Engine createEngine() {
        return new EngineClient();
    }

    @Override
    public UserScope getUser() {
        return ((EngineClient) getEngine()).getUser();
    }

    @Override
    protected Scope getScope() {
        return getUser();
    }

    @Override
    public String configurationPath() {
        return "modeler";
    }
}
