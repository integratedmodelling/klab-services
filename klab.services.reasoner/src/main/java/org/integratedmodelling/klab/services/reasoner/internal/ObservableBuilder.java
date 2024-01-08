package org.integratedmodelling.klab.services.reasoner.internal;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.mediation.Currency;
import org.integratedmodelling.klab.api.data.mediation.NumericRange;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.lang.impl.kim.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.Notification.Level;
import org.integratedmodelling.klab.knowledge.ObservableImpl;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS;
import org.integratedmodelling.klab.services.reasoner.owl.Axiom;
import org.integratedmodelling.klab.services.reasoner.owl.OWL;
import org.integratedmodelling.klab.services.reasoner.owl.Ontology;
import org.integratedmodelling.klab.services.reasoner.owl.QualifiedName;
import org.integratedmodelling.klab.utilities.Utils;

import java.util.*;

public class ObservableBuilder implements Observable.Builder {

    private Scope scope;

    private Ontology ontology;

    private Concept main;
    private String mainId;
    private Set<SemanticType> type = EnumSet.noneOf(SemanticType.class);
    private Concept inherent;
    //    private Concept context;
    private Concept compresent;
    private Concept causant;
    private Concept caused;
    private Concept goal;
    private Concept cooccurrent;
    private Concept adjacent;
    private Concept comparison;
    private Concept relationshipSource;
    private Concept relationshipTarget;
    private boolean optional;
    private String name;
    //    private Concept targetPredicate;
    private Concept temporalInherent;
    private boolean mustContextualize = false;
    private String statedName;
    private String url;

    private List<Concept> traits = new ArrayList<>();
    private List<Concept> roles = new ArrayList<>();
    private List<Concept> removed = new ArrayList<>();
    private List<Pair<ValueOperator, Literal>> valueOperators = new ArrayList<>();
    private List<Notification> notifications = new ArrayList<>();
    private Unit unit;
    private Currency currency;
    private List<Annotation> annotations = new ArrayList<>();
    private String dereifiedAttribute;
    private boolean isTrivial = true;
    private boolean distributedInherency = false;
    private KimConcept declaration;
    private boolean axiomsAdded = false;
    private String referenceName = null;
    String unitStatement;
    String currencyStatement;
    Object inlineValue;
    NumericRange range;
    boolean generic;

    private Literal defaultValue = null;
    private Set<Observable.ResolutionException> resolutionExceptions = EnumSet
            .noneOf(Observable.ResolutionException.class);
    private OWL owl;
    // this gets set to true if a finished declaration is set using
    // withDeclaration() and the
    // builder is merely building it.
    private boolean declarationIsComplete = false;

    // marks the observable to build as dereifying for a resolution of inherents
    private boolean dereified = false;

    // private boolean global;

    private boolean hasUnaryOp;

    private Observable incarnatedAbstractObservable;

    private Observable deferredTarget;
    private DescriptionType descriptionType;

    public static ObservableBuilder getBuilder(Concept concept, Scope scope, OWL owl) {
        return new ObservableBuilder(concept, scope, owl);
    }

    public static ObservableBuilder getBuilder(Observable observable, Scope scope, OWL owl) {
        return new ObservableBuilder(observable, scope, owl);
    }

    public ObservableBuilder(Concept main, Ontology ontology, Scope scope, OWL owl) {
        this.owl = owl;
        this.main = main;
        this.scope = scope;
        this.ontology = ontology;
        this.declaration = getDeclaration(main);
        this.type = main.getType();
    }

    public ObservableBuilder(Concept main, Scope scope, OWL owl) {
        this.owl = owl;
        this.main = main;
        this.scope = scope;
        this.ontology = owl.getOntology(main.getNamespace());
        this.declaration = getDeclaration(main);
        this.type.addAll(main.getType());
    }

    /**
     * Copies all info from the first level of specification of the passed observable. Will retain the
     * original semantics, so it won't separate prefix operators from the original observables: at the moment
     * it will simply collect the traits, roles, and operands of infix operators.
     *
     * @param observable
     */
    public ObservableBuilder(Observable observable, Scope scope, OWL owl) {
        this.owl = owl;
        this.main = owl.reasoner().rawObservable(observable.getSemantics());
        this.scope = scope;
        this.type = this.main.getType();
        this.ontology = owl.getOntology(observable.getSemantics().getNamespace());
//        this.context = owl.reasoner().directContext(observable.getSemantics());
        this.adjacent = owl.reasoner().directAdjacent(observable.getSemantics());
        this.inherent = owl.reasoner().directInherent(observable.getSemantics());
        this.causant = owl.reasoner().directCausant(observable.getSemantics());
        this.caused = owl.reasoner().directCaused(observable.getSemantics());
        this.cooccurrent = owl.reasoner().directCooccurrent(observable.getSemantics());
        this.goal = owl.reasoner().directGoal(observable.getSemantics());
        this.compresent = owl.reasoner().directCompresent(observable.getSemantics());
        this.declaration = getDeclaration(observable.getSemantics());
        // this.mustContextualize = observable.isMustContextualizeAtResolution();
        // this.temporalInherent = observable.temporalInherent();
        this.annotations.addAll(observable.getAnnotations());
        // this.incarnatedAbstractObservable =
        // observable.getIncarnatedAbstractObservable();
        // this.deferredTarget = observable.getDeferredTarget();
        this.defaultValue = observable.getDefaultValue();
        this.resolutionExceptions.addAll(observable.getResolutionExceptions());

        for (Concept role : owl.reasoner().directRoles(observable.getSemantics())) {
            this.roles.add(role);
        }
        for (Concept trait : owl.reasoner().directTraits(observable.getSemantics())) {
            this.traits.add(trait);
        }

        this.isTrivial =
                /*this.context == null &&
                 * */this.adjacent == null && this.inherent == null && this.causant == null
                && this.caused == null && this.cooccurrent == null && this.goal == null && this.compresent == null
                && this.roles.isEmpty() && this.traits.isEmpty();

        // these are only used if buildObservable() is called
        this.unit = observable.getUnit();
        this.currency = observable.getCurrency();
        this.valueOperators.addAll(observable.getValueOperators());
        this.scope = scope;
    }

    private KimConcept getDeclaration(Concept semantics) {
        return scope.getService(ResourcesService.class).resolveConcept(semantics.getUrn());
    }

    public ObservableBuilder(ObservableBuilder other) {

        this.main = other.main;
        this.adjacent = other.adjacent;
        this.causant = other.causant;
        this.caused = other.caused;
        this.comparison = other.comparison;
        this.compresent = other.compresent;
//        this.context = other.context;
        this.inherent = other.inherent;
        this.cooccurrent = other.cooccurrent;
        this.goal = other.goal;
        this.traits.addAll(other.traits);
        this.roles.addAll(other.roles);
        this.ontology = other.ontology;
        this.type = other.type;
        this.declaration = other.declaration;
        this.scope = other.scope;
        this.valueOperators.addAll(other.valueOperators);
        // this.mustContextualize = other.mustContextualize;
        this.annotations.addAll(other.annotations);
        this.temporalInherent = other.temporalInherent;
        this.statedName = other.statedName;
        this.dereified = other.dereified;
        this.incarnatedAbstractObservable = other.incarnatedAbstractObservable;
        this.deferredTarget = other.deferredTarget;
        this.url = other.url;
        this.owl = other.owl;
        this.defaultValue = other.defaultValue;
        this.resolutionExceptions.addAll(other.resolutionExceptions);

        checkTrivial();
    }

    //    @Override
    public ObservableBuilder withDeclaration(KimConcept declaration) {
        this.declaration = (KimConceptImpl) declaration;
        this.declarationIsComplete = true;
        return this;
    }

    @Override
    public Observable.Builder of(Concept concept) {
        this.inherent = concept;
        if (!declarationIsComplete) {
            ((KimConceptImpl) this.declaration).setInherent(getDeclaration(concept));
        }
        isTrivial = false;
        return this;
    }

    @Override
    public Observable.Builder optional(boolean optional) {
        this.optional = optional;
        return this;
    }

//    @Override
//    public Observable.Builder within(Concept concept) {
//        this.context = concept;
//        if (this.declaration != null) {
//            ((KimConceptImpl) this.declaration).setContext(getDeclaration(concept));
//        }
//        isTrivial = false;
//        return this;
//    }

    @Override
    public Observable.Builder withTemporalInherent(Concept concept) {
        this.temporalInherent = concept;
        isTrivial = false;
        return this;
    }

    @Override
    public Observable.Builder to(Concept concept) {
        this.caused = concept;
        if (!declarationIsComplete) {
            ((KimConceptImpl) this.declaration).setCaused(getDeclaration(concept));
        }
        isTrivial = false;
        return this;
    }

    @Override
    public Observable.Builder from(Concept concept) {
        this.causant = concept;
        if (!declarationIsComplete) {
            ((KimConceptImpl) this.declaration).setCausant(getDeclaration(concept));
        }
        isTrivial = false;
        return this;
    }

    @Override
    public Observable.Builder with(Concept concept) {
        this.compresent = concept;
        if (!declarationIsComplete) {
            ((KimConceptImpl) this.declaration).setCompresent(getDeclaration(concept));
        }
        isTrivial = false;
        return this;
    }

    @Override
    public Observable.Builder withRole(Concept concept) {
        if (!concept.is(SemanticType.ROLE)) {
            notifications.add(Notification.of("cannot use concept " + concept + " as a role", Level.Error));
        }
        if (!declarationIsComplete) {
            this.declaration.getRoles().add(getDeclaration(concept));
        }
        this.roles.add(concept);
        isTrivial = false;
        return this;
    }

    @Override
    public Observable.Builder withGoal(Concept goal) {
        this.goal = goal;
        if (!declarationIsComplete) {
            ((KimConceptImpl) this.declaration).setGoal(getDeclaration(goal));
        }
        isTrivial = false;
        return this;
    }

    @Override
    public Observable.Builder withCooccurrent(Concept cooccurrent) {
        this.cooccurrent = cooccurrent;
        if (!declarationIsComplete) {
            ((KimConceptImpl) this.declaration).setCooccurrent(getDeclaration(cooccurrent));
        }
        isTrivial = false;
        return this;
    }

    @Override
    public Observable.Builder withAdjacent(Concept adjacent) {
        this.adjacent = adjacent;
        if (!declarationIsComplete) {
            ((KimConceptImpl) this.declaration).setAdjacent(getDeclaration(adjacent));
        }
        isTrivial = false;
        return this;
    }

    @Override
    public Observable.Builder linking(Concept source, Concept target) {
        this.relationshipSource = source;
        this.relationshipTarget = target;
        isTrivial = false;
        return this;
    }

    @Override
    public Observable.Builder as(UnarySemanticOperator type, Concept... participants) throws KlabValidationException {

        Concept argument = null;
        if (resolveMain()) {
            argument = getArgumentBuilder().buildConcept();
        }

        if (!declarationIsComplete) {
            ((KimConceptImpl) this.declaration).setSemanticModifier(type);
        }

        if (participants != null && participants.length > 0) {
            this.comparison = participants[0];
            if (!declarationIsComplete) {
                ((KimConceptImpl) this.declaration).setComparisonConcept(getDeclaration((participants[0])));
            }
            if (participants.length > 1) {
                throw new KlabValidationException(
                        "cannot handle more than one participant concept in semantic operator");
            }
        }

        if (incarnatedAbstractObservable != null) {
            incarnatedAbstractObservable = incarnatedAbstractObservable.builder(scope).as(type, participants)
                    .build();
        }

        if (argument != null) {

            try {
                switch (type) {
                    case CHANGE:
                        reset(owl.makeChange(argument), type);
                        break;
                    case CHANGED:
                        reset(owl.makeChanged(argument), type);
                        break;
                    case COUNT:
                        reset(owl.makeCount(argument), type);
                        break;
                    case DISTANCE:
                        reset(owl.makeDistance(argument), type);
                        break;
                    case OCCURRENCE:
                        reset(owl.makeOccurrence(argument), type);
                        break;
                    case PRESENCE:
                        reset(owl.makePresence(argument), type);
                        break;
                    case PROBABILITY:
                        reset(owl.makeProbability(argument), type);
                        break;
                    case PROPORTION:
                        reset(owl.makeProportion(argument, this.comparison, false), type);
                        break;
                    case PERCENTAGE:
                        reset(owl.makeProportion(argument, this.comparison, true), type);
                        break;
                    case RATIO:
                        reset(owl.makeRatio(argument, this.comparison), type);
                        break;
                    case RATE:
                        reset(owl.makeRate(argument), type);
                        break;
                    case UNCERTAINTY:
                        reset(owl.makeUncertainty(argument), type);
                        break;
                    case VALUE:
                    case MONETARY_VALUE:
                        reset(owl.makeValue(argument, this.comparison,
                                        type == UnarySemanticOperator.MONETARY_VALUE),
                                type);
                        break;
                    case MAGNITUDE:
                        reset(owl.makeMagnitude(argument), type);
                        break;
                    case LEVEL:
                        reset(owl.makeLevel(argument), type);
                        break;
                    case TYPE:
                        reset(owl.makeType(argument), type);
                        break;
                    default:
                        break;
                }
            } catch (KlabValidationException e) {
                // thrown by the makeXXX functions in case of incompatibility
                scope.error(e.getMessage(), declaration);
            }
        }

        return this;
    }

    @Override
    public Observable.Builder as(DescriptionType descriptionType) {
        this.descriptionType = descriptionType;
        return this;
    }

    /**
     * Copy the builder exactly but revise the declaration so that it does not include the operator.
     *
     * @return a new builder for the concept w/o operator
     */
    private Observable.Builder getArgumentBuilder() {
        ObservableBuilder ret = new ObservableBuilder(this);
        ret.declaration = ((KimConceptImpl) declaration).removeOperator();
        ret.type = declaration.getType();
        return ret;
    }

    private void reset(Concept main, UnarySemanticOperator op) {
        this.main = main;
        this.type = main.getType();
        traits.clear();
        roles.clear();
        unit = null;
        currency = null;
        hasUnaryOp = true;
        comparison = /*context =*/ inherent = /* classifier = downTo = */ caused = compresent = inherent =
                null;
        isTrivial = true;
    }

    @Override
    public Observable.Builder without(Collection<Concept> concepts) {
        return without(concepts.toArray(new Concept[concepts.size()]));
    }

    @Override
    public Observable.Builder without(SemanticRole... roles) {

        Set<SemanticRole> r = EnumSet.noneOf(SemanticRole.class);
        if (roles != null) {
            for (SemanticRole role : roles) {
                r.add(role);
            }
        }

        KimConcept newDeclaration = ((KimConceptImpl) this.declaration).removeComponents(roles);
        ObservableBuilder ret = new ObservableBuilder(owl.reasoner().declareConcept(newDeclaration), scope,
                owl);

        /*
         * copy the rest unless excluded
         */
        if (!r.contains(SemanticRole.UNIT)) {
            ret.unit = unit;
        }
        if (!r.contains(SemanticRole.CURRENCY)) {
            ret.currency = currency;
        }
        if (!r.contains(SemanticRole.VALUE_OPERATOR)) {
            ret.valueOperators.addAll(valueOperators);
        }

        /*
         * for now these have no roles associated
         */
        ret.name = name;
//        ret.targetPredicate = targetPredicate;
        ret.optional = this.optional;
        ret.mustContextualize = mustContextualize;
        ret.annotations.addAll(annotations);
        ret.deferredTarget = deferredTarget;

        return ret;
    }

    @Override
    public Observable.Builder withoutAny(Collection<Concept> concepts) {
        return withoutAny(concepts.toArray(new Concept[concepts.size()]));
    }

    @Override
    public Observable.Builder without(Concept... concepts) {

        ObservableBuilder ret = new ObservableBuilder(this);
        List<SemanticRole> removedRoles = new ArrayList<>();
        for (Concept concept : concepts) {
            Pair<Collection<Concept>, Collection<Concept>> tdelta = copyWithout(ret.traits, concept);
            ret.traits = new ArrayList<>(tdelta.getFirst());
            ret.removed.addAll(tdelta.getSecond());
            for (int i = 0; i < tdelta.getSecond().size(); i++) {
                removedRoles.add(SemanticRole.TRAIT);
            }
            Pair<Collection<Concept>, Collection<Concept>> rdelta = copyWithout(ret.roles, concept);
            ret.roles = new ArrayList<>(rdelta.getFirst());
            ret.removed.addAll(rdelta.getSecond());
            for (int i = 0; i < rdelta.getSecond().size(); i++) {
                removedRoles.add(SemanticRole.ROLE);
            }
//            if (ret.context != null && ret.context.equals(concept)) {
//                ret.context = null;
//                ret.removed.add(concept);
//                removedRoles.add(SemanticRole.CONTEXT);
//            }
            if (ret.inherent != null && ret.inherent.equals(concept)) {
                ret.inherent = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.INHERENT);
            }
            if (ret.adjacent != null && ret.adjacent.equals(concept)) {
                ret.adjacent = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.ADJACENT);
            }
            if (ret.caused != null && ret.caused.equals(concept)) {
                ret.caused = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.CAUSED);
            }
            if (ret.causant != null && ret.causant.equals(concept)) {
                ret.causant = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.CAUSANT);
            }
            if (ret.compresent != null && ret.compresent.equals(concept)) {
                ret.compresent = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.COMPRESENT);
            }
            if (ret.goal != null && ret.goal.equals(concept)) {
                ret.goal = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.GOAL);
            }
            if (ret.cooccurrent != null && ret.cooccurrent.equals(concept)) {
                ret.cooccurrent = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.COOCCURRENT);
            }
            if (ret.temporalInherent != null && owl.reasoner().subsumes(ret.temporalInherent, concept)) {
                ret.temporalInherent = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.TEMPORAL_INHERENT);
            }
        }
        if (ret.removed.size() > 0) {
            List<String> declarations = new ArrayList<>();
            for (Concept r : ret.removed) {
                declarations.add(r.getUrn());
            }
            ret.declaration = ((KimConceptImpl) ret.declaration).removeComponents(declarations, removedRoles);
        }

        ret.checkTrivial();

        return ret;
    }

    @Override
    public Observable.Builder withoutAny(SemanticType... concepts) {

        ObservableBuilder ret = new ObservableBuilder(this);
        List<SemanticRole> removedRoles = new ArrayList<>();
        for (SemanticType concept : concepts) {
            Pair<Collection<Concept>, Collection<Concept>> tdelta = copyWithoutAny(ret.traits, concept);
            ret.traits = new ArrayList<>(tdelta.getFirst());
            ret.removed.addAll(tdelta.getSecond());
            for (int i = 0; i < tdelta.getSecond().size(); i++) {
                removedRoles.add(SemanticRole.TRAIT);
            }
            Pair<Collection<Concept>, Collection<Concept>> rdelta = copyWithoutAny(ret.roles, concept);
            ret.roles = new ArrayList<>(rdelta.getFirst());
            ret.removed.addAll(rdelta.getSecond());
            for (int i = 0; i < tdelta.getSecond().size(); i++) {
                removedRoles.add(SemanticRole.ROLE);
            }
//            if (ret.context != null && ret.context.is(concept)) {
//                ret.removed.add(ret.context);
//                ret.context = null;
//                removedRoles.add(SemanticRole.CONTEXT);
//            }
            if (ret.inherent != null && ret.inherent.is(concept)) {
                ret.removed.add(ret.inherent);
                ret.inherent = null;
                removedRoles.add(SemanticRole.INHERENT);
            }
            if (ret.adjacent != null && ret.adjacent.is(concept)) {
                ret.removed.add(ret.adjacent);
                ret.adjacent = null;
                removedRoles.add(SemanticRole.ADJACENT);
            }
            if (ret.caused != null && ret.caused.is(concept)) {
                ret.removed.add(ret.caused);
                ret.caused = null;
                removedRoles.add(SemanticRole.CAUSED);
            }
            if (ret.causant != null && ret.causant.is(concept)) {
                ret.removed.add(ret.causant);
                ret.causant = null;
                removedRoles.add(SemanticRole.CAUSANT);
            }
            if (ret.compresent != null && ret.compresent.is(concept)) {
                ret.removed.add(ret.compresent);
                ret.compresent = null;
                removedRoles.add(SemanticRole.COMPRESENT);
            }
            if (ret.goal != null && ret.goal.is(concept)) {
                ret.removed.add(ret.goal);
                ret.goal = null;
                removedRoles.add(SemanticRole.GOAL);
            }
            if (ret.cooccurrent != null && ret.cooccurrent.is(concept)) {
                ret.removed.add(ret.cooccurrent);
                ret.cooccurrent = null;
                removedRoles.add(SemanticRole.COOCCURRENT);
            }
            if (ret.temporalInherent != null && ret.temporalInherent.is(concept)) {
                ret.temporalInherent = null;
                ret.removed.add(ret.temporalInherent);
                removedRoles.add(SemanticRole.TEMPORAL_INHERENT);
            }
        }
        if (ret.removed.size() > 0) {
            List<String> declarations = new ArrayList<>();
            for (Concept r : ret.removed) {
                declarations.add(r.getUrn());
            }
            ret.declaration = ((KimConceptImpl) ret.declaration).removeComponents(declarations, removedRoles);
        }

        ret.checkTrivial();

        return ret;

    }

    @Override
    public Observable.Builder withoutAny(Concept... concepts) {

        ObservableBuilder ret = new ObservableBuilder(this);
        List<SemanticRole> removedRoles = new ArrayList<>();
        for (Concept concept : concepts) {
            Pair<Collection<Concept>, Collection<Concept>> tdelta = copyWithoutAny(ret.traits, concept);
            ret.traits = new ArrayList<>(tdelta.getFirst());
            ret.removed.addAll(tdelta.getSecond());
            for (int i = 0; i < tdelta.getSecond().size(); i++) {
                removedRoles.add(SemanticRole.TRAIT);
            }
            Pair<Collection<Concept>, Collection<Concept>> rdelta = copyWithoutAny(ret.roles, concept);
            ret.roles = new ArrayList<>(rdelta.getFirst());
            ret.removed.addAll(rdelta.getSecond());
            for (int i = 0; i < tdelta.getSecond().size(); i++) {
                removedRoles.add(SemanticRole.ROLE);
            }
//            if (ret.context != null && owl.reasoner().subsumes(ret.context, concept)) {
//                ret.context = null;
//                ret.removed.add(concept);
//                removedRoles.add(SemanticRole.CONTEXT);
//            }
            if (ret.inherent != null && owl.reasoner().subsumes(ret.inherent, concept)) {
                ret.inherent = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.INHERENT);
            }
            if (ret.adjacent != null && owl.reasoner().subsumes(ret.adjacent, concept)) {
                ret.adjacent = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.ADJACENT);
            }
            if (ret.caused != null && owl.reasoner().subsumes(ret.caused, concept)) {
                ret.caused = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.CAUSED);
            }
            if (ret.causant != null && owl.reasoner().subsumes(ret.causant, concept)) {
                ret.causant = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.CAUSANT);
            }
            if (ret.compresent != null && owl.reasoner().subsumes(ret.compresent, concept)) {
                ret.compresent = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.COMPRESENT);
            }
            if (ret.goal != null && owl.reasoner().subsumes(ret.goal, concept)) {
                ret.goal = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.GOAL);
            }
            if (ret.cooccurrent != null && owl.reasoner().subsumes(ret.cooccurrent, concept)) {
                ret.cooccurrent = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.COOCCURRENT);
            }
            if (ret.temporalInherent != null && owl.reasoner().subsumes(ret.temporalInherent, concept)) {
                ret.temporalInherent = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.TEMPORAL_INHERENT);
            }
        }
        if (ret.removed.size() > 0) {
            List<String> declarations = new ArrayList<>();
            for (Concept r : ret.removed) {
                declarations.add(r.getUrn());
            }
            ret.declaration = ((KimConceptImpl) ret.declaration).removeComponents(declarations, removedRoles);
        }

        ret.checkTrivial();

        return ret;

    }

    void checkTrivial() {
        this.isTrivial = causant == null && adjacent == null && caused == null && comparison == null
                && compresent == null /*&& context ==
                null*/ && inherent == null && cooccurrent == null & goal == null
                && traits.isEmpty() && roles.isEmpty() && deferredTarget == null;
    }

    @Override
    public Observable.Builder withTrait(Concept... concepts) {
        for (Concept concept : concepts) {
            if (!concept.is(SemanticType.TRAIT)) {
                notifications.add(Notification.of("cannot use concept " + concept + " as a trait",
                        Level.Error));
            } else {
                traits.add(concept);
                if (!declarationIsComplete) {
                    this.declaration.getTraits().add(owl.resources().resolveConcept(concept.getUrn()));
                }
            }
        }
        isTrivial = false;
        return this;
    }

    @Override
    public Observable.Builder withTrait(Collection<Concept> concepts) {
        return withTrait(concepts.toArray(new Concept[concepts.size()]));
    }

    private boolean resolveMain() {

        if (main != null) {
            return true;
        }

        if (ontology == null) {
            if (mainId != null) {
                if (mainId.contains(":")) {
                    QualifiedName st = new QualifiedName(mainId);
                    ontology = owl.getOntology(st.getNamespace());
                    mainId = st.getName();
                    if ((main = ontology.getConcept(mainId)) != null) {
                        mainId = null;
                    }
                }
                if (ontology == null) {
                    notifications.add(Notification.of(
                            "cannot create a new concept from an ID if the ontology is not specified",
                            Level.Error));
                }
            }
        }

        return main != null;
    }

    @Override
    public Concept buildConcept() throws KlabValidationException {

        // finalize the concept by recomputing its URN
        if (declaration instanceof KimConceptImpl impl) {
            impl.finalizeDefinition();
        }

        if (notifications.size() > 0) {

            // build anyway but leave errors for notification

            String message = "";
            for (Notification error : notifications) {
                message += (message.isEmpty() ? "" : "\n") + error.getMessage();
            }
            scope.error(message, declaration);
        }

        if (!resolveMain()) {
            return null;
        }

        /*
         * correctly support trival case so we can use this without checking.
         */
        if (isTrivial()) {
            return main;
        }

        this.ontology = getTargetOntology();

        /*
         * retrieve the ID for the declaration; if present, just return the
         * corresponding concept
         */
        String conceptId = this.ontology.getIdForDefinition(declaration.getUrn());
        if (conceptId != null && this.ontology.getConcept(conceptId) != null) {
            return this.ontology.getConcept(conceptId);
        }

        // System.out.println("building " + declaration + " in " + ontology);

        conceptId = this.ontology.createIdForDefinition(declaration.getUrn());

        Set<Concept> identities = new HashSet<>();
        Set<Concept> attributes = new HashSet<>();
        Set<Concept> realms = new HashSet<>();

        /*
         * to ensure traits are not conflicting
         */
        Set<Concept> baseTraits = new HashSet<>();

        /*
         * to ensure we know if we concretized any abstract traits so we can properly
         * compute our abstract status.
         */
        Set<Concept> abstractTraitBases = new HashSet<>();

        Concept ret = main;
        // display IDs without namespaces
        ArrayList<String> tids = new ArrayList<>();
        // reference IDs with namespaces
        ArrayList<String> refIds = new ArrayList<>();

        /*
         * preload any base traits we already have. If any of them is abstract, take
         * notice so we can see if they are all concretized later.
         */
        for (Concept c : owl.reasoner().traits(main)) {
            Concept base = owl.reasoner().baseParentTrait(c);
            baseTraits.add(base);
            if (c.isAbstract()) {
                abstractTraitBases.add(base);
            }
        }

        /*
         * name and display label for the finished concept. NOTE: since 0.10.0 these are
         * no longer guaranteed unique. The authoritative name is the
         * ontology-attributed ID.
         */
        String cId = "";
        String cDs = "";
        /*
         * reference ID is guaranteed unique since 0.11 and used in all catalogs as the
         * reference name of the observable
         */
        String rId = "";

        if (traits != null && traits.size() > 0) {

            for (Concept t : traits) {

                if (t.equals(main)) {
                    continue;
                }

                if (owl.reasoner().traits(main).contains(t)) {
                    continue;
                    // monitor.error("concept " + Concepts.INSTANCE.getDisplayName(main) + " already
                    // adopts trait "
                    // + Concepts.INSTANCE.getDisplayName(t), declaration);
                }

                if (t.is(SemanticType.IDENTITY)) {
                    identities.add(t);
                } else if (t.is(SemanticType.REALM)) {
                    realms.add(t);
                } else if (!t.is(SemanticType.SUBJECTIVE)) {
                    attributes.add(t);
                }

                Concept base = owl.reasoner().baseParentTrait(t);

                if (base == null) {
                    scope.error("base declaration for trait " + t + " could not be found", declaration);
                } else {
                    if (!baseTraits.add(base)) {
                        scope.error("cannot add trait " + t.displayName() + " to concept " + main
                                + " as it already adopts a trait of type " + base.displayName(), declaration);
                    } else {
                        if (t.isAbstract()) {
                            abstractTraitBases.add(base);
                        } else {
                            abstractTraitBases.remove(base);
                        }
                    }
                }

                tids.add(getCleanId(t));
                refIds.add(t.getReferenceName());
            }
        }

        /*
         * FIXME using the display name to build an ID is wrong and forces us to use
         * display names that are legal for concept names. The two should work
         * independently.
         */
        if (tids.size() > 0) {
            Collections.sort(tids);
            for (String s : tids) {
                cId += s;
                cDs += s;
                // uId += s;
            }
        }

        rId += dumpIds(refIds);

        /*
         * add the main identity to the ID after all traits and before any context
         */
        String cleanId = getCleanId(main);
        cId += cleanId;
        cDs += cleanId;
        rId += (rId.isEmpty() ? "" : "_") + main.getReferenceName();

        /*
         * handle context, inherency etc.
         */
        if (inherent != null) {
            Concept other = owl.reasoner().inherent(main);
            if (other != null && !owl.reasoner().compatible(inherent, other)) {
                scope.error("cannot set the inherent type of " + main.displayName() + " to " + inherent.displayName()
                                + " as it already has an incompatible inherency: " + other.displayName(),
                        declaration);
                var removeme = owl.reasoner().compatible(inherent, other);
            }
            cleanId = getCleanId(inherent);
            cId += (distributedInherency ? "OfEach" : "Of") + cleanId;
            cDs += (distributedInherency ? "OfEach" : "Of") + cleanId;
            rId += (distributedInherency ? "_of_each_" : "_of_") + inherent.getReferenceName();
        }

//        if (context != null) {
//            Concept other = owl.reasoner().context(main);
//            // use the version of isCompatible that allows for observations that are
//            // compatible with
//            // the context's context if the context is an occurrent (e.g. Precipitation of
//            // Storm)
//            if (other != null && !owl.reasoner().contextuallyCompatible(main, context, other)) {
//                scope.error("cannot set the context type of " + main.displayName() + " to " + context
//                .displayName()
//                        + " as it already has an incompatible context: " + other.displayName(),
//                        declaration);
//            }
//            cleanId = getCleanId(context);
//            cId += "In" + cleanId;
//            cDs += "In" + cleanId;
//            rId += "_within_" + context.getReferenceName();
//            // uId += "In" + cleanId;
//        }

        if (compresent != null) {
            Concept other = owl.reasoner().compresent(main);
            if (other != null && !owl.reasoner().compatible(compresent, other)) {
                scope.error(
                        "cannot set the compresent type of " + main.displayName() + " to " + compresent.displayName()
                                + " as it already has an incompatible compresent type: " + other.displayName(),
                        declaration);
            }
            cleanId = getCleanId(compresent);
            cId += "With" + cleanId;
            cDs += "With" + cleanId;
            rId += "_with_" + compresent.getReferenceName();
        }

        if (goal != null) {
            // TODO transform as necessary
            Concept other = owl.reasoner().goal(main);
            if (other != null && !owl.reasoner().compatible(goal, other)) {
                scope.error("cannot set the goal type of " + main.displayName() + " to " + goal.displayName()
                                + " as it already has an incompatible goal type: " + other.displayName(),
                        declaration);
            }
            cleanId = getCleanId(goal);
            cId += "For" + cleanId;
            cDs += "For" + cleanId;
            rId += "_for_" + goal.getReferenceName();
        }

        if (caused != null) {
            Concept other = owl.reasoner().caused(main);
            if (other != null && !owl.reasoner().compatible(caused, other)) {
                scope.error(
                        "cannot set the caused type of " + main.displayName() + " to " + caused.displayName()
                                + " as it already has an incompatible caused type: " + other.displayName(),
                        declaration);
            }
            cleanId = getCleanId(caused);
            cId += "To" + cleanId;
            cDs += "To" + cleanId;
            rId += "_to_" + caused.getReferenceName();
        }

        if (causant != null) {
            Concept other = owl.reasoner().causant(main);
            if (other != null && !owl.reasoner().compatible(causant, other)) {
                scope.error(
                        "cannot set the causant type of " + main.displayName() + " to " + causant.displayName()
                                + " as it already has an incompatible causant type: " + other.displayName(),
                        declaration);
            }
            cleanId = getCleanId(causant);
            cId += "From" + cleanId;
            cDs += "From" + cleanId;
            rId += "_from_" + causant.getReferenceName();
        }

        if (adjacent != null) {
            Concept other = owl.reasoner().adjacent(main);
            if (other != null && !owl.reasoner().compatible(adjacent, other)) {
                scope.error(
                        "cannot set the adjacent type of " + main.displayName() + " to " + adjacent.displayName()
                                + " as it already has an incompatible adjacent type: " + other.displayName(),
                        declaration);
            }
            cleanId = getCleanId(adjacent);
            cId += "AdjacentTo" + cleanId;
            cDs += "AdjacentTo" + cleanId;
            rId += "_adjacent_" + adjacent.getReferenceName();
        }

        if (cooccurrent != null) {
            Concept other = owl.reasoner().cooccurrent(main);
            if (other != null && !owl.reasoner().compatible(cooccurrent, other)) {
                scope.error(
                        "cannot set the co-occurrent type of " + main.displayName() + " to " + cooccurrent.displayName()
                                + " as it already has an incompatible co-occurrent type: " + other.displayName(),
                        declaration);
            }
            cleanId = getCleanId(cooccurrent);
            cId += "During" + cleanId;
            cDs += "During" + cleanId;
            rId += "_during_" + cooccurrent.getReferenceName();
        }

        if (relationshipSource != null) {
            Concept other = owl.reasoner().relationshipSource(main);
            if (other != null && !owl.reasoner().compatible(relationshipSource, other)) {
                scope.error("cannot set the relationship source type of " + main.displayName() + " to "
                        + relationshipSource.displayName() + " as it already has an incompatible source " +
                        "type: "
                        + other.displayName(), declaration);
            }
            Concept other2 = owl.reasoner().relationshipTarget(main);
            if (other2 != null && !owl.reasoner().compatible(relationshipTarget, other2)) {
                scope.error("cannot set the relationship target type of " + main.displayName() + " to "
                        + relationshipTarget.displayName() + " as it already has an incompatible target " +
                        "type: "
                        + other2.displayName(), declaration);
            }
            cleanId = getCleanId(relationshipSource);
            cId += "Linking" + cleanId;
            cDs += "Linking" + cleanId;
            rId += "_linking_" + relationshipSource.getReferenceName();
            String cid2 = getCleanId(relationshipTarget);
            cId += "To" + cid2;
            cDs += "To" + cid2;
            rId += "_to_" + relationshipTarget.getReferenceName();
        }

        String roleIds = "";
        List<String> rids = new ArrayList<>();
        Set<Concept> acceptedRoles = new HashSet<>();

        if (roles != null && roles.size() > 0) {
            for (Concept role : roles) {
                if (owl.reasoner().roles(main).contains(role)) {
                    scope.error("concept " + main.displayName() + " already has role " + role.displayName(),
                            declaration);
                }
                rids.add(role.displayName());
                refIds.add("_as_" + role.getReferenceName());
                acceptedRoles.add(role);
            }
        }

        if (rids.size() > 0) {
            Collections.sort(rids);
            for (String s : rids) {
                roleIds += s;
            }
        }

        String rolRefIds = dumpIds(refIds);
        if (!rolRefIds.isEmpty()) {
            rId += "_" + rolRefIds;
        }

        /*
         * add the main identity to the ID after all traits and before any context
         */
        if (!roleIds.isEmpty()) {
            cId += "As" + roleIds;
            // only add role names to user description if roles are not from the
            // root of the worldview
            // if (!rolesAreFundamental(roles)) {
            cDs = roleIds + main.displayName();
            // }
        }

        // if (distributedInherency) {
        // // TODO revise - this must have proper declaration etc.
        // // distinguish the label to avoid conflicts; semantically we are the same, so
        // // the display label remains unchanged.
        // cId += "Classifier";
        // }

        /*
         * now that we use the builder to create even a simple concept, the abstract
         * status must be re-evaluated according to the engine's rules. TODO integrate
         * this with the one in KimConcept, which behaves slightly differently.
         */
        evaluateAbstractStatus();

        List<Axiom> axioms = new ArrayList<>();
        axioms.add(Axiom.ClassAssertion(conceptId, type));
        axioms.add(Axiom.AnnotationAssertion(conceptId, NS.DISPLAY_LABEL_PROPERTY, cDs));
        axioms.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cId));
        axioms.add(Axiom.SubClass(main.getNamespace() + ":" + main.getName(), conceptId));
        if (distributedInherency) {
            axioms.add(Axiom.AnnotationAssertion(conceptId, NS.INHERENCY_IS_DISTRIBUTED, "true"));
        }

        /*
         * add the core observable concept ID using NS.CORE_OBSERVABLE_PROPERTY
         */
        axioms.add(Axiom.AnnotationAssertion(conceptId, NS.CORE_OBSERVABLE_PROPERTY, main.toString()));
        axioms.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, rId));
        axioms.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY,
                declaration.getUrn()));

        if (type.contains(SemanticType.ABSTRACT)) {
            axioms.add(Axiom.AnnotationAssertion(conceptId, NS.IS_ABSTRACT, "true"));
        }

        ontology.define(axioms);
        ret = ontology.getConcept(conceptId);

        this.axiomsAdded = true;

        /*
         * restrictions
         */

        if (identities.size() > 0) {
            owl.restrict(ret, owl.getProperty(NS.HAS_IDENTITY_PROPERTY), LogicalConnector.UNION, identities
                    , ontology);
        }
        if (realms.size() > 0) {
            owl.restrict(ret, owl.getProperty(NS.HAS_REALM_PROPERTY), LogicalConnector.UNION, realms,
                    ontology);
        }
        if (attributes.size() > 0) {
            owl.restrict(ret, owl.getProperty(NS.HAS_ATTRIBUTE_PROPERTY), LogicalConnector.UNION,
                    attributes, ontology);
        }
        if (acceptedRoles.size() > 0) {
            owl.restrictSome(ret, owl.getProperty(NS.HAS_ROLE_PROPERTY), LogicalConnector.UNION,
                    acceptedRoles,
                    ontology);
        }
        if (inherent != null) {
            owl.restrictSome(ret, owl.getProperty(NS.IS_INHERENT_TO_PROPERTY), inherent, ontology);
        }
//        if (context != null) {
//            owl.restrictSome(ret, owl.getProperty(NS.HAS_CONTEXT_PROPERTY), context, ontology);
//        }
        if (caused != null) {
            owl.restrictSome(ret, owl.getProperty(NS.HAS_CAUSED_PROPERTY), caused, ontology);
        }
        if (causant != null) {
            owl.restrictSome(ret, owl.getProperty(NS.HAS_CAUSANT_PROPERTY), causant, ontology);
        }
        if (compresent != null) {
            owl.restrictSome(ret, owl.getProperty(NS.HAS_COMPRESENT_PROPERTY), compresent, ontology);
        }
        if (goal != null) {
            owl.restrictSome(ret, owl.getProperty(NS.HAS_PURPOSE_PROPERTY), goal, ontology);
        }
        if (cooccurrent != null) {
            owl.restrictSome(ret, owl.getProperty(NS.OCCURS_DURING_PROPERTY), cooccurrent, ontology);
        }
        if (adjacent != null) {
            owl.restrictSome(ret, owl.getProperty(NS.IS_ADJACENT_TO_PROPERTY), adjacent, ontology);
        }
        if (relationshipSource != null) {
            owl.restrictSome(ret, owl.getProperty(NS.IMPLIES_SOURCE_PROPERTY), relationshipSource, ontology);
            owl.restrictSome(ret, owl.getProperty(NS.IMPLIES_DESTINATION_PROPERTY), relationshipTarget,
                    ontology);
        }

        if (scope != null && !owl.reasoner().satisfiable(ret)) {
            scope.error("this declaration has logical errors and is inconsistent", declaration);
        }

        return ret;
    }

    private void evaluateAbstractStatus() {

        if (this.type.contains(SemanticType.ABSTRACT)) {
            // see if we need to remove it
            boolean remove = hasUnaryOp;
            if (!remove) {
                for (Concept t : traits) {
                    if (t.is(SemanticType.IDENTITY) && !t.isAbstract()) {
                        remove = true;
                        break;
                    }
                }
            }
            if (!remove && inherent != null) {
                remove = !inherent.isAbstract();
            }
            if (this.type.contains(SemanticType.RELATIONSHIP)) {
                remove =
                        relationshipSource != null && !relationshipSource.isAbstract() && relationshipTarget != null
                                && !relationshipTarget.isAbstract();
            }

            if (remove) {
                this.type.remove(SemanticType.ABSTRACT);
            }

        } else {
            // TODO see if we need to add it
        }
    }

    private String dumpIds(ArrayList<String> refIds) {
        if (refIds.isEmpty()) {
            return "";
        }
        Collections.sort(refIds);
        String ret = Utils.Strings.join(refIds, "_");
        refIds.clear();
        return ret;
    }

    private Ontology getTargetOntology() {
        return owl.getTargetOntology(ontology, main, traits, roles, inherent, /*context,*/ caused, causant,
                compresent,
                goal, cooccurrent, adjacent);
    }

    public static String getCleanId(Concept main) {
        String id = main.getMetadata().get(Metadata.DC_LABEL, String.class);
        if (id == null) {
            id = main.getName();
        }
        return id;
    }

    private boolean isTrivial() {
        return isTrivial;
    }

    public Concept getMainConcept() {
        return main;
    }

    @Override
    public Collection<Concept> removed(Semantics result) {
        return removed;
    }

    @Override
    public Observable build() throws KlabValidationException {

        Concept obs = buildConcept();

        if (obs == null) {
            return null;
        }

        ObservableImpl ret = ObservableImpl.promote(obs, scope);

        if (currency != null) {
            ret.setCurrency(currency);
            ret.setUrn(ret.getUrn() + " in " + ret.getCurrency());
        } else if (unit != null) {
            ret.setUnit(unit);
            ret.setUrn(ret.getUrn() + " in " + ret.getUnit());
        }

        String opId = "";
        String cdId = "";

        for (Pair<ValueOperator, Literal> op : valueOperators) {

            ValueOperator valueOperator = op.getFirst();
            Object valueOperand = op.getSecond().get(Object.class);

            ret.setUrn(ret.getUrn() + " " + valueOperator.declaration);

            opId += (opId.isEmpty() ? "" : "_") + valueOperator.textForm;
            cdId += (cdId.isEmpty() ? "" : "_") + valueOperator.textForm;

            /*
             * turn these into their parsed form so we have their properly computed
             * reference name
             */
            if (valueOperand instanceof KimObservable) {
                valueOperand = owl.reasoner().declareObservable((KimObservable) valueOperand);
            } else if (valueOperand instanceof KimConcept) {
                valueOperand = owl.reasoner().declareConcept((KimConcept) valueOperand);
            }

            if (valueOperand instanceof Concept) {

                ret.setUrn(ret.getUrn() + " " + ((Concept) valueOperand).getUrn());

                opId += (opId.isEmpty() ? "" : "_") + ((Concept) valueOperand).getReferenceName();
                cdId += (cdId.isEmpty() ? "" : "_")
                        + ((Concept) valueOperand).displayName().replaceAll("\\-", "_").replaceAll(" ", "_");

                if (name == null) {
                    ret.setName(ret.getName() + "_"
                            + ((Concept) valueOperand).displayName().replaceAll("\\-", "_").replaceAll(" ",
                            "_"));
                }

            } else if (valueOperand instanceof Observable) {

                ret.setUrn(ret.getUrn() + " (" + ((Observable) valueOperand).getUrn() + ")");
                opId += (opId.isEmpty() ? "" : "_") + ((Observable) valueOperand).getReferenceName();
                cdId += (cdId.isEmpty() ? "" : "_") + ((Observable) valueOperand).displayName();

            } else {

                if (valueOperand != null) {

                    ret.setUrn(ret.getUrn() + " " + valueOperand);

                    opId += (opId.isEmpty() ? "" : "_") + getCodeForm(valueOperand, true);
                    cdId += (cdId.isEmpty() ? "" : "_") + getCodeForm(valueOperand, false);
                }
            }

            ret.getValueOperators().add(Pair.of(valueOperator, Literal.of(valueOperand)));

        }

        if (!opId.isEmpty()) {
            ret.setReferenceName(ret.getReferenceName() + "_" + opId);
        }

        if (!cdId.isEmpty()) {
            ret.setName(ret.getName() + "_" + cdId);
        }

        // Override for special purposes.
        if (referenceName != null) {
            ret.setReferenceName(referenceName);
        }

        ret.setStatedName(this.statedName);
        // ret.setTargetPredicate(targetPredicate);
        ret.setOptional(this.optional);
        // ret.setMustContextualizeAtResolution(mustContextualize);
        ret.getAnnotations().addAll(annotations);
        ret.setDistributedInherency(distributedInherency);
        // ret.setTemporalInherent(temporalInherent);
        ret.setDereifiedAttribute(this.dereifiedAttribute);
        // ret.setDereified(this.dereified);
        ret.setGeneric(this.generic);
        // ret.setGlobal(this.global);
        // ret.setIncarnatedAbstractObservable(this.incarnatedAbstractObservable);
        // ret.setDeferredTarget(this.deferredTarget);
        // ret.setUrl(this.url);

        // if (Units.INSTANCE.needsUnits(ret) && this.unit == null && this.currency ==
        // null) {
        // ret.setFluidUnits(true);
        // }

        if (unitStatement != null) {
            /* TODO CHECK */
            Unit unit = new UnitImpl(this.unitStatement);
            ret.setUnit(unit);
            ret.setUrn(ret.getUrn() + " in " + ret.getCurrency());
        }
        if (currencyStatement != null) {
            /* TODO CHECK */
            Currency currency = Currency.create(currencyStatement);
            ret.setCurrency(currency);
            ret.setUrn(ret.getUrn() + " in " + ret.getCurrency());
        }

        if (this.inlineValue != null) {
            ret.setValue(Literal.of(this.inlineValue));
        }

        if (this.range != null) {
            /* TODO CHECK */
            ret.setRange(this.range);
            ret.setUrn(ret.getUrn() + " " + this.range);
        }

        if (this.optional) {
            ret.setOptional(true);
            ret.setUrn(ret.getUrn() + " optional");
        }

        if (this.descriptionType != null) {
            // TODO validate
            ret.setDescriptionType(this.descriptionType);
        }

        return ret;
    }

    private String getCodeForm(Object o, boolean reference) {
        if (o == null) {
            return "empty";
        } else if (o instanceof Concept) {
            return reference ? ((Concept) o).getReferenceName() : ((Concept) o).codeName();
        } else if (o instanceof Integer || o instanceof Long) {
            return ("i" + o).replaceAll("-", "_");
        } else if (o instanceof KimConcept) {
            return reference ? owl.reasoner().declareConcept((KimConcept) o).getReferenceName()
                             : owl.reasoner().declareConcept((KimConcept) o).getName();
        } else if (o instanceof KimObservable) {
            return reference ? owl.reasoner().declareObservable((KimObservable) o).getReferenceName()
                             : owl.reasoner().declareObservable((KimObservable) o).getName();
        }
        return ("h" + o.hashCode()).replaceAll("-", "_");
    }

    @Override
    public Observable.Builder withUnit(Unit unit) {
        this.unit = unit;
        return this;
    }

    @Override
    public Observable.Builder withCurrency(Currency currency) {
        this.currency = currency;
        return this;
    }

    @Override
    public Observable.Builder named(String name) {
        this.statedName = name;
        return this;
    }

    @Override
    public Observable.Builder withDistributedInherency(boolean b) {
        this.distributedInherency = b;
        return this;
    }

    @Override
    public Observable.Builder withValueOperator(ValueOperator operator, Object operand) {
        this.valueOperators.add(Pair.of(operator, Literal.of(operand)));
        return this;
    }

    @Override
    public Observable.Builder withoutValueOperators() {
        this.valueOperators.clear();
        return this;
    }

//    @Override
//    public Observable.Builder withTargetPredicate(Concept targetPredicate) {
//        this.targetPredicate = targetPredicate;
//        return this;
//    }

    @Override
    public Observable.Builder withDereifiedAttribute(String dereifiedAttribute) {
        this.dereifiedAttribute = dereifiedAttribute;
        return this;
    }

    public boolean axiomsAdded() {
        return this.axiomsAdded;
    }

    // @Override
    // public Observable.Builder setDereified() {
    // this.dereified = true;
    // return this;
    // }

    @Override
    public Observable.Builder named(String name, String referenceName) {
        this.referenceName = referenceName;
        return named(name);
    }

    @Override
    public Observable.Builder withUnit(String unit) {
        this.unitStatement = unit;
        return this;
    }

    @Override
    public Observable.Builder withCurrency(String currency) {
        this.currencyStatement = currency;
        return this;
    }

    @Override
    public Observable.Builder withInlineValue(Object value) {
        this.inlineValue = value;
        return this;
    }

    @Override
    public Observable.Builder withRange(NumericRange range) {
        this.range = range;
        return this;
    }

    @Override
    public Observable.Builder generic(boolean generic) {
        this.generic = generic;
        return this;
    }

    @Override
    public Observable.Builder withAnnotation(Annotation annotation) {
        this.annotations.add(annotation);
        return this;
    }

    @Override
    public Observable.Builder withReferenceName(String s) {
        this.referenceName = s;
        return this;
    }

    @Override
    public Observable.Builder withDefaultValue(Object defaultValue) {
        this.defaultValue = Literal.of(defaultValue);
        return this;
    }

    @Override
    public Observable.Builder withResolutionException(Observable.ResolutionException resolutionException) {
        this.resolutionExceptions.add(resolutionException);
        return this;
    }

    /**
     * Utility to filter a concept list
     *
     * @param concepts
     * @param concept
     * @return collection without the concepts and the concepts removed
     */
    public Pair<Collection<Concept>, Collection<Concept>> copyWithout(Collection<Concept> concepts,
                                                                      Concept concept) {
        Set<Concept> ret = new HashSet<>();
        Set<Concept> rem = new HashSet<>();
        for (Concept c : concepts) {
            if (!c.equals(concept)) {
                ret.add(c);
            } else {
                rem.add(c);
            }
        }
        return Pair.of(ret, rem);
    }

    /**
     * Utility to filter a concept list
     *
     * @param concepts
     * @param concept
     * @return
     */
    public Pair<Collection<Concept>, Collection<Concept>> copyWithoutAny(Collection<Concept> concepts,
                                                                         Concept concept) {
        Set<Concept> ret = new HashSet<>();
        Set<Concept> rem = new HashSet<>();
        for (Concept c : concepts) {
            if (!owl.reasoner().subsumes(c, concept)) {
                ret.add(c);
            } else {
                rem.add(c);
            }
        }
        return Pair.of(ret, rem);
    }

    /**
     * Utility to filter a concept list
     *
     * @param concepts
     * @param concept
     * @return
     */
    public Pair<Collection<Concept>, Collection<Concept>> copyWithoutAny(Collection<Concept> concepts,
                                                                         SemanticType concept) {
        Set<Concept> ret = new HashSet<>();
        Set<Concept> rem = new HashSet<>();
        for (Concept c : concepts) {
            if (!c.is(concept)) {
                ret.add(c);
            } else {
                rem.add(c);
            }
        }
        return Pair.of(ret, rem);
    }

    public List<Notification> getNotifications() {
        return notifications;
    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
    }

}
