package org.integratedmodelling.klab.services.scopes;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Message;

/**
 * The service-side {@link SessionScope}. One of these will be created by {@link ServiceUserScope} at each new
 * session, script, application or test case run.
 * <p>
 * Maintained by the {@link ScopeManager}
 */
public class ServiceSessionScope extends ServiceUserScope implements SessionScope {

    private String name;

    public void setName(String name) {
        this.name = name;
    }

    ServiceSessionScope(ServiceUserScope parent) {
        super(parent);
        this.data = Parameters.create();
        this.data.putAll(parent.data);
    }

    @Override
    public ContextScope createContext(String contextName) {

        final ServiceContextScope ret = new ServiceContextScope(this);
        ret.setName(contextName);
        ret.setStatus(Status.WAITING);
        Ref contextAgent = ask(Ref.class, Message.MessageType.CreateContext, ret);
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
    public <T extends KlabService> T getService(Class<T> serviceClass) {
        // TODO
        return parentScope.getService(serviceClass);
    }


    @Override
    public void logout() {
        // TODO
    }
}
