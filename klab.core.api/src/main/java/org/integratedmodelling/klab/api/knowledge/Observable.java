package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.mediation.Currency;
import org.integratedmodelling.klab.api.data.mediation.NumericRange;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.data.mediation.ValueMediator;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * Observables are concepts with additional information that specifies how they may be observed. This
 * additional information may include units of measurement, value ranges, currency, or expressions involving
 * {@link ValueOperator}s and their arguments. Observables may also be explicitly "named" by the user.
 * <p>
 * From the perspective of the k.LAB reasoner and engine, two observables are equal if and only if both the
 * semantic and the observational parts are equal. The stated name does not enter the comparison.
 * <p>
 * The reference name of an observable ({@link #getReferenceName()} is as unique and stable as its full URN
 * but is a valid identifier in k.LAB. It can be used as an unambiguous identifier in code that uses
 * observables.
 *
 * @author Ferd
 */
public interface Observable extends Semantics, Resolvable {

    /**
     * Conditions stated in the observable that trigger the use of the default value. Only meaningful if a
     * default value is given.
     *
     * @author Ferd
     */
    enum ResolutionDirective {
        Missing, Nodata, Error
    }

    /**
     * The observable builder provides a uniform interface to create and declare concepts that incarnate all
     * the possible features for an observable. The builder is smart and fast when concepts that already exist
     * due to previous declarations are requested.
     * <p>
     * NOTE: the builder's methods should return the same builder, not a child builder. This means that, for
     * example, of(concept).withTrait(trait) will apply the trait to the <em>main</em> concept, not the
     * inherent one. In most programmatical applications, this is the desired behavior for fluent observable
     * specification and modification. We provide an ObservableComposer which acts as a stateful builder and
     * implements the alternative behavior and validates each new specification against semantic constraints,
     * meant to be used with interactive applications that build concepts incrementally.
     *
     * @author ferdinando.villa
     */
    interface Builder extends Serializable {

        /**
         * Add an inherent type to the concept built so far.
         *
         * @param inherent
         * @return the same builder this was called on, for chaining calls
         */
        Builder of(Concept inherent);

        /**
         * @param compresent
         * @return the same builder this was called on, for chaining calls
         */
        Builder with(Concept compresent);

        //        /**
        //         * @param context
        //         * @return the same builder this was called on, for chaining calls
        //         */
        //        Builder within(Concept context);

        /**
         * @param goal
         * @return the same builder this was called on, for chaining calls
         */
        Builder withGoal(Concept goal);

        /**
         * @param causant
         * @return the same builder this was called on, for chaining calls
         */
        Builder from(Concept causant);

        /**
         * @param caused
         * @return the same builder this was called on, for chaining calls
         */
        Builder to(Concept caused);

        /**
         * Add roles that become part of the semantics of the observable (Role Trait ... Observable)
         *
         * @param role
         * @return the same builder this was called on, for chaining calls
         */
        Builder withRole(Concept role);

        /**
         * Transform the original concept into its equivalent filtered by the passed semantic operator. For
         * example, transform an original event into its probability by passing SemanticOperator.PROBABILITY.
         * If the operator implies additional operands (for example a ratio) these should be passed after the
         * semantic type. This one transforms the concept in the builder right away, leaving nothing to do for
         * build() but return the transformed concept, unless more build actions are called after it. If the
         * original concept cannot be transformed into the specified one, build() will return an informative
         * exception, but no error will be reported when the method is called. The getErrors() call will
         * report the exceptions accumulated if necessary.
         *
         * @param type
         * @param participants
         * @return the same builder this was called on, for chaining calls
         * @throws KlabValidationException
         */
        Builder as(UnarySemanticOperator type, Concept... participants) throws KlabValidationException;

        /**
         * Change the description type. Due to ontological constraints, basically only useful to swap
         * {@link DescriptionType#ACKNOWLEDGEMENT} and {@link DescriptionType#INSTANTIATION} on substantials.
         *
         * @param descriptionType
         * @return
         */
        Builder as(DescriptionType descriptionType);

        /**
         * Add traits to the concept being built. Pair with (@link {@link #withTrait(Collection)} as Java is
         * WriteEverythingTwice, not DontRepeatYourself.
         *
         * @param concepts
         * @return the same builder this was called on, for chaining calls
         */
        Builder withTrait(Concept... concepts);

        /**
         * Add traits to the concept being built. Pair with (@link {@link #withTrait(Concept...)} as Java is
         * WriteEverythingTwice, not DontRepeatYourself.
         *
         * @param concepts
         * @return the same builder this was called on, for chaining calls
         */
        Builder withTrait(Collection<Concept> concepts);

        /**
         * Remove traits or roles from the concept being built. Do nothing if the concept so far does not have
         * those traits or roles. Pair with (@link {@link #without(Concept...)} as Java is
         * WriteEverythingTwice, not DontRepeatYourself.
         *
         * @param concepts
         * @return the same builder this was called on, for chaining calls
         */
        Builder without(Collection<Concept> concepts);

        /**
         * Remove traits or roles from the concept being built. Do nothing if the concept so far does not have
         * those traits or roles. Pair with (@link {@link #without(Collection)} as Java is
         * WriteEverythingTwice, not DontRepeatYourself.
         *
         * @param concepts
         * @return the same builder this was called on, for chaining calls
         */
        Builder without(Concept... concepts);

        /**
         * Build the concept (if necessary) as specified in the configured ontology. If the concept as
         * specified already exists, just return it.
         *
         * @return the built concept
         * @throws KlabValidationException
         */
        Concept buildConcept() throws KlabValidationException;

        /**
         * Build an observable using the observable-specific options (currency, unit, classification and
         * detail types). Use after constructing from an observable using {@link Observable#builder(Scope)}.
         *
         * @return the built concept
         * @throws KlabValidationException
         */
        Observable build() throws KlabValidationException;

        /**
         * Return any exceptions accumulated through the building process before build() is called. If build()
         * is called when getErrors() returns a non-empty collection, it will throw an exception collecting
         * the messages from all exception in the list.
         *
         * @return any errors accumulated
         */
        Collection<Notification> getNotifications();

        /**
         * @param cooccurrent
         * @return
         */
        Builder withCooccurrent(Concept cooccurrent);

        /**
         *
         */
        Builder withAdjacent(Concept adjacent);

        /**
         * @param concepts
         * @return
         */
        Builder withoutAny(Collection<Concept> concepts);

        /**
         * @param type
         * @return
         */
        Builder withoutAny(SemanticType... type);

        /**
         * @param concepts
         * @return
         */
        Builder withoutAny(Concept... concepts);

        /**
         * @param unit
         * @return
         */
        Builder withUnit(Unit unit);

        /**
         * @param currency
         * @return
         */
        Builder withCurrency(Currency currency);

        /**
         * Value operators are added in the order they are received.
         *
         * @param operator
         * @param valueOperand
         * @return
         */
        Builder withValueOperator(ValueOperator operator, Object valueOperand);

        /**
         * After any of the "without" functions get called, this can be checked on the resulting builder to
         * see what exactly was removed.
         *
         * @return
         */
        Collection<Concept> removed(Semantics result);

        /**
         * @param source
         * @param target
         * @return
         */
        Builder linking(Concept source, Concept target);

        /**
         * Set the stated name for the observable, which will shadow the read-only "given" name based on the
         * semantics (and make it inaccessible). The read-only reference name (uniquely linked to the
         * semantics) remains unaltered.
         *
         * @param name
         * @return
         */
        Builder named(String name);

        //        /**
        //         * Set the flag that signifies distributed inherency (of each).
        //         *
        //         * @param ofEach
        //         * @return
        //         */
        //        Builder withDistributedInherency(boolean ofEach);

        /**
         * Remove any value operators
         *
         * @return
         */
        Builder withoutValueOperators();

        //        /**
        //         * Tags the classifier of an abstract attribute as targeting a specific concrete
        //         attribute, so that any
        //         * classified objects that won't have that specific attribute can be recognized as
        //         irrelevant to this
        //         * observation and hidden.
        //         *
        //         * @param targetPredicate
        //         * @return
        //         */
        //        Builder withTargetPredicate(Concept targetPredicate);

        /**
         * Set the observable resulting from buildObservable() as optional.
         *
         * @param optional
         * @return
         */
        Builder optional(boolean optional);

        /**
         * Remove all the elements <em>directly</em> stated in the current concept corresponding to the passed
         * role, if existing, and return a builder for the concept without them.
         *
         * @param roles
         * @return
         */
        Builder without(SemanticRole... roles);

        /**
         * Set the temporal inherency for the occurrent observable we specify. Does not change the semantics.
         *
         * @param concept
         * @return
         */
        Builder withTemporalInherent(Concept concept);


        /**
         * Set both the name and the reference name, to preserve a previous setting
         *
         * @param name
         * @param referenceName
         * @return
         */
        Builder named(String name, String referenceName);

        /**
         * Pass the unit as a string (also checks for correctness at build)
         *
         * @param unit
         * @return
         */
        Builder withUnit(String unit);

        /**
         * | Pass a currency as a string (also check for monetary value at build)
         *
         * @param currency
         * @return
         */
        Builder withCurrency(String currency);

        /**
         * Add an inline value to the observable (will check with the IArtifact.Type of the observable at
         * build).
         *
         * @param value
         * @return
         */
        Builder withInlineValue(Object value);

        /**
         * @param defaultValue
         * @return
         */
        Builder withDefaultValue(Object defaultValue);

        /**
         * @param resolutionDirective
         * @return
         */
        Builder withResolutionException(ResolutionDirective resolutionDirective);

        /**
         * Add a numeric range (check that the artifact type is numeric at build)
         *
         * @param range
         * @return
         */
        Builder withRange(NumericRange range);

        /**
         * Make this observable generic or not
         *
         * @param generic
         * @return
         */
        Builder generic(boolean generic);

        /**
         * Make this observable collective. Must be a substantial.
         *
         * @param collective
         * @return
         */
        Builder collective(boolean collective);

        /**
         * Add an annotation to the result observable.
         *
         * @param annotation
         * @return
         */
        Builder withAnnotation(Annotation annotation);

        /**
         * Advanced. Use ONLY internally!
         *
         * @param s
         * @return
         */
        Builder withReferenceName(String s);
    }

    /**
     * Return a builder to operate on this observable. A scope must be passed that references a valid
     * reasoner.
     *
     * @return
     */
    Builder builder(Scope scope);

    /**
     * @return
     */
    Concept getSemantics();

    /**
     * @return
     */
    Unit getUnit();

    /**
     * @return
     */
    Currency getCurrency();

    /**
     * @return
     */
    NumericRange getRange();

    /**
     * @return
     */
    List<Pair<ValueOperator, Object>> getValueOperators();

    /**
     * Each observable must be able to quickly assess the type of the description (observation activity) that
     * will produce an IObservation of it. This is also used to instantiate storage for states. This is also a
     * key in the equals() and hashcode functions, so that direct observables for instantiation and resolution
     * are differentiated.
     *
     * @return the necessary observation type
     */
    DescriptionType getDescriptionType();

    /**
     * Check if the description type equals or is subsumed by the passed one.
     *
     * @param descriptionType
     * @return true if subsumed
     */
    boolean is(DescriptionType descriptionType);

    /**
     * Return the type of the artifact correspondent to an observation of this observable.
     *
     * @return the artifact type.
     */
    Artifact.Type getArtifactType();

    /**
     * The stated name is either null or whatever was given in the 'named' clause, and will never be modified
     * or redefined. It is meant to preserve the original name to capture references in models that are
     * derived from others.
     *
     * @return the stated name of this observable.
     */
    String getStatedName();

    /**
     * Return any mediator in the state: unit, currency or range. These are also returned separately by other
     * methods if we need to discriminate.
     *
     * @return
     */
    ValueMediator mediator();

    /**
     * Not null if the (quality) observable has been given a pre-observed value.
     *
     * @return
     */
    Object getValue();

    /**
     * If a default value was defined for a quality observable, it is returned here. It will be applied
     * according to the stated resolution exceptions and the optional status.
     *
     * @return
     */
    Object getDefaultValue();

    /**
     * What to do when resolution fails, allowing to specify default values or move on with other optional
     * actions instead of simply failing. Linked to stated metadata with stated default values or actions.
     *
     * @return
     */
    Collection<ResolutionDirective> getResolutionDirectives();


    /**
     * True if the observable was declared optional. This can only happen in model dependencies and for the
     * observables of acknowledged subjects. In this case resolution may fail without consequences, removing
     * the unresolved observation from the observation tree and leaving the context scope in a consistent
     * state.
     *
     * @return optional status
     */
    boolean isOptional();

    /**
     * If this observable is the subjective point of view of a subject, return the subject's type. This limits
     * the use of the observable to where the observer matches the type.
     *
     * @return
     */
    Concept getObserver();

    /**
     * Abstract status of an observable depends on having an abstract component in a defining place (e.g. not
     * after
     * <code>type of</code>) and/or having non-abstract generic components (introduced by <code>any</code> or
     * another quantifier). {@link #isGeneric()} implies {@link #isAbstract()} but not the other way around.
     * In both cases the abstract/generic components must be resolved in context to concrete components, whose
     * cartesian product is used to incarnate concrete observables for deferred resolution.
     *
     * @return
     */
    boolean isAbstract();

    /**
     * Return all concepts that are generic or abstract within the statement of this observable. Generic means
     * that their getQualifier() returns a non-null qualifier, therefore they must be resolved before the
     * observable is usable. Abstract concepts in semantic roles that make the observable abstract (i.e., not
     * where they're legitimate such as in <code>type of X</code>) are also returned here.
     *
     * @return
     */
    Collection<Concept> getGenericComponents();

    /**
     * If the observable results from specializing a generic/abstract observable, return the pairs of matched
     * generic -> specialized concepts substituted in this instance. The specialized concepts may have been
     * generic or abstract.
     *
     * @return
     */
    Collection<Pair<Concept, Concept>> getSpecializedComponents();

    /**
     * Collective observables have <code>each</code> in front of their declaration and specify the
     * instantiation of substantials. The same observable without <code>each</code> specifies its resolution.
     * when collective observables are the subject of a
     * {@link org.integratedmodelling.klab.api.lang.BinarySemanticOperator}, they imply a "collective",
     * distributing resolution strategy for their observable, possibly resorting to observing qualities in the
     * surrounding context instead of individually observing the values of each observation made.
     *
     * @return
     */
    boolean isCollective();

    public static Observable promote(Concept concept) {
        Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
        if (configuration == null) {
            throw new KlabIllegalStateException("k.LAB environment not configured to promote a concept to " +
                    "observable");
        }
        return configuration.promoteConceptToObservable(concept);
    }

}
