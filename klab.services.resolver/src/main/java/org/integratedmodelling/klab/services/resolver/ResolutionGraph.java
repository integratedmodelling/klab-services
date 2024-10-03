package org.integratedmodelling.klab.services.resolver;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resolvable;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.HashMap;
import java.util.Map;

/**
 * Next-gen Resolution graph, to substitute Resolution/ResolutionImpl.
 * <p>
 * The nodes can be:
 * <ol>
 *     <li>Observations</li>
 *     <li>Models</li>
 *     <li>Observables to defer resolution to</li>
 * </ol>
 * <p>
 * These are inserted in the graph before resolution. If they are unresolved (i.e. are models or are
 * unresolved observations) they remain there as cached unresolved. The root nodes can only be observations of
 * substantials.
 * <p>
 * The edges report the resolution coverage of the source resolvable. When the target is an observable, the
 * coverage is unknown until there has been a trip back to the runtime.
 * <p>
 * The graph also contains a cache of resolved resolvables with their native coverage, indexed by
 * observable, so that the resolver can quickly assess if a previously used resolvable can be used for other
 * resolutions before searching for models.
 */
public class ResolutionGraph extends DefaultDirectedGraph<Resolvable, ResolutionGraph.ResolutionEdge> {

    public ResolutionGraph() {
        super(ResolutionEdge.class);
    }

    public static class ResolutionEdge extends DefaultEdge {
        public Coverage coverage;
    }

    /**
     * All the currently used resolvables with their ORIGINAL, NATIVE coverage. This includes models and
     * resolved observations.
     */
    Map<Observable, Pair<Resolvable, Coverage>> available = new HashMap<>();

}
