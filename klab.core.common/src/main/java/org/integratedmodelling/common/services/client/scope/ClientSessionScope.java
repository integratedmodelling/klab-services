package org.integratedmodelling.common.services.client.scope;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.RuntimeService;

/**
 * Client-side session scope
 */
public abstract class ClientSessionScope extends ClientUserScope implements SessionScope {

    private final RuntimeService runtimeService;

    public ClientSessionScope(ClientUserScope parent, String sessionId, RuntimeService runtimeService) {
        // FIXME use a copy constructor that inherits the environment from the parent
        super(parent.getIdentity(), Type.SESSION);
        this.runtimeService = runtimeService;
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public ContextScope createContext(String contextName, Object... observerData) {
        return null;
    }

    @Override
    public ContextScope getContext(String urn) {
        return null;
    }

    @Override
    public void logout() {

    }

    /**
     * TODO lock to the specific runtime service redefining getService() for the RuntimeService.
     */
}
