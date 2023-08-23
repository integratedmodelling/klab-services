package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.lang.Contextualizable;

import java.util.Collections;
import java.util.List;

/**
 * <p>A resolution strategy is the result of analyzing an observable to assess the different ways it can be
 * contextualized. Given an observable and a context, the reasoner produces strategies in increasing order of cost
 * and/or complexity. The resolver will resolve them in sequence, stopping when the context coverage is complete. Unless
 * the observable is a non-resolvable abstract, the first strategy will always be the direct observation of the
 * observable with no further computations.</p>
 *
 * <p>The observation strategy, by listing all the observables that must be resolved prior to contextualization of the
 * target observable, also ensures that the dataflow contains all the needed references to properly maintain the
 * influence graph in the digital twin, which picks up links as new observations are made.</p>
 *
 * <h3>Direct observation</h3>
 *
 * <p>These considerations apply to the direct observation of an observable, not handled through alternative
 * strategies:</p>
 *
 * <p>Resolution uses the semantic closure of the concept, choosing, all else being equal, the model that resolves the
 * observable whose semantic distance to the observable being resolved is closest to 0, with the caveat that models of
 * abstract <em>main</em> observables are illegal, so catch-alls like <code>model Quality</code> cannot be written. The
 * ability of using resolvers or instantiators for the main observable that have semantic distance > 0 could be
 * questioned, and may be a configurable resolver option along with the other priorities. But in general, resolution by
 * semantic distance should catch the least generic models analyzing the structure of the observable. This should always
 * apply for concepts used as the arguments of operators, both unary or binary. So for example, <code>distance to
 * Mountain</code> should catch <code>distance to Subject</code> (or, better, <code>distance to Geolocated
 * Subject</code>), which is legal due to the operator, if one is present and nothing more specific is available. This
 * enables resolution with operators without requiring any specific handling.</p>
 *
 * <p>An exception to the above is that models with more specific inherency than the context can be compatible, as long
 * as the inherency observation can be made. These should only be looked up after the direct observable has not
 * resolved, unless the observable has a direct <code>of</code> inherency specified.</p>
 *
 * <p>The direct observation of a direct observable with concrete traits should always check if instances of
 * the base type have been observed already (i.e. the scope contains an observation of the base type) and see if the
 * base trait(s) has been resolved previously <em>within</em> the instances. If so, the result is present and the query
 * is resolved through a RESOLVED strategy, which simply produces the observation group ("folder") containing the
 * classified instances.</p>
 *
 * <p>After each new observation, the emergence detector must check for new matching patterns and resolve whatever
 * configuration, relationship, subject or event has emerged, also installing the correspondent <code>change in
 * Configuration</code> with the configuration triggers as dependencies. Configurations such as networks will be
 * influenced by the relevant observation groups, which change when instances are added, removed or modified (e.g.
 * through classification and characterization).</p>
 *
 * <h3>Alternative strategies</h3>
 *
 * <p> Current list of observation strategy rules by observable beyond the direct observation, waiting for actual
 * documentation:</p>
 *
 * <dl>
 *
 *     <dt>Direct observable O without traits</dt>
 *     <dd>Instantiate O without traits, then defer resolution of all instances</dd>
 *     <dt>Direct observable O with concrete traits (see above for direct strategy)</dt>
 *     <dd>Instantiate O without traits, then classify Os according to base traits of all traits (cartesian product),
 *     deactivate any not matching unless previously observed, then defer resolution of all classified instances</dd>
 *     <dt>Abstract trait T of direct observable O</dt>
 *     <dd>Proceed like Direct observable O with concrete traits, limiting resolution to those classified as T</dd>
 *     <dt>Quality Q with abstract trait R</dt>
 *     <dd>
 *         <dl><dt>If R is a role and role resolution is set in the scope (external setting):</dt><dd>Resolve each Rx
 *         of Q where Rx is each one of the resolved roles</dd></dl>
 *         <dl><dt>Otherwise:</dt><dd>Resolve R of Q into set of
 *  *         <code>Rx Q</code> where Rx is each one of the resolved roles for R, then d, then defer resolution of
 *  each <code>Rx O</code></dd></dl>
 *     </dd>
 *     <dt>Direct observable O with abstract trait R</dt>
 *     <dd>
 *         <dl><dt>If R is a role and role resolution is set in the scope (external setting):</dt><dd>Instantiate O,
 *         then classify according to  R of O, then defer characterization of each resulting
 *         <code>Rx O</code> where Rx is each one of the resolved roles for R</dd></dl>
 *         <dl><dt>If role is not resolved by the scope:</dt><dd> Instantiate <code>O</code> followed by classification
 *              <code>R of O</code>, then defer resolution of each <code>Rx O</code></dd></dl>
 *     </dd>
 *
 *     <dt>Quality Q with concrete trait T</dt><dd>Resolve <code>Q</code> followed by characterization
 *     <code>T of Q</code></dd>
 *
 *     <dt>Instantiation of Relationship R</dt><dd>Instantiate source and, if different, target of R, most specific
 *     first, then instantiate R.</dd>
 *
 *     <dt>Quality Q during event E</dt><dd>Resolve Q with temporal extension (including its change) in the context
 *     of the context of E, instantiate E and add an aggregator that only measures the quality within the E temporal
 *     span. This enables the correct actions for individually classified concrete events, such as March.</dd>
 *
 *     <dt>Any process, including <code>change in quality Q</code></dt><dd>Must ensure that the initial conditions
 *     for all Qs that are <code>affected</code> by the process but not <code>created</code> by it are observed, i.e.
 *     the models must also resolve and depend on the qualities themselves, unless they are qualities
 *     <code>created</code> by the process. Note that <code>change in Q</code> affects but does not create Q. These
 *     resolutions precede the actual resolution of P. Another strategy, if P is not resolved directly and Q is
 *     quantifiable, is to use instead of resolution of P (after any added  dependencies) the resolution of
 *     <code>change rate of Q</code> and add an integrator to produce the change.</dd>
 *
 *     <dt>Quality observables with value operators</dt><dd>Must resolve the main quality without operators,  any
 *     observables used as arguments for the value operators, plus the contextualizers that perform the filtering</dd>
 *
 *     <dt><code>changed Quality</code></dt><dd>Resolve Quality with temporal extension, then insert event detector
 *     (not sure if this can be looked up using generics)</dd>
 *
 *     <dt>Observables with more specialized stated inherency than the context</dt><dd>These observables specify
 *     <code>of</code> to explicitly specialize the context, Resolve the inherency and defer the
 *     observable, without the explicit inherency if any, to within each instance. This should also be triggered if
 *     only models <code>within</code> a speciaiized inherent are found. This situation can also happen if a model
 *     that only explicitly resolves <code>within</code> a specialized context is chosen.</dd>
 *
 * </dl>
 *
 * @author Ferd
 */
public interface ObservationStrategy {

    enum Type {

        /**
         * the strategy implies the direct observation of the observable, with or without runtime recontextualization
         */
        DIRECT,

        /**
         * The strategy implies the observation of other direct observables, then the application of the child
         * strateg(ies) to each, then the computations.
         */
        DEREIFYING,

    }

    /**
     * @return
     */
    Type getType();

    /**
     * @return
     */
    Observable getObservable();

    /**
     * The strategy may consist of alternative strategies, prioritized as needed. More than one may be applied according
     * to their resolved coverage.
     *
     * @return
     */
    List<ObservationStrategy> getChildStrategies();

    /**
     * @return
     */
    List<Contextualizable> getComputations();

    static ObservationStrategy direct(Observable observable) {
        return new ObservationStrategy() {

            @Override
            public Type getType() {
                return Type.DIRECT;
            }

            @Override
            public Observable getObservable() {
                return observable;
            }

            @Override
            public List<ObservationStrategy> getChildStrategies() {
                return Collections.emptyList();
            }

            @Override
            public List<Contextualizable> getComputations() {
                return Collections.emptyList();
            }

        };
    }
//
//    public static ObservationStrategy resolved(Observable observable) {
//        return new ObservationStrategy() {
//
//            @Override
//            public Type getType() {
//                return Type.RESOLVED;
//            }
//
//            @Override
//            public Observable getObservable() {
//                return observable;
//            }
//
//            @Override
//            public List<ObservationStrategy> getChildStrategies() {
//                return Collections.emptyList();
//            }
//
//            @Override
//            public List<Contextualizable> getComputations() {
//                return Collections.emptyList();
//            }
//
//        };
//    }

}
