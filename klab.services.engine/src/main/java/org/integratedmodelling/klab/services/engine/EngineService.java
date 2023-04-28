//package org.integratedmodelling.klab.services.engine;
//
//import java.util.Collections;
//import java.util.HashMap;
//import java.util.Map;
//
//import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;
//import org.integratedmodelling.klab.api.authentication.scope.UserScope;
//import org.integratedmodelling.klab.api.identities.UserIdentity;
//import org.integratedmodelling.klab.api.services.Authentication;
//import org.integratedmodelling.klab.api.services.Engine;
//import org.integratedmodelling.klab.configuration.Services;
//import org.integratedmodelling.klab.services.actors.KAgent.KAgentRef;
//import org.integratedmodelling.klab.services.actors.UserAgent;
//import org.integratedmodelling.klab.services.scope.EngineScopeImpl;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import io.reacted.core.config.reactorsystem.ReActorSystemConfig;
//import io.reacted.core.reactorsystem.ReActorSystem;
//
///**
// * Reference implementation for the new modular engine. Should eventually allow substituting
// * external RPC services for the default ones, based on configuration and a dedicated API.
// * 
// * @author Ferd
// *
// */
//@Service
//public class EngineService implements Engine {
//
//    private static final long serialVersionUID = -5132403746054203201L;
//
//    transient private Map<String, EngineScopeImpl> userScopes = Collections.synchronizedMap(new HashMap<>());
//    transient private ReActorSystem actorSystem;
//    transient private ServiceScope scope;
//
//    private Authentication authenticationService;
//
//    interface Capabilities extends ServiceCapabilities {
//
//    }
//
//    @Autowired
//    public EngineService(Authentication authenticationService) {
//        this.authenticationService = authenticationService;
//        this.scope = authenticationService.authorizeService(this);
//        boot();
//    }
//    
//    public void boot() {
//
//        Services.INSTANCE.setEngine(this);
//
//        /*
//         * boot the actor system
//         */
//        // Actors.INSTANCE.setup();
//
//        this.actorSystem = new ReActorSystem(ReActorSystemConfig.newBuilder().setReactorSystemName("klab").build())
//                .initReActorSystem();
//
//    }
//
//    @Override
//    public UserScope login(UserIdentity user) {
//
//        EngineScopeImpl ret = userScopes.get(user.getUsername());
//        if (ret == null) {
//            // TODO have the auth service authenticate the user, then wrap the scope
//            ret = new EngineScopeImpl(user);
//            final EngineScopeImpl scope = ret;
//            String agentName = user.getUsername();
//            actorSystem.spawn(new UserAgent(agentName)).ifSuccess((t) -> scope.setAgent(KAgentRef.get(t))).orElseSneakyThrow();
//            userScopes.put(user.getUsername(), ret);
//        }
//        return ret;
//    }
//
//    public void registerScope(EngineScopeImpl scope) {
//        userScopes.put(scope.getUser().getUsername(), scope);
//    }
//
//    public void deregisterScope(String token) {
//        userScopes.remove(token);
//    }
//
//    public ReActorSystem getActors() {
//        return this.actorSystem;
//    }
//
//    public Object getSystemRef() {
//        // TODO Auto-generated method stub
//        return null;
//    }
//
//    @Override
//    public String getUrl() {
//        // TODO Auto-generated method stub
//        return null;
//    }
//
//    @Override
//    public String getLocalName() {
//        // TODO Auto-generated method stub
//        return null;
//    }
//
//    @Override
//    public ServiceCapabilities getCapabilities() {
//        // TODO Auto-generated method stub
//        return null;
//    }
//
//    @Override
//    public ServiceScope scope() {
//        return this.scope;
//    }
//
//    @Override
//    public boolean shutdown() {
//        // TODO Auto-generated method stub
//        return false;
//    }
//
//}
