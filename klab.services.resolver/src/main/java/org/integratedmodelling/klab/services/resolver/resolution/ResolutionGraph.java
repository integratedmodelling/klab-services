package org.integratedmodelling.klab.services.resolver.resolution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.services.resolver.resolution.ResolutionGraph.Resolution;
import org.integratedmodelling.klab.services.resolver.resolution.ResolutionGraph.ResolutionEdge;
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
 * The resolution graph contains models paired with their accumulated coverage.
 * There may be zero or more "root" nodes, with an overall coverage equal to the
 * union of the coverage of the root nodes. Each root node will generate a
 * portion of the dataflow in the context scope.
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
	private Map<Model, Metadata> resolutionMetadata = new HashMap<>();

	public Map<Model, Metadata> getResolutionMetadata() {
		return resolutionMetadata;
	}

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
	 * strategy through the {@link Resolution.Type} enum. These should be treated
	 * separately when building the dataflow: all DIRECT should be done first by
	 * creating new actuators, then any FILTERs added, and last the DEFERRED
	 * strategies should cause the runtime to invoke the <em>same</em> resolver
	 * again. The "resolving" nodes for each strategy can be accessed from a node
	 * using the {@link #getResolving(Resolution.Type)} method.
	 * 
	 * @author Ferd
	 *
	 */
	public class Resolution {

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

		public Observable observable;
		public Model model;
		public Coverage coverage;
		public LogicalConnector mergeStrategy;
		Map<Observable, Resolution> accepted = new HashMap<>();

		/**
		 * Any new node is initialized with the accepted overall nodes in the graph.
		 * 
		 * @param observable
		 * @param mergeStrategy
		 */
		public Resolution(Observable observable, LogicalConnector mergeStrategy) {
			this.observable = observable;
			this.mergeStrategy = mergeStrategy;
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
		public ResolutionEdge resolve(Resolution child, Resolution.Type resolutionType) {

			ResolutionEdge ret = new ResolutionEdge(resolutionType, child.observable);
			coverage = coverage == null ? child.coverage : coverage.merge(child.coverage, mergeStrategy);

			if (model != null && !coverage.isEmpty()) {
				resolutionGraph.addVertex(this);
				resolutionGraph.addVertex(child);
				resolutionGraph.addEdge(child, this, ret);
				accepted.putAll(child.accepted);
				// all observables of the accepted model are now available for resolution
				for (int i = 1; i < model.getObservables().size(); i++) {
					accepted.put(model.getObservables().get(i), this);
				}
				if (roots.contains(this) && coverage.isComplete()) {
					accept();
				}
			}
			return ret;
		}

		/**
		 * Call at the end of a model's resolution to set the model into a valid node.
		 * 
		 * @param model
		 */
		public void accept(Model model) {
			this.model = model;
		}

		/**
		 * Called only on a complete root resolution to accept it and merge the results
		 * into the overall graph. Any other use will be rewarded with an illegal state
		 * exception. It only needs to be called explicitly if for any reason the
		 * resolver ends up not calling
		 * {@link #resolve(Resolution, ResolutionGraph.Type)} on a node that should be
		 * accepted in the graph, which shouldn't really happen.
		 */
		public void accept() {

			if (!roots.contains(this) || !isComplete()) {
				throw new KIllegalStateException("logical error: accept() called on a non-root resolution node");
			}

			// root model: complete the overall catalog but only when the coverage is full
			nodes.putAll(accepted);
			nodes.put(observable, this);

			// update the overall coverage
			if (ResolutionGraph.this.coverage == null) {
				ResolutionGraph.this.coverage = this.coverage;
			} else {
				ResolutionGraph.this.coverage.merge(this.coverage, this.mergeStrategy);
			}
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

		public ContextScope scope() {
			return ResolutionGraph.this.scope;
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
			return new Resolution(toResolve, mergeStrategy);
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
		public List<Resolution> getResolving(Resolution.Type strategy) {
			List<Resolution> ret = new ArrayList<>();
			for (ResolutionEdge edge : resolutionGraph.incomingEdgesOf(this)) {
				if (edge.strategy == strategy) {
					ret.add(resolutionGraph.getEdgeSource(edge));
				}
			}
			return ret;
		}

		public Observable getObservable() {
			return this.observable;
		}
	}

	/**
	 * Get a new root node to resolve the passed resolvable in the passed context
	 * scope.
	 * 
	 * @param resolvable
	 * @return
	 */
	public Resolution newResolution(Knowledge resolvable, Scale scale) {
		Resolution ret = new Resolution(promoteResolvable(resolvable), LogicalConnector.UNION);
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
		 * A resolution edge always contains an observable. The observable's formal name
		 * is the name this is known locally within the target node.
		 */
		Observable observable;

		/**
		 * If the link defers the observation to a sub-instance, this carries the
		 * deferred observable to be resolved within each instance.
		 */
		Observable deferredObservable;

		/**
		 * And the chosen strategy
		 */
		Resolution.Type strategy;

		public ResolutionEdge(Resolution.Type resolutionType, Observable observable) {
			this.strategy = resolutionType;
			this.observable = observable;
		}

		public ResolutionEdge deferring(Observable observable) {
			this.deferredObservable = observable;
			return this;
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
