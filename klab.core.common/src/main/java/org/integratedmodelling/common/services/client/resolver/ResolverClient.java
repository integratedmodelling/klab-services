package org.integratedmodelling.common.services.client.resolver;

import org.integratedmodelling.common.services.ResolverCapabilitiesImpl;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resolvable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.resolver.Resolution;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * TODO/CHECK this should only be used by the runtime, unless in a testing configuration. The resolve() call is
 *  all wrong.
 */
public class ResolverClient extends ServiceClient implements Resolver {

    public ResolverClient() {
        super(Type.RESOLVER);
    }

    public ResolverClient(Identity identity, List<ServiceReference> services) {
        super(Type.RESOLVER, identity, services);
    }

    public ResolverClient(URL url, Identity identity) {
        super(Type.RESOLVER, url, identity, List.of());
    }

    public ResolverClient(URL url, Identity identity, List<ServiceReference> services, BiConsumer<Channel, Message>... listeners) {
        super(Type.RESOLVER, url, identity, services, listeners);
    }

    public ResolverClient(URL url) {
        super(url);
    }

    @Override
    public Capabilities capabilities(Scope scope) {
        return client.get(ServicesAPI.CAPABILITIES, ResolverCapabilitiesImpl.class);
    }

    @Override
    public Resolution resolve(String resolvableUrn, ContextScope scope) {

        ResolutionRequest request = new ResolutionRequest();
        if (resolvableUrn.contains("://")) {
            /**
             * Split the URN from the service. To avoid issues we just look for the {urn} part in the
             * constant and decompile it from URL encoding.
             */
            String urnPart = ServicesAPI.RESOURCES.RESOLVE_URN.replace(ServicesAPI.URN_PARAMETER, "");
            int callPos = resolvableUrn.indexOf(urnPart);
            if (callPos <= 0) {
                scope.error("Resolver client: malformed resolvable URL: " + resolvableUrn);
                return null;
            }
            String host = resolvableUrn.substring(0, callPos);
            callPos += urnPart.length() + 1;
            String urn = Utils.Escape.fromURL(resolvableUrn.substring(callPos));

            try {
                request.setResolverUrl(new URI(host).toURL());
            } catch (Throwable e) {
                scope.error(e);
                return null;
            }
            request.setUrn(urn);
        } else {
            request.setUrn(resolvableUrn);
        }

        /**
         * TODO add all contingent info to rebuild the context beyond the root context: scenarios, observer,
         *  metadata and anything remaining.
         */

        return client.post(ServicesAPI.RESOLVER.RESOLVE_KNOWLEDGE, request, Resolution.class);
    }

    @Override
    public List<Model> queryModels(Observable observable, ContextScope scope, Scale scale) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Dataflow<Observation> compile(Resolvable resolved, Resolution resolution, ContextScope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String encodeDataflow(Dataflow<Observation> dataflow) {
        // TODO Auto-generated method stub
        return null;
    }

}
