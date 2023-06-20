package org.integratedmodelling.klab.services.actors;

import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;

public class ApplicationAgent extends SessionAgent {

    public ApplicationAgent(String name) {
        super(name);
    }

    public ApplicationAgent(KActorsBehavior application, Scope scope) {
        super(application.getName());
        run(application, scope);
    }
}
