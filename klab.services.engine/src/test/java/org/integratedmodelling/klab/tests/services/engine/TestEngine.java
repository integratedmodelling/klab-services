package org.integratedmodelling.klab.tests.services.engine;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;
import org.integratedmodelling.klab.api.authentication.scope.UserScope;
import org.integratedmodelling.klab.api.services.Authentication;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourceProvider;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.services.reasoner.ReasonerClient;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.resources.ResourcesService;
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

    static class TestAuthentication implements Authentication {

        private static final long serialVersionUID = -8805140708277187846L;

        Reasoner reasoner;
        ResourceProvider resources;
        RuntimeService runtime;
        Resolver resolver;

        TestAuthentication() {
            // TODO check for a locally running service for each category; if existing, create a
            // client, otherwise create an embedded service
            if (Utils.Network.isAlive("http://127.0.0.1:8092/resources/actuator")) {
                this.resources = null; // new ResourcesClient("http://127.0.0.1:8091/resources");
            } else {
                this.resources =  new ResourcesService(this);
            }

            if (Utils.Network.isAlive("http://127.0.0.1:8091/reasoner/actuator")) {
                this.reasoner = new ReasonerClient("http://127.0.0.1:8091/reasoner");
            } else {
                this.reasoner = new ReasonerService(this, this.resources, null);
            }
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

        @Override
        public ServiceScope scope() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean shutdown() {
            this.reasoner.shutdown();
            this.resources.shutdown();
            // TODO
            return false;
        }

        @Override
        public Capabilities getCapabilities() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean checkPermissions(ResourcePrivileges permissions, Scope scope) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public UserScope getAnonymousScope() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public ServiceScope authenticateService(KlabService service) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public UserScope authenticateUser(ServiceScope serviceScope) {
            // TODO Auto-generated method stub
            return null;
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
