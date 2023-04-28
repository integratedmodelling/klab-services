package org.integratedmodelling.klab.services.engine;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.integratedmodelling.klab.api.authentication.scope.UserScope;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourceProvider;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.services.actors.KAgent.KAgentRef;
import org.integratedmodelling.klab.services.actors.UserAgent;
import org.integratedmodelling.klab.services.scope.EngineScopeImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.reacted.core.config.reactorsystem.ReActorSystemConfig;
import io.reacted.core.reactorsystem.ReActorSystem;

/**
 * Reference implementation for the new modular engine. Should eventually allow substituting
 * external RPC services for the default ones, based on configuration and a dedicated API.
 * 
 * @author Ferd
 *
 */
@Service
public class EngineService /* implements Engine */ {

    private Map<String, EngineScopeImpl> userScopes = Collections.synchronizedMap(new HashMap<>());
    private ReActorSystem actorSystem;

    private Reasoner reasoner;
    private ResourceProvider resources;
    private RuntimeService runtime;
    private Resolver resolver;

    @Autowired
    public EngineService() {
        boot();
    }

    public void boot() {

        /*
         * boot the actor system
         */
        this.actorSystem = new ReActorSystem(ReActorSystemConfig.newBuilder().setReactorSystemName("klab").build())
                .initReActorSystem();

    }

    public UserScope login(UserIdentity user) {

        EngineScopeImpl ret = userScopes.get(user.getUsername());
        if (ret == null) {
            ret = new EngineScopeImpl(user){
                private static final long serialVersionUID = 2259089014852859140L;

                @SuppressWarnings("unchecked")
                @Override
                public <T extends KlabService> T getService(Class<T> serviceClass) {
                    if (serviceClass.isAssignableFrom(Reasoner.class)) {
                        return (T)reasoner;
                    } else if (serviceClass.isAssignableFrom(ResourceProvider.class)) {
                        return (T)resources;
                    } else if (serviceClass.isAssignableFrom(Resolver.class)) {
                        return (T)resolver;
                    } else if (serviceClass.isAssignableFrom(RuntimeService.class)) {
                        return (T)runtime;
                    }
                    return null;
                }

            };
            final EngineScopeImpl scope = ret;
            String agentName = user.getUsername();
            actorSystem.spawn(new UserAgent(agentName)).ifSuccess((t) -> scope.setAgent(KAgentRef.get(t))).orElseSneakyThrow();
            userScopes.put(user.getUsername(), ret);
        }
        return ret;
    }

    public void registerScope(EngineScopeImpl scope) {
        userScopes.put(scope.getUser().getUsername(), scope);
    }

    public void deregisterScope(String token) {
        userScopes.remove(token);
    }

    public ReActorSystem getActors() {
        return this.actorSystem;
    }

    public Object getSystemRef() {
        // TODO Auto-generated method stub
        return null;
    }

    public Reasoner getReasoner() {
        return reasoner;
    }

    public void setReasoner(Reasoner reasoner) {
        this.reasoner = reasoner;
    }

    public ResourceProvider getResources() {
        return resources;
    }

    public void setResources(ResourceProvider resources) {
        this.resources = resources;
    }

    public RuntimeService getRuntime() {
        return runtime;
    }

    public void setRuntime(RuntimeService runtime) {
        this.runtime = runtime;
    }

    public Resolver getResolver() {
        return resolver;
    }

    public void setResolver(Resolver resolver) {
        this.resolver = resolver;
    }

    public boolean shutdown() {
        this.reasoner.shutdown();
        this.resources.shutdown();
        this.reasoner.shutdown();
        this.runtime.shutdown();
        return true;
    }

}
