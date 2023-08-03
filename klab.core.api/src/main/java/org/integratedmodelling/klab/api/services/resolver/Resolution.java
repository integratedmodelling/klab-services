package org.integratedmodelling.klab.api.services.resolver;

import java.util.List;

import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Resolver;

/**
 * A resolution is the output of
 * {@link Resolver#resolve(Knowledge, ContextScope)} and consists of a graph
 * that details how a particular knowledge object has been resolved through
 * other knowledge in a given scope. When the resolution is deferred, further
 * resolution is needed after the execution of the corresponding dataflow to
 * complete the observation.
 * <p>
 * A resolution is compiled into a dataflow to contextualize the strategy.
 * Resolution has been successful if the coverage of the resolution is
 * acceptable (which depends on configuration, but {@link Coverage#isEmpty()}
 * should never be accepted). Each knowledge object in the graph is linked to
 * others through connections that detail the type of resolution that has
 * happened; dataflow compilation starts at the root of the graph, retrieving
 * the resolving knowledge from the graph according to the possible resolution
 * types.
 * 
 * @author Ferd
 *
 */
public interface Resolution {

	/**
	 * The resolution type describes the way knowledge resolves other knowledge.
	 * 
	 * @author Ferd
	 *
	 */
	public enum ResolutionType {

		/**
		 * Links an observable to a pre-resolved observation that was present in the
		 * resolution scope.
		 */
		RESOLVED,

		/**
		 * The strategy implies the direct observation of the observable through the
		 * model it links to, and will result in an actuator being defined in the
		 * dataflow.
		 */
		DIRECT,

		/**
		 * The strategy implies the observation of other direct observables, then the
		 * application of the child strateg(ies) to each, then the computations. An
		 * actuator will be created and the resolver will be called again on each
		 * observation produced by it.
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
	 * Return the total coverage of the resolution w.r.t. the context scope, i.e.
	 * the coverage of the root node.
	 * 
	 * @return
	 */
	Coverage getCoverage();

	/**
	 * The knowledge that was resolved into this resolution.
	 * 
	 * @return
	 */
	Knowledge getResolvedKnowledge();

	/**
	 * Return the collection of whatever resolves the passed knowledge using the
	 * passed strategy. The order is that of resolution, which matters as the first
	 * objects should override the ones after them when overlaps exist.
	 * 
	 * @param target
	 * @param type
	 * @return
	 */
	List<Knowledge> getResolving(Knowledge target, ResolutionType type);

	/**
	 * Empty means that resolution has failed. A non-empty graph may contain zero
	 * nodes, meaning that no contextualization is necessary.
	 * 
	 * @return
	 */
	boolean isEmpty();

}
