package org.integratedmodelling.common.services.client.runtime;

import org.integratedmodelling.common.services.RuntimeCapabilitiesImpl;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextRequest;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

public class RuntimeClient extends ServiceClient implements RuntimeService {

    private GraphQLClient graphClient;

    public RuntimeClient() {
        super(Type.RUNTIME);
    }

    //    public RuntimeClient(Identity identity, List<ServiceReference> services) {
    //        super(Type.RUNTIME, identity, services);
    //    }

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
    protected void establishConnection() {
        super.establishConnection();
        this.graphClient = new GraphQLClient(this.getUrl() + ServicesAPI.RUNTIME.DIGITAL_TWIN_GRAPH);
    }

    @Override
    public String registerSession(SessionScope scope) {
        return client.get(ServicesAPI.RUNTIME.CREATE_SESSION, String.class, "name", scope.getName());
    }

    @Override
    public String registerContext(ContextScope scope) {

        ContextRequest request = new ContextRequest();
        request.setName(scope.getName());

        var runtime = scope.getService(RuntimeService.class);

        // The runtime needs to use our resolver(s) and resource service(s), as long as they're accessible.
        // The reasoner can be the runtime's own unless we have locked worldview projects.
        for (var service : scope.getServices(ResourcesService.class)) {
            if (service instanceof ServiceClient serviceClient) {
                // we only send a local URL if we're local ourselves
                if (!serviceClient.isLocal() || (serviceClient.isLocal() && isLocal())) {
                    request.getResourceServices().add(serviceClient.getUrl());
                }
            }
        }
        for (var service : scope.getServices(Resolver.class)) {
            if (service instanceof ServiceClient serviceClient) {
                // we only send a local URL if we're local ourselves
                if (!serviceClient.isLocal() || (serviceClient.isLocal() && isLocal())) {
                    request.getResolverServices().add(serviceClient.getUrl());
                }
            }
        }

        if (isLocal() && scope.getService(Reasoner.class) instanceof ServiceClient reasonerClient && reasonerClient.isLocal()) {
            request.getReasonerServices().add(reasonerClient.getUrl());
        }

        return client.withScope(scope.getParentScope()).post(ServicesAPI.RUNTIME.CREATE_CONTEXT, request,
                String.class);
    }

    @Override
    public Capabilities capabilities(Scope scope) {
        return client.get(ServicesAPI.CAPABILITIES, RuntimeCapabilitiesImpl.class);
    }

}
