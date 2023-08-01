package org.integratedmodelling.klab.api.services.resolver;

import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time.Resolution;

/**
 * The resolution is associated with a context scope and contains a graph of
 * {@link Resolution} objects linked by connections that specify the strategy
 * and the specific observables resolved (including their local names)
 * <p>
 * The resolution graph contains models paired with their accumulated coverage.
 * There may be zero or more "root" nodes, with an overall coverage equal to the
 * union of the coverage of the root nodes. Each root node will generate a
 * portion of the dataflow in the context scope.
 * <p>
 * TODO rename "Resolution"
 * 
 * @author Ferd
 *
 */
public interface ResolutionGraph {

	/**
	 * Return the total coverage of the resolution w.r.t. the context scope, i.e.
	 * the coverage of the root node.
	 * 
	 * @return
	 */
	Coverage getCoverage();

	Knowledge getResolvedKnowledge();

//    ContextScope getScope();

//    List<Resolution> getRoots();

}
