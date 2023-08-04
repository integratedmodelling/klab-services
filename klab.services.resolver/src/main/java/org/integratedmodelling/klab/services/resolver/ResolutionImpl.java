package org.integratedmodelling.klab.services.resolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resolver.Resolution;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/**
 * Each graph resolves one observable through models.
 * 
 * @author Ferd
 *
 */
public class ResolutionImpl extends DefaultDirectedGraph<Model, ResolutionImpl.ResolutionEdge> implements Resolution {

    private static final long serialVersionUID = 4088657660423175126L;

    private Observable resolvable;
    private Coverage coverage;
    private Set<Observable> resolving = new HashSet<>();
    private Map<Observable, Knowledge> resolved = new HashMap<>();
    private List<Pair<Model, Coverage>> resolution = new ArrayList<>();

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
     * Create a root resolution graph for the passed knowledge, pre-loading whatever has been
     * already resolved in the passed scope.
     * 
     * @param root
     * @param scope
     */
    public ResolutionImpl(Observable root, Scale scale, ContextScope scope) {

        super(ResolutionImpl.ResolutionEdge.class);

        this.resolvable = root;
        this.resolving.add(root);

        // pre-resolved observations
        resolved.putAll(scope.getCatalog());
    }

    public ResolutionImpl(ContextScope scope, ResolutionImpl parent) {

        super(ResolutionImpl.ResolutionEdge.class);
        this.resolving.addAll(parent.resolving);
        this.resolved.putAll(parent.resolved);
        this.resolvable = parent.resolvable;
        Graphs.addGraph(this, parent);

        // this.resolving.add(root);
        // this.resolving.addAll(parent.resolving);
        // this.resolving.add(root);
    }

    /**
     * Add a link (K) <--direct(observable)-- (O)
     * 
     * @param model
     * 
     * @param child
     * @param mergingStrategy
     * @param direct
     * @return
     */
    public Coverage merge(Model model, ResolutionImpl child, Observable observable, LogicalConnector mergingStrategy,
            ResolutionType resolutionType) {

        if (this.coverage == null) {
            this.coverage = child.getCoverage();
        } else {
            this.coverage = this.getCoverage().merge(child.getCoverage(), mergingStrategy);
        }

        mergeGraph(child, observable, resolutionType);
        return this.coverage;
    }

//    /**
//     * Add a link (K) <--filter-- (model F applying to K)
//     * 
//     * @param child
//     * @param mergingStrategy
//     * @return
//     */
//    public Coverage mergeFilter(ResolutionImpl child, Observable observable, LogicalConnector mergingStrategy) {
//
//        if (this.coverage == null) {
//            this.coverage = child.getCoverage();
//        } else {
//            this.coverage = this.getCoverage().merge(child.getCoverage(), mergingStrategy);
//        }
//
//        mergeGraph(child, observable, ResolutionType.FILTERING);
//        return this.coverage;
//
//    }
//
//    /**
//     * Add a link (K) <--direct(deferring)-- (Z instantiator) <--deferred--(deferred = unresolved
//     * observable K within Z)
//     * 
//     * @param child
//     * @param mergingStrategy
//     * @param deferred
//     * @return
//     */
//    public Coverage mergeDeferred(ResolutionImpl child, LogicalConnector mergingStrategy, Observable deferring,
//            Observable deferred) {
//
//        if (this.coverage == null) {
//            this.coverage = child.getCoverage();
//        } else {
//            this.coverage = this.coverage.merge(child.getCoverage(), mergingStrategy);
//        }
//
//        mergeGraph(child, deferring, ResolutionType.DEFERRAL);
//        return this.coverage;
//    }

    private void mergeGraph(ResolutionImpl child, Observable observable, ResolutionType resolution) {

        // for (Knowledge knowledge : child.vertexSet()) {
        // this.addVertex(knowledge);
        // }
        // for (ResolutionEdge edge : child.edgeSet()) {
        // this.addEdge(child.getEdgeSource(edge), child.getEdgeTarget(edge), edge);
        // }
        // this.addVertex(this.resolvedKnowledge);
        // this.addVertex(child.resolvedKnowledge);
        // this.addEdge(child.getResolvedKnowledge(), this.resolvedKnowledge, new
        // ResolutionEdge(observable, resolution));
    }

    public Coverage getCoverage() {
        return coverage == null ? Coverage.empty() : coverage;
    }

    public void setCoverage(Coverage coverage) {
        this.coverage = coverage;
    }

    public ResolutionImpl setEmpty() {
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
    public List<Pair<Model, Coverage>> getResolving(Model target, ResolutionType strategy) {
        List<Pair<Model, Coverage>> ret = new ArrayList<>();
        for (ResolutionEdge edge : incomingEdgesOf(target)) {
            if (edge.type == strategy) {
                ret.add(Pair.of(getEdgeSource(edge), edge.getCoverage()));
            }
        }
        return ret;
    }

    @Override
    public Knowledge getResolved(Observable observable) {
        return resolved.get(observable);
    }

    @Override
    public Observable getResolvable() {
        return this.resolvable;
    }

    @Override
    public List<Pair<Model, Coverage>> getResolution() {
        return resolution;
    }

}
