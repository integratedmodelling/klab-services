package org.integratedmodelling.klab.services.resolver;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resolvable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.w3.xlink.XlinkFactory;

import java.util.*;

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
public class ResolutionGraph {

    private Resolvable target;
    private Coverage targetCoverage;
    private ContextScope rootScope;
    private List<Contextualizable> contextualization = new ArrayList<>();
    private DefaultDirectedGraph<Resolvable, ResolutionGraph.ResolutionEdge> graph =
            new DefaultDirectedGraph<>(ResolutionEdge.class);
    private ResolutionGraph parent;

    /**
     * A catalog per observable of all resolving sources seen, used by merging their native coverage with any
     * resolving candidate before new strategies are attempted. Includes resolved observations, resolved
     * models and (eventually) resolved external dataflows.
     */
    private Map<Observable, Set<Resolvable>> resolutionCatalog = new HashMap<>();
    private boolean empty;

    private ResolutionGraph(ContextScope rootScope) {
        this.rootScope = rootScope;
    }

    private ResolutionGraph(Resolvable target, Scale scaleToCover, ResolutionGraph parent) {

        if (parent.empty) {
            throw new KlabIllegalStateException("cannot use an empty resolution graph");
        }

        this.parent = parent;

        /**
         * Models are resolved from full down, intersecting the coverage of the dependencies. Everything
         * else is resolved from zero up, uniting the coverages.
         */
        this.target = target;
        this.targetCoverage = Coverage.create(scaleToCover, target instanceof Model ? 1.0 : 0.0);
        var tc = getCoverage(target);
        if (tc != null) {
            this.targetCoverage = targetCoverage.merge(tc, LogicalConnector.INTERSECTION);
        }

        this.rootScope = parent.rootScope;
        this.resolutionCatalog.putAll(parent.resolutionCatalog);
    }

    private Scale getCoverage(Resolvable target) {
        return switch (target) {
            case Model model -> model.getCoverage();
            case Observation observation -> Scale.create(observation.getGeometry());
            default -> null;
        };
    }

    /**
     * Merge the coverage and return the result without setting the coverage in the graph, just invoked for
     * testing stop conditions.
     *
     * @param resolution
     * @return
     */
    public Coverage checkCoverage(ResolutionGraph resolution) {

        if (resolution.isEmpty()) {
            return Coverage.empty();
        }
        return resolution.targetCoverage.merge(this.targetCoverage, target instanceof Model ?
                                                                    LogicalConnector.INTERSECTION :
                                                                    LogicalConnector.UNION);
    }

    /**
     * Accept the resolution contained in the passed graph for its target, adding our resolvable to the graph
     * and updating the target coverage and the catalog. Return true if our own target coverage is made
     * complete by the merge.
     */
    public boolean merge(ResolutionGraph childGraph) {
        System.out.println("ACCEPTING SLAVE INTO PARENT, MERGE GRAPH INTO PARENT'S, MERGE COVERAGE AND " +
                "UPDATE CATALOG");

        Graphs.addAllVertices(this.graph, childGraph.graph.vertexSet());
        Graphs.addAllEdges(this.graph, childGraph.graph, childGraph.graph.edgeSet());

        /*
        Our resolvable is resolved by the child's
         */
        this.graph.addVertex(this.target);
        this.graph.addVertex(childGraph.target);
        this.graph.addEdge(childGraph.target, this.target, new ResolutionEdge(childGraph.targetCoverage));

        /*
        ...by the amount determined in its coverage, "painting" the incoming extents onto ours.
         */
        this.targetCoverage = this.targetCoverage.merge(childGraph.targetCoverage,
                this.target instanceof Model ? LogicalConnector.INTERSECTION : LogicalConnector.UNION);

        /*
        if our coverage is satisfactory, signal that the merge has done all we need
         */
        return targetCoverage.isComplete();
    }

    /**
     * Accept the resolution contained in this graph's objects, adding our resolvable to the graph and
     * updating the catalog. Called directly on a parent graph when an existing resolvable is enough to
     * resolve the target.
     */
    public void accept(Resolvable resolvable, Coverage finalCoverage) {
        System.out.println("ACCEPTING EXISTING RESOLVABLE INTO SAME GRAPH FOR THIS RESOLVABLE - JUST CREATE" +
                " THE LINK FROM THE TARGET TO THE RESOLVABLE AND SET THE COVERAGE");
    }

    public static ResolutionGraph create(ContextScope rootScope) {
        return new ResolutionGraph(rootScope);
    }

    public static ResolutionGraph empty() {
        var ret = new ResolutionGraph(null);
        ret.empty = true;
        return ret;
    }

    /**
     * Add to this if operations must be compiled in the dataflow after resolution.
     *
     * @return
     */
    public List<Contextualizable> getContextualization() {
        return contextualization;
    }

    public boolean isEmpty() {
        return empty;
    }

    /**
     * Spawn a new resolution graph to resolve the passed observation in the passed scale, as a child of the
     * previous.
     *
     * @param target
     * @param scaleToCover
     * @return
     */
    public ResolutionGraph createChild(Resolvable target, Scale scaleToCover) {
        return new ResolutionGraph(target, scaleToCover, this);
    }

    /**
     * Return any known resolvable (already present in the graph) that can resolve the passed observable,
     * paired with the result of intersecting its native coverage with the passed scale.
     *
     * @param observable
     * @return
     */
    public List<Pair<Resolvable, Coverage>> getResolving(Observable observable, Scale scale) {
        return List.of();
    }

    public Observation getContextObservation() {
        ResolutionGraph target = this;
        while (target != null && !(target.target instanceof Observation)) {
            target = target.parent;
        }
        return target == null ? null : (Observation) target.target;
    }

    public static class ResolutionEdge extends DefaultEdge {

        public Coverage coverage;

        public ResolutionEdge() {}
        public ResolutionEdge(Coverage coverage) {
            this.coverage = coverage;
        }
    }

}