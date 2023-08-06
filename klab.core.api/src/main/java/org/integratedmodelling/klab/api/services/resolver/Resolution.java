package org.integratedmodelling.klab.api.services.resolver;

import java.util.Collection;
import java.util.List;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Resolver;

/**
 * A resolution is the output of {@link Resolver#resolve(Knowledge, ContextScope)} and consists of a
 * graph of {@link Model} objects that details how a particular knowledge object has been resolved
 * through other knowledge in a given scope. When the resolution is deferred, further resolution is
 * needed after the execution of the corresponding dataflow to complete the observation.
 * <p>
 * A resolution is compiled into a dataflow to contextualize the strategy. Resolution has been
 * successful if the coverage of the resolution is acceptable (which depends on configuration, but
 * {@link Coverage#isEmpty()} should never be accepted). Each knowledge object in the graph is
 * linked to others through connections that detail the type of resolution that has happened;
 * dataflow compilation starts at the root of the graph, retrieving the resolving knowledge from the
 * graph according to the possible resolution types.
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
         * Links an observable to a pre-resolved observation that was present in the resolution
         * scope or pre-defined in the observable.
         */
        RESOLVED,

        /**
         * The strategy implies the direct observation of the observable through the model it links
         * to, and will result in an actuator being defined in the dataflow.
         */
        DIRECT,

        /**
         * The strategy implies the observation of other direct observables, then the application of
         * the child strateg(ies) to each, then the merging of the results into the original
         * observable (which may become "of X" if the deferred inherency is incompatible). An
         * actuator will be created and the resolver will be called again on each observation
         * produced by it. The resolution link originates in an observable, not a model.
         */
        DEFER_INHERENCY,

        /**
         * The strategy implies the observation of a model that contextualizes the concrete
         * semantics for an abstract observable in the context, followed by the deferred observation
         * of the resulting observable(s) through successive calls to the resolver. The resolution
         * link originates in an observable, not a model. The resolving model will create a OR-ed
         * observable containing all concepts, to substitute the abstract one, and a merged
         * observation as appropriate.
         */
        DEFER_SEMANTICS,

        /**
         * The target model is applied to the outputs of the receiving one to modify its value.
         * Filters are inserted into the actuator created for the resolved node: no specific
         * actuator for a filtering node is created.
         */
        FILTER

    }

    /**
     * Return the total coverage of the resolution w.r.t. the context scope, i.e. the coverage of
     * the root node(s).
     * 
     * @return
     */
    Coverage getCoverage();

    /**
     * The knowledge that this resolution has been built for.
     * 
     * @return
     */
    Observable getResolvable();

    /**
     * The root-level models resolving the resolvable, each with their coverage of the resolved
     * knowledge. Use {@link #getResolving(Model, ResolutionType)} to walk the resolution graph.
     * 
     * @return
     */
    List<Pair<Model, Coverage>> getResolution();

    /**
     * The resolution keeps tabs on anything that has been resolved already, either through models
     * or pre-existing observations. The result, if not null, contains all the knowledge used to
     * resolve the observable in the scope.
     * 
     * @param observable
     * @return
     */
    Collection<Knowledge> getResolved(Observable observable);

    /**
     * Return the collection of whatever resolves the passed model using the passed strategy. The
     * order is that of resolution, which matters as the first objects should override the ones
     * after them when overlaps exist.
     * 
     * @param target
     * @param type
     * @return
     */
    List<Triple<Model, Observable, Coverage>> getResolving(Model target, ResolutionType type);

    /**
     * Empty means that resolution has failed. A non-empty graph may contain zero nodes, meaning
     * that no contextualization is necessary.
     * 
     * @return
     */
    boolean isEmpty();

}
