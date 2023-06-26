package org.integratedmodelling.kcli.engine;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;
import org.integratedmodelling.klab.api.authentication.scope.UserScope;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref;
import org.integratedmodelling.klab.api.services.Authentication;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourceProvider;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.indexing.Indexer;
import org.integratedmodelling.klab.services.authentication.impl.AnonymousUser;
import org.integratedmodelling.klab.services.authentication.impl.LocalServiceScope;
import org.integratedmodelling.klab.services.engine.EngineService;
import org.integratedmodelling.klab.services.reasoner.ReasonerClient;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.resolver.ResolverClient;
import org.integratedmodelling.klab.services.resolver.ResolverService;
import org.integratedmodelling.klab.services.resources.ResourcesClient;
import org.integratedmodelling.klab.services.resources.ResourcesService;
import org.integratedmodelling.klab.services.runtime.RuntimeClient;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.klab.utils.Parameters;

public enum Engine implements Authentication {

	INSTANCE;

	/*
	 * these are set/unset by the user and can be used for substitutions in CLs
	 */
	Parameters<String> userData = Parameters.create();
	Map<String, UserScope> authorizedIdentities = new LinkedHashMap<>();
	UserScope currentUser;

	private Engine() {

		// boot right away
		EngineService.INSTANCE.boot();

		/*
		 * discover and catalog services
		 */

		/*
		 * check for a locally running service for each category; if existing, create a
		 * client, otherwise create an embedded service
		 */
		if (Utils.Network.isAlive("http://127.0.0.1:" + ResourceProvider.DEFAULT_PORT + " /resources/actuator")) {
			EngineService.INSTANCE
					.setResources(new ResourcesClient("http://127.0.0.1:" + Reasoner.DEFAULT_PORT + " /resources"));
		} else {
			EngineService.INSTANCE.setResources(new ResourcesService(this));
		}

		if (Utils.Network.isAlive("http://127.0.0.1:" + Reasoner.DEFAULT_PORT + " /reasoner/actuator")) {
			EngineService.INSTANCE
					.setReasoner(new ReasonerClient("http://127.0.0.1:" + Reasoner.DEFAULT_PORT + " /reasoner"));
		} else {
			EngineService.INSTANCE
					.setReasoner(new ReasonerService(this, EngineService.INSTANCE.getResources(), new Indexer()));
		}

		// FIXME mutual dependency between resolver and runtime guarantees screwup
		if (Utils.Network.isAlive("http://127.0.0.1:" + Resolver.DEFAULT_PORT + " /resolver/actuator")) {
			EngineService.INSTANCE
					.setResolver(new ResolverClient("http://127.0.0.1:" + Resolver.DEFAULT_PORT + " /resolver"));
		} else {
			EngineService.INSTANCE.setResolver(new ResolverService(this, EngineService.INSTANCE.getResources(),
					EngineService.INSTANCE.getRuntime()));
		}

		if (Utils.Network.isAlive("http://127.0.0.1:" + RuntimeService.DEFAULT_PORT + " /runtime/actuator")) {
			EngineService.INSTANCE
					.setRuntime(new RuntimeClient("http://127.0.0.1:" + RuntimeService.DEFAULT_PORT + " /runtime"));
		} else {
			EngineService.INSTANCE.setRuntime(new org.integratedmodelling.klab.services.runtime.RuntimeService(this,
					EngineService.INSTANCE.getResources(), EngineService.INSTANCE.getResolver()));
		}

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

	@Override
	public UserScope getAnonymousScope() {
		return EngineService.INSTANCE.login(new AnonymousUser());
	}

	public UserScope getCurrentUser() {
		return currentUser;
	}

	public Parameters<String> getUserData() {
		return userData;
	}
	
	@Override
	public ServiceScope authorizeService(KlabService service) {
		return new LocalServiceScope(service) {

			@Override
			public <T extends KlabService> T getService(Class<T> serviceClass) {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Ref getAgent() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public void stop() {
				// TODO Auto-generated method stub
				
			}

		};
	}

	public Collection<UserScope> getUsers() {
		return authorizedIdentities.values();
	}

}
