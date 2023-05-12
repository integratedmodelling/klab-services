package org.integratedmodelling.klab.services.resolver;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;
import org.integratedmodelling.klab.api.knowledge.Instance;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.services.Authentication;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourceProvider;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.services.resolver.dataflow.DataflowService;
import org.integratedmodelling.klab.services.resolver.resolution.Resolution;
import org.integratedmodelling.klab.services.resolver.resolution.Resolution.Node;
import org.springframework.beans.factory.annotation.Autowired;

public class ResolverService implements Resolver {

    private static final long serialVersionUID = 5606716353692671802L;

    // TODO autowire? For now only a "service" by name. Need to expose Resolution at the API level
    // for this to change.
    private DataflowService dataflowService = new DataflowService();
    private ResourceProvider resources;
    private RuntimeService runtime;
    private ServiceScope serviceScope;

    @Autowired
    public ResolverService(Authentication authentication, ResourceProvider resources, RuntimeService runtime) {
        this.serviceScope = authentication.authorizeService(this);
        this.resources = resources;
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
        return serviceScope;
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
    public Dataflow<?> resolve(Knowledge resolvable, ContextScope scope) {

        Resolution resolution = new Resolution(resolvable, scope);

        // reduce to either observable or instance
        switch(Knowledge.classify(resolvable)) {
        case CONCEPT:
            // promote to observable
            break;
        case MODEL:
            // same
            break;
        case RESOURCE:
            // same
            break;
        default:
            break;
        }

        if (resolvable instanceof Observable) {
            resolveObservable((Observable) resolvable, resolution.root());
        } else if (resolvable instanceof Instance) {
            resolveInstance((Instance) resolvable, resolution.root());
        }

        if (resolution.getCoverage().isRelevant()) {
            return dataflowService.compile(resolution);
        }

        return Dataflow.empty(
                resolvable instanceof Observable ? (Observable) resolvable : ((Instance) resolvable).getObservable(),
                resolution.getCoverage());
    }

    private void resolveInstance(Instance resolvable, Node root) {
        // TODO Auto-generated method stub

    }

    private void resolveObservable(Observable resolvable, Node root) {
        // TODO Auto-generated method stub

    }

}
