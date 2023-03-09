package org.integratedmodelling.klab.services.engine;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.EngineIdentity;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.observation.scope.Scope;
import org.integratedmodelling.klab.api.services.Engine;
import org.integratedmodelling.klab.api.services.KlabFederatedService;
import org.integratedmodelling.klab.api.services.KlabFederatedService.FederatedServiceCapabilities;
import org.integratedmodelling.klab.api.services.Runtime.Capabilities;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.configuration.Services;
import org.integratedmodelling.klab.runtime.Monitor;
import org.integratedmodelling.klab.services.actors.KAgent.KAgentRef;
import org.integratedmodelling.klab.services.actors.UserAgent;
import org.integratedmodelling.klab.services.scope.EngineScopeImpl;
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
public class EngineService implements Engine, EngineIdentity {

    private static final long serialVersionUID = -5132403746054203201L;
    private String name = "modular-klab-engine"; // TODO read from configuration

    transient private Map<String, EngineScopeImpl> userScopes = Collections.synchronizedMap(new HashMap<>());
    transient private Monitor monitor = new Monitor(this);
    transient private ReActorSystem actorSystem;

    
    interface Capabilities extends FederatedServiceCapabilities {
        
    }
    
    
    public void boot() {

        Services.INSTANCE.setEngine(this);

        /*
         * boot the actor system
         */
        // Actors.INSTANCE.setup();

        this.actorSystem = new ReActorSystem(ReActorSystemConfig.newBuilder().setReactorSystemName("klab").build())
                .initReActorSystem();

    }

    @Override
    public Scope login(UserIdentity user) {

        EngineScopeImpl ret = userScopes.get(user.getUsername());
        if (ret == null) {
            ret = new EngineScopeImpl(user);
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

    public static EngineService start() {
        EngineService ret = new EngineService();
        ret.boot();
        return ret;
    }
    
    public ReActorSystem getActors() {
        return this.actorSystem;
    }

    public Object getSystemRef() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Date getBootTime() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<String> getUrls() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isOnline() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean stop() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Channel getMonitor() {
        return monitor;
    }

    @Override
    public Parameters<String> getState() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Type getIdentityType() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getId() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Identity getParentIdentity() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean is(Type type) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public <T extends Identity> T getParentIdentity(Class<T> type) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getUrl() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getLocalName() {
        // TODO Auto-generated method stub
        return null;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public FederatedServiceCapabilities getCapabilities() {
        // TODO Auto-generated method stub
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public EngineService exclusive(Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public EngineService dedicated(Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }


}
