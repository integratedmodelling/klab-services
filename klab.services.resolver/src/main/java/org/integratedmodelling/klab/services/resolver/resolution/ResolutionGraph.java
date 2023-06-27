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
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/**
 * The resolution is associated with a context scope and contains a graph of
 * {@link Resolution} objects linked by connections that specify the strategy
 * and the specific observables resolved (including their local names)
 * <p>
 * The graph can contain multiple resolutions for the same context, each started
 * by calling {@link #newResolution(Knowledge)} with some {@link Knowledge} to
 * be resolved in the {@link ContextScope}. All explicitly triggered, successful
 * resolutions have their roots in {@link #getRoots()}. If successful, the
 * overall {@link #getCoverage()} will have a sufficient coverage percentage
 * according to configuration. The dataflow must generate them in the order
 * given to preserve referential integrity.
 * <p>
 * The resolution graph contains models with their accumulated coverage. There
 * may be zero or more "root" nodes, with an overall coverage equal to the union
 * of the coverage of the root nodes. Each root node will generate a portion of
 * the dataflow in the context scope.
 * <p>
 * 
 * 
 * @author Ferd
 *
 */
public class ResolutionGraph {

	/**
	 * Note that the implementation depends on edge ordering in the graph: the
	 * resolver adds nodes with the "best" models first, and the actuators must be
	 * created in reverse order so that the best resources overwrite any overlapping
	 * states. The JGraphT default directed graph guarantees it but the interface
	 * does not require it.
	 */
	private Map<Observable, Resolution> nodes = new HashMap<>();
	private ContextScope scope;
	private Graph<Resolution, ResolutionEdge> resolutionGraph = new DefaultDirectedGraph<>(ResolutionEdge.class);
	private List<Resolution> roots = new ArrayList<>();
	private Coverage coverage;

	/**
	 * Each vertex in the resolution graph contains the resolved observable, a model
	 * that represents the chosen resolution of the resolvable in the context scope
	 * and the coverage this resolution has determined in it. The way nodes resolve
	 * one another is stored in the edges of the graph; deferring resolution to
	 * after the initial contextualization will require further queries after
	 * executing the dataflow.
	 * <p>
	 * Each element has its coverage, expressing its "natural" one intersected with
	 * the coverage of any incoming nodes, and the merging strategy (UNION or
	 * INTERSECTION, maybe eventually EXCLUSION) used to merge the coverage of its
	 * children.
	 * <p>
	 * The resolved nodes are connected by links that specify the resolution
	 * strategy through the {@link ResolutionEdge.Type} enum. These should be
	 * treated separately when building the dataflow: all DIRECT should be done
	 * first by creating new actuators, then any FILTERs added, and last the
	 * DEFERRED strategies should cause the runtime to invoke the <em>same</em>
	 * resolver again. The "resolving" nodes for each strategy can be accessed from
	 * a node using the {@link #getResolving(ResolutionEdge.Type)} method.
	 * 
	 * @author Ferd
	 *
	 */
	public class Resolution {

		public Observable observable;
		public Model model;
		public Coverage coverage;
		public LogicalConnector mergeStrategy;
		Map<Observable, Resolution> accepted = new HashMap<>();
		Resolution parent = null;

		/**
		 * Any new node is initialized with the accepted overall nodes in the graph.
		 * 
		 * @param observable
		 * @param mergeStrategy
		 */
		public Resolution(Observable observable, LogicalConnector mergeStrategy, Resolution parent) {
			this.observable = observable;
			this.mergeStrategy = mergeStrategy;
			this.parent = parent;
			this.accepted.putAll(nodes);
		}

		/**
		 * The resolve method is called by the resolver at every resolution, after which
		 * the coverage is inspected to determine if resolution is finished. Returns the
		 * same node it's called on, with updated coverage so that the replicated
		 * methods from coverage can be called on it.
		 * <p>
		 * A new link is stored and coverage is adjusted according to context, which the
		 * resolver communicates using a logical operator (AND for dependencies, OR for
		 * alternative resolutions).
		 * <p>
		 * If a root node is accepting a resolution and as a result its coverage is
		 * complete, it will also update the global catalog with itself and all the
		 * accepted nodes so far.
		 */
		public Resolution resolve(Resolution child, ResolutionEdge.Type resolutionType) {
			coverage = coverage == null ? child.coverage : coverage.merge(child.coverage, mergeStrategy);
			if (model != null && !coverage.isEmpty()) {
				resolutionGraph.addVertex(this);
				resolutionGraph.addVertex(child);
				resolutionGraph.addEdge(child, this, new ResolutionEdge(resolutionType, child.observable));
				accepted.putAll(child.accepted);
				for (int i = 1; i < model.getObservables().size(); i++) {
					nodes.put(model.getObservables().get(i), this);
				}
				if (parent == null && coverage.isComplete()) {
					// root model: complete the overall catalog but only when the coverage is full
					nodes.putAll(accepted);
					nodes.put(observable, this);
				}
			}
			return this;
		}

		public boolean isComplete() {
			return model != null && coverage != null && coverage.isComplete();
		}

		public boolean isRelevant() {
			return model != null && coverage != null && coverage.isRelevant();
		}

		public boolean isEmpty() {
			return model == null || coverage == null || coverage.isEmpty();
		}

		public ResolutionGraph resolutionGraph() {
			return ResolutionGraph.this;
		}

		/**
		 * This should be called before any new resolution to ensure that pre-resolved
		 * observables are reused.
		 * 
		 * @param observable
		 * @return
		 */
		public Resolution getResolution(Observable observable) {
			return accepted.get(observable);
		}

		/**
		 * At each dependent resolution, a new empty child is created which, if
		 * {@link #resolve(Model)} is called on it, will be connected to the root.
		 * 
		 * @param toResolve
		 * @param contribution
		 * @return
		 */
		public Resolution newResolution(Observable toResolve, LogicalConnector mergeStrategy) {
			return new Resolution(toResolve, mergeStrategy, this);
		}

		public Coverage getCoverage() {
			return coverage;
		}

		/**
		 * Return all the nodes that resolve this node using the passed strategy.
		 * 
		 * @param strategy
		 * @return
		 */
		public List<Resolution> getResolving(ResolutionEdge.Type strategy) {
			List<Resolution> ret = new ArrayList<>();
			for (ResolutionEdge edge : resolutionGraph.incomingEdgesOf(this)) {
				if (edge.strategy == strategy) {
					ret.add(resolutionGraph.getEdgeSource(edge));
				}
			}
			return ret;
		}
	}

	/**
	 * Get a new root node to resolve the passed resolvable in the passed context
	 * scope.
	 * 
	 * @param resolvable
	 * @return
	 */
	public Resolution newResolution(Knowledge resolvable) {
		Resolution ret = new Resolution(promoteResolvable(resolvable), LogicalConnector.UNION, null);
		roots.add(ret);
		return ret;
	}

	/*
	 * Promote the passed knowledge to a suitable observable, possibly (creating
	 * and) caching a pre-resolved model or instance.
	 */
	private Observable promoteResolvable(Knowledge resolvable) {

		Observable ret = null;

		// reduce to either observable or instance
		switch (Knowledge.classify(resolvable)) {
		case OBSERVABLE:
			ret = (Observable) resolvable;
			break;
		case CONCEPT:
			// promote to observable
			break;
		case MODEL:
			// same
			break;
		case RESOURCE:
			// same
			break;
		case INSTANCE:
			// also
			break;
		default:
			break;
		}

		return ret;
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

		public ResolutionEdge(Type resolutionType, Observable observable) {
			this.strategy = resolutionType;
			this.observable = observable;
		}

	}

	public ResolutionGraph(ContextScope scope) {
		this.scope = scope;
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

	public List<Resolution> getRoots() {
		return roots;
	}

	public void setRoots(List<Resolution> roots) {
		this.roots = roots;
	}

	public void setCoverage(Coverage coverage) {
		this.coverage = coverage;
	}

}
