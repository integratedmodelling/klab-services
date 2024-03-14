package org.integratedmodelling.common.services.client.runtime;

import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;

public class RuntimeClient extends ServiceClient implements RuntimeService {

    public RuntimeClient() {
        super(Type.RUNTIME);
    }

    public RuntimeClient(Identity identity, List<ServiceReference> services) {
        super(Type.RUNTIME, identity, services);
    }

    public RuntimeClient(URL url, Identity identity, List<ServiceReference> services) {
        super(Type.RUNTIME, url, identity, services);
    }

    public RuntimeClient(URL url) {
        super(url);
    }

    @Override
    public boolean releaseScope(ContextScope scope) {
        return false;
    }

    @Override
    public Capabilities capabilities() {
        return client.get(ServicesAPI.CAPABILITIES, Capabilities.class);
    }

    @Override
    public Future<Observation> run(Dataflow<Observation> dataflow, ContextScope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Observation> children(ContextScope scope, Observation rootObservation) {
        return null;
    }

    @Override
    public Observation parent(ContextScope scope, Observation rootObservation) {
        return null;
    }

}
