package org.integratedmodelling.common.services.client.scope;

import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.Observer;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Client-side session scope
 */
public abstract class ClientSessionScope extends ClientUserScope implements SessionScope {

    private final RuntimeService runtimeService;
    private String name;

    public ClientSessionScope(ClientUserScope parent, String sessionName, RuntimeService runtimeService) {
        // FIXME use a copy constructor that inherits the environment from the parent
        super(parent.getIdentity(), Type.SESSION);
        this.runtimeService = runtimeService;
        this.name = sessionName;
        this.parentScope = parent;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public ContextScope createContext(String contextName) {
        /**
         * Must have runtime
         *
         * Send REGISTER_SCOPE to runtime; returned ID becomes part of the token for requests
         * Wait for result, set ID and data/permissions/metadata, expirations, quotas into metadata
         * Create peer object and return
         */

        var runtime = getService(RuntimeService.class);
        if (runtime == null) {
            throw new KlabResourceAccessException("Runtime service is not accessible: cannot create context");
        }

        /**
         * Registration with the runtime succeeded. Return a peer scope locked to the
         * runtime service that hosts it.
         */
        var ret = new ClientContextScope(this, contextName, runtime) {

            @Override
            public <T extends KlabService> T getService(Class<T> serviceClass) {
                if (serviceClass.isAssignableFrom(RuntimeService.class)) {
                    return (T)runtime;
                }
                return ClientSessionScope.this.getService(serviceClass);
            }

            @Override
            public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
                if (serviceClass.isAssignableFrom(RuntimeService.class)) {
                    return List.of((T)runtime);
                }
                return ClientSessionScope.this.getServices(serviceClass);
            }
        };

        var sessionId = runtime.registerContext(ret);

        if (sessionId != null) {
            ret.setId(sessionId);
            return ret;
        }

        return null;
    }

    @Override
    public void logout() {

    }

    /**
     * TODO lock to the specific runtime service redefining getService() for the RuntimeService.
     */
}
