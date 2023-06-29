package org.integratedmodelling.klab.services.resolver;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.lang.kim.KimModelStatement;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

public class ResolverService implements Resolver, Resolver.Admin {

    private static final long serialVersionUID = -698503852745518898L;

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
    public Concept addModel(KimModelStatement statement) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Capabilities getCapabilities() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Dataflow<?> resolve(Knowledge resolvable, ContextScope scope) {
        // TODO Auto-generated method stub
        return null;
    }

	@Override
	public <T extends Knowledge> T resolveKnowledge(String urn, Class<T> knowledgeClass, Scope scope) {
		// TODO Auto-generated method stub
		return null;
	}

}
