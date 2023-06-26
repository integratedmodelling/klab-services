package org.integratedmodelling.klab.services.resolver;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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

	private static final String RESOLUTION_KEY = "#_KLAB_RESOLUTION";

	// TODO autowire? For now only a "service" by name. Need to expose Resolution at
	// the API level
	// for this to change.
	private DataflowService dataflowService = new DataflowService();
	private ServiceScope serviceScope;

	@Autowired
	public ResolverService(Authentication authentication, ResourceProvider resources, RuntimeService runtime) {
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
	public Capabilities getCapabilities() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Dataflow<?> resolve(Knowledge resolvable, ContextScope scope) {

		Resolution resolution = getResolution(scope);
		Node node = resolve(resolution.getNode(resolvable));

		if (node.getCoverage().isRelevant()) {
			return dataflowService.compile(resolution);
		}

		return Dataflow.empty(
				resolvable instanceof Observable ? (Observable) resolvable : ((Instance) resolvable).getObservable(),
				resolution.getCoverage());
	}

	private Resolution getResolution(ContextScope scope) {
		if (!scope.getData().containsKey(RESOLUTION_KEY)) {
			scope.setData(RESOLUTION_KEY, new Resolution(scope));
		}
		return scope.getData().get(RESOLUTION_KEY, Resolution.class);
	}

	/**
	 * Top-level: resolve the observable that's already in the node and put a model
	 * and a coverage in it. Return the same node.
	 * 
	 * @param node
	 * @return
	 */
	private Node resolve(Node node) {
		// TODO Auto-generated method stub
		return node;
	}

	private Collection<Observable> resolveAbstractPredicates(Observable observable, Resolution resolution) {

		Set<Observable> ret = new HashSet<>();

		// TODO!
		ret.add(observable);

		return ret;
	}

}
