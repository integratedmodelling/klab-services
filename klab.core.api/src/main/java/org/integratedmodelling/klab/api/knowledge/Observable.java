package org.integratedmodelling.klab.api.knowledge;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.mediation.Currency;
import org.integratedmodelling.klab.api.data.mediation.NumericRange;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.data.mediation.ValueMediator;
import org.integratedmodelling.klab.api.exceptions.KIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KValidationException;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * Observables are concepts with additional information that specifies how they
 * may be observed. This additional information may include units of
 * measurement, value ranges, currency, or expressions involving
 * {@link ValueOperator}s and their arguments. Observables may also be
 * explicitly "named" by the user.
 * <p>
 * From the perspective of the k.LAB reasoner and engine, two observables are
 * equal if and only if both the semantic and the observational parts are equal.
 * The stated name does not enter the comparison.
 * <p>
 * The reference name of an observable ({@link #getReferenceName()} is as unique
 * and stable as its full URN but is a valid identifier in k.LAB. It can be used
 * as an unambiguous identifier in code that uses observables.
 * 
 * @author Ferd
 *
 */
public interface Observable extends Semantics {

	enum Resolution {
		/**
		 * Makes the observable specify "any" child or itself, normally excluding the
		 * abstract ones or those with children.
		 */
		Any,
		/**
		 * Makes the observable specify all children and itself, normally excluding the
		 * abstract ones.
		 */
		All,
		/**
		 * Ensures the observable specifies only itself in contexts where it would
		 * normally specify subclasses too.
		 */
		Only
	}

	/**
	 * Conditions stated in the observable that trigger the use of the default
	 * value. Only meaningful if a default value is given.
	 * 
	 * @author Ferd
	 *
	 */
	enum ResolutionException {
		Missing, Nodata, Error
	}

	/**
	 * The observable builder provides a uniform interface to create and declare
	 * concepts that incarnate all the possible features for an observable. The
	 * builder is smart and fast when concepts that already exist due to previous
	 * declarations are requested.
	 * <p>
	 * NOTE: the builder's methods should return the same builder, not a child
	 * builder. This means that, for example, of(concept).withTrait(trait) will
	 * apply the trait to the <em>main</em> concept, not the inherent one. In most
	 * programmatical applications, this is the desired behavior for fluent
	 * observable specification and modification. We provide an ObservableComposer
	 * which acts as a stateful builder and implements the alternative behavior and
	 * validates each new specification against semantic constraints, meant to be
	 * used with interactive applications that build concepts incrementally.
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

		/**
		 * @param context
		 * @return the same builder this was called on, for chaining calls
		 */
		Builder within(Concept context);

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
		 * Add roles that become part of the semantics of the observable (Role Trait ...
		 * Observable)
		 * 
		 * @param role
		 * @return the same builder this was called on, for chaining calls
		 */
		Builder withRole(Concept role);

		/**
		 * Transform the original concept into its equivalent filtered by the passed
		 * semantic operator. For example, transform an original event into its
		 * probability by passing SemanticOperator.PROBABILITY. If the operator implies
		 * additional operands (for example a ratio) these should be passed after the
		 * semantic type. This one transforms the concept in the builder right away,
		 * leaving nothing to do for build() but return the transformed concept, unless
		 * more build actions are called after it. If the original concept cannot be
		 * transformed into the specified one, build() will return an informative
		 * exception, but no error will be reported when the method is called. The
		 * getErrors() call will report the exceptions accumulated if necessary.
		 * 
		 * @param type
		 * @param participants
		 * @return the same builder this was called on, for chaining calls
		 * @throws KValidationException
		 */
		Builder as(UnarySemanticOperator type, Concept... participants) throws KValidationException;

		/**
		 * Add traits to the concept being built. Pair with (@link
		 * {@link #withTrait(Collection)} as Java is WriteEverythingTwice, not
		 * DontRepeatYourself.
		 *
		 * @param concepts
		 * @return the same builder this was called on, for chaining calls
		 */
		Builder withTrait(Concept... concepts);

		/**
		 * Add traits to the concept being built. Pair with (@link
		 * {@link #withTrait(IConcept...)} as Java is WriteEverythingTwice, not
		 * DontRepeatYourself.
		 * 
		 * @param concepts
		 * @return the same builder this was called on, for chaining calls
		 */
		Builder withTrait(Collection<Concept> concepts);

		/**
		 * Remove traits or roles from the concept being built. Do nothing if the
		 * concept so far does not have those traits or roles. Pair with (@link
		 * {@link #without(IConcept...)} as Java is WriteEverythingTwice, not
		 * DontRepeatYourself.
		 * 
		 * @param concepts
		 * @return the same builder this was called on, for chaining calls
		 */
		Builder without(Collection<Concept> concepts);

		/**
		 * Remove traits or roles from the concept being built. Do nothing if the
		 * concept so far does not have those traits or roles. Pair with (@link
		 * {@link #without(Collection)} as Java is WriteEverythingTwice, not
		 * DontRepeatYourself.
		 *
		 * @param concepts
		 * @return the same builder this was called on, for chaining calls
		 */
		Builder without(Concept... concepts);

		/**
		 * Build the concept (if necessary) as specified in the configured ontology. If
		 * the concept as specified already exists, just return it.
		 * 
		 * @return the built concept
		 * @throws KValidationException
		 */
		Concept buildConcept() throws KValidationException;

		/**
		 * Build an observable using the observable-specific options (currency, unit,
		 * classification and detail types). Use after constructing from an observable
		 * using {@link IObservable#getBuilder()}.
		 * 
		 * @return the built concept
		 * @throws KValidationException
		 */
		Observable buildObservable() throws KValidationException;

		/**
		 * Return any exceptions accumulated through the building process before build()
		 * is called. If build() is called when getErrors() returns a non-empty
		 * collection, it will throw an exception collecting the messages from all
		 * exception in the list.
		 * 
		 * @return any errors accumulated
		 */
		Collection<Notification> getNotifications();

//        /**
//         * Use this to pass a declaration being parsed and set up a monitor so that logically
//         * inconsistent declarations can be reported.
//         * 
//         * @param declaration (may be null)
//         * @return the same builder this was called on, for chaining calls
//         */
//        Builder withDeclaration(KimConcept declaration);

		/**
		 * 
		 * @param cooccurrent
		 * @return
		 */
		Builder withCooccurrent(Concept cooccurrent);

		/**
		 * 
		 */
		Builder withAdjacent(Concept adjacent);

		/**
		 * 
		 * @param concepts
		 * @return
		 */
		Builder withoutAny(Collection<Concept> concepts);

		/**
		 * 
		 * @param type
		 * @return
		 */
		Builder withoutAny(SemanticType... type);

		/**
		 * 
		 * @param concepts
		 * @return
		 */
		Builder withoutAny(Concept... concepts);

		/**
		 * 
		 * @param unit
		 * @return
		 */
		Builder withUnit(Unit unit);

		/**
		 * 
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
		 * After any of the "without" functions get called, this can be checked on the
		 * resulting builder to see what exactly was removed.
		 * 
		 * @return
		 */
		Collection<Concept> getRemoved();

		/**
		 * 
		 * @param source
		 * @param target
		 * @return
		 */
		Builder linking(Concept source, Concept target);

		/**
		 * Set the stated name for the observable, which will shadow the read-only
		 * "given" name based on the semantics (and make it inaccessible). The read-only
		 * reference name (uniquely linked to the semantics) remains unaltered.
		 * 
		 * @param name
		 * @return
		 */
		Builder named(String name);

		/**
		 * Set the flag that signifies distributed inherency (of each).
		 * 
		 * @param ofEach
		 * @return
		 */
		Builder withDistributedInherency(boolean ofEach);

		/**
		 * Remove any value operators
		 * 
		 * @return
		 */
		Builder withoutValueOperators();

		/**
		 * Tags the classifier of an abstract attribute as targeting a specific concrete
		 * attribute, so that any classified objects that won't have that specific
		 * attribute can be recognized as irrelevant to this observation and hidden.
		 * 
		 * @param targetPredicate
		 * @return
		 */
		Builder withTargetPredicate(Concept targetPredicate);

		/**
		 * Set the observable resulting from buildObservable() as optional.
		 * 
		 * @param optional
		 * @return
		 */
		Builder optional(boolean optional);

		/**
		 * Remove all the elements <em>directly</em> stated in the current concept
		 * corresponding to the passed role, if existing, and return a builder for the
		 * concept without them.
		 * 
		 * @param roles
		 * @return
		 */
		Builder without(SemanticRole... roles);

		/**
		 * Set the temporal inherency for the occurrent observable we specify. Does not
		 * change the semantics.
		 * 
		 * @param concept
		 * @return
		 */
		Builder withTemporalInherent(Concept concept);

		/**
		 * Add the dereified attribute to the observable. Will only affect the
		 * computations built from it after it's resolved.
		 * 
		 * @param dereifiedAttribute
		 * @return
		 */
		Builder withDereifiedAttribute(String dereifiedAttribute);

//        /**
//         * Call after {@link #buildConcept()} or {@link #buildObservable()} to check if any change
//         * to the ontologies were made. Returns false if the concept expression requested was
//         * already available.
//         * 
//         * @return
//         */
//        boolean axiomsAdded();

		// /**
		// * Set the dereified status to true, so that the observable can be recognized
		// as
		// * being "virtual" and not linked to a model.
		// *
		// * @return
		// */
		// Builder setDereified();

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
		 * Add an inline value to the observable (will check with the IArtifact.Type of
		 * the observable at build).
		 * 
		 * @param value
		 * @return
		 */
		Builder withInlineValue(Object value);

		/**
		 * 
		 * @param defaultValue
		 * @return
		 */
		Builder withDefaultValue(Object defaultValue);

		/**
		 * 
		 * @param resolutionException
		 * @return
		 */
		Builder withResolutionException(ResolutionException resolutionException);

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
		 * Define the resolution type for the observable.
		 * 
		 * @param only
		 * @return
		 */
		Builder withResolution(Resolution only);

		/**
		 * Add an annotation to the result observable.
		 * 
		 * @param annotation
		 * @return
		 */
		Builder withAnnotation(Annotation annotation);

		// /**
		// * TODO check if still used
		// *
		// * @param global
		// * @return
		// */
		// Builder global(boolean global);
		//
		// /**
		// * Set the URL for the observable when it comes from a k.IM specification.
		// Only
		// * use with full awareness.
		// *
		// * @param uri
		// * @return
		// */
		// Builder withUrl(String uri);

	}

	/**
	 * Return a builder to operate on this observable. A scope must be passed that
	 * references a valid reasoner.
	 * 
	 * @return
	 */
	Builder builder(Scope scope);

	/**
	 * 
	 * @return
	 */
	Concept getSemantics();

	/**
	 * 
	 * @return
	 */
	Unit getUnit();

	/**
	 * 
	 * @return
	 */
	Currency getCurrency();

	/**
	 * 
	 * @return
	 */
	NumericRange getRange();

	/**
	 * 
	 * @return
	 */
	Collection<Pair<ValueOperator, Object>> getValueOperators();

	// /**
	// *
	// * @return
	// */
	// Collection<Concept> abstractPredicates();

	/**
	 * Each observable must be able to quickly assess the type of the description
	 * (observation activity) that will produce an IObservation of it. This is also
	 * used to instantiate storage for states. This is also a key in the equals()
	 * and hashcode functions, so that direct observables for instantiation and
	 * resolution are differentiated.
	 *
	 * @return the necessary observation type
	 */
	DescriptionType getDescriptionType();

	/**
	 * Return the type of the artifact correspondent to an observation of this
	 * observable.
	 * 
	 * @return the artifact type.
	 */
	Artifact.Type getArtifactType();

	/**
	 * The stated name is either null or whatever was given in the 'named' clause,
	 * and will never be modified or redefined. It is meant to preserve the original
	 * name to capture references in models that are derived from others.
	 * 
	 * @return the stated name of this observable.
	 */
	String getStatedName();

	/**
	 * Return any mediator in the state: unit, currency or range. These are also
	 * returned separately by other methods if we need to discriminate.
	 * 
	 * @return
	 */
	ValueMediator mediator();

	/**
	 * Not null if the (quality) observable has been given a pre-observed value.
	 * 
	 * @return
	 */
	Literal getValue();

	// /**
	// * The context type, direct or indirect, and revised according to the stated
	// * inherency (will be reverted to null if the indirect context is X and the
	// * concept is <this> of X). The revision only applies to observables and does
	// * not affect the underlying semantics.
	// *
	// * @return the context type
	// */
	// Concept context();
	//
	// /**
	// * The inherent type, direct or indirect.
	// *
	// * @return the inherent type
	// */
	// Concept inherent();
	//
	// /**
	// * An occurrent observable may be temporally inherent to an event, i.e. it
	// will
	// * happen during each instance of it. Specified by 'during each' in observable
	// * syntax.
	// *
	// * @return
	// */
	// Concept temporalInherent();

	/**
	 * If a default value was defined for a quality observable, it is returned here.
	 * It will be applied according to the stated resolution exceptions and the
	 * optional status.
	 * 
	 * @return
	 */
	Literal getDefaultValue();

	/**
	 * Resolution exceptions linked to the use of a stated default value.
	 * 
	 * @return
	 */
	Collection<ResolutionException> getResolutionExceptions();

	/**
	 * A generic observable expects to be resolved extensively - i.e., all the
	 * subtypes, leaving the base type last if the subtypes don't provide full
	 * coverage. This subsumes the abstract nature of the observable concept, but
	 * may also be true in dependency observables, which may explicitly ask to be
	 * generic even if not abstract ('any' modifier), or result from an abstract
	 * clause (e.g. 'during <abstract event type>').
	 *
	 * @return true if generic
	 */
	boolean isGeneric();

	/**
	 * True if the observable was declared optional. This can only happen in model
	 * dependencies and for the observables of acknowledged subjects.
	 *
	 * @return optional status
	 */
	boolean isOptional();

	/**
	 * If this observable is the subjective point of view of a subject, return that
	 * subject. A null return value implies the observer is the owner of the
	 * session, i.e. what we can most legitimately call the "objective" observer for
	 * the observable.
	 * 
	 * @return
	 */
	DirectObservation getObserver();

	/**
	 * Abstract status of an observable may be more involved than just the abstract
	 * status of the main type, although in most cases that will be the result.
	 * 
	 * @return
	 */
	boolean isAbstract();

	// /**
	// * Globalized observables have "all" prepended and are used in classifiers and
	// * other situations (but never in models) to indicate that all levels of the
	// * subsumed asserted hierarchy should be considered, including abstract ones.
	// *
	// * @return
	// */
	// boolean isGlobal();

	/**
	 * If a resolution was specified, return it. If not, return null - the default
	 * resolution will depend on the context of use, and will be ignored in most
	 * models.
	 * 
	 * @return
	 */
	Resolution getResolution();

	/**
	 * Return any role picked up during resolution for this observable. This happens
	 * when the observable has been resolved from a generic dependency on the role,
	 * which may have been defined by the session or implied during the resolution
	 * of an upstream process or direct observable.
	 * <p>
	 * The roles returned here are not part of the observable's semantics and only
	 * apply to it in the specific resolution and contextualization scope.
	 * 
	 * @return
	 */
	Collection<Concept> getContextualRoles();

	// /**
	// * Complements the equivalent {@link IConcept#resolves(IConcept, IConcept)}
	// with
	// * a check on value operators and other possible differences.
	// *
	// * @param other
	// * @param context
	// * @return
	// */
	// boolean resolves(Observable other, Concept context);

	// /**
	// * Return any abstract identity or role that are set in this observable, and
	// * will need to be resolved to concrete ones before the observable can be
	// * resolved. This will return an empty set if the observable is generic, as
	// that
	// * is handled differently.
	// * <p>
	// * For now abstract roles are always returned, and abstract identities are
	// * returned only if they are required by the observable ('requires identity
	// * ....'). This prevents unwanted resolutions of abstract predicates that may
	// be
	// * used as tags only: the "need" for identification must be explicitly stated.
	// *
	// * @return
	// */
	// Collection<Concept> getAbstractPredicates();

	/**
	 * If the observable results from resolving another with abstract predicates,
	 * return the mapping of abstract -> concrete made by the resolver. This enables
	 * reconstructing the original abstract observable by replacing the concept
	 * after translating it
	 * ({@link IConceptService#replaceComponent(IConcept, Map)}) using the reverse
	 * mapping of the result.
	 * 
	 * @return
	 */
	Map<Concept, Concept> getResolvedPredicates();

	// /**
	// * If true, the observable is for a dereifying observation, which just merges
	// * the results of observations of inherent sub-contexts (e.g. runoff of
	// * watershed, within region). Actuators for those observations aren't
	// scheduled
	// * and may be treated differently.
	// *
	// * @return true if dereified
	// */
	// boolean isDereified();

	/**
	 * // * If true, this observable is explicitly declaring a context that is a
	 * subclass // * of the "natural" context of the primary observable. For
	 * example, "X within // * RiverBasin" when the natural context (declared for X)
	 * is "Region". This is // * used to speed search for alternative explanations
	 * that require distributing // * calculations across different objects, only
	 * done if the natural observation // * is not possible. These should only be
	 * used as the observables of models, with // * full knowledge of the drawbacks
	 * (i.e., X must be observable in the context // * and the observation within X
	 * must fully cover the observation in the natural // * context) and the flag
	 * won't be set if the same specification is given in a // * semantic
	 * declaration. // * // * @return //
	 */
	// boolean isSpecialized();
	//
	// /**
	// *
	// * @return
	// */
	// Observable getDeferredTarget();
	//
	// /**
	// *
	// * @return
	// */
	// Observable getIncarnatedAbstractObservable();
	//
	// /**
	// *
	// * @return
	// */
	// boolean isMustContextualizeAtResolution();
	//
	// /**
	// *
	// * @return
	// */
	// String getUrl();

	/**
	 * 
	 * @return
	 */
	String getDereifiedAttribute();

	// /**
	// *
	// * @return
	// */
	// Concept getTemporalInherent();

	/**
	 * 
	 * @return
	 */
	boolean isDistributedInherency();

	// /**
	// *
	// * @return
	// */
	// Concept getTargetPredicate();

//	/**
//	 * This should create a copy of the observable with a transient field containing
//	 * the knowledge object passed (a {@link Model} or an {@link Instance}), which
//	 * resolves the observable.
//	 * 
//	 * @param resolvable
//	 * @return
//	 */
//	public Observable resolvedWith(Knowledge resolvable);
//
//	/**
//	 * Return any object set into this observable using
//	 * {@link #resolvedWith(Knowledge)}.
//	 * 
//	 * @return
//	 */
//	public Knowledge resolving();

	public static Observable promote(Concept concept) {
		Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
		if (configuration == null) {
			throw new KIllegalStateException("k.LAB environment not configured to promote a concept to observable");
		}
		return configuration.promoteConceptToObservable(concept);
	}

}
