package org.integratedmodelling.klab.modeler;

import org.integratedmodelling.common.services.client.engine.EngineClient;
import org.integratedmodelling.common.view.AbstractUIController;
import org.integratedmodelling.klab.api.configuration.PropertyHolder;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.modeler.configuration.EngineConfiguration;

/**
 * A {@link UIController} specialized to provide and orchestrate the views and panels that compose the
 * k.Modeler application. Uses an {@link EngineClient} which will connect to local services if available. Also
 * handles one or more users and keeps a catalog of sessions and contexts, tagging the "current" one in focus
 * in the UI.
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
    public UserScope user() {
        return ((EngineClient) engine()).getUser();
    }

    @Override
    protected Scope scope() {
        return user();
    }

    @Override
    public String configurationPath() {
        return "modeler";
    }

    public UserScope currentUser() {
        // TODO
        return null;
    }

    public SessionScope currentSession() {
        // TODO
        return null;
    }

    public ContextScope currentContext() {
        // TODO
        return null;
    }

    public ContextScope context(String context) {
        // TODO named context
        return null;
    }

    public UserScope user(String username) {
        // TODO named user
        return null;
    }

    public SessionScope session(String session) {
        // TODO named session
        return null;
    }
}
