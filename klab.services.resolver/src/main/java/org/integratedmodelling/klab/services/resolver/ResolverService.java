package org.integratedmodelling.klab.services.resolver;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.integratedmodelling.klab.api.knowledge.Instance;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.Authentication;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.resolver.dataflow.DataflowService;
import org.integratedmodelling.klab.services.resolver.resolution.ResolutionGraph;
import org.integratedmodelling.klab.services.resolver.resolution.ResolutionGraph.Resolution;
import org.springframework.beans.factory.annotation.Autowired;

public class ResolverService extends BaseService implements Resolver {

	private static final long serialVersionUID = 5606716353692671802L;

	private static final String RESOLUTION_KEY = "#_KLAB_RESOLUTION";

	// TODO autowire? For now only a "service" by name. Need to expose Resolution at
	// the API level
	// for this to change.
	private DataflowService dataflowService = new DataflowService();
	private ServiceScope serviceScope;

	@Autowired
	public ResolverService(Authentication authentication) {
		this.serviceScope = authentication.authorizeService(this);
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
	public Capabilities capabilities() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Dataflow<?> resolve(Knowledge resolvable, ContextScope scope) {

		ResolutionGraph resolutionGraph = getResolution(scope);

		Resolution resolution = resolve(resolutionGraph.newResolution(resolvable));
		if (resolution.isComplete()) {
			return dataflowService.compile(resolution);
		}

		return Dataflow.empty(
				resolvable instanceof Observable ? (Observable) resolvable : ((Instance) resolvable).getObservable(),
				resolutionGraph.getCoverage());
	}

	private ResolutionGraph getResolution(ContextScope scope) {
		if (!scope.getData().containsKey(RESOLUTION_KEY)) {
			scope.setData(RESOLUTION_KEY, new ResolutionGraph(scope));
		}
		return scope.getData().get(RESOLUTION_KEY, ResolutionGraph.class);
	}

	/**
	 * Top-level: resolve the observable that's already in the node and put a model
	 * and a coverage in it. Return the same node with updated data.
	 * 
	 * @param node
	 * @return
	 */
	private Resolution resolve(Resolution node) {

		// check for pre-resolved in this branch
		Resolution previous = node.getResolution(node.observable);
		if (previous != null) {
			return previous;
		}

		return node;
	}

	private Collection<Observable> resolveAbstractPredicates(Observable observable, ResolutionGraph resolution) {

		Set<Observable> ret = new HashSet<>();

		// TODO!
		ret.add(observable);

		return ret;
	}

	@Override
	public <T extends Knowledge> T resolveKnowledge(String urn, Class<T> knowledgeClass, Scope scope) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Query all the resource servers available in the scope to find the models that
	 * can observe the passed observable. The result should be ranked in decreasing
	 * order of fit to the context and the RESOLUTION_SCORE ranking should be in
	 * their metadata.
	 * 
	 * @param observable
	 * @param scope
	 * @return
	 */
	@Override
	public List<Model> queryModels(Observable observable, ContextScope scope) {
		return null;
	}

	@Override
	public void initializeService(Scope scope) {
		// TODO Auto-generated method stub
		
	}

}
