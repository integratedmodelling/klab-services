package org.integratedmodelling.klab.services.scopes;

import java.util.HashMap;
import java.util.Map;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.runtime.kactors.messages.CreateContext;

/**
 * The service-side {@link SessionScope}. One of these will be created by {@link ServiceUserScope} at each new
 * session, script, application or test case run.
 * <p>
 * Maintained by the {@link ScopeManager}
 */
public class ServiceSessionScope extends ServiceUserScope implements SessionScope {

    private String name;
    private Map<String, ContextScope> contexts = new HashMap<>();
//    private Scale geometry;

    public void setName(String name) {
        this.name = name;
    }

    ServiceSessionScope(ServiceUserScope parent) {
        super(parent);
        this.setId(parent.getIdentity().getId() + "." + name + Utils.Names.shortUUID());
        this.data = Parameters.create();
        this.data.putAll(parent.data);
    }

//    @Override
//    public Scale getScale() {
//        return geometry;
//    }

    @Override
    public ContextScope createContext(String contextName, Object... observerData) {

        Geometry geometry = null; // TODO check in observerData
        final ServiceContextScope ret = new ServiceContextScope(this);
        ret.setName(contextName);
        ret.setStatus(Status.WAITING);
        Ref contextAgent = this.getAgent()
                               .ask(new CreateContext(ret, contextName, geometry), Ref.class);
        if (!contextAgent.isEmpty()) {
            ret.setStatus(Status.STARTED);
            ret.setAgent(contextAgent);
//            contexts.put(contextId, ret);
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
    public ContextScope getContext(String urn) {
        return contexts.get(urn);
    }

    @Override
    public void logout() {
        // TODO
    }

}
