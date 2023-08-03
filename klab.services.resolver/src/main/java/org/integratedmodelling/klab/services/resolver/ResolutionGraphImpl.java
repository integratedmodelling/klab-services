package org.integratedmodelling.klab.services.resolver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.integratedmodelling.klab.api.knowledge.Instance;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resolver.Resolution;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

public class ResolutionGraphImpl extends DefaultDirectedGraph<Knowledge, ResolutionGraphImpl.ResolutionEdge>
		implements Resolution {

	private static final long serialVersionUID = 4088657660423175126L;

	private Knowledge resolvedKnowledge;
	private Coverage coverage;
	private Set<Knowledge> resolving = new HashSet<>();

	private boolean empty;

	class ResolutionEdge extends DefaultEdge {

		private static final long serialVersionUID = 5876676141844919004L;

		private Observable observable;
		private ResolutionType type;

		public ResolutionEdge(Observable observable, ResolutionType type) {
			this.observable = observable;
			this.type = type;
		}

		public ResolutionEdge() {
		}

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
	 * Create a root resolution graph for the passed knowledge, pre-loading whatever
	 * has been already resolved in the passed scope.
	 * 
	 * @param root
	 * @param scope
	 */
	public ResolutionGraphImpl(Knowledge root, ContextScope scope) {
		super(ResolutionGraphImpl.ResolutionEdge.class);
		this.resolvedKnowledge = root;
		this.resolving.add(root);
		if (root instanceof Instance) {
			this.coverage = Coverage.create(((Instance) root).getScale(), 1.0);
		} else if (scope.getGeometry() != null) {
			this.coverage = Coverage.create(scope.getGeometry(), 0.0);
		}
		// pre-resolve
		for (Observable observable : scope.getCatalog().keySet()) {
			addVertex(observable);
			addVertex(scope.getCatalog().get(observable));
			addEdge(scope.getCatalog().get(observable), observable,
					new ResolutionEdge(observable, ResolutionType.RESOLVED));
		}
	}

	public ResolutionGraphImpl(Knowledge root, ContextScope scope, ResolutionGraphImpl parent) {

		super(ResolutionGraphImpl.ResolutionEdge.class);
		this.resolvedKnowledge = root;
		this.coverage = parent.getCoverage();
		Coverage kcov = null;
		if (root instanceof Instance) {
			kcov = Coverage.create(((Instance) root).getScale(), 1.0);
		} else if (root instanceof Model) {
			kcov = Coverage.create(((Model) root).getCoverage(), 1.0);
		}

		if (kcov != null) {
			this.coverage = this.coverage.merge(kcov, LogicalConnector.INTERSECTION);
		}

		Graphs.addGraph(this, parent);
		this.resolving.add(root);
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

		mergeGraph(child, observable, ResolutionType.DIRECT);

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

		mergeGraph(child, observable, ResolutionType.FILTERING);

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

		mergeGraph(child, deferring, ResolutionType.DEFERRAL);

		return this.coverage;
	}

	private void mergeGraph(ResolutionGraphImpl child, Observable observable, ResolutionType resolution) {

		for (Knowledge knowledge : child.vertexSet()) {
			this.addVertex(knowledge);
		}
		for (ResolutionEdge edge : child.edgeSet()) {
			this.addEdge(child.getEdgeSource(edge), child.getEdgeTarget(edge), edge);
		}
		this.addVertex(this.resolvedKnowledge);
		this.addVertex(child.resolvedKnowledge);
		this.addEdge(child.getResolvedKnowledge(), this.resolvedKnowledge, new ResolutionEdge(observable, resolution));
	}

	public Coverage getCoverage() {
		return coverage;
	}

	public void setCoverage(Coverage coverage) {
		this.coverage = coverage;
	}

	public ResolutionGraphImpl setEmpty() {
		this.empty = true;
		return this;
	}

	public boolean isResolving(Knowledge observable) {
		return resolving.contains(observable);
	}

	@Override
	public boolean isEmpty() {
		return this.empty;
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
