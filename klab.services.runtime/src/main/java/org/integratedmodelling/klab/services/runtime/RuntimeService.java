package org.integratedmodelling.klab.services.runtime;

import java.util.concurrent.Future;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.Authentication;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourceProvider;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

public class RuntimeService implements org.integratedmodelling.klab.api.services.RuntimeService {

    private static final long serialVersionUID = -3119521647259754846L;

    public RuntimeService(Authentication testAuthentication, ResourceProvider resources, Resolver resolver) {
        // TODO Auto-generated constructor stub
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
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Capabilities getCapabilities() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Future<Observation> run(Dataflow<?> dataflow, ContextScope scope) {
        // TODO Auto-generated method stub
        return null;
    }

}
