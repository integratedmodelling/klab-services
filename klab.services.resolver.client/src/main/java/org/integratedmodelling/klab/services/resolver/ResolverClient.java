package org.integratedmodelling.klab.services.resolver;

import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.resolver.Resolution;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.net.URL;
import java.util.List;

public class ResolverClient extends ServiceClient implements Resolver {

    public ResolverClient() {
        super(Type.RESOLVER);
    }

    public ResolverClient(Identity identity, List<ServiceReference> services) {
        super(Type.RESOLVER, identity, services);
    }

    public ResolverClient(URL url) {
        super(url);
    }

    public boolean isLocal() {
        String serverId = Utils.Strings.hash(Utils.OS.getMACAddress());
        return (capabilities().getServerId() == null && serverId == null) ||
                (capabilities().getServerId() != null && capabilities().getServerId().equals("RESOLVER_" + serverId));
    }

    @Override
    public Capabilities capabilities() {
        return client.get(ServicesAPI.CAPABILITIES, Capabilities.class);
    }

    @Override
    public Resolution resolve(Knowledge resolvable, ContextScope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends Knowledge> T resolveKnowledge(String urn, Class<T> knowledgeClass, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Model> queryModels(Observable observable, ContextScope scope, Scale scale) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Dataflow<Observation> compile(Knowledge resolved, Resolution resolution, ContextScope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String encodeDataflow(Dataflow<Observation> dataflow) {
        // TODO Auto-generated method stub
        return null;
    }

}
