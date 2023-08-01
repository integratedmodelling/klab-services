//package org.integratedmodelling.klab.api.services.resolver;
//
//import java.util.List;
//
//import org.integratedmodelling.klab.api.knowledge.Model;
//import org.integratedmodelling.klab.api.knowledge.Observable;
//import org.integratedmodelling.klab.api.lang.LogicalConnector;
//
///**
// * Each vertex in the resolution graph contains the resolved observable, a model that represents the
// * chosen resolution of the resolvable in the context scope and the coverage this resolution has
// * determined in it. The way nodes resolve one another is stored in the edges of the graph;
// * deferring resolution to after the initial contextualization will require further queries after
// * executing the dataflow.
// * <p>
// * Each element has its coverage, expressing its "natural" one intersected with the coverage of any
// * incoming nodes, and the merging strategy (UNION or INTERSECTION, maybe eventually EXCLUSION) used
// * to merge the coverage of its children ({@link #getMergeStrategy()}).
// * <p>
// * The resolved nodes are connected by links that specify the resolution strategy through the
// * {@link Resolution.Type} enum. These should be treated separately when building the dataflow: all
// * DIRECT should be done first by creating new actuators, then any FILTERs added, and last the
// * DEFERRED strategies should cause the runtime to invoke the <em>same</em> resolver again. The
// * "resolving" nodes for each strategy can be accessed from a node using the
// * {@link #getResolving(Resolution.Type)} method.
// * 
// * @author Ferd
// *
// */
//
//public interface Resolution {
//
//    /**
//     * The resolution type is how a model contextualizes an observable.
//     * 
//     * @author Ferd
//     *
//     */
//    public enum Type {
//        /**
//         * the strategy implies the direct observation of the observable and will result in an
//         * actuator being defined in the dataflow.
//         */
//        DIRECT,
//
//        /**
//         * The strategy implies the observation of other direct observables, then the application of
//         * the child strateg(ies) to each, then the computations. An actuator will be created and
//         * the resolver will be called again on each instance produced by it.
//         */
//        DEFERRAL,
//
//        /**
//         * The target model is applied to the outputs of the receiving one to modify its value.
//         * Filters are inserted into the actuator created for the resolved node: no specific
//         * actuator for a filtering node is created.
//         */
//        FILTERING
//
//    }
//
//    /**
//     * Return all the nodes that resolve this node using the passed strategy. The order is
//     * important: each resolution is "better" than the one following it, which contributes to the
//     * coverage. The dataflow should compute them in reverse order, each in their own coverage, to
//     * ensure that any portions of the context covered by more than one are overridden by the better
//     * resolution.
//     * 
//     * @param strategy
//     * @return
//     */
//    List<Resolution> getResolving(Type strategy);
//
//    /**
//     * The observable of this resolution. Never null
//     * 
//     * @return
//     */
//    Observable getObservable();
//
//    /**
//     * The coverage of this resolution. Never null
//     * 
//     * @return
//     */
//    Coverage getCoverage();
//
//    /**
//     * The model that made this resolution. Can only be null if the resolution is of an
//     * acknowledgement.
//     * 
//     * @return
//     */
//    Model getModel();
//
//    LogicalConnector getMergeStrategy();
//}
