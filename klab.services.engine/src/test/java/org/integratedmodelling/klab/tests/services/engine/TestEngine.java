package org.integratedmodelling.klab.tests.services.engine;

import org.integratedmodelling.klab.api.authentication.scope.UserScope;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.services.Authentication;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourceProvider;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.services.authentication.AuthenticationService;
import org.integratedmodelling.klab.services.reasoner.ReasonerClient;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.resolver.ResolverClient;
import org.integratedmodelling.klab.services.resolver.ResolverService;
import org.integratedmodelling.klab.services.resources.ResourcesClient;
import org.integratedmodelling.klab.services.resources.ResourcesService;
import org.integratedmodelling.klab.services.runtime.RuntimeClient;
import org.integratedmodelling.klab.services.scope.EngineScopeImpl;
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

    static class TestAuthentication extends AuthenticationService {

        private static final long serialVersionUID = -8805140708277187846L;

        Reasoner reasoner;
        ResourceProvider resources;
        RuntimeService runtime;
        Resolver resolver;

        TestAuthentication() {
            /*
             * check for a locally running service for each category; if existing, create a client,
             * otherwise create an embedded service
             */
            if (Utils.Network.isAlive("http://127.0.0.1:" + ResourceProvider.DEFAULT_PORT + " /resources/actuator")) {
                this.resources = new ResourcesClient("http://127.0.0.1:" + Reasoner.DEFAULT_PORT + " /resources");
            } else {
                this.resources = new ResourcesService(this);
            }

            if (Utils.Network.isAlive("http://127.0.0.1:" + Reasoner.DEFAULT_PORT + " /reasoner/actuator")) {
                this.reasoner = new ReasonerClient("http://127.0.0.1:" + Reasoner.DEFAULT_PORT + " /reasoner");
            } else {
                this.reasoner = new ReasonerService(this, this.resources, null);
            }

            if (Utils.Network.isAlive("http://127.0.0.1:" + Resolver.DEFAULT_PORT + " /resolver/actuator")) {
                this.resolver = new ResolverClient("http://127.0.0.1:" + Resolver.DEFAULT_PORT + " /resolver");
            } else {
                this.resolver = new ResolverService(this, this.resources);
            }

            if (Utils.Network.isAlive("http://127.0.0.1:" + RuntimeService.DEFAULT_PORT + " /runtime/actuator")) {
                this.runtime = new RuntimeClient("http://127.0.0.1:" + RuntimeService.DEFAULT_PORT + " /runtime");
            } else {
                this.runtime = new org.integratedmodelling.klab.services.runtime.RuntimeService(this, this.resources,
                        this.resolver);
            }

        }

        @Override
        public boolean shutdown() {
            this.reasoner.shutdown();
            this.resources.shutdown();
            this.reasoner.shutdown();
            this.runtime.shutdown();
            return true;
        }

        @Override
        public UserScope authorizeUser(UserIdentity user) {
            return new EngineScopeImpl(user) {

                private static final long serialVersionUID = -7075324015105362816L;

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
    public static Authentication setup() {
        return new TestAuthentication();
    }
}
