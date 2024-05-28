package org.integratedmodelling.klab.services.scopes;

import io.reacted.core.config.reactorsystem.ReActorSystemConfig;
import io.reacted.core.reactorsystem.ReActorSystem;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.rest.ScopeReference;
import org.integratedmodelling.klab.runtime.kactors.messages.RunBehavior;
import org.integratedmodelling.klab.services.actors.KAgent;
import org.integratedmodelling.klab.services.actors.UserAgent;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The scope manager maintains service-side scopes that are generated through the orchestrating engine.
 */
public class ScopeManager {

    private final ReActorSystem actorSystem;
    KlabService service;
    private Map<String, EngineScope> userScopes = Collections.synchronizedMap(new HashMap<>());

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
        /*
         * boot the actor system right away, so that we can call login() before boot().
         */
        this.actorSystem =
                new ReActorSystem(ReActorSystemConfig.newBuilder().setReactorSystemName("klab").build())
                        .initReActorSystem();
    }

    public UserScope login(UserIdentity user) {

        EngineScope ret = userScopes.get(user.getUsername());
        if (ret == null) {

            ret = new EngineScope(user) {

                @Override
                public void switchService(KlabService service) {
                    // don't
                }

                @Override
                public <T extends KlabService> T getService(Class<T> serviceClass) {
                    return service.serviceScope().getService(serviceClass);
                }

                public String toString() {
                    return user.toString();
                }

            };

            String agentName = user.getUsername();
            KActorsBehavior.Ref agent = KAgent.KAgentRef.get(actorSystem.spawn(new UserAgent(agentName, ret)).get());
            ret.setAgent(agent);

            userScopes.put(user.getUsername(), ret);

            File userBehavior = new File(Configuration.INSTANCE.getDataPath() + File.separator + "user" +
                    ".kactors");
            if (userBehavior.isFile() && userBehavior.canRead()) {
                try {
                    var message = new RunBehavior();
                    message.setBehaviorUrl(userBehavior.toURI().toURL());
                    agent.tell(message);
                } catch (MalformedURLException e) {
                    ret.error(e, "while reading user.kactors behavior");
                }
            }
        }

        return ret;
    }

    /**
     * Logout a previously logged in scope. Based on the ID, this will match a scope at any level and
     * release any resources held by that scope or any scope at a lower level.
     *
     * @param scopeId
     * @return true if the scope existed and was released.
     */
    public boolean logout(String scopeId) {
        // TODO
        return false;
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
