package org.integratedmodelling.common.services.client.resolver;

import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.services.ResolverCapabilitiesImpl;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.api.services.runtime.objects.ScopeRequest;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.net.URL;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * TODO/CHECK this should only be used by the runtime, unless in a testing configuration. The resolve()
 * call is
 *  all wrong.
 */
public class ResolverClient extends ServiceClient implements Resolver {

    //    public ResolverClient() {
    //        super(Type.RESOLVER);
    //    }
    //
    //    public ResolverClient(Identity identity, List<ServiceReference> services) {
    //        super(Type.RESOLVER, identity, services);
    //    }

    public ResolverClient(URL url, Identity identity, KlabService owner, Parameters<Engine.Setting> settings) {
        super(Type.RESOLVER, url, identity, List.of(), settings, owner);
    }

    public ResolverClient(URL url, Identity identity, List<ServiceReference> services, Parameters<Engine.Setting> settings, BiConsumer<Channel,
            Message>... listeners) {
        super(Type.RESOLVER, url, identity, settings, services, listeners);
    }

    public ResolverClient(URL url, Parameters<Engine.Setting> settings) {
        super(url, settings);
    }

    @Override
    public Capabilities capabilities(Scope scope) {
        return client.withScope(scope).get(ServicesAPI.CAPABILITIES, ResolverCapabilitiesImpl.class,
                Notification.Mode.Silent);
    }

    @Override
    public Dataflow<Observation> resolve(Observation observation, ContextScope contextScope) {
        ResolutionRequest request = new ResolutionRequest();
        request.setObservation(observation);
        request.getResolutionConstraints().addAll(contextScope.getResolutionConstraints());
        return client.withScope(contextScope).post(ServicesAPI.RESOLVER.RESOLVE_OBSERVATION, request,
                Dataflow.class);
    }

    //    @Override
    //    public Resolution resolve(String resolvableUrn, ContextScope scope) {
    //
    //        ResolutionRequest request = new ResolutionRequest();
    //        if (resolvableUrn.contains("://")) {
    //            /**
    //             * Split the URN from the service. To avoid issues we just look for the {urn} part in the
    //             * constant and decompile it from URL encoding.
    //             */
    //            String urnPart = ServicesAPI.RESOURCES.RESOLVE_URN.replace(ServicesAPI.URN_PARAMETER, "");
    //            int callPos = resolvableUrn.indexOf(urnPart);
    //            if (callPos <= 0) {
    //                scope.error("Resolver client: malformed resolvable URL: " + resolvableUrn);
    //                return null;
    //            }
    //            String host = resolvableUrn.substring(0, callPos);
    //            callPos += urnPart.length() + 1;
    //            String urn = Utils.Escape.fromURL(resolvableUrn.substring(callPos));
    //
    //            try {
    ////                request.setResolverUrl(new URI(host).toURL());
    //            } catch (Throwable e) {
    //                scope.error(e);
    //                return null;
    //            }
    ////            request.setUrn(urn);
    //        } else {
    ////            request.setUrn(resolvableUrn);
    //        }
    //
    //        /**
    //         * TODO add all contingent info to rebuild the context beyond the root context: scenarios,
    //          observer,
    //         *  metadata and anything remaining.
    //         */
    //
    //        return null; // client.post(ServicesAPI.RESOLVER.RESOLVE_OBSERVATION, request, Resolution
    //        .class);
    //    }

//    @Override
//    public List<Model> queryModels(Observable observable, ContextScope scope, Scale scale) {
//        // TODO Auto-generated method stub
//        return null;
//    }

    //    @Override
    //    public Dataflow<Observation> compile(Resolvable resolved, Resolution resolution, ContextScope
    //    scope) {
    //        // TODO Auto-generated method stub
    //        return null;
    //    }

    @Override
    public String encodeDataflow(Dataflow<Observation> dataflow) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * When called as a slave from a service, add the sessionId parameter to build a peer scope at the remote
     * service side.
     *
     * @param scope a client scope that should record the ID for future communication. If the ID is null, the
     *              call has failed.
     * @return
     */
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

        if (getOwnerService() != null) {
            switch (getOwnerService()) {
                case Resolver resolver -> request.getResolverServices().add(resolver.getUrl());
                case RuntimeService runtimeService ->
                        request.getRuntimeServices().add(runtimeService.getUrl());
                case ResourcesService resourcesService ->
                        request.getResourceServices().add(resourcesService.getUrl());
                case Reasoner reasoner -> request.getReasonerServices().add(reasoner.getUrl());
                default -> {
                }
            }
        }

        if (isLocal() && scope.getService(Reasoner.class) instanceof ServiceClient reasonerClient && reasonerClient.isLocal()) {
            request.getReasonerServices().add(reasonerClient.getUrl());
        }

        if (hasMessaging) {
            // TODO setup desired request. This will send no header and use the defaults.
            // Resolver should probably only catch events and errors.
        }

        var ret = client.withScope(scope.getParentScope()).post(ServicesAPI.CREATE_SESSION, request,
                String.class, "id", scope instanceof ServiceSideScope serviceSideScope ?
                                    serviceSideScope.getId() : null);

        var brokerURI = client.getResponseHeader(ServicesAPI.MESSAGING_URN_HEADER);
        if (brokerURI != null && scope instanceof MessagingChannelImpl messagingChannel) {
            var queues = getQueuesFromHeader(scope,
                    client.getResponseHeader(ServicesAPI.MESSAGING_QUEUES_HEADER));
            messagingChannel.setupMessaging(brokerURI, ret, queues);
        }

        return ret;
    }

    /**
     * When called as a slave from a service, add the sessionId parameter to build a peer scope at the remote
     * service side.
     *
     * @param scope a client scope that should record the ID for future communication. If the ID is null, the
     *              call has failed.
     * @return
     */
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

        if (isLocal() && scope.getService(Reasoner.class) instanceof ServiceClient reasonerClient && reasonerClient.isLocal()) {
            request.getReasonerServices().add(reasonerClient.getUrl());
        }

        if (getOwnerService() != null) {
            switch (getOwnerService()) {
                case Resolver resolver -> request.getResolverServices().add(resolver.getUrl());
                case RuntimeService runtimeService ->
                        request.getRuntimeServices().add(runtimeService.getUrl());
                case ResourcesService resourcesService ->
                        request.getResourceServices().add(resourcesService.getUrl());
                case Reasoner reasoner -> request.getReasonerServices().add(reasoner.getUrl());
                default -> {
                }
            }
        }

        if (hasMessaging) {
            // TODO setup desired request. This will send no header and use the defaults.
            // Resolver should probably only catch events and errors.
        }

        var ret = client.withScope(scope.getParentScope()).post(ServicesAPI.CREATE_CONTEXT, request,
                String.class, "id", scope instanceof ServiceSideScope serviceSideScope ?
                                    serviceSideScope.getId() : null);

        if (hasMessaging) {
            var queues = getQueuesFromHeader(scope,
                    client.getResponseHeader(ServicesAPI.MESSAGING_QUEUES_HEADER));
            if (scope instanceof MessagingChannelImpl messagingChannel) {
                messagingChannel.setupMessagingQueues(ret, queues);
            }
        }

        return ret;
    }
}
