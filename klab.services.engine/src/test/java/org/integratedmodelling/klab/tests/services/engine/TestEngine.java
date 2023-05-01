package org.integratedmodelling.klab.tests.services.engine;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;
import org.integratedmodelling.klab.api.authentication.scope.UserScope;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.services.Authentication;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourceProvider;
import org.integratedmodelling.klab.api.services.RuntimeService;
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
import org.integratedmodelling.klab.utils.Utils;

/**
 * An "engine" implementation for local testing which will connect to any local services it finds
 * running, and create local embedded services for the others. Only needs to expose a local
 * authentication service to retrieve a user scope for the anonymous user, then the service proxies
 * are available from it.
 * 
 * @author Ferd
 *
 */
public class TestEngine {

    static class TestAuthentication extends EngineService implements Authentication {

        TestAuthentication() {
            /*
             * check for a locally running service for each category; if existing, create a client,
             * otherwise create an embedded service
             */
            if (Utils.Network.isAlive("http://127.0.0.1:" + ResourceProvider.DEFAULT_PORT + " /resources/actuator")) {
                setResources(new ResourcesClient("http://127.0.0.1:" + Reasoner.DEFAULT_PORT + " /resources"));
            } else {
                setResources(new ResourcesService(this));
            }

            if (Utils.Network.isAlive("http://127.0.0.1:" + Reasoner.DEFAULT_PORT + " /reasoner/actuator")) {
                setReasoner(new ReasonerClient("http://127.0.0.1:" + Reasoner.DEFAULT_PORT + " /reasoner"));
            } else {
                setReasoner(new ReasonerService(this, getResources(), null));
            }

            if (Utils.Network.isAlive("http://127.0.0.1:" + Resolver.DEFAULT_PORT + " /resolver/actuator")) {
                setResolver(new ResolverClient("http://127.0.0.1:" + Resolver.DEFAULT_PORT + " /resolver"));
            } else {
                setResolver(new ResolverService(this, getResources()));
            }

            if (Utils.Network.isAlive("http://127.0.0.1:" + RuntimeService.DEFAULT_PORT + " /runtime/actuator")) {
                setRuntime(new RuntimeClient("http://127.0.0.1:" + RuntimeService.DEFAULT_PORT + " /runtime"));
            } else {
                setRuntime(new org.integratedmodelling.klab.services.runtime.RuntimeService(this, getResources(), getResolver()));
            }

        }

        @Override
        public UserScope authorizeUser(UserIdentity user) {
            return login(user);
        }

        @Override
        public boolean checkPermissions(ResourcePrivileges permissions, Scope scope) {
            // everything is allowed
            return true;
        }

        @Override
        public UserScope getAnonymousScope() {
            return login(new AnonymousUser());
        }

        @Override
        public ServiceScope authorizeService(KlabService service) {
            return new LocalServiceScope(service) {

                private static final long serialVersionUID = 5357455713111028422L;

                @Override
                public <T extends KlabService> T getService(Class<T> serviceClass) {
                    // TODO Auto-generated method stub
                    return null;
                }
                
            };
        }

    }

    /**
     * Return an authentication service that will only authenticate anonymous users and connect them
     * with clients for any locally running service, filling in the remaining services with local
     * instances.
     * 
     * @return
     */
    public static TestAuthentication setup() {
        return new TestAuthentication();
    }
}
