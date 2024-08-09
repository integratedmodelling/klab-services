package org.integratedmodelling.common.services.client.runtime;

import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.services.RuntimeCapabilitiesImpl;
import org.integratedmodelling.common.services.client.GraphQLClient;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextRequest;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.net.URL;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class RuntimeClient extends ServiceClient implements RuntimeService {

    private GraphQLClient graphClient;

    public RuntimeClient() {
        super(Type.RUNTIME);
    }

    public RuntimeClient(URL url, Identity identity, List<ServiceReference> services, BiConsumer<Channel,
            Message>... listeners) {
        super(Type.RUNTIME, url, identity, services, listeners);
    }

    public RuntimeClient(URL url) {
        super(url);
    }

    @Override
    protected String establishConnection() {
        var ret = super.establishConnection();
        this.graphClient = new GraphQLClient(this.getUrl() + ServicesAPI.RUNTIME.DIGITAL_TWIN_GRAPH, ret);
        return ret;
    }

    @Override
    public String registerSession(SessionScope scope) {
        var ret = client.get(ServicesAPI.RUNTIME.CREATE_SESSION, String.class, "name", scope.getName());
        var brokerURI = client.getResponseHeader(ServicesAPI.MESSAGING_URN_HEADER);
        if (brokerURI != null && scope instanceof MessagingChannelImpl messagingChannel) {
            var queues = getQueuesFromHeader(scope, client.getResponseHeader(ServicesAPI.MESSAGING_QUEUES_HEADER));
            messagingChannel.setupMessaging(brokerURI, ret, queues);
        }
        return ret;
    }

    private Set<Message.Queue> getQueuesFromHeader(SessionScope scope, String responseHeader) {
        if (responseHeader != null) {
            var ret = EnumSet.noneOf(Message.Queue.class);
            String[] qq = responseHeader.split(", ");
            for (var q : qq) {
                ret.add(Message.Queue.valueOf(q));
            }
            return ret;
        }
        return scope.defaultQueues();
    }

    @Override
    public String registerContext(ContextScope scope) {

        ContextRequest request = new ContextRequest();
        request.setName(scope.getName());

        var runtime = scope.getService(RuntimeService.class);
        var hasMessaging =
                scope.getParentScope() instanceof MessagingChannel messagingChannel && messagingChannel.hasMessaging();

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

        if (hasMessaging) {
            // TODO setup desired request. This will send no header and use the defaults.
        }

        var ret = client.withScope(scope.getParentScope()).post(ServicesAPI.RUNTIME.CREATE_CONTEXT, request,
                String.class);

        if (hasMessaging) {
            var queues = getQueuesFromHeader(scope, client.getResponseHeader(ServicesAPI.MESSAGING_QUEUES_HEADER));
            if (scope instanceof MessagingChannelImpl messagingChannel) {
                messagingChannel.setupMessagingQueues(ret, queues);
            }
        }

        return ret;
    }

    @Override
    public Capabilities capabilities(Scope scope) {
        return client.get(ServicesAPI.CAPABILITIES, RuntimeCapabilitiesImpl.class);
    }

    public GraphQLClient graphClient() {
        return graphClient;
    }
}
