package org.integratedmodelling.klab.services.scopes;

import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.rest.ScopeReference;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The scope manager maintains service-side scopes that are generated through the orchestrating engine.
 */
public class ScopeManager {

    KlabService service;

    /**
     * Every scope managed by this service. The relationship between scopes is managed through the scope
     * graph, using only the IDs.
     */
    private Map<String, Scope> scopes = Collections.synchronizedMap(new HashMap<>());
    /**
     * ScopeID->ScopeID means that the scope with the source ID is a child scope of the target, and all are in
     * the scopes map. Closing one scope should recursively close all the children and free every bit of data
     * associated with each of them.
     */
    private Graph<String, DefaultEdge> scopeGraph = new DefaultDirectedGraph<>(DefaultEdge.class);

    public ScopeManager(KlabService service) {
        this.service = service;
    }


    public ScopeReference createScope(Scope.Type scopeType, EngineAuthorization engineAuthorization) {

        /*
        This may be the service scope if the service is local. Otherwise it should be a user scope
        matching the logged in user, which we create on demand.
         */
        Scope userScope = engineAuthorization.getScope();
        if (userScope == null) {
            engineAuthorization.setScope(userScope = newUserScope(engineAuthorization));
        }

        return switch (scopeType) {
            default -> null;
        };

    }

    public SessionScope newSessionScope(UserScope scope) {
        return null;
    }

    public ContextScope newContextScope(SessionScope scope) {
        return null;
    }

    public UserScope newUserScope(EngineAuthorization engineAuthorization) {
        return null;
    }
}
