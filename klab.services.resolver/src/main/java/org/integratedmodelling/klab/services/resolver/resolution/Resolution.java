package org.integratedmodelling.klab.services.resolver.resolution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.services.resolver.resolution.Resolution.ResolutionEdge;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/**
 * The resolution associates a resolution graph with some {@link Knowledge} to
 * be resolved in a {@link ContextScope}. When successful, the overall
 * {@link #getCoverage()} will have a sufficient coverage percent according to
 * configuration. The resolution graph is made of models with accumulated
 * coverage and there may be zero or more "root" nodes, with an overall coverage
 * equal to the union of the coverage of the root nodes.
 * <p>
 * 
 * 
 * @author Ferd
 *
 */
public class Resolution {

	/**
	 * Each vertex in the resolution graph is a model that represents the chosen
	 * resolution of the root resolvable in the context scope. The way this resolves
	 * the model is stored in the edge and deferring resolution to after an initial
	 * contextualization may be necessary to complete the query. Each element has
	 * its coverage, expressing its "natural" one intersected with the coverage of
	 * any incoming nodes.
	 * 
	 * @author Ferd
	 *
	 */
	public class Node {

		public Model model;
		public Coverage coverage;

		/**
		 * The resolve method is called by the resolver at every resolution, after which
		 * the coverage is inspected to determine if resolution is finished.
		 * <p>
		 * A new link is stored and coverage is adjusted according to context, which the
		 * resolver communicates using a logical operator (AND for dependencies, OR for
		 * alternative resolutions).
		 */
		public Coverage resolve() {
			// TODO
			return coverage;
		}

		/**
		 * At each dependent resolution, a new empty child is created which, if
		 * {@link #resolve(Model)} is called on it, will be connected to the root.
		 * 
		 * @param toResolve
		 * @param contribution
		 * @return
		 */
		public Node newChild(Observable toResolve, Model model, LogicalConnector contribution) {
			return null;
		}

		/*
		 * TODO we should have a resolving() method returning all the incoming nodes or
		 * a visitor.
		 */
	}

	/*
	 * all the resolved nodes by the observable they resolve. Within the same
	 * context there must be a functional relationship between the observable and
	 * the model.
	 * 
	 * Note that the implementation depends on edge ordering in the graph: the
	 * resolver adds nodes with the "best" models first, and the actuators must be
	 * created in reverse order so that the best resources overwrite any overlapping
	 * states. The JGraphT default directed graph guarantees it but the interface
	 * does not require it.
	 */
	private Map<Observable, Node> nodes = new HashMap<>();
	private ContextScope scope;
	private Graph<Node, ResolutionEdge> resolutionGraph = new DefaultDirectedGraph<>(ResolutionEdge.class);
	private List<Node> roots = new ArrayList<>();
	private Coverage coverage;
	private Knowledge resolvable;

	/*
	 * Promote the passed knowledge to a suitable observable, possibly (creating
	 * and) caching a pre-resolved model.
	 */
	private Observable promoteResolvable(Knowledge resolvable) {
		return null;
	}

	/**
	 * The edges of the graph contain the actual requested observable (with its
	 * stated name if any) along with the "strategy" that links it to the resolved
	 * node. If the strategy is {@link ResolutionEdge.Type.DEFERRAL} the resolution
	 * will need to be deferred to the observed instances individually.
	 * 
	 * @author Ferd
	 *
	 */
	public class ResolutionEdge extends DefaultEdge {

		private static final long serialVersionUID = 3592176546593487293L;

		/**
		 * The resolution type is how a model contextualizes an observable.
		 * 
		 * @author Ferd
		 *
		 */
		enum Type {
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

		/**
		 * A resolution edge always contains an observable. The observable's formal name
		 * is the name this is known locally within the target node.
		 */
		Observable observable;

		/**
		 * And the chosen strategy
		 */
		Type strategy;

	}

	public Resolution(Knowledge resolvable, ContextScope scope) {
		this.scope = scope;
		this.resolvable = resolvable;
		this.coverage = Coverage.create(scope.getGeometry(), 0.0);
	}

	/**
	 * Return the total coverage of the resolution w.r.t. the context scope, i.e.
	 * the coverage of the root node.
	 * 
	 * @return
	 */
	public Coverage getCoverage() {
		return coverage;
	}

	public ContextScope getScope() {
		return scope;
	}

	public void setScope(ContextScope scope) {
		this.scope = scope;
	}

	public List<Node> getRoots() {
		return roots;
	}

	public void setRoots(List<Node> roots) {
		this.roots = roots;
	}

	public Knowledge getResolvable() {
		return resolvable;
	}

	public void setResolvable(Knowledge resolvable) {
		this.resolvable = resolvable;
	}

	public void setCoverage(Coverage coverage) {
		this.coverage = coverage;
	}

}
