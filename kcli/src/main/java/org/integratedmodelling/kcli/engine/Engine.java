package org.integratedmodelling.kcli.engine;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.exceptions.KIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.Authentication;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.services.authentication.impl.AnonymousUser;
import org.integratedmodelling.klab.services.engine.EngineService;
import org.integratedmodelling.klab.services.reasoner.ReasonerClient;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.resolver.ResolverClient;
import org.integratedmodelling.klab.services.resolver.ResolverService;
import org.integratedmodelling.klab.services.resources.ResourcesClient;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.services.runtime.RuntimeClient;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.klab.utils.NameGenerator;
import org.integratedmodelling.klab.utils.Parameters;

public enum Engine implements Authentication {

	INSTANCE;

	/*
	 * these are set/unset by the user and can be used for substitutions in CLs
	 */
	Parameters<String> userData = Parameters.create();
	Map<String, UserScope> authorizedIdentities = new LinkedHashMap<>();

	UserScope currentUser;
	SessionScope currentSession;
	ContextScope currentContext;

	Map<String, SessionScope> sessions = new LinkedHashMap<>();
	Map<String, ContextScope> contexts = new LinkedHashMap<>();

	private Engine() {

		/*
		 * discover and catalog services
		 */

		/*
		 * check for a locally running service for each category; if existing, create a
		 * client, otherwise create an embedded service
		 */
		if (Utils.Network.isAlive("http://127.0.0.1:" + ResourcesService.DEFAULT_PORT + " /resources/actuator")) {
			EngineService.INSTANCE
					.setResources(new ResourcesClient("http://127.0.0.1:" + Reasoner.DEFAULT_PORT + " /resources"));
		} else {
			EngineService.INSTANCE.setResources(
					new ResourcesProvider(this, EngineService.INSTANCE.newServiceScope(ResourcesService.class)));
		}

		if (Utils.Network.isAlive("http://127.0.0.1:" + Reasoner.DEFAULT_PORT + " /reasoner/actuator")) {
			EngineService.INSTANCE
					.setReasoner(new ReasonerClient("http://127.0.0.1:" + Reasoner.DEFAULT_PORT + " /reasoner"));
		} else {
			EngineService.INSTANCE
					.setReasoner(new ReasonerService(this, EngineService.INSTANCE.newServiceScope(Reasoner.class)));
		}

		// FIXME mutual dependency between resolver and runtime guarantees screwup
		if (Utils.Network.isAlive("http://127.0.0.1:" + Resolver.DEFAULT_PORT + " /resolver/actuator")) {
			EngineService.INSTANCE
					.setResolver(new ResolverClient("http://127.0.0.1:" + Resolver.DEFAULT_PORT + " /resolver"));
		} else {
			EngineService.INSTANCE
					.setResolver(new ResolverService(this, EngineService.INSTANCE.newServiceScope(Resolver.class)));
		}

		if (Utils.Network.isAlive("http://127.0.0.1:" + RuntimeService.DEFAULT_PORT + " /runtime/actuator")) {
			EngineService.INSTANCE
					.setRuntime(new RuntimeClient("http://127.0.0.1:" + RuntimeService.DEFAULT_PORT + " /runtime"));
		} else {
			EngineService.INSTANCE.setRuntime(new org.integratedmodelling.klab.services.runtime.RuntimeService(this,
					EngineService.INSTANCE.newServiceScope(RuntimeService.class)));
		}

		// Boot will initialize all embedded services
		EngineService.INSTANCE.boot();

		/*
		 * add the anonymous identity
		 */
		this.authorizedIdentities.put("anonymous", this.currentUser = getAnonymousScope());

	}

	@Override
	public UserScope authorizeUser(UserIdentity user) {
		return EngineService.INSTANCE.login(user);
	}

	@Override
	public boolean checkPermissions(ResourcePrivileges permissions, Scope scope) {
		// everything is allowed
		return true;
	}

	/**
	 * Return the specific service among the various available. The "local" keyword
	 * returns the service installed as the default in the user scope. The "active"
	 * keyword should get the currently active service. Anything else will look up
	 * the service using the discovery mechanism.
	 * 
	 * @param <T>
	 * @param name
	 * @param serviceClass
	 * @return
	 */
	public <T extends KlabService> T getServiceNamed(String name, Class<T> serviceClass) {
		return "local".equals(name) ? currentUser.getService(serviceClass) : /* TODO */ null;
	}

	@Override
	public UserScope getAnonymousScope() {
		return EngineService.INSTANCE.login(new AnonymousUser());
	}

	/**
	 * Get or optionally create the current user. Report using the channel.
	 * 
	 * @param createIfNull
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
			currentSession = createSession(NameGenerator.shortUUID(), true);
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
				createSession(NameGenerator.shortUUID(), true);
			}
			currentContext = currentSession.createContext(NameGenerator.shortUUID(), Geometry.EMPTY);
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
			throw new KIllegalStateException("no current user scope: cannot create session " + name);
		}
		if (this.sessions.containsKey(name)) {
			throw new KIllegalStateException("session already exists: cannot create session " + name);
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
