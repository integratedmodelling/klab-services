package org.integratedmodelling.klab.tests.services.engine;

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
import org.integratedmodelling.klab.api.services.ResourcesService;
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
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.services.runtime.RuntimeClient;
import org.integratedmodelling.klab.utilities.Utils;

/**
 * An "engine" implementation for local testing which will connect to any local
 * services it finds running, and create local embedded services for the others.
 * Only needs to expose a local authentication service to retrieve a user scope
 * for the anonymous user, then the service proxies are available from it.
 * 
 * @author Ferd
 *
 */
public class TestEngine {

	static class TestAuthentication implements Authentication {

		TestAuthentication() {
			/*
			 * check for a locally running service for each category; if existing, create a
			 * client, otherwise create an embedded service
			 */
			if (Utils.Network.isAlive("http://127.0.0.1:" + ResourcesService.DEFAULT_PORT + " /resources/actuator")) {
				EngineService.INSTANCE
						.setResources(new ResourcesClient("http://127.0.0.1:" + Reasoner.DEFAULT_PORT + " /resources"));
			} else {
				EngineService.INSTANCE.setResources(new ResourcesProvider(this));
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

		public void shutdown() {
			EngineService.INSTANCE.shutdown();
		}

	}

	/**
	 * Return an authentication service that will only authenticate anonymous users
	 * and connect them with clients for any locally running service, filling in the
	 * remaining services with local instances.
	 * 
	 * @return
	 */
	public static TestAuthentication setup() {
		return new TestAuthentication();
	}
}
