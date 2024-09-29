package org.integratedmodelling.common.services.client.runtime;

import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.services.RuntimeCapabilitiesImpl;
import org.integratedmodelling.common.services.client.GraphQLClient;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceSideScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.api.services.runtime.objects.ScopeRequest;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.net.URL;
import java.util.List;
import java.util.function.BiConsumer;

public class RuntimeClient extends ServiceClient implements RuntimeService {

    private GraphQLClient graphClient;

    public RuntimeClient(URL url, Identity identity, List<ServiceReference> services, Parameters<Engine.Setting> settings, BiConsumer<Channel,
            Message>... listeners) {
        super(Type.RUNTIME, url, identity, settings, services, listeners);
    }

    public RuntimeClient(URL url, Identity identity, KlabService owner, Parameters<Engine.Setting> settings) {
        super(Type.RUNTIME, url, identity, List.of(), settings, owner);
    }

    @Override
    protected String establishConnection() {
        var ret = super.establishConnection();
        this.graphClient = new GraphQLClient(this.getUrl() + ServicesAPI.RUNTIME.DIGITAL_TWIN_GRAPH, ret);
        return ret;
    }

    @Override
    public String registerSession(SessionScope scope) {

        ScopeRequest request = new ScopeRequest();
        request.setName(scope.getName());

        var hasMessaging =
                scope.getParentScope() instanceof MessagingChannel messagingChannel && messagingChannel.hasMessaging();

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

        if (isLocal() && scope.getService(
                Reasoner.class) instanceof ServiceClient reasonerClient && reasonerClient.isLocal()) {
            request.getReasonerServices().add(reasonerClient.getUrl());
        }

        if (hasMessaging) {
            // TODO setup desired request. This will send no header and use the defaults.
            // Resolver should probably only catch events and errors.
        }

        var ret = client./*withScope(scope.getParentScope()).*/post(ServicesAPI.CREATE_SESSION, request,
                String.class, "id", scope instanceof ServiceSideScope serviceSideScope ?
                                    serviceSideScope.getId() : null);

        var brokerURI = client.getResponseHeader(ServicesAPI.MESSAGING_URN_HEADER);
        if (brokerURI != null && scope instanceof MessagingChannelImpl messagingChannel) {
            var queues = getQueuesFromHeader(
                    scope,
                    client.getResponseHeader(ServicesAPI.MESSAGING_QUEUES_HEADER));
            messagingChannel.setupMessaging(brokerURI, ret, queues);
        }

        return ret;
    }

    @Override
    public String registerContext(ContextScope scope) {

        ScopeRequest request = new ScopeRequest();
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

        request.getRuntimeServices().add(getUrl());

        if (isLocal() && scope.getService(
                Reasoner.class) instanceof ServiceClient reasonerClient && reasonerClient.isLocal()) {
            request.getReasonerServices().add(reasonerClient.getUrl());
        }

        if (hasMessaging) {
            // TODO setup desired request. This will send no header and use the defaults.
        }

        var ret = client.withScope(scope.getParentScope()).post(ServicesAPI.CREATE_CONTEXT, request,
                String.class, "id", scope instanceof ServiceSideScope serviceSideScope ?
                                    serviceSideScope.getId() : null);

        if (hasMessaging) {
            var queues = getQueuesFromHeader(
                    scope,
                    client.getResponseHeader(ServicesAPI.MESSAGING_QUEUES_HEADER));
            if (scope instanceof MessagingChannelImpl messagingChannel) {
                messagingChannel.setupMessagingQueues(ret, queues);
            }
        }

        return ret;
    }

    @Override
    public long submit(Observation observation, ContextScope scope) {
        ResolutionRequest resolutionRequest = new ResolutionRequest();
        resolutionRequest.setObservation(observation);
        resolutionRequest.setResolutionConstraints(scope.getResolutionConstraints());
        return client.withScope(scope).post(ServicesAPI.RUNTIME.OBSERVE, resolutionRequest, Long.class);
    }

    @Override
    public Provenance runDataflow(Dataflow<Observation> dataflow, ContextScope contextScope) {
        return null;
    }

    @Override
    public Capabilities capabilities(Scope scope) {
        return client.withScope(scope).get(ServicesAPI.CAPABILITIES, RuntimeCapabilitiesImpl.class,
                Notification.Mode.Silent);
    }

    @Override
    public List<SessionInfo> getSessionInfo(Scope scope) {
        return client.withScope(scope).getCollection(ServicesAPI.RUNTIME.GET_SESSION_INFO, SessionInfo.class);
    }

    public GraphQLClient graphClient() {
        return graphClient;
    }
}
