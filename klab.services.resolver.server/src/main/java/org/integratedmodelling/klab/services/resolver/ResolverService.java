package org.integratedmodelling.klab.services.resolver;

import java.util.List;

import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.resolver.Resolution;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

public class ResolverService implements Resolver, Resolver.Admin {

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

    @Override
    public Capabilities capabilities() {
        // TODO Auto-generated method stub
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
	public List<Model> queryModels(Observable observable, ContextScope scope) {
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

//    @Override
//    public ResolutionGraph computeResolutionGraph(Knowledge resolvable, ContextScope scope) {
//        // TODO Auto-generated method stub
//        return null;
//    }
//
//    @Override
//    public Dataflow<Observation> computeDataflow(ResolutionGraph resolutionGraph) {
//        // TODO Auto-generated method stub
//        return null;
//    }

}
