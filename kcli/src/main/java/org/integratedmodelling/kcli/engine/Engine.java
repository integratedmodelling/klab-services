package org.integratedmodelling.kcli.engine;

import org.integratedmodelling.common.authentication.AnonymousUser;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.services.engine.EngineService;
import org.integratedmodelling.klab.services.reasoner.ReasonerClient;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.resolver.ResolverClient;
import org.integratedmodelling.klab.services.resolver.ResolverService;
import org.integratedmodelling.klab.services.resources.ResourcesClient;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.services.runtime.RuntimeClient;
import org.integratedmodelling.klab.utilities.Utils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public enum Engine {

    INSTANCE;

    /*
     * these are set/unset by the user and can be used for substitutions in CLs
     */ Parameters<String> userData = Parameters.create();
    Map<String, UserScope> authorizedIdentities = new LinkedHashMap<>();

    UserScope currentUser;
    SessionScope currentSession;
    ContextScope currentContext;

    Map<String, SessionScope> sessions = new LinkedHashMap<>();
    Map<String, ContextScope> contexts = new LinkedHashMap<>();

    EngineService engineService;

    private Engine() {

        engineService = new EngineService();

        /*
         * discover and catalog services
         */

        /*
         * check for a locally running service for each category; if existing, create a
         * client, otherwise create an embedded service
         */
        if (Utils.Network.isAlive(KlabService.Type.RESOURCES.localServiceUrl())) {
            engineService.setResources(new ResourcesClient(KlabService.Type.RESOURCES.localServiceUrl()));
        } else {
//            engineService.setResources(new ResourcesProvider(engineService.newServiceScope(ResourcesService.class)));
        }

        if (Utils.Network.isAlive(KlabService.Type.REASONER.localServiceUrl())) {
            engineService.setReasoner(new ReasonerClient(KlabService.Type.REASONER.localServiceUrl()));
        } else {
//            engineService.setReasoner(new ReasonerService(engineService.newServiceScope(Reasoner.class)));
        }

        // FIXME mutual dependency between resolver and runtime guarantees screwup
        if (Utils.Network.isAlive(KlabService.Type.RESOLVER.localServiceUrl())) {
            engineService.setResolver(new ResolverClient(KlabService.Type.RESOLVER.localServiceUrl()));
        } else {
//            engineService.setResolver(new ResolverService(engineService.newServiceScope(Resolver.class)));
        }

        if (Utils.Network.isAlive(KlabService.Type.RUNTIME.localServiceUrl())) {
            engineService.setRuntime(new RuntimeClient(KlabService.Type.RUNTIME.localServiceUrl()));
        } else {
//            engineService.setRuntime(new org.integratedmodelling.klab.services.runtime.RuntimeService(engineService.newServiceScope(RuntimeService.class)));
        }

        // Boot will initialize all embedded services
        engineService.boot();

        /*
         * add the anonymous identity
         */
        this.authorizedIdentities.put("anonymous", this.currentUser = getAnonymousScope());

    }

    //    @Override
    public UserScope authorizeUser(UserIdentity user) {
        return engineService.login(user);
    }

    //    @Override
    public boolean checkPermissions(ResourcePrivileges permissions, Scope scope) {
        // everything is allowed
        return true;
    }

    /**
     * Return the specific service among the various available. The "local" keyword returns the service
     * installed as the default in the user scope. The "active" keyword should get the currently active
     * service. Anything else will look up the service using the discovery mechanism.
     *
     * @param <T>
     * @param name
     * @param serviceClass
     * @return
     */
    public <T extends KlabService> T getServiceNamed(String name, Class<T> serviceClass) {
        return "local".equals(name) ? currentUser.getService(serviceClass) : /* TODO */ null;
    }

    //    @Override
    public UserScope getAnonymousScope() {
        return engineService.login(new AnonymousUser());
    }

    /**
     * Get or optionally create the current user. Report using the channel.
     *
     * @param loginAnonymousIfNull
     * @param channel
     * @return
     */
    public UserScope getCurrentUser(boolean loginAnonymousIfNull, Channel channel) {
        return currentUser;
    }

    /**
     * Use this when you know it's there
     *
     * @return
     */
    public UserScope getCurrentUser() {
        return currentUser;
    }

    /**
     * Get or optionally create the current session. Report using the channel.
     *
     * @param createIfNull
     * @param channel
     * @return
     */
    public SessionScope getCurrentSession(boolean createIfNull, Channel channel) {
        if (currentSession == null) {
            currentSession = createSession(Utils.Names.shortUUID(), true);
        }
        return currentSession;
    }

    /**
     * Return the current session or null.
     *
     * @return
     */
    public SessionScope getCurrentSession() {
        return currentSession;
    }

    public ContextScope getCurrentContext(boolean createIfNull) {

        if (currentContext == null) {
            if (currentSession == null) {
                createSession(Utils.Names.shortUUID(), true);
            }
            currentContext = currentSession.createContext(Utils.Names.shortUUID(), Geometry.EMPTY);
        }

        return currentContext;
    }

    public Parameters<String> getUserData() {
        return userData;
    }

    public Collection<UserScope> getUsers() {
        return authorizedIdentities.values();
    }

    public SessionScope getSession(String name) {
        return this.sessions.get(name);
    }

    public SessionScope createSession(String name, boolean makeCurrent) {

        if (currentUser == null) {
            throw new KlabIllegalStateException("no current user scope: cannot create session " + name);
        }
        if (this.sessions.containsKey(name)) {
            throw new KlabIllegalStateException("session already exists: cannot create session " + name);
        }

        SessionScope ret = currentUser.runSession(name);
        this.sessions.put(name, ret);

        if (makeCurrent) {
            this.currentSession = ret;
        }
        return ret;
    }

    public void setDefaultSession(SessionScope session) {
        this.currentSession = session;
    }

    public void setCurrentContext(ContextScope context) {
        this.contexts.put(context.getName(), context);
        this.currentContext = context;
    }

    public ContextScope getContext(String context) {
        return this.contexts.get(context);
    }

}
