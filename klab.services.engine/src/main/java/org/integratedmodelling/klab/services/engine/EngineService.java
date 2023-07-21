package org.integratedmodelling.klab.services.engine;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.services.actors.KAgent.KAgentRef;
import org.integratedmodelling.klab.services.actors.UserAgent;
import org.integratedmodelling.klab.services.actors.messages.kactor.RunBehavior;
import org.integratedmodelling.klab.services.scope.EngineScope;
import org.springframework.beans.factory.annotation.Autowired;

import io.reacted.core.config.reactorsystem.ReActorSystemConfig;
import io.reacted.core.reactorsystem.ReActorSystem;

/**
 * Reference implementation for the new modular engine. Should eventually allow
 * substituting external RPC services for the default ones, based on
 * configuration and a dedicated API.
 * 
 * @author Ferd
 *
 */
public enum EngineService {

	INSTANCE;

	private Map<String, EngineScope> userScopes = Collections.synchronizedMap(new HashMap<>());
	private ReActorSystem actorSystem;

	private Reasoner defaultReasoner;
	private ResourcesService defaultResourcesService;
	private RuntimeService defaultRuntime;
	private Resolver defaultResolver;
	private boolean booted;

	@Autowired
	private EngineService() {
		boot();
	}

	public void boot() {

		if (!booted) {
			booted = true;
			/*
			 * boot the actor system
			 */
			this.actorSystem = new ReActorSystem(ReActorSystemConfig.newBuilder().setReactorSystemName("klab").build())
					.initReActorSystem();

			/*
			 * Components
			 */
			Set<String> extensionPackages = new LinkedHashSet<>();
			extensionPackages.add("org.integratedmodelling.klab");
			/*
			 * Check for updates, load and scan all new plug-ins, returning the main
			 * packages to scan
			 */
			extensionPackages.addAll(Configuration.INSTANCE.updateAndLoadComponents());

			/*
			 * Scan all packages registered under the parent package of all k.LAB services.
			 * TODO all assets from there should be given default permissions (or those
			 * encoded with their annotations) that are exposed to the admin API.
			 */
			for (String pack : extensionPackages) {
				Configuration.INSTANCE.scanPackage(pack);
			}
		}

	}

	public UserScope login(UserIdentity user) {

		EngineScope ret = userScopes.get(user.getUsername());
		if (ret == null) {

			ret = new EngineScope(user) {

				@SuppressWarnings("unchecked")
				@Override
				public <T extends KlabService> T getService(Class<T> serviceClass) {
					if (serviceClass.isAssignableFrom(Reasoner.class)) {
						return (T) defaultReasoner;
					} else if (serviceClass.isAssignableFrom(ResourcesService.class)) {
						return (T) defaultResourcesService;
					} else if (serviceClass.isAssignableFrom(Resolver.class)) {
						return (T) defaultResolver;
					} else if (serviceClass.isAssignableFrom(RuntimeService.class)) {
						return (T) defaultRuntime;
					}
					return null;
				}

				public String toString() {
					return user.toString();
				}

			};

			String agentName = user.getUsername();
			Ref agent = KAgentRef.get(actorSystem.spawn(new UserAgent(agentName, ret)).get());
			ret.setAgent(agent);

			userScopes.put(user.getUsername(), ret);

			File userBehavior = new File(Configuration.INSTANCE.getDataPath() + File.separator + "user.kactors");
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
	
	public void registerScope(EngineScope scope) {
		userScopes.put(scope.getUser().getUsername(), scope);
	}

	public void deregisterScope(String token) {
		userScopes.remove(token);
	}

	public ReActorSystem getActors() {
		return this.actorSystem;
	}

	public Reasoner getReasoner() {
		return defaultReasoner;
	}

	public void setReasoner(Reasoner reasoner) {
		this.defaultReasoner = reasoner;
	}

	public ResourcesService getResources() {
		return defaultResourcesService;
	}

	public void setResources(ResourcesService resources) {
		this.defaultResourcesService = resources;
	}

	public RuntimeService getRuntime() {
		return defaultRuntime;
	}

	public void setRuntime(RuntimeService runtime) {
		this.defaultRuntime = runtime;
	}

	public Resolver getResolver() {
		return defaultResolver;
	}

	public void setResolver(Resolver resolver) {
		this.defaultResolver = resolver;
	}

	public boolean shutdown() {
		this.defaultReasoner.shutdown();
		this.defaultResourcesService.shutdown();
		this.defaultReasoner.shutdown();
		this.defaultRuntime.shutdown();
		return true;
	}

}
