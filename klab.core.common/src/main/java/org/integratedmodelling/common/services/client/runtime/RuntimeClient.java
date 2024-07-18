package org.integratedmodelling.common.services.client.runtime;

import org.integratedmodelling.common.services.RuntimeCapabilitiesImpl;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

public class RuntimeClient extends ServiceClient implements RuntimeService {

    public RuntimeClient() {
        super(Type.RUNTIME);
    }

    public RuntimeClient(Identity identity, List<ServiceReference> services) {
        super(Type.RUNTIME, identity, services);
    }

    public RuntimeClient(URL url, Identity identity, List<ServiceReference> services, BiConsumer<Channel,
            Message>... listeners) {
        super(Type.RUNTIME, url, identity, services, listeners);
    }

    public RuntimeClient(URL url) {
        super(url);
    }

    @Override
    public boolean releaseScope(Scope scope) {
        return false;
    }

    @Override
    public String createSession(UserScope scope, String sessionName) {
        return client.get(ServicesAPI.RUNTIME.CREATE_SESSION, String.class, "name", sessionName);
    }

    @Override
    public String createContext(SessionScope scope, String contextName) {
        return client.withScope(scope).get(ServicesAPI.RUNTIME.CREATE_CONTEXT, String.class, "name", contextName);
    }

    @Override
    public Capabilities capabilities(Scope scope) {
        return client.get(ServicesAPI.CAPABILITIES, RuntimeCapabilitiesImpl.class);
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
