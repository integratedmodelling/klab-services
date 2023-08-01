package org.integratedmodelling.klab.services.resolver.resolution;

import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/**
 * 
 * @author Ferd
 *
 */
public class ResolutionGraph extends DefaultDirectedGraph<Knowledge, ResolutionGraph.ResolutionEdge> {

	private static final long serialVersionUID = 4088657660423175126L;

	/**
	 * The resolution type is how a model contextualizes an observable.
	 * 
	 * @author Ferd
	 *
	 */
	public enum Type {
		/**
		 * the strategy implies the direct observation of the observable and will result
		 * in an actuator being defined in the dataflow.
		 */
		DIRECT,

		/**
		 * The strategy implies the observation of other direct observables, then the
		 * application of the child strateg(ies) to each, then the computations. An
		 * actuator will be created and the resolver will be called again on each
		 * instance produced by it.
		 */
		DEFERRAL,

		/**
		 * The target model is applied to the outputs of the receiving one to modify its
		 * value. Filters are inserted into the actuator created for the resolved node:
		 * no specific actuator for a filtering node is created.
		 */
		FILTERING

	}

	private Knowledge root;
	private Coverage coverage;

	class ResolutionEdge extends DefaultEdge {

		private static final long serialVersionUID = 5876676141844919004L;

		private Observable observable;
		private Type type;

		public Coverage getCoverage() {
			return coverage;
		}

		public Observable getObservable() {
			return observable;
		}

		public void setObservable(Observable observable) {
			this.observable = observable;
		}

		public Type getType() {
			return type;
		}

		public void setType(Type type) {
			this.type = type;
		}

	}

	public ResolutionGraph(Knowledge root) {
		super(ResolutionGraph.ResolutionEdge.class);
		this.root = root;
	}

	/**
	 * Add a link (K) <--direct(observable)-- (O)
	 * 
	 * @param child
	 * @param mergingStrategy
	 * @return
	 */
	public Coverage merge(ResolutionGraph child, Observable observable, LogicalConnector mergingStrategy) {

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
	public Coverage mergeFilter(ResolutionGraph child, Observable observable, LogicalConnector mergingStrategy) {

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
	 * Add a link (K) <--direct(deferring)-- (Z instantiator) <--deferred--(deferred = unresolved observable K within Z)
	 * 
	 * @param child
	 * @param mergingStrategy
	 * @param deferred
	 * @return
	 */
	public Coverage mergeDeferred(ResolutionGraph child, LogicalConnector mergingStrategy, Observable deferring,
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

	public Knowledge getRoot() {
		return root;
	}

	public void setRoot(Knowledge root) {
		this.root = root;
	}

	public Coverage getCoverage() {
		return coverage;
	}

	public void setCoverage(Coverage coverage) {
		this.coverage = coverage;
	}

	public ResolutionGraph setEmpty() {
		// TODO Auto-generated method stub
		return null;
	}

}
