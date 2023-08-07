package org.integratedmodelling.klab.services.resolver;

import java.util.List;
import java.util.Map;

import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Prioritizer;

public class PrioritizerImpl implements Prioritizer<Model> {

	private ContextScope scope;
	private Scale scale;

	public PrioritizerImpl(ContextScope scope, Scale scale) {
		this.scope = scope;
		this.scale = scale;
	}

	@Override
	public int compare(Model o1, Model o2) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Map<String, Double> computeCriteria(Model model, ContextScope context) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, Double> getRanks(Model md) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> listCriteria() {
		// TODO Auto-generated method stub
		return null;
	}

}
