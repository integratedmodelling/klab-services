package org.integratedmodelling.klab.modeler;

import org.integratedmodelling.common.services.client.engine.EngineClient;
import org.integratedmodelling.common.view.AbstractUIController;
import org.integratedmodelling.klab.api.configuration.PropertyHolder;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.modeler.Modeler;
import org.integratedmodelling.klab.modeler.configuration.EngineConfiguration;

import java.util.function.BiConsumer;

/**
 * A {@link UIController} specialized to provide and orchestrate the views and panels that compose the
 * k.Modeler application. Uses an {@link org.integratedmodelling.common.services.client.engine.EngineClient}
 * which will connect to local services if available. Also handles one or more users and keeps a catalog of
 * sessions and contexts, tagging the "current" one in focus in the UI.
 * <p>
 * Call {@link #boot()} in a separate thread when the view is initialized and let the UI events do the rest.
 */
public class ModelerImpl extends AbstractUIController implements Modeler, PropertyHolder {


    private final BiConsumer<Scope, Message>[] listeners;
    EngineConfiguration workbench;
    private ContextScope currentContext;
    private SessionScope currentSession;

    public ModelerImpl(BiConsumer<Scope, Message>... listeners) {
        this.listeners = listeners;
        // TODO instantiate all the default views. They exist independent of what happens in the view.

        // TODO read the workbench config
    }

    @Override
    public Engine createEngine() {
        var ret = new EngineClient();
        //        ret.addEventListener((scope, message) -> onMessage(scope, message));
        if (this.listeners != null) {
            for (var listener : this.listeners) {
                ret.addEventListener(listener);
            }
        }
        return ret;
    }

    //    private void onMessage(Scope scope, Message message) {
    //        // TODO react to events
    //        System.out.println("AHA " + message);
    //    }

    @Override
    public void setOption(Option option, Object... payload) {
        // TODO validate option
        // TODO react
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
        return engine() == null || engine().getUsers().isEmpty() ? null : engine().getUsers().getFirst();
    }

    public SessionScope currentSession() {
        // TODO
        return currentSession;
    }

    public ContextScope currentContext() {
        // TODO
        return currentContext;
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


    @Override
    public void importProject(String projectUrl, String workspace, String service) {
    }


}