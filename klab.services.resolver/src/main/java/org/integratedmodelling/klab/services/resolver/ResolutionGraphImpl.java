package org.integratedmodelling.klab.services.resolver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.integratedmodelling.klab.api.knowledge.Instance;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resolver.Resolution;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

public class ResolutionGraphImpl extends DefaultDirectedGraph<Knowledge, ResolutionGraphImpl.ResolutionEdge>
		implements Resolution {

	private static final long serialVersionUID = 4088657660423175126L;

	private Knowledge resolvedKnowledge;
	private Coverage coverage;
	private Set<Knowledge> resolving = new HashSet<>();

	class ResolutionEdge extends DefaultEdge {

		private static final long serialVersionUID = 5876676141844919004L;

		private Observable observable;
		private ResolutionType type;

		public Coverage getCoverage() {
			return coverage;
		}

		public Observable getObservable() {
			return observable;
		}

		public void setObservable(Observable observable) {
			this.observable = observable;
		}

		public ResolutionType getType() {
			return type;
		}

		public void setType(ResolutionType type) {
			this.type = type;
		}

	}

	/**
	 * Create a resolution graph for the passed knowledge, pre-loading whatever has
	 * been already resolved in the passed scope.
	 * 
	 * @param root
	 * @param scope
	 */
	public ResolutionGraphImpl(Knowledge root, ContextScope scope) {
		super(ResolutionGraphImpl.ResolutionEdge.class);
		this.resolvedKnowledge = root;
		this.coverage = computeInitialCoverage(root, scope);
		for (Observable observable : scope.getCatalog().keySet()) {
			addVertex(observable);
			addVertex(scope.getCatalog().get(observable));
		}
	}

	private Coverage computeInitialCoverage(Knowledge root, ContextScope scope) {
		if (root instanceof Instance) {
			return Coverage.create(((Instance)root).getScale(), 1.0);
		} else if (scope.getGeometry() != null) {
			return Coverage.create(scope.getGeometry(), 0.0);
		}
		return Coverage.empty();
	}

	public ResolutionGraphImpl(Knowledge root, ContextScope scope, ResolutionGraphImpl parent) {
		this(root, scope);
		if (this.coverage == null) {
			this.coverage = parent.coverage;
		}
		this.resolvedKnowledge = root;
		this.resolving.addAll(parent.resolving);
		this.resolving.add(root);
	}

	/**
	 * Add a link (K) <--direct(observable)-- (O)
	 * 
	 * @param child
	 * @param mergingStrategy
	 * @return
	 */
	public Coverage merge(ResolutionGraphImpl child, Observable observable, LogicalConnector mergingStrategy) {

		if (this.coverage == null) {
			this.coverage = child.getCoverage();
		} else {
			this.coverage = this.coverage.merge(child.getCoverage(), mergingStrategy);
		}

		this.vertexSet().addAll(child.vertexSet());
		this.edgeSet().addAll(child.edgeSet());

		// TODO link our knowledge to the root in the incoming using Type.DIRECT

		return this.coverage;
	}

	/**
	 * Add a link (K) <--filter-- (model F applying to K)
	 * 
	 * @param child
	 * @param mergingStrategy
	 * @return
	 */
	public Coverage mergeFilter(ResolutionGraphImpl child, Observable observable, LogicalConnector mergingStrategy) {

		if (this.coverage == null) {
			this.coverage = child.getCoverage();
		} else {
			this.coverage = this.coverage.merge(child.getCoverage(), mergingStrategy);
		}

		this.vertexSet().addAll(child.vertexSet());
		this.edgeSet().addAll(child.edgeSet());

		// TODO link our knowledge to the root in the incoming using Type.FILTER

		return this.coverage;

	}

	/**
	 * Add a link (K) <--direct(deferring)-- (Z instantiator) <--deferred--(deferred
	 * = unresolved observable K within Z)
	 * 
	 * @param child
	 * @param mergingStrategy
	 * @param deferred
	 * @return
	 */
	public Coverage mergeDeferred(ResolutionGraphImpl child, LogicalConnector mergingStrategy, Observable deferring,
			Observable deferred) {

		if (this.coverage == null) {
			this.coverage = child.getCoverage();
		} else {
			this.coverage = this.coverage.merge(child.getCoverage(), mergingStrategy);
		}

		this.vertexSet().addAll(child.vertexSet());
		this.edgeSet().addAll(child.edgeSet());

		// TODO link our knowledge to the root in the incoming using Type.DEFERRED

		return this.coverage;
	}

	public Coverage getCoverage() {
		return coverage;
	}

	public void setCoverage(Coverage coverage) {
		this.coverage = coverage;
	}

	public ResolutionGraphImpl setEmpty() {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean isResolving(Knowledge observable) {
		return resolving.contains(observable);
	}

	@Override
	public Knowledge getResolvedKnowledge() {
		return this.resolvedKnowledge;
	}

	@Override
	public List<Knowledge> getResolving(Knowledge target, ResolutionType strategy) {
		List<Knowledge> ret = new ArrayList<>();
		for (ResolutionEdge edge : incomingEdgesOf(target)) {
			if (edge.type == strategy) {
				ret.add(getEdgeSource(edge));
			}
		}
		return ret;
	}

}
