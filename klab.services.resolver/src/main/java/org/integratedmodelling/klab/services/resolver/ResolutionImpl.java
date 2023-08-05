package org.integratedmodelling.klab.services.resolver;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resolver.Resolution;
import org.integratedmodelling.klab.utilities.Utils;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import com.google.common.collect.Sets;

/**
 * Each graph resolves one observable through a graph of models connected by the resolver.
 * 
 * @author Ferd
 *
 */
public class ResolutionImpl extends DefaultDirectedGraph<Model, ResolutionImpl.ResolutionEdge> implements Resolution {

    private static final long serialVersionUID = 4088657660423175126L;

    private Observable resolvable;
    private Coverage coverage;
    private Set<Observable> resolving = new HashSet<>();
    private Map<Observable, Collection<Knowledge>> resolved = new HashMap<>();
    private List<Pair<Model, Coverage>> resolution = new ArrayList<>();

    private boolean empty;

    class ResolutionEdge extends DefaultEdge {

        private static final long serialVersionUID = 5876676141844919004L;

        private Observable observable;
        private ResolutionType type;
        private Coverage coverage;

        public ResolutionEdge(Observable observable, Coverage coverage, ResolutionType type) {
            this.observable = observable;
            this.coverage = coverage;
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

        public String toString() {
            return observable + "\n" + Utils.Strings.capitalize(this.type.name().toLowerCase()) + " ("
                    + NumberFormat.getPercentInstance().format(coverage.getCoverage()) + ")";
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
        // pre-resolved observations. Can't use better idioms for apparent bug in Java type system.
        for (Observable o : scope.getCatalog().keySet()) {
            Set<Knowledge> set = new HashSet<>();
            set.add(scope.getCatalog().get(o));
            resolved.put(o, set);
        }
    }

    /**
     * For child resolutions, add the hash of resolved and resolving knowledge.
     * 
     * @param root
     * @param scale
     * @param scope
     * @param parent
     */
    public ResolutionImpl(Observable root, Scale scale, ContextScope scope, ResolutionImpl parent) {

        super(ResolutionImpl.ResolutionEdge.class);
        this.resolving.addAll(parent.resolving);
        this.resolved.putAll(parent.resolved);
        this.resolvable = root;
    }

    /**
     * Merge in a successful model resolution with its original observable and coverage. Its root
     * models become connected to the parent model passed, which is not part of the passed graph.
     * 
     * @param model
     * 
     * @param child
     * @param mergingStrategy
     * @param direct
     * @return
     */
    public Coverage merge(Model parentModel, ResolutionImpl child, ResolutionType resolutionType) {

        if (parentModel != null) {
            addVertex(parentModel);
        }

        // merge the child graph
        Graphs.addGraph(this, child);

        // merge the child's resolved knowledge
        for (Observable res : child.resolved.keySet()) {
            if (resolved.containsKey(res)) {
                resolved.get(res).addAll(child.getResolved(res));
            } else {
                resolved.put(res, child.getResolved(res));
            }
        }

        // link the child resolution's root nodes
        for (Pair<Model, Coverage> resolved : child.resolution) {
            addVertex(resolved.getFirst());
            if (parentModel != null) {
                addEdge(resolved.getFirst(), parentModel, new ResolutionEdge(child.resolvable, child.coverage, resolutionType));
            } else {
                this.resolution.add(Pair.of(resolved.getFirst(), resolved.getSecond()));
            }
            if (this.coverage == null) {
                this.coverage = child.getCoverage();
            } else {
                this.coverage = this.getCoverage().merge(child.getCoverage(), LogicalConnector.UNION);
            }
        }

        return this.coverage;
    }

    public Coverage getCoverage() {
        return coverage == null ? Coverage.empty() : coverage;
    }

    public ResolutionImpl withCoverage(Coverage coverage) {
        this.coverage = coverage;
        return this;
    }

    public ResolutionImpl setEmpty() {
        this.empty = true;
        return this;
    }

    @Override
    public String toString() {
        StringBuffer ret = new StringBuffer(512);
        for (Pair<Model, Coverage> resolved : resolution) {
            ret.append(resolved.getFirst() + " [" + NumberFormat.getPercentInstance().format(resolved.getSecond().getCoverage())
                    + "]\n");
            ret.append(printResolution(resolved.getFirst(), 3));
        }
        return ret.toString();
    }

    private Object printResolution(Model first, int i) {
        StringBuffer ret = new StringBuffer(512);
        for (ResolutionType type : new ResolutionType[]{ResolutionType.DIRECT, ResolutionType.DEFERRAL,
                ResolutionType.RESOLVED}) {
            for (Triple<Model, Observable, Coverage> resolved : getResolving(first, type)) {
                ret.append(Utils.Strings.spaces(i) + resolved.getFirst() + " [" + resolved.getSecond() + ": "
                        + Utils.Strings.capitalize(type.name().toLowerCase()) + ", "
                        + NumberFormat.getPercentInstance().format(resolved.getThird().getCoverage()) + "]\n");
                ret.append(printResolution(resolved.getFirst(), i + 3));
            }
        }
        return ret;
    }

    /**
     * Call when starting to resolve an observable. If it's already being resolved, true will return
     * and resolution should not take place. Otherwise it will be added to the resolving set for
     * downstream resolutions.
     * 
     * @param observable
     * @return
     */
    public boolean checkResolving(Observable observable) {
        if (resolving.contains(observable)) {
            return true;
        }
        resolving.add(observable);
        return false;
    }

    @Override
    public boolean isEmpty() {
        return this.empty;
    }

    @Override
    public List<Triple<Model, Observable, Coverage>> getResolving(Model target, ResolutionType strategy) {
        List<Triple<Model, Observable, Coverage>> ret = new ArrayList<>();
        for (ResolutionEdge edge : incomingEdgesOf(target)) {
            if (edge.type == strategy) {
                ret.add(Triple.of(getEdgeSource(edge), edge.getObservable(), edge.getCoverage()));
            }
        }
        return ret;
    }

    @Override
    public Collection<Knowledge> getResolved(Observable observable) {
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

    /**
     * Merge in an accepted model at root level.
     * 
     * @param model the model accepted
     * @param parentModel another model whose dependency is being resolved by the new model, or null
     *        if resolving the top-level knowledge
     * @param coverage coverage of this resolution
     * @param observable the observable being resolved
     * @param resolution the type of resolution that this models enables
     */
    public void merge(Model model, Coverage coverage, Observable observable, ResolutionType resolution) {

        addVertex(model);
        this.resolution.add(Pair.of(model, coverage));

        for (Observable o : model.getObservables()) {
            if (resolved.containsKey(o)) {
                resolved.get(o).add(model);
            } else {
                resolved.put(o, Sets.newHashSet(model));
            }
        }

    }

}
