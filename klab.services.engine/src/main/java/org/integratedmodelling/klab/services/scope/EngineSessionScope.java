package org.integratedmodelling.klab.services.scope;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.authentication.scope.SessionScope;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.actors.messages.user.CreateContext;

public class EngineSessionScope extends EngineScope implements SessionScope {

    private Status status = Status.STARTED;
    private String name;

    public void setName(String name) {
        this.name = name;
    }

    EngineSessionScope(EngineScope parent) {
        super(parent);
    }

    @Override
    public Geometry getGeometry() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ContextScope createContext(String contextId) {

        final EngineContextScope ret = new EngineContextScope(this);
        ret.setName(contextId);
        ret.setStatus(Status.WAITING);
        Ref contextAgent = this.getAgent().ask(new CreateContext(ret, contextId), Ref.class);
        if (!contextAgent.isEmpty()) {
            ret.setStatus(Status.STARTED);
            ret.setAgent(contextAgent);
        } else {
            ret.setStatus(Status.ABORTED);
        }
        return ret;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Status getStatus() {
        return this.status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public <T extends KlabService> T getService(Class<T> serviceClass) {
        // TODO
        return parentScope.getService(serviceClass);
    }

}
