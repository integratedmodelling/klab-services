package org.integratedmodelling.klab.services.resolver;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.resolver.Resolution;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.utilities.Utils;

import java.net.URL;
import java.util.List;

public class ResolverService extends BaseService implements Resolver, Resolver.Admin {

    public ResolverService(ServiceScope scope, Type serviceType, ServiceStartupOptions options) {
        super(scope, serviceType, options);
    }

    @Override
    public void initializeService() {

    }

    @Override
    public URL getUrl() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getLocalName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String serviceId() {
        return null;
    }

    @Override
    public ServiceScope serviceScope() {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public boolean shutdown() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean loadKnowledge(ResourceSet resources) {
        // TODO Auto-generated method stub
        return false;
    }
//    public boolean isLocal() {
//        String serverId = org.integratedmodelling.common.utils.Utils.Strings.hash(Utils.OS.getMACAddress());
//        return (capabilities(null).getServerId() == null && serverId == null) ||
//                (capabilities(null).getServerId() != null && capabilities(null).getServerId().equals("RESOLVER_" + serverId));
//    }

    @Override
    public Capabilities capabilities(Scope scope) {
        return null;
    }

    @Override
    public ServiceStatus status() {
        return null;
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
