package org.integratedmodelling.klab.api.services;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable.Builder;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The reasoner service collects all functionalities that use semantics in k.LAB.
 */
public interface Reasoner extends KlabService {

    default String getServiceName() {
        return "klab.reasoner.service";
    }

    /**
     * All services publish capabilities and have a call to obtain them. Capabilities may depend on
     * authentication but the endpoint should be publicly available as well.
     *
     * @author Ferd
     */
    interface Capabilities extends ServiceCapabilities {

        /**
         * Get the unique ID of the worldview loaded in this service. If this is null, the service knows
         * nothing and is unusable for reasoning.
         *
         * @return
         */
        String getWorldviewId();
    }

    @Override
    default boolean scopesAreReactive() {
        return false;
    }

    /**
     * @return
     */
    @Override
    Capabilities capabilities(Scope scope);

    /**
     * @param definition
     * @return
     */
    Concept resolveConcept(String definition);

    /**
     * Extract any component concept from the concept definition that matches all the semantic types passed.
     * For example, pass {@link SemanticType#ABSTRACT} and {@link SemanticType#PREDICATE} to obtain all the
     * abstract predicates involved in the concept definition.
     *
     * @param concept
     * @return
     */
    Collection<Concept> collectComponents(Concept concept, Collection<SemanticType> type);

    /**
     * @param definition
     * @return
     */
    Observable resolveObservable(String definition);

    /**
     * True if the candidate resolves the first observable, i.e. a candidate's description (model) can make an
     * observation of it.
     *
     * @param toResolve
     * @param candidate
     * @param context   can be null, defaulting to the natural context of the observables
     * @return
     */
    boolean resolves(Semantics toResolve, Semantics candidate, Semantics context);

    /**
     * @param conceptDeclaration
     * @return
     */
    Concept declareConcept(KimConcept conceptDeclaration);

    /**
     * @param observableDeclaration
     * @return
     */
    Observable declareObservable(KimObservable observableDeclaration);

    /**
     * Declare an observable from a pattern using any pattern variables. If the observable is not a
     * pattern, ignore the variables and return {@link #declareObservable(KimObservable)}. If some of the
     * pattern variables are undefined in the passed array, return null.
     *
     * @param observableDeclaration
     * @param patternVariables
     * @return
     */
    Observable declareObservable(KimObservable observableDeclaration, Map<String, Object> patternVariables);

    /**
     * Basic operation for subsumption between concepts.
     *
     * @param conceptImpl
     * @param other
     * @return
     */
    boolean subsumes(Semantics conceptImpl, Semantics other);

    /**
     * If the target is a union or intersection, return the operands. Otherwise return a singleton with the
     * target itself in it.
     *
     * @param target
     * @return
     */
    Collection<Concept> operands(Semantics target);

    /**
     * The direct asserted children of the target.
     *
     * @param target
     * @return
     */
    Collection<Concept> children(Semantics target);

    /**
     * The direct asserted parents of the target.
     *
     * @param target
     * @return
     */
    Collection<Concept> parents(Semantics target);

    /**
     * Get a builder specified on the passed observable, used to obtain a modified observable.
     *
     * @param observableImpl
     * @return
     */
    Builder observableBuilder(Observable observableImpl);

    /**
     * For fluency. Returns a single parent for a concept known to be part of a straight hierarchy. If the
     * concept has multiple parents, an exception is thrown.
     *
     * @param c
     * @return
     */
    Concept parent(Semantics c);

    /**
     * Produce the logical OR or AND of the passed concepts. k.LAB does not support exclusion so passing
     * {@link LogicalConnector#EXCLUSION} should throw an exception.
     *
     * @param concepts
     * @param connector
     * @return
     */
    Concept compose(Collection<Concept> concepts, LogicalConnector connector);

    /**
     * Return the set of all children of the target, direct or indirect, using only the asserted hierarchy.
     * For the inferred version use {@link #closure(Semantics)}.
     *
     * @param target
     * @return
     */
    Collection<Concept> allChildren(Semantics target);

    /**
     * Return all the asserted parents of the target, direct or indirect.
     *
     * @param target
     * @return
     */
    Collection<Concept> allParents(Semantics target);

    /**
     * The closure is the inferred version of {@link #allChildren(Semantics)}, which only uses the asserted
     * hierarchy.
     *
     * @param target
     * @return
     */
    Collection<Concept> closure(Semantics target);

    /**
     * @param target
     * @return
     */
    int semanticDistance(Semantics target, Semantics other);

    /**
     * Contextual version of {@link #semanticDistance(Semantics, Semantics)}.
     *
     * @param target
     * @param other
     * @param context
     * @return
     */
    int semanticDistance(Semantics target, Semantics other, Semantics context);

    /**
     * The core observable represented by the target, from the root namespace of the worldview.
     *
     * @param first
     * @return
     */
    Concept coreObservable(Semantics first);

    /**
     * @param concept
     * @return
     */
    Pair<Concept, List<SemanticType>> splitOperators(Semantics concept);

    /**
     * The number of hops in the asserted hierarchy to reach the second concept starting at from. If there is
     * no path, return -1.
     *
     * @param from
     * @param to
     * @return
     */
    int assertedDistance(Semantics from, Semantics to);

    /**
     * All roles adopted by the target, directly or indirectly.
     *
     * @param concept
     * @return
     */
    Collection<Concept> roles(Semantics concept);

    /**
     * @param concept
     * @param role
     * @return
     */
    boolean hasRole(Semantics concept, Concept role);

    /**
     * @param concept
     * @param role
     * @return
     */
    boolean hasDirectRole(Semantics concept, Concept role);

    /**
     * @param concept
     * @return
     */
    //    Concept directContext(Semantics concept);

    //    /**
    //     * Context is declared in the <code>within</code> clause in the worldview, but is no longer
    //     modifiable
    //     * @param concept
    //     * @return
    //     */
    //    Concept context(Semantics concept);

    /**
     * @param concept
     * @return
     */
    Concept directInherent(Semantics concept);

    /**
     * @param concept
     * @return
     */
    Concept inherent(Semantics concept);

    /**
     * @param concept
     * @return
     */
    Concept directGoal(Semantics concept);

    /**
     * @param concept
     * @return
     */
    Concept goal(Semantics concept);

    /**
     * @param concept
     * @return
     */
    Concept directCooccurrent(Semantics concept);

    /**
     * @param concept
     * @return
     */
    Concept directCausant(Semantics concept);

    /**
     * @param concept
     * @return
     */
    Concept directCaused(Semantics concept);

    /**
     * @param concept
     * @return
     */
    Concept directAdjacent(Semantics concept);

    /**
     * @param concept
     * @return
     */
    Concept directCompresent(Semantics concept);

    /**
     * @param concept
     * @return
     */
    Concept directRelativeTo(Semantics concept);

    /**
     * @param concept
     * @return
     */
    Concept cooccurrent(Semantics concept);

    /**
     * @param concept
     * @return
     */
    Concept causant(Semantics concept);

    /**
     * @param concept
     * @return
     */
    Concept caused(Semantics concept);

    /**
     * @param concept
     * @return
     */
    Concept adjacent(Semantics concept);

    /**
     * @param concept
     * @return
     */
    Concept compresent(Semantics concept);

    /**
     * The "comparison" concept when the passed semantics has been modified by a unary operator that implies
     * comparison, such as ratio, proportion or pairwise value.
     *
     * @param concept
     * @return
     */
    Concept relativeTo(Semantics concept);

    /**
     * Return all traits, i.e. identities, attributes and realms.
     *
     * @param concept
     * @return
     */
    Collection<Concept> traits(Semantics concept);

    /**
     * Return all identities.
     *
     * @param concept
     * @return identities
     */
    Collection<Concept> identities(Semantics concept);

    /**
     * Return all identities.
     *
     * @param concept
     * @return identities
     */
    Collection<Concept> directIdentities(Semantics concept);

    /**
     * Return all identities.
     *
     * @param concept
     * @return identities
     */
    Collection<Concept> directAttributes(Semantics concept);

    /**
     * Return all attributes
     *
     * @param concept
     * @return attributes
     */
    Collection<Concept> attributes(Semantics concept);

    /**
     * Return all realms.
     *
     * @param concept
     * @return realms
     */
    Collection<Concept> realms(Semantics concept);

    /**
     * Return all identities.
     *
     * @param concept
     * @return identities
     */
    Collection<Concept> directRealms(Semantics concept);

    /**
     * <p>
     * getBaseParentTrait.
     * </p>
     *
     * @param trait
     * @return
     */
    Concept baseParentTrait(Semantics trait);

    /**
     * The base observable is the one that was specified in k.IM as the main concept for the observable when
     * specified, without any predicates or modifiers (but keeping any unary operators). If a concept is
     * passed, the base observable is the concept itself.
     *
     * @param observable
     * @return
     */
    Concept baseObservable(Semantics observable);

    /**
     * Remove any attribute or explicit restriction and return the raw observable, without digging down to the
     * core definition.
     *
     * @param observable
     * @return
     */
    Concept rawObservable(Semantics observable);

    /**
     * Check if concept k carries the passed trait. Uses is() on all explicitly expressed traits.
     *
     * @param type
     * @param trait
     * @return a boolean.
     */
    boolean hasTrait(Semantics type, Concept trait);

    /**
     * Check if concept k carries a trait T so that the passed trait is-a T.
     *
     * @return a boolean.
     */
    boolean hasDirectTrait(Semantics type, Concept trait);

    /**
     * Check if concept k carries a role T so that the passed role is-a T.
     *
     * @return a boolean.
     */
    boolean hasParentRole(Semantics o1, Concept t);

    //    /**
    //     * True if the concept was declared with distributed inherency (<code>of each</code>)
    //     *
    //     * @param c
    //     * @return
    //     */
    //    boolean hasDistributedInherency(Concept c);

    /**
     * Like {@link #traits(Semantics)} but only returns the traits directly attributed to this concept.
     *
     * @param concept
     * @return
     */
    Collection<Concept> directTraits(Semantics concept);

    /**
     * Like {@link #traits(Semantics)} but only returns the traits directly attributed to this concept.
     *
     * @param concept
     * @return
     */
    Collection<Concept> directRoles(Semantics concept);

    /**
     * @param semantics
     * @return
     */
    String displayName(Semantics semantics);

    /**
     * @param concept
     * @return
     */
    String displayLabel(Semantics concept);

    /**
     * @param concept
     * @return
     */
    String style(Concept concept);

    /**
     * Return the base enum type (quality, subject....) for the passed observable.
     *
     * @param observable
     * @param acceptTraits if true, will return a trait type (which can be the observable of a class model
     *                     although it's not an observable per se).
     * @return the enum type
     */
    SemanticType observableType(Semantics observable, boolean acceptTraits);

    /**
     * Return the asserted source of the relationship, assuming it is unique. If it is not unique, the result
     * is arbitrary among the possible sources.
     *
     * @param relationship a relationship concept
     * @return the source. May be null in abstract relationships.
     */
    Concept relationshipSource(Semantics relationship);

    /**
     * Return all the asserted sources of the relationship.
     *
     * @param relationship a relationship concept
     * @return the sources. May be empty in abstract relationships.
     */
    Collection<Concept> relationshipSources(Semantics relationship);

    /**
     * Return the asserted target of the relationship, assuming it is unique. If it is not unique, the result
     * is arbitrary among the possible targets.
     *
     * @param relationship a relationship concept
     * @return the target. May be null in abstract relationships.
     */
    Concept relationshipTarget(Semantics relationship);

    /**
     * Return all the asserted targets of the relationship. If the asserted target are the same concept,
     * return only one concept.
     *
     * @param relationship a relationship concept
     * @return the targets. May be empty in abstract relationships.
     */
    Collection<Concept> relationshipTargets(Semantics relationship);

    /**
     * If the passed concept is not negated, return its negation. Otherwise return the concept this has been
     * asserted to be the negation of. If the concept is not a deniable attribute, throw an exception.
     *
     * @param concept
     * @return
     */
    Concept negated(Concept concept);

    /**
     * Use the DL reasoner to check if the passed concept is semantically consistent. As all concepts are
     * automatically checked at the time of definition, the fast way to check for consistency is
     * {@link Concept#is(SemanticType)} using the value NOTHING.
     *
     * @param ret
     * @return
     */
    boolean satisfiable(Semantics ret);

    /**
     * The knowledge domain this concept is part of, defined through the worldview. Should never be null,
     * although currently it may be.
     *
     * @param conceptImpl
     * @return
     */
    Semantics domain(Semantics conceptImpl);

    /**
     * Get all the restricted target of the "applies to" specification for this concept.
     *
     * @param main a concept.
     * @return all applicable concepts or an empty collection
     */
    Collection<Concept> applicableObservables(Concept main);

    /**
     * Return the type described by a quality that results from applying a unary operator to it. For example
     * <code>magnitude of earth:AtmosphericTemperature</code> describes AtmosphericTemperature.
     *
     * @param concept
     * @return
     */
    Concept describedType(Semantics concept);

    /**
     * @param concept
     * @param other
     * @return
     */
    boolean compatible(Semantics concept, Semantics other);

    /**
     * Check for compatibility of context1 and context2 as the context for an observation of focus (i.e.,
     * focus can be observed by an observation process that happens in context1). Works like isCompatible, but
     * if context1 is an occurrent, it will let through situations where it affects focus in whatever context
     * it is, or where the its own context is the same as context2, thereby there is a common context to refer
     * to.
     *
     * @param focus    the focal observable whose context we are checking
     * @param context1 the specific context of the observation (model) that will observe focus
     * @param context2 the mandated context of focus
     * @return true if focus can be observed by an observation process that happens in context1.
     */
    boolean contextuallyCompatible(Semantics focus, Semantics context1, Semantics context2);

    /**
     * @param concept
     * @return
     */
    boolean occurrent(Semantics concept);

    /**
     * Return the most specific ancestor that the concepts in the passed collection have in common, or null if
     * none.
     *
     * @param cc
     * @return
     */
    Concept leastGeneralCommon(Collection<Concept> cc);

    /**
     * True if affecting affects affected. Uses inference when checking. Also true if the concept is a quality
     * describing anything that is affected.
     *
     * @param affected
     * @param affecting
     * @return
     */
    boolean affectedBy(Semantics affected, Semantics affecting);

    /**
     * True if affecting creates affected. Uses inference when checking. Also true if the concept is a quality
     * describing anything that is created or the affecting type itself (this last condition only holds for
     * created, as the affecting type, an occurrent, must occur for its derived quality to exist).
     *
     * @param affected
     * @param affecting
     * @return true if created.
     */
    boolean createdBy(Semantics affected, Semantics affecting);

    /**
     * @param semantics
     * @return
     */
    Collection<Concept> affectedOrCreated(Semantics semantics);

    /**
     * @param semantics
     * @return
     */
    Collection<Concept> affected(Semantics semantics);

    /**
     * @param semantics
     * @return
     */
    Collection<Concept> created(Semantics semantics);

    /**
     * Check for a match between a concept/observable and a pattern that may use generics. There is a match if
     * the pattern is generic and the passed candidate aligns, or if the concepts are identical. Use the XXX
     * version if you need to retrieve the actually matching generics.
     *
     * @param candidate
     * @param pattern
     * @return
     */
    boolean match(Semantics candidate, Semantics pattern);

    /**
     * Like {@link #match(Semantics, Semantics)}, if the return value is true the <code>matches</code> map
     * will be filled in with the generics that are incarnated in the candidate.
     *
     * @param candidate
     * @param pattern
     * @param matches
     * @return
     */
    boolean match(Semantics candidate, Semantics pattern, Map<Concept, Concept> matches);

    /**
     * Create the concrete version of the passed generic pattern using the passed concrete concepts to
     * substitute the corresponding generic components.
     *
     * @param pattern
     * @param concreteConcepts
     * @param <T>
     * @return
     */
    <T extends Semantics> T concretize(T pattern, Map<Concept, Concept> concreteConcepts);

    /**
     * Create the concrete version of the passed generic pattern using the passed concrete concepts to
     * substitute the corresponding generic components. Use inference to establish which generic components
     * must be substituted by the passed concepts, ignoring those that don't fit. If there are ambiguities, an
     * exception should be thrown as this method should be used only with patterns that are specifically
     * designed for it (e.g. in a generic concept definition with identity redistribution).
     *
     * @param pattern
     * @param concreteConcepts
     * @param <T>
     * @return
     */
    <T extends Semantics> T concretize(T pattern, List<Concept> concreteConcepts);

    /**
     * All the roles played by the passed observable within the passed context.
     *
     * @param observable
     * @param context
     * @return
     */
    Collection<Concept> rolesFor(Concept observable, Concept context);

    /**
     * Return the specific role that is baseRole and is implied by the context observable, either directly or
     * through its implication closure.
     *
     * @param baseRole
     * @param contextObservable
     * @return
     */
    Concept impliedRole(Concept baseRole, Concept contextObservable);

    /**
     * Get all other roles implied by this one. These must be concrete when the role is used in the main
     * observable for a model, which must produce or use them. Optionally include the source and destination
     * endpoints for all roles that apply to a relationship.
     *
     * @param role
     * @param includeRelationshipEndpoints if true, roles that apply to relationships will add the specialized
     *                                     source and destination types.
     * @return
     */
    Collection<Concept> impliedRoles(Concept role, boolean includeRelationshipEndpoints);

    /**
     * @param observable
     * @param scope
     * @return all the observation strategies for this observable in this context, best first
     *
     * <p>Return all the possible strategies to observe the passed observable in this context, in order of
     * increasing cost/complexity. These will be resolved by the resolver in the order returned, stopping when
     * coverage is enough. Except in case of abstract observables or patterns, the first should always be the
     * direct observation of the observable without any additional computation.</p>
     *
     * <p>A resolution strategy is the result of analyzing an observable to assess the different ways it can
     * be contextualized. Given an observable and a context, the reasoner produces strategies in increasing
     * order of cost and/or complexity. The resolver will resolve them in sequence, stopping when the context
     * coverage is complete. Unless the observable is a non-resolvable abstract/pattern, the first strategy
     * will always be the direct observation of the observable with no further computations.</p>
     *
     * <p>The observation strategy, by listing all the observables that must be resolved prior to
     * contextualization of the target observable, also ensures that the dataflow contains all the needed
     * references to properly maintain the influence graph in the digital twin, which picks up links as new
     * observations are made.</p>
     *
     * <p>Inferring these is not a job for the AI, as most of the needed reasoning is contextual and
     * priority-driven, way beyond the scope of DL reasoning. For the time being, this function is expected to
     * hard-code the majority of the resolution rules, including ¶as a minimum those summarized below. Stubs
     * exist for an experimental extension strategy based on {@link ObservationStrategy} but for the time
     * being it's not specified or used. As we're talking about reproducible science, I do NOT think that this
     * is a place for machine-learned correlative inference.</p>
     *
     * <h3>Direct observation</h3>
     *
     * <p>These considerations apply to the direct observation of an observable, not handled through
     * alternative strategies:</p>
     *
     * <p>Resolution uses the semantic closure of the concept, choosing, all else being equal, the model that
     * resolves the observable whose semantic distance to the observable being resolved is closest to 0, with
     * the caveat that models of abstract <em>main</em> observables are illegal, so catch-alls like
     * <code>model Quality</code> cannot be written. The ability of using resolvers or instantiators for the
     * main observable that have semantic distance > 0 could be questioned, and may be a configurable resolver
     * option along with the other priorities. But in general, resolution by semantic distance should catch
     * the least generic models analyzing the structure of the observable. This should always apply for
     * concepts used as the arguments of operators, both unary or binary. So for example,
     * <code>distance to Mountain</code> should catch <code>distance to Subject</code> (or, better,
     * <code>distance to
     * Geolocated Subject</code>), which is legal due to the operator, if one is present and nothing more
     * specific is available. This enables resolution with operators without requiring any specific
     * handling.</p>
     *
     * <p>The direct observation of a direct observable with concrete traits should always check if instances
     * of the base type have been observed already (i.e. the scope contains an observation of the base type)
     * and see if the base trait(s) has been resolved previously <em>within</em> the instances. If so, the
     * result is present and the query is resolved through a RESOLVED strategy, which simply produces the
     * observation group ("folder") containing the classified instances.</p>
     *
     * <p>After each new observation, the emergence detector must check for new matching patterns and resolve
     * whatever configuration, relationship, subject or event has emerged, also installing the correspondent
     * <code>change in Configuration</code> with the configuration triggers as dependencies. Configurations
     * such as networks will be influenced by the relevant observation groups, which change when instances are
     * added, removed or modified (e.g. through classification and characterization).</p>
     *
     * <h3>Alternative strategies</h3>
     *
     * <p> Current list of observation strategy rules by observable beyond the direct observation, waiting
     * for actual documentation:</p>
     *
     * <dl>
     *
     *     <dt>Concrete Quality or resolution of direct observable</dt>
     *     <dd>Last strategy should be the resolvable within its generalized natural context (concept
     *     pattern, e.g.
     *     <code>geography:Elevation within any earth:Location</code>) plus aggregator</dd>
     *     <dt>Direct observable O without traits</dt>
     *     <dd>Instantiate O, then defer resolution of all instances by adding resolution observable after
     *     instantiator</dd>
     *     <dt>Direct observable O with concrete traits (see above for direct strategy)</dt>
     *     <dd>Instantiate O without traits, then classify Os according to base traits of all traits
     *     (cartesian
     *     product),
     *     deactivate any not matching unless previously observed, then defer resolution of all classified
     *     instances</dd>
     *     <dt>Abstract trait T of direct observable O</dt>
     *     <dd>Proceed like Direct observable O with concrete traits, limiting resolution to those
     *     classified as T</dd>
     *     <dt>Quality Q with abstract trait R</dt>
     *     <dd>
     *         <dl><dt>If R is a role and role resolution is set in the scope (external setting)
     *         :</dt><dd>Resolve
     *         each Rx
     *         of Q where Rx is each one of the resolved roles</dd></dl>
     *         <dl><dt>Otherwise:</dt><dd>Resolve R of Q into set of
     *  *         <code>Rx Q</code> where Rx is each one of the resolved roles for R, then d, then defer
     *  resolution of
     *  each <code>Rx O</code></dd></dl>
     *     </dd>
     *     <dt>Direct observable O with abstract trait R</dt>
     *     <dd>
     *         <dl><dt>If R is a role and role resolution is set in the scope (external setting)
     *         :</dt><dd>Instantiate O,
     *         then classify according to  R of O, then defer characterization of each resulting
     *         <code>Rx O</code> where Rx is each one of the resolved roles for R</dd></dl>
     *         <dl><dt>If role is not resolved by the scope:</dt><dd> Instantiate <code>O</code> followed by
     *         classification
     *              <code>R of O</code>, then defer resolution of each <code>Rx O</code></dd></dl>
     *     </dd>
     *
     *     <dt>Quality Q with concrete trait T</dt><dd>Resolve <code>Q</code> followed by characterization
     *     <code>T of Q</code></dd>
     *
     *     <dt>Instantiation of Relationship R</dt><dd>Instantiate source and, if different, target of R,
     *     most specific
     *     first, then instantiate R.</dd>
     *
     *     <dt>Quality Q during event E</dt><dd>Resolve Q with temporal extension (including its change) in
     *     the context
     *     of the context of E, instantiate E and add an aggregator that only measures the quality within
     *     the E temporal
     *     span. This enables the correct actions for individually classified concrete events, such as
     *     March.</dd>
     *
     *     <dt>Any process, including <code>change in quality Q</code></dt><dd>Must ensure that the initial
     *     conditions
     *     for all Qs that are <code>affected</code> by the process but not <code>created</code> by it are
     *     observed,
     *     i.e.
     *     the models must also resolve and depend on the qualities themselves, unless they are qualities
     *     <code>created</code> by the process. Note that <code>change in Q</code> affects but does not
     *     create Q. These
     *     resolutions precede the actual resolution of P. Another strategy, if P is not resolved directly
     *     and Q is
     *     quantifiable, is to use instead of resolution of P (after any added  dependencies) the
     *     resolution of
     *     <code>change rate of Q</code> and add an integrator to produce the change.</dd>
     *
     *     <dt>Quality observables with value operators</dt><dd>Must resolve the main quality without
     *     operators,  any
     *     observables used as arguments for the value operators, plus the contextualizers that perform the
     *     filtering</dd>
     *
     *     <dt><code>changed Quality</code></dt><dd>Resolve Quality with temporal extension, then insert
     *     event detector
     *     (not sure if this can be looked up using generics)</dd>
     *
     *     <dt>Observables with more specialized stated inherency than the context</dt><dd>These
     *     observables specify
     *     <code>of</code> to explicitly specialize the context, Resolve the inherency and defer the
     *     observable, without the explicit inherency if any, to within each instance. This should also be
     *     triggered if
     *     only models <code>within</code> a speciaiized inherent are found. This situation can also happen
     *     if a model
     *     that only explicitly resolves <code>within</code> a specialized context is chosen.</dd>
     */
    List<ObservationStrategy> inferStrategies(Observable observable, ContextScope scope);

    /**
     * Entry point of the assisted semantic search behind interactive concept definition. If the request has a
     * new searchId, start a new SemanticExpression and keep it until timeout or completion.
     *
     * @param request
     */
    SemanticSearchResponse semanticSearch(SemanticSearchRequest request);

    /**
     * Replace conceptual components as requested in the conceptual expression defining the concept and return
     * the result.
     *
     * @param original
     * @param replacements
     * @return
     */
    Concept replaceComponent(Concept original, Map<Concept, Concept> replacements);

    /**
     * Send a build strategy constructed through a builder and return the result as a concept.
     * <p>
     * TODO this must return a response with inspectable fields to fulfill all the Builder contract.
     *
     * @param builder
     * @return
     */
    Concept buildConcept(ObservableBuildStrategy builder);

    /**
     * Send a build strategy constructed through a builder and return the result as an observable.
     * <p>
     * TODO this must return a response with inspectable fields to fulfill all the Builder contract.
     *
     * @param builder
     * @return
     */
    Observable buildObservable(ObservableBuildStrategy builder);

    /**
     * Administration of a semantic server includes loading specific knowledge and defining the
     * configuration.
     *
     * @author Ferd
     */
    interface Admin {

        /**
         * Load all usable knowledge from the ontologies returned by a resources server. The ontologies are
         * assumed in correct load order, consistent and complete. This must be reentrant, enabling the bulk
         * substitution of the entire knowledge base in one shot.
         *
         * @param worldview
         * @param scope     admin user scope to report and validate
         * @return a {@link ResourceSet} including the ontologies, observation strategies, each with any
         * notifications created during the load process, with the lexical context set as needed
         */
        ResourceSet loadKnowledge(Worldview worldview, UserScope scope);

        /**
         * When a reasoner is registered with a resources service by an external orchestrator, changes in the
         * worldview are communicated through this endpoint. This call should only be made after ensuring that
         * the ID of the loaded worldview (reported by
         * {@link org.integratedmodelling.klab.api.services.Reasoner.Capabilities#getWorldviewId()} is the
         * same as the one where the changes have been made. The resources to load must be available through
         * the URL of the reasoner service, which must be in the request, and be open for access by the
         * reasoner through its
         * {@link org.integratedmodelling.klab.api.ServicesAPI.RESOURCES#RESOLVE_ONTOLOGY_URN} and
         * {@link
         * org.integratedmodelling.klab.api.ServicesAPI.RESOURCES#RESOLVE_OBSERVATION_STRATEGY_DOCUMENT_URN}.
         * <p>
         * As always with a resource set, the order of loading in the input is assumed to be the correct one,
         * courtesy of the resources service that has produced the input {@link ResourceSet}. The final output
         * of this method should have the same structure of the input ResourceSet, with the addition of any
         * notifications and metadata that parsing into knowledge has produced.
         *
         * @param changes the set of changed ontology and strategy resources with their URLs
         * @param scope   admin user scope to report and validate
         * @return a {@link ResourceSet} including the passed ontologies, observation strategies, each with
         * any notifications created during the load process, with the lexical context set as needed
         */
        ResourceSet updateKnowledge(ResourceSet changes, UserScope scope);

        /**
         * The "port" to ingest an individual concept definition, called by
         * {@link #loadKnowledge(Worldview, UserScope)} (Worldview)}. Provided separately to make it possible
         * for a resolver service to declare individual local concepts, as long as it owns the semantic
         * service. Definition must be made only in terms of known concepts (no forward declaration is
         * allowed), so order of ingestion is critical.
         *
         * @param statement
         * @param scope     admin user scope to report and validate
         * @return
         */
        Concept defineConcept(KimConceptStatement statement, UserScope scope);

        /**
         * Export a namespace as an OWL ontology with all dependencies.
         *
         * @param namespace
         * @return
         */
        boolean exportNamespace(String namespace, File directory);

    }

}
