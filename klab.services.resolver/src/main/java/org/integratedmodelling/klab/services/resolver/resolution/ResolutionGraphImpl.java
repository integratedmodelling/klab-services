package org.integratedmodelling.klab.services.resolver.resolution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.DescriptionType;
import org.integratedmodelling.klab.api.knowledge.Instance;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resolver.Resolution;
import org.integratedmodelling.klab.api.services.resolver.ResolutionGraph;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/**
 * * The graph can contain multiple resolutions for the same context, each started by calling
 * {@link #newResolution(Knowledge)} with some {@link Knowledge} to be resolved in the
 * {@link ContextScope}. All explicitly triggered, successful resolutions have their roots in
 * {@link #getRoots()}. If successful, the overall {@link #getCoverage()} will have a sufficient
 * coverage percentage according to configuration. The dataflow must generate them in the order
 * given to preserve referential integrity.
 * <p>
 * 
 * @author mario
 *
 */
public class ResolutionGraphImpl implements ResolutionGraph {

    /**
     * Note that the implementation depends on edge ordering in the graph: the resolver adds nodes
     * with the "best" models first, and the actuators must be created in reverse order so that the
     * best resources overwrite any overlapping states. The JGraphT default directed graph
     * guarantees it but the interface does not require it.
     */
    private Map<Observable, ResolutionImpl> nodes = new HashMap<>();
    private ContextScope scope;
    private Graph<ResolutionImpl, ResolutionEdge> resolutionGraph = new DefaultDirectedGraph<>(ResolutionEdge.class);
    private List<Resolution> roots = new ArrayList<>();
    private Coverage coverage;
    private Map<Model, Metadata> resolutionMetadata = new HashMap<>();

    public Map<Model, Metadata> getResolutionMetadata() {
        return resolutionMetadata;
    }

    public class ResolutionImpl implements Resolution {

        public Observable observable;
        public Model model;
        public Coverage coverage;
        public LogicalConnector mergeStrategy;
        Map<Observable, ResolutionImpl> accepted = new HashMap<>();

        /**
         * Any new node is initialized with the accepted overall nodes in the graph. The coverage is
         * the resolution coverage, which must be adjusted as indicated when a child is merged in.
         * 
         * @param observable
         * @param mergeStrategy
         */
        public ResolutionImpl(Observable observable, Coverage coverage, LogicalConnector mergeStrategy) {
            this.observable = observable;
            this.coverage = coverage;
            this.mergeStrategy = mergeStrategy;
            this.accepted.putAll(nodes);
        }

        public ResolutionImpl(Knowledge resolvable, Scale scale) {
            this.observable = promoteResolvable(resolvable);
            this.mergeStrategy = LogicalConnector.UNION;
            this.coverage = Coverage.create(scale, resolvable instanceof Instance ? 1.0 : 0.0);
            this.accepted.putAll(nodes);
        }

        /**
         * The resolve method is called by the resolver at every resolution, after which the
         * coverage is inspected to determine if resolution is finished. Returns the same node it's
         * called on, with updated coverage so that the replicated methods from coverage can be
         * called on it.
         * <p>
         * A new link is stored and coverage is adjusted according to context, which the resolver
         * communicates using a logical operator (AND for dependencies, OR for alternative
         * resolutions).
         * <p>
         * If a root node is accepting a resolution and as a result its coverage is complete, it
         * will also update the global catalog with itself and all the accepted nodes so far.
         */
        public ResolutionEdge resolve(ResolutionImpl child, Resolution.Type resolutionType) {

            ResolutionEdge ret = new ResolutionEdge(resolutionType, child.observable);
            coverage = coverage == null ? child.coverage : coverage.merge(child.coverage, mergeStrategy);

            if ((model != null || observable.getDescriptionType() == DescriptionType.ACKNOWLEDGEMENT) && !coverage.isEmpty()) {
                resolutionGraph.addVertex(this);
                resolutionGraph.addVertex(child);
                resolutionGraph.addEdge(child, this, ret);
                accepted.putAll(child.accepted);
                // all observables of the accepted model are now available for resolution
                if (model != null) {
                    for (int i = 1; i < model.getObservables().size(); i++) {
                        accepted.put(model.getObservables().get(i), this);
                    }
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
         * Called only on a complete root resolution to accept it and merge the results into the
         * overall graph. Any other use will be rewarded with an illegal state exception. It only
         * needs to be called explicitly if for any reason the resolver ends up not calling
         * {@link #resolve(ResolutionImpl, ResolutionGraphImpl.Type)} on a node that should be
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
            if (ResolutionGraphImpl.this.coverage == null) {
                ResolutionGraphImpl.this.coverage = this.coverage;
            } else {
                ResolutionGraphImpl.this.coverage.merge(this.coverage, this.mergeStrategy);
            }
        }

        public boolean isComplete() {
            return (model != null || observable.getDescriptionType() == DescriptionType.ACKNOWLEDGEMENT) && coverage != null
                    && coverage.isComplete();
        }

        public boolean isRelevant() {
            return (model != null || observable.getDescriptionType() == DescriptionType.ACKNOWLEDGEMENT) && coverage != null
                    && coverage.isRelevant();
        }

        public boolean isEmpty() {
            return (model != null && observable.getDescriptionType() != DescriptionType.ACKNOWLEDGEMENT) || coverage == null
                    || coverage.isEmpty();
        }

        public ResolutionGraphImpl resolutionGraph() {
            return ResolutionGraphImpl.this;
        }

        public ContextScope scope() {
            return ResolutionGraphImpl.this.scope;
        }

        /**
         * This should be called before any new resolution to ensure that pre-resolved observables
         * are reused.
         * 
         * @param observable
         * @return
         */
        public ResolutionImpl getResolution(Observable observable) {
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
        public ResolutionImpl newResolution(Observable toResolve, LogicalConnector mergeStrategy) {
            return new ResolutionImpl(toResolve, coverage, mergeStrategy);
        }

        @Override
        public Coverage getCoverage() {
            return coverage;
        }
        
        @Override
        public LogicalConnector getMergeStrategy() {
            return mergeStrategy;
        }

        /**
         * Return all the nodes that resolve this node using the passed strategy.
         * 
         * @param strategy
         * @return
         */
        @Override
        public List<Resolution> getResolving(Resolution.Type strategy) {
            List<Resolution> ret = new ArrayList<>();
            for (ResolutionEdge edge : resolutionGraph.incomingEdgesOf(this)) {
                if (edge.strategy == strategy) {
                    ret.add(resolutionGraph.getEdgeSource(edge));
                }
            }
            return ret;
        }

        @Override
        public Observable getObservable() {
            return this.observable;
        }
        
        @Override
        public Model getModel() {
            return model;
        }
    }

    /**
     * Get a new root node to resolve the passed resolvable in the passed context scope.
     * 
     * @param resolvable
     * @return
     */
    public ResolutionImpl newResolution(Knowledge resolvable, Scale scale) {
        ResolutionImpl ret = new ResolutionImpl(resolvable, scale);
        roots.add(ret);
        return ret;
    }

    /*
     * Promote the passed knowledge to a suitable observable, possibly (creating and) caching a
     * pre-resolved model or instance.
     */
    private Observable promoteResolvable(Knowledge resolvable) {

        Observable ret = null;

        // reduce to either observable or instance
        switch(Knowledge.classify(resolvable)) {
        case OBSERVABLE:
            ret = (Observable) resolvable;
            break;
        case CONCEPT:
            ret = Observable.promote((Concept) resolvable);
            break;
        case MODEL:
            ret = ((Model) resolvable).getObservables().get(0).resolvedWith(resolvable);
            break;
        case RESOURCE:
            // same, maybe not
            break;
        case INSTANCE:
            ret = ((Instance) resolvable).getObservable().resolvedWith(resolvable);
            break;
        default:
            break;
        }

        return ret;
    }

    /**
     * The edges of the graph contain the actual requested observable (with its stated name if any)
     * along with the "strategy" that links it to the resolved node. If the strategy is
     * {@link ResolutionEdge.Type.DEFERRAL} the resolution will need to be deferred to the observed
     * instances individually.
     * 
     * @author Ferd
     *
     */
    public class ResolutionEdge extends DefaultEdge {

        private static final long serialVersionUID = 3592176546593487293L;

        /**
         * A resolution edge always contains an observable. The observable's formal name is the name
         * this is known locally within the target node.
         */
        Observable observable;

        /**
         * If the link defers the observation to a sub-instance, this carries the deferred
         * observable to be resolved within each instance.
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

    public ResolutionGraphImpl(ContextScope scope) {
        this.scope = scope;
        this.coverage = Coverage.create(scope.getGeometry(), 0.0);
    }

    /**
     * Return the total coverage of the resolution w.r.t. the context scope, i.e. the coverage of
     * the root node.
     * 
     * @return
     */
    @Override
    public Coverage getCoverage() {
        return coverage;
    }

    @Override
    public ContextScope getScope() {
        return scope;
    }

    public void setScope(ContextScope scope) {
        this.scope = scope;
    }

    @Override
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
