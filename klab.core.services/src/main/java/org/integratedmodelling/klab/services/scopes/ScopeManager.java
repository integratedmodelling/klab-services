package org.integratedmodelling.klab.services.scopes;

import io.reacted.core.config.reactorsystem.ReActorSystemConfig;
import io.reacted.core.reactorsystem.ReActorSystem;
import org.integratedmodelling.common.authentication.UserIdentityImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
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
 * The scope manager maintains service-side scopes that are generated through the orchestrating engine. When
 * actors are requested, the necessary chain is created as needed. The main strategy for resource maintenance
 * and session expiration is here.
 */
public class ScopeManager {

    private final ReActorSystem actorSystem;
    KlabService service;
    /**
     * Every scope managed by this service. The relationship between scopes is managed through the scope
     * graph, using only the IDs.
     */
    private Map<String, ServiceUserScope> scopes = Collections.synchronizedMap(new HashMap<>());
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
                new ReActorSystem(ReActorSystemConfig.newBuilder().setReactorSystemName("klab").build()).initReActorSystem();

        Logging.INSTANCE.info("Actor system booted");

    }

    public ServiceUserScope login(UserIdentity user) {

        ServiceUserScope ret = scopes.get(user.getUsername());
        if (ret == null) {

            ret = new ServiceUserScope(user) {

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
            // TODO move to lazy logics
            KActorsBehavior.Ref agent = KAgent.KAgentRef.get(actorSystem.spawn(new UserAgent(agentName,
                    ret)).get());
            ret.setAgent(agent);

            scopes.put(user.getUsername(), ret);

            File userBehavior = new File(ServiceConfiguration.INSTANCE.getDataPath() + File.separator + "user" +
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
     * Logout a previously logged in scope. Based on the ID, this will match a scope at any level and release
     * any resources held by that scope or any scope at a lower level.
     *
     * @param scopeId
     * @return true if the scope existed and was released.
     */
    public boolean logout(String scopeId) {
        // TODO kill the actor if it's there, that should release all resources
        var scope = scopes.get(scopeId);
        return false;
    }

    public ServiceUserScope createScope(EngineAuthorization engineAuthorization) {

        String[] path = engineAuthorization.getScopeId().split("\\/");
        ServiceUserScope ret = null;

        /**
         * The three physical scope levels are user/session/context. Below that, scopes are "virtual"
         * incarnations of the context scope with modified state and
         * their hierarchy is handled internally by the {@link ContextScope} implementation.
         */
        ServiceUserScope scope = null;
        StringBuilder scopeId = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            scopeId.append((scopeId.isEmpty()) ? path[i] : ("/" + path[i]));
            var currentScope = scopes.get(scopeId.toString());
            if (currentScope == null) {
                // create from the previous scope according to level
                currentScope = switch (i) {
                    case 0 -> login(createUserIdentity(engineAuthorization));
                    case 1 -> null; // currentScope.runSession();
                    case 2 -> null; // ((SessionScope)currentScope)....
                    default -> null; // should exist but we keep the scope and ask it to specialize
                };
            }

            scope = currentScope;
        }

        ret = scope;
        ret.setLocal(engineAuthorization.isLocal());

        return ret;
    }

    private UserIdentity createUserIdentity(EngineAuthorization engineAuthorization) {
        UserIdentityImpl ret = new UserIdentityImpl();
        ret.setUsername(engineAuthorization.getUsername());
        ret.setEmailAddress(engineAuthorization.getIdentity().getEmail());
        ret.setId(engineAuthorization.getToken());
        // TODO continue
        return ret;
    }


    public <T extends Scope> T getOrCreateScope(String scopeId) {
        return null;
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

    public void register(EngineAuthorization ret) {
        if (ret.getScopeId() != null) {
            scopes.getOrDefault(ret.getScopeId(), createScope(ret));
        }
    }
}
