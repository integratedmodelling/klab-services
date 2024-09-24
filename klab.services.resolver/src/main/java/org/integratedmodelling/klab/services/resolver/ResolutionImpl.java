package org.integratedmodelling.klab.services.resolver;

import com.google.common.collect.Sets;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resolvable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resolver.Resolution;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.text.NumberFormat;
import java.util.*;

/**
 * The resolution is the result of {@link Resolver#resolve(Observation, ContextScope)}. It contains the
 * resolution strategy for the top-level observable, resolved by zero or more models that are connected to the
 * resolution of all their dependencies in a graph. In the graph, each link reports the portion of the context
 * covered by the incoming model (possibly partial), the dependency observable resolved, and the type of
 * resolution: direct, i.e. final after computation, or deferring to further observations to be made in the
 * context of the observation(s) resulting from the computation, which must be made later and may fail.
 * Deferred resolution is invoked by the runtime on the same resolver, which will add nodes to the same
 * resolution graph, and the observation task finalizes only after all deferred resolutions have completed
 * successfully.
 *
 * @author Ferd
 */
public class ResolutionImpl extends DefaultDirectedGraph<Resolvable, ResolutionImpl.ResolutionEdge> implements Resolution {

    private static final long serialVersionUID = 4088657660423175126L;

    private Resolvable resolvable;
    private Coverage coverage;
    private Set<Observable> resolving = new HashSet<>();
    private Map<Observable, Collection<Resolvable>> resolved = new HashMap<>();
    private List<Pair<Resolvable, Coverage>> resolution = new ArrayList<>();
    private DirectObservation resolutionContext;

    private boolean empty;
    private List<Notification> notifications = new ArrayList<>();

    class ResolutionEdge extends DefaultEdge {

        private static final long serialVersionUID = 5876676141844919004L;

        private Resolvable observable;
        private ResolutionType type;
        private Coverage coverage;

        public ResolutionEdge(Resolvable observable, Coverage coverage, ResolutionType type) {
            this.observable = observable;
            this.coverage = coverage;
            this.type = type;
        }

        public ResolutionEdge() {
        }

        public Coverage getCoverage() {
            return coverage;
        }

        public Resolvable getObservable() {
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
            return observable + "\n" + org.integratedmodelling.common.utils.Utils.Strings.capitalize(
                    this.type.name().toLowerCase()) + " (" + NumberFormat.getPercentInstance().format(
                    coverage.getCoverage()) + ")";
        }
    }

    /**
     * Create a root resolution graph for the passed knowledge, pre-loading whatever has been already resolved
     * in the passed scope.
     *
     * @param root
     * @param scope
     */
    public ResolutionImpl(Resolvable root, Scale scale, ContextScope scope) {

        super(ResolutionImpl.ResolutionEdge.class);
        this.resolvable = root;
        // pre-resolved observations. Can't use better idioms for apparent bug in Java type system.
        for (Observable o : scope.getObservations().keySet()) {
            Set<Resolvable> set = new HashSet<>();
            set.add(scope.getObservations().get(o));
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
    public ResolutionImpl(Resolvable root, Scale scale, ContextScope scope, ResolutionImpl parent) {

        super(ResolutionImpl.ResolutionEdge.class);
        this.resolving.addAll(parent.resolving);
        this.resolved.putAll(parent.resolved);
        this.resolvable = root;
    }

    /**
     * Merge in a successful model resolution with its original observable and coverage. Its root models
     * become connected to the parent model passed, which is not part of the passed graph.
     *
     * @param parentModel
     * @param child
     * @param resolutionType
     * @return
     */
    public Coverage merge(Resolvable parentModel, ResolutionImpl child, ResolutionType resolutionType) {

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
        for (Pair<Resolvable, Coverage> resolved : child.resolution) {
            addVertex(resolved.getFirst());
            if (parentModel != null) {
                addEdge(resolved.getFirst(), parentModel, new ResolutionEdge(child.resolvable,
                        child.coverage, resolutionType));
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
        for (Pair<Resolvable, Coverage> resolved : resolution) {
            ret.append(resolved.getFirst() + " [" + NumberFormat.getPercentInstance().format(
                    resolved.getSecond().getCoverage()) + "]\n");
            ret.append(printResolution(resolved.getFirst(), 3));
        }
        return ret.toString();
    }

    private String printResolution(Resolvable first, int i) {
        StringBuffer ret = new StringBuffer(512);
        for (ResolutionType type : ResolutionType.values()) {
            for (Triple<Resolvable, Resolvable, Coverage> resolved : getResolving(first, type)) {
                ret.append(org.integratedmodelling.common.utils.Utils.Strings.spaces(
                        i) + resolved.getFirst() + " [" + resolved.getSecond() + ": " + Utils.Strings.capitalize(
                        type.name().toLowerCase()) + ", " + NumberFormat.getPercentInstance().format(
                        resolved.getThird().getCoverage()) + "]\n");
                String child = printResolution(resolved.getFirst(), i + 3);
                if (!child.isBlank()) {
                    ret.append(child);
                }
            }
        }
        return ret.toString();
    }

    /**
     * Call when starting to resolve an observable. If it's already being resolved, true will return and
     * resolution should not take place. Otherwise it will be added to the resolving set for downstream
     * resolutions.
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
    public List<Notification> getNotifications() {
        return notifications;
    }

    @Override
    public List<Triple<Resolvable, Resolvable, Coverage>> getResolving(Resolvable target,
                                                                       ResolutionType strategy) {
        List<Triple<Resolvable, Resolvable, Coverage>> ret = new ArrayList<>();
        for (ResolutionEdge edge : incomingEdgesOf(target)) {
            if (edge.type == strategy) {
                ret.add(Triple.of(getEdgeSource(edge), edge.getObservable(), edge.getCoverage()));
            }
        }
        return ret;
    }

    @Override
    public Collection<Resolvable> getResolved(Observable observable) {
        return resolved.get(observable);
    }

    @Override
    public Resolvable getResolvable() {
        return this.resolvable;
    }

    @Override
    public DirectObservation getResolutionContext() {
        return resolutionContext;
    }

    @Override
    public List<Pair<Resolvable, Coverage>> getResolution() {
        return resolution;
    }

    /**
     * Merge in an accepted model at root level.
     *
     * @param model      the model accepted
     * @param coverage   coverage of this resolution
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

    public void setResolvable(Resolvable resolvable) {
        this.resolvable = resolvable;
    }

    public void setCoverage(Coverage coverage) {
        this.coverage = coverage;
    }

    public Set<Observable> getResolving() {
        return resolving;
    }

    public void setResolving(Set<Observable> resolving) {
        this.resolving = resolving;
    }

    public Map<Observable, Collection<Resolvable>> getResolved() {
        return resolved;
    }

    public void setResolved(Map<Observable, Collection<Resolvable>> resolved) {
        this.resolved = resolved;
    }

    public void setResolution(List<Pair<Resolvable, Coverage>> resolution) {
        this.resolution = resolution;
    }

    public void setResolutionContext(DirectObservation resolutionContext) {
        this.resolutionContext = resolutionContext;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
    }

    public static Resolution empty(Resolvable resolvable, ContextScope scope) {
        var ret = new ResolutionImpl(resolvable, null, scope);
        ret.empty = true;
        return ret;
    }
}
