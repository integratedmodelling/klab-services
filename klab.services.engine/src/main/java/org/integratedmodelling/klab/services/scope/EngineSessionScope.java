package org.integratedmodelling.klab.services.scope;

import java.util.HashMap;
import java.util.Map;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.services.actors.messages.user.CreateContext;

public class EngineSessionScope extends EngineScope implements SessionScope {

	private String name;
	private Map<String, ContextScope> contexts = new HashMap<>();
	private Scale geometry;

	public void setName(String name) {
		this.name = name;
	}

	EngineSessionScope(EngineScope parent) {
		super(parent);
		this.data = Parameters.create();
		this.data.putAll(parent.data);
	}

	@Override
	public Scale getScale() {
		return geometry;
	}

	@Override
	public ContextScope createContext(String contextId, Geometry geometry) {

		final EngineContextScope ret = new EngineContextScope(this);
		ret.setName(contextId);
		ret.setStatus(Status.WAITING);
		Ref contextAgent = this.getAgent()
				.ask(new CreateContext(ret, contextId, geometry == null ? this.geometry : geometry), Ref.class);
		if (!contextAgent.isEmpty()) {
			ret.setStatus(Status.STARTED);
			ret.setAgent(contextAgent);
			contexts.put(contextId, ret);
		} else {
			ret.setStatus(Status.ABORTED);
		}
		return ret;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public <T extends KlabService> T getService(Class<T> serviceClass) {
		// TODO
		return parentScope.getService(serviceClass);
	}

	@Override
	public ContextScope getContext(String urn) {
		return contexts.get(urn);
	}

}
