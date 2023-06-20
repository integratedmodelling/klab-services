package org.integratedmodelling.klab.services.actors;

import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;

public class ScriptAgent extends SessionAgent {

    public ScriptAgent(String name) {
        super(name);
    }

    public ScriptAgent(KActorsBehavior application, Scope scope) {
        super(application.getName());
        run(application, scope);
    }
}
