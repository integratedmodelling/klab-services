package org.integratedmodelling.klab.api.services;

import java.util.Collection;
import java.util.List;

import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;

public interface Reasoner extends KlabService {

    default String getServiceName() {
        return "klab.reasoner.service";
    }

    /**
     * All services publish capabilities and have a call to obtain them. Capabilities may depend on
     * authentication but the endpoint should be publicly available as well.
     * 
     * @author Ferd
     *
     */
    interface Capabilities extends ServiceCapabilities {

        /**
         * If true, the service is local or dedicated to the service that uses it.
         * 
         * @return
         */
        boolean isExclusive();

        /**
         * If true, the user asking for capabilities can use the admin functions. If isExclusive()
         * returns false, using admin functions can be dangerous.
         * 
         * @return
         */
        boolean canWrite();

    }

    /**
     * 
     * @return
     */
    @Override
    Capabilities getCapabilities();

    /**
     * 
     * @param definition
     * @return
     */
    Concept resolveConcept(String definition);

    /**
     * 
     * @param definition
     * @return
     */
    Observable resolveObservable(String definition);

    boolean subsumes(Semantics conceptImpl, Semantics other);

    Collection<Concept> operands(Semantics target);

    Collection<Concept> children(Semantics target);

    Collection<Concept> parents(Semantics target);

    Collection<Concept> allChildren(Semantics target);

    Collection<Concept> allParents(Semantics target);

    Collection<Concept> closure(Semantics target);

    int semanticDistance(Semantics target);

    int semanticDistance(Semantics target, Semantics context);

    Concept coreObservable(Concept first);

    Pair<Concept, List<SemanticType>> splitOperators(Concept concept);

    int assertedDistance(Concept kConcept, Concept t);

    Collection<Concept> roles(Concept concept);

    boolean hasRole(Concept concept, Concept t);

    Concept directContext(Concept concept);

    Concept context(Concept concept);

    Concept directInherent(Concept concept);

    Concept inherent(Concept concept);

    Concept directGoal(Concept concept);

    Concept goal(Concept concept);

    Concept directCooccurrent(Concept concept);

    Concept directCausant(Concept concept);

    Concept directCaused(Concept concept);

    Concept directAdjacent(Concept concept);

    Concept directCompresent(Concept concept);

    Concept directRelativeTo(Concept concept);

    Concept cooccurrent(Concept concept);

    Concept causant(Concept concept);

    Concept caused(Concept concept);

    Concept adjacent(Concept concept);

    Concept compresent(Concept concept);

    Concept relativeTo(Concept concept);

    /**
     * Return all traits, i.e. identities, attributes and realms.
     *
     * @param concept
     * @return
     */
    Collection<Concept> traits(Concept concept);

    /**
     * Return all identities.
     * 
     * @param concept
     * @return identities
     */
    Collection<Concept> identities(Concept concept);

    /**
     * Return all attributes
     * 
     * @param concept
     * @return attributes
     */
    Collection<Concept> attributes(Concept concept);

    /**
     * Return all realms.
     * 
     * @param concept
     * @return realms
     */
    Collection<Concept> realms(Concept concept);

    /**
     * <p>
     * getBaseParentTrait.
     * </p>
     *
     * @param trait
     * @return
     */
    Concept baseParentTrait(Concept trait);

    /**
     * Check if concept k carries the passed trait. Uses is() on all explicitly expressed traits.
     *
     * @param type
     * @param trait
     * @return a boolean.
     */
    boolean hasTrait(Concept type, Concept trait);

    /**
     * Check if concept k carries a trait T so that the passed trait is-a T.
     *
     * @return a boolean.
     */
    boolean hasParentTrait(Concept type, Concept trait);

    /**
     * Like {@link #traits(Concept)} but only returns the traits directly attributed to this
     * concept.
     * 
     * @param concept
     * @return
     */
    Collection<Concept> directTraits(Concept concept);

    /**
     * Like {@link #traits(Concept)} but only returns the traits directly attributed to this
     * concept.
     * 
     * @param concept
     * @return
     */
    Collection<Concept> directRoles(Concept concept);

    Object displayLabel(Semantics concept);

    Object codeName(Semantics concept);

    String style(Concept concept);

    /**
     * Return the Java class of the observation type corresponding to the passed observable.
     *
     * @param observable a {@link org.integratedmodelling.klab.api.knowledge.IObservable} object.
     * @return a {@link java.lang.Class} object.
     */
    Class<? extends Observation> observationClass(Observable observable);

    /**
     * Return the base enum type (quality, subject....) for the passed observable.
     *
     * @param observable
     * @param acceptTraits if true, will return a trait type (which can be the observable of a class
     *        model although it's not an observable per se).
     * @return the enum type
     */
    SemanticType observableType(Observable observable, boolean acceptTraits);

    /**
     * Return the asserted source of the relationship, assuming it is unique. If it is not unique,
     * the result is arbitrary among the possible sources.
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
     * Return the asserted target of the relationship, assuming it is unique. If it is not unique,
     * the result is arbitrary among the possible targets.
     * 
     * @param relationship a relationship concept
     * @return the target. May be null in abstract relationships.
     */
    Concept relationshipTarget(Semantics relationship);

    /**
     * Return all the asserted targets of the relationship.
     * 
     * @param relationship a relationship concept
     * @return the targets. May be empty in abstract relationships.
     */
    Collection<Concept> relationshipTargets(Semantics relationship);

    /**
     * Return the concept this has been asserted to be the negation of, or null.
     * 
     * @param concept
     * @return
     */
    Concept negated(Concept concept);

    /**
     * 
     * @param ret
     * @return
     */
    boolean satisfiable(Semantics ret);

    /**
     * 
     * @param conceptImpl
     * @return
     */
    Semantics domain(Semantics conceptImpl);

    /**
     * Get all the restricted target of the "applies to" specification for this concept.
     *
     * @param main a {@link org.integratedmodelling.klab.api.knowledge.IConcept} object.
     * @return all applicable concepts or an empty collection
     */
    Collection<Concept> applicableObservables(Concept main);

    interface Admin {

        /**
         * Load all usable knowledge from the namespaces included in the passed resource set.
         * 
         * @param resources
         * @return
         */
        boolean loadKnowledge(ResourceSet resources, Scope scope);

        /**
         * The "port" to ingest a wordview, available only to admins. Also makes it possible for the
         * resolver to declare local concepts as long as it owns the semantic service. Definition
         * must be made only in terms of known concepts (no forward declaration is allowed), so
         * order of ingestion is critical.
         * 
         * @param statement
         * @return
         */
        Concept defineConcept(KimConceptStatement statement, Scope scope);

    }

}
