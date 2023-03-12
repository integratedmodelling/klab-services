package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.integratedmodelling.kim.api.IKimConcept;
import org.integratedmodelling.kim.api.IKimObservable;
import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.impl.Range;
import org.integratedmodelling.klab.api.data.mediation.Currency;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.exceptions.KValidationException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.IKnowledge;
import org.integratedmodelling.klab.api.knowledge.IMetadata;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticRole;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.configuration.Services;
import org.integratedmodelling.klab.exceptions.KlabValidationException;
import org.integratedmodelling.klab.knowledge.ObservableImpl;
import org.integratedmodelling.klab.services.reasoner.api.IAxiom;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS;
import org.integratedmodelling.klab.services.reasoner.owl.Axiom;
import org.integratedmodelling.klab.services.reasoner.owl.OWL;
import org.integratedmodelling.klab.services.reasoner.owl.Ontology;
import org.integratedmodelling.klab.services.reasoner.owl.QualifiedName;
import org.integratedmodelling.klab.utils.Utils;

public class ObservableBuilder implements Observable.Builder {

    private Channel monitor;

    private Ontology ontology;

    private Concept main;
    private String mainId;
    private Set<SemanticType> type = EnumSet.noneOf(SemanticType.class);
    private Concept inherent;
    private Concept context;
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
    private Concept targetPredicate;
    private Concept temporalInherent;
    private boolean mustContextualize = false;
    private String statedName;
    private String url;

    private List<Concept> traits = new ArrayList<>();
    private List<Concept> roles = new ArrayList<>();
    private List<Concept> removed = new ArrayList<>();
    private List<Pair<ValueOperator, Object>> valueOperators = new ArrayList<>();
    private List<KValidationException> errors = new ArrayList<>();
    private Unit unit;
    private Currency currency;
    private List<Annotation> annotations = new ArrayList<>();
    private String dereifiedAttribute;
    private boolean isTrivial = true;
    private boolean distributedInherency = false;
    private KimConceptImpl declaration;
    private boolean axiomsAdded = false;
    private String referenceName = null;
    String unitStatement;
    String currencyStatement;
    Object inlineValue;
    Range range;
    boolean generic;
    Observable.Resolution resolution;
    boolean fluidUnits;
    private Literal defaultValue = null;
    private Set<Observable.ResolutionException> resolutionExceptions = EnumSet.noneOf(Observable.ResolutionException.class);

    // this gets set to true if a finished declaration is set using
    // withDeclaration() and the
    // builder is merely building it.
    private boolean declarationIsComplete = false;

    // marks the observable to build as dereifying for a resolution of inherents
    private boolean dereified = false;

    private boolean global;

    private boolean hasUnaryOp;

    private Observable incarnatedAbstractObservable;

    private Observable deferredTarget;

    public static ObservableBuilder getBuilder(Concept concept, Channel monitor) {
        return new ObservableBuilder(concept, monitor);
    }

    public ObservableBuilder(Concept main, Ontology ontology, Channel monitor) {
        this.main = main;
        this.ontology = ontology;
        this.declaration = getDeclaration(main);
        this.type = main.getType();
        this.monitor = monitor;
    }

    public ObservableBuilder(Concept main, Channel monitor) {
        this.main = main;
        this.ontology = OWL.INSTANCE.getOntology(main.getNamespace());
        this.declaration = getDeclaration(main);
        this.type.addAll(main.getType());
        this.monitor = monitor;
    }

    /**
     * Copies all info from the first level of specification of the passed observable. Will retain
     * the original semantics, so it won't separate prefix operators from the original observables:
     * at the moment it will simply collect the traits, roles, and operands of infix operators.
     * 
     * @param observable
     */
    public ObservableBuilder(Observable observable, Channel monitor) {

        this.main = Services.INSTANCE.getReasoner().rawObservable(observable.getSemantics());
        this.type = this.main.getType();
        this.ontology = OWL.INSTANCE.getOntology(observable.getSemantics().getNamespace());
        this.context = Services.INSTANCE.getReasoner().directContext(observable.getSemantics());
        this.adjacent = Services.INSTANCE.getReasoner().directAdjacent(observable.getSemantics());
        this.inherent = Services.INSTANCE.getReasoner().directInherent(observable.getSemantics());
        this.causant = Services.INSTANCE.getReasoner().directCausant(observable.getSemantics());
        this.caused = Services.INSTANCE.getReasoner().directCaused(observable.getSemantics());
        this.cooccurrent = Services.INSTANCE.getReasoner().directCooccurrent(observable.getSemantics());
        this.goal = Services.INSTANCE.getReasoner().directGoal(observable.getSemantics());
        this.compresent = Services.INSTANCE.getReasoner().directCompresent(observable.getSemantics());
        this.declaration = getDeclaration(observable.getSemantics());
        this.mustContextualize = observable.mustContextualizeAtResolution();
        this.temporalInherent = observable.temporalInherent();
        this.annotations.addAll(observable.getAnnotations());
        this.incarnatedAbstractObservable = observable.incarnatedAbstractObservable;
        this.deferredTarget = observable.getDeferredTarget();
        this.defaultValue = observable.getDefaultValue();
        this.resolutionExceptions.addAll(observable.getResolutionExceptions());

        for (Concept role : Services.INSTANCE.getReasoner().directRoles(observable.getSemantics())) {
            this.roles.add(role);
        }
        for (Concept trait : Services.INSTANCE.getReasoner().directTraits(observable.getSemantics())) {
            this.traits.add(trait);
        }

        this.isTrivial = this.context == null && this.adjacent == null && this.inherent == null && this.causant == null
                && this.caused == null && this.cooccurrent == null && this.goal == null && this.compresent == null
                && this.roles.isEmpty() && this.traits.isEmpty();

        // these are only used if buildObservable() is called
        this.unit = observable.getUnit();
        this.currency = observable.getCurrency();
        this.valueOperators.addAll(observable.getValueOperators());
        this.monitor = monitor;
    }

    public ObservableBuilder(ObservableBuilder other) {

        this.main = other.main;
        this.adjacent = other.adjacent;
        this.causant = other.causant;
        this.caused = other.caused;
        this.comparison = other.comparison;
        this.compresent = other.compresent;
        this.context = other.context;
        this.inherent = other.inherent;
        this.cooccurrent = other.cooccurrent;
        this.goal = other.goal;
        this.traits.addAll(other.traits);
        this.roles.addAll(other.roles);
        this.ontology = other.ontology;
        this.type = other.type;
        this.declaration = other.declaration;
        this.monitor = other.monitor;
        this.valueOperators.addAll(other.valueOperators);
        this.mustContextualize = other.mustContextualize;
        this.annotations.addAll(other.annotations);
        this.temporalInherent = other.temporalInherent;
        this.statedName = other.statedName;
        this.dereified = other.dereified;
        this.incarnatedAbstractObservable = other.incarnatedAbstractObservable;
        this.deferredTarget = other.deferredTarget;
        this.url = other.url;
        this.defaultValue = other.defaultValue;
        this.resolutionExceptions.addAll(other.resolutionExceptions);

        checkTrivial();
    }

    @Override
    public Observable.Builder withDeclaration(KimConcept declaration, Channel monitor) {
        this.declaration = (KimConceptImpl) declaration;
        this.monitor = monitor;
        this.declarationIsComplete = true;
        return this;
    }

    @Override
    public Observable.Builder of(Concept concept) {
        this.inherent = concept;
        if (!declarationIsComplete) {
            this.declaration.setInherent(getDeclaration(concept));
        }
        isTrivial = false;
        return this;
    }

    @Override
    public Observable.Builder optional(boolean optional) {
        this.optional = optional;
        return this;
    }

    @Override
    public Observable.Builder within(Concept concept) {
        this.context = concept;
        if (this.declaration != null) {
            this.declaration.setContext(getDeclaration(concept));
        }
        isTrivial = false;
        return this;
    }

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
            this.declaration.setCaused(getDeclaration(concept));
        }
        isTrivial = false;
        return this;
    }

    @Override
    public Observable.Builder from(Concept concept) {
        this.causant = concept;
        if (!declarationIsComplete) {
            this.declaration.setCausant(getDeclaration(concept));
        }
        isTrivial = false;
        return this;
    }

    @Override
    public Observable.Builder with(Concept concept) {
        this.compresent = concept;
        if (!declarationIsComplete) {
            this.declaration.setCompresent(getDeclaration(concept));
        }
        isTrivial = false;
        return this;
    }

    @Override
    public Observable.Builder withRole(Concept concept) {
        if (!concept.is(SemanticType.ROLE)) {
            errors.add(new KValidationException("cannot use concept " + concept + " as a role"));
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
            this.declaration.setMotivation(getDeclaration(goal));
        }
        isTrivial = false;
        return this;
    }

    @Override
    public Observable.Builder withCooccurrent(Concept cooccurrent) {
        this.cooccurrent = cooccurrent;
        if (!declarationIsComplete) {
            this.declaration.setCooccurrent(getDeclaration(cooccurrent));
        }
        isTrivial = false;
        return this;
    }

    @Override
    public Observable.Builder withAdjacent(Concept adjacent) {
        this.adjacent = adjacent;
        if (!declarationIsComplete) {
            this.declaration.setAdjacent(getDeclaration(adjacent));
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

    KimConceptImpl getDeclaration(Concept concept) {
        return (KimConceptImpl) Services.INSTANCE.getResources().resolveConcept(concept.getUrn());
    }

    @Override
    public Observable.Builder as(UnarySemanticOperator type, Concept... participants) throws KlabValidationException {

        Concept argument = null;
        if (resolveMain()) {
            argument = getArgumentBuilder().buildConcept();
        }

        if (!declarationIsComplete) {
            this.declaration.setObservationType(type);
        }

        if (participants != null && participants.length > 0) {
            this.comparison = participants[0];
            if (!declarationIsComplete) {
                this.declaration.setOtherConcept(getDeclaration(participants[0]));
            }
            if (participants.length > 1) {
                throw new KlabValidationException("cannot handle more than one participant concept in semantic operator");
            }
        }

        if (incarnatedAbstractObservable != null) {
            incarnatedAbstractObservable = incarnatedAbstractObservable.builder().as(type, participants).buildObservable();
        }

        if (argument != null) {

            try {
                switch(type) {
                case CHANGE:
                    reset(OWL.INSTANCE.makeChange(argument, true), type);
                    break;
                case CHANGED:
                    reset(OWL.INSTANCE.makeChanged(argument, true), type);
                    break;
                case COUNT:
                    reset(OWL.INSTANCE.makeCount(argument, true), type);
                    break;
                case DISTANCE:
                    reset(OWL.INSTANCE.makeDistance(argument, true), type);
                    break;
                case OCCURRENCE:
                    reset(OWL.INSTANCE.makeOccurrence(argument, true), type);
                    break;
                case PRESENCE:
                    reset(OWL.INSTANCE.makePresence(argument, true), type);
                    break;
                case PROBABILITY:
                    reset(OWL.INSTANCE.makeProbability(argument, true), type);
                    break;
                case PROPORTION:
                    reset(OWL.INSTANCE.makeProportion(argument, this.comparison, true, false), type);
                    break;
                case PERCENTAGE:
                    reset(OWL.INSTANCE.makeProportion(argument, this.comparison, true, true), type);
                    break;
                case RATIO:
                    reset(OWL.INSTANCE.makeRatio(argument, this.comparison, true), type);
                    break;
                case RATE:
                    reset(OWL.INSTANCE.makeRate(argument, true), type);
                    break;
                case UNCERTAINTY:
                    reset(OWL.INSTANCE.makeUncertainty(argument, true), type);
                    break;
                case VALUE:
                case MONETARY_VALUE:
                    reset(OWL.INSTANCE.makeValue(argument, this.comparison, true, type == UnarySemanticOperator.MONETARY_VALUE),
                            type);
                    break;
                case MAGNITUDE:
                    reset(OWL.INSTANCE.makeMagnitude(argument, true), type);
                    break;
                case LEVEL:
                    reset(OWL.INSTANCE.makeLevel(argument, true), type);
                    break;
                case TYPE:
                    reset(OWL.INSTANCE.makeType(argument, true), type);
                    break;
                default:
                    break;
                }
            } catch (KValidationException e) {
                // thrown by the makeXXX functions in case of incompatibility
                monitor.error(e.getMessage(), declaration);
            }
        }

        return this;
    }

    /**
     * Copy the builder exactly but revise the declaration so that it does not include the operator.
     * 
     * @return a new builder for the concept w/o operator
     */
    private Observable.Builder getArgumentBuilder() {
        ObservableBuilder ret = new ObservableBuilder(this);
        ret.declaration = declaration.removeOperator();
        ret.type = ret.declaration.getType();
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
        comparison = context = inherent = /* classifier = downTo = */ caused = compresent = inherent = null;
        isTrivial = true;
    }

    @Override
    public Collection<KValidationException> getErrors() {
        return errors;
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

        KimConceptImpl newDeclaration = this.declaration.removeComponents(roles);
        ObservableBuilder ret = new ObservableBuilder(Services.INSTANCE.getReasoner().declareConcept(newDeclaration), monitor);

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
        ret.targetPredicate = targetPredicate;
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
            Pair<Collection<Concept>, Collection<Concept>> tdelta = Concepts.INSTANCE.copyWithout(ret.traits, concept);
            ret.traits = new ArrayList<>(tdelta.getFirst());
            ret.removed.addAll(tdelta.getSecond());
            for (int i = 0; i < tdelta.getSecond().size(); i++) {
                removedRoles.add(SemanticRole.TRAIT);
            }
            Pair<Collection<Concept>, Collection<Concept>> rdelta = Concepts.INSTANCE.copyWithout(ret.roles, concept);
            ret.roles = new ArrayList<>(rdelta.getFirst());
            ret.removed.addAll(rdelta.getSecond());
            for (int i = 0; i < rdelta.getSecond().size(); i++) {
                removedRoles.add(SemanticRole.ROLE);
            }
            if (ret.context != null && ret.context.equals(concept)) {
                ret.context = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.CONTEXT);
            }
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
            if (ret.temporalInherent != null && ret.temporalInherent.is(concept)) {
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
            ret.declaration = ret.declaration.removeComponents(declarations, removedRoles);
        }

        ret.checkTrivial();

        return ret;
    }

    @Override
    public Observable.Builder withoutAny(SemanticType... concepts) {

        ObservableBuilder ret = new ObservableBuilder(this);
        List<SemanticRole> removedRoles = new ArrayList<>();
        for (SemanticType concept : concepts) {
            Pair<Collection<Concept>, Collection<Concept>> tdelta = Concepts.INSTANCE.copyWithoutAny(ret.traits, concept);
            ret.traits = new ArrayList<>(tdelta.getFirst());
            ret.removed.addAll(tdelta.getSecond());
            for (int i = 0; i < tdelta.getSecond().size(); i++) {
                removedRoles.add(SemanticRole.TRAIT);
            }
            Pair<Collection<Concept>, Collection<Concept>> rdelta = Concepts.INSTANCE.copyWithoutAny(ret.roles, concept);
            ret.roles = new ArrayList<>(rdelta.getFirst());
            ret.removed.addAll(rdelta.getSecond());
            for (int i = 0; i < tdelta.getSecond().size(); i++) {
                removedRoles.add(SemanticRole.ROLE);
            }
            if (ret.context != null && ret.context.is(concept)) {
                ret.removed.add(ret.context);
                ret.context = null;
                removedRoles.add(SemanticRole.CONTEXT);
            }
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
            ret.declaration = ret.declaration.removeComponents(declarations, removedRoles);
        }

        ret.checkTrivial();

        return ret;

    }

    @Override
    public Observable.Builder withoutAny(Concept... concepts) {

        ObservableBuilder ret = new ObservableBuilder(this);
        List<SemanticRole> removedRoles = new ArrayList<>();
        for (Concept concept : concepts) {
            Pair<Collection<Concept>, Collection<Concept>> tdelta = Concepts.INSTANCE.copyWithoutAny(ret.traits, concept);
            ret.traits = new ArrayList<>(tdelta.getFirst());
            ret.removed.addAll(tdelta.getSecond());
            for (int i = 0; i < tdelta.getSecond().size(); i++) {
                removedRoles.add(SemanticRole.TRAIT);
            }
            Pair<Collection<Concept>, Collection<Concept>> rdelta = Concepts.INSTANCE.copyWithoutAny(ret.roles, concept);
            ret.roles = new ArrayList<>(rdelta.getFirst());
            ret.removed.addAll(rdelta.getSecond());
            for (int i = 0; i < tdelta.getSecond().size(); i++) {
                removedRoles.add(SemanticRole.ROLE);
            }
            if (ret.context != null && ret.context.is(concept)) {
                ret.context = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.CONTEXT);
            }
            if (ret.inherent != null && ret.inherent.is(concept)) {
                ret.inherent = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.INHERENT);
            }
            if (ret.adjacent != null && ret.adjacent.is(concept)) {
                ret.adjacent = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.ADJACENT);
            }
            if (ret.caused != null && ret.caused.is(concept)) {
                ret.caused = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.CAUSED);
            }
            if (ret.causant != null && ret.causant.is(concept)) {
                ret.causant = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.CAUSANT);
            }
            if (ret.compresent != null && ret.compresent.is(concept)) {
                ret.compresent = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.COMPRESENT);
            }
            if (ret.goal != null && ret.goal.is(concept)) {
                ret.goal = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.GOAL);
            }
            if (ret.cooccurrent != null && ret.cooccurrent.is(concept)) {
                ret.cooccurrent = null;
                ret.removed.add(concept);
                removedRoles.add(SemanticRole.COOCCURRENT);
            }
            if (ret.temporalInherent != null && ret.temporalInherent.is(concept)) {
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
            ret.declaration = ret.declaration.removeComponents(declarations, removedRoles);
        }

        ret.checkTrivial();

        return ret;

    }

    void checkTrivial() {
        this.isTrivial = causant == null && adjacent == null && caused == null && comparison == null && compresent == null
                && context == null && inherent == null && cooccurrent == null & goal == null && traits.isEmpty()
                && roles.isEmpty() && deferredTarget == null;
    }

    @Override
    public Observable.Builder withTrait(Concept... concepts) {
        for (Concept concept : concepts) {
            if (!concept.is(SemanticType.TRAIT)) {
                errors.add(new KValidationException("cannot use concept " + concept + " as a trait"));
            } else {
                traits.add(concept);
                if (!declarationIsComplete) {
                    this.declaration.getTraits().add(Services.INSTANCE.getResources().resolveConcept(concept.getUrn()));
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
                    ontology = OWL.INSTANCE.getOntology(st.getNamespace());
                    mainId = st.getName();
                    if ((main = ontology.getConcept(mainId)) != null) {
                        mainId = null;
                    }
                }
                if (ontology == null) {
                    errors.add(
                            new KValidationException("cannot create a new concept from an ID if the ontology is not specified"));
                }
            }
        }

        return main != null;
    }

    @Override
    public Concept buildConcept() throws KlabValidationException {

        if (errors.size() > 0) {

            // build anyway but leave errors for notification

            String message = "";
            for (KValidationException error : errors) {
                message += (message.isEmpty() ? "" : "\n") + error.getLocalizedMessage();
            }
            monitor.error(message, declaration);
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
         * retrieve the ID for the declaration; if present, just return the corresponding concept
         */
        String conceptId = this.ontology.getIdForDefinition(declaration.getUri());
        if (conceptId != null && this.ontology.getConcept(conceptId) != null) {
            return this.ontology.getConcept(conceptId);
        }

        // System.out.println("building " + declaration + " in " + ontology);

        conceptId = this.ontology.createIdForDefinition(declaration.getUri());

        Set<Concept> identities = new HashSet<>();
        Set<Concept> attributes = new HashSet<>();
        Set<Concept> realms = new HashSet<>();

        /*
         * to ensure traits are not conflicting
         */
        Set<Concept> baseTraits = new HashSet<>();

        /*
         * to ensure we know if we concretized any abstract traits so we can properly compute our
         * abstract status.
         */
        Set<Concept> abstractTraitBases = new HashSet<>();

        Concept ret = main;
        // display IDs without namespaces
        ArrayList<String> tids = new ArrayList<>();
        // reference IDs with namespaces
        ArrayList<String> refIds = new ArrayList<>();

        /*
         * preload any base traits we already have. If any of them is abstract, take notice so we
         * can see if they are all concretized later.
         */
        for (Concept c : Services.INSTANCE.getReasoner().traits(main)) {
            Concept base = Services.INSTANCE.getReasoner().baseParentTrait(c);
            baseTraits.add(base);
            if (c.isAbstract()) {
                abstractTraitBases.add(base);
            }
        }

        /*
         * name and display label for the finished concept. NOTE: since 0.10.0 these are no longer
         * guaranteed unique. The authoritative name is the ontology-attributed ID.
         */
        String cId = "";
        String cDs = "";
        /*
         * reference ID is guaranteed unique since 0.11 and used in all catalogs as the reference
         * name of the observable
         */
        String rId = "";

        if (traits != null && traits.size() > 0) {

            for (Concept t : traits) {

                if (t.equals(main)) {
                    continue;
                }

                if (Services.INSTANCE.getReasoner().traits(main).contains(t)) {
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

                Concept base = Services.INSTANCE.getReasoner().baseParentTrait(t);

                if (base == null) {
                    monitor.error("base declaration for trait " + t + " could not be found", declaration);
                }

                if (!baseTraits.add(base)) {
                    monitor.error("cannot add trait " + t.displayName() + " to concept " + main
                            + " as it already adopts a trait of type " + base.displayName(), declaration);
                }

                if (t.isAbstract()) {
                    abstractTraitBases.add(base);
                } else {
                    abstractTraitBases.remove(base);
                }

                tids.add(getCleanId(t));
                refIds.add(t.getReferenceName());
            }
        }

        /*
         * FIXME using the display name to build an ID is wrong and forces us to use display names
         * that are legal for concept names. The two should work independently.
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
            Concept other = Services.INSTANCE.getReasoner().inherent(main);
            if (other != null && !Services.INSTANCE.getReasoner().compatible(inherent, other)) {
                monitor.error("cannot set the inherent type of " + main.displayName() + " to " + inherent.displayName()
                        + " as it already has an incompatible inherency: " + other.displayName(), declaration);
            }
            cleanId = getCleanId(inherent);
            cId += (distributedInherency ? "OfEach" : "Of") + cleanId;
            cDs += (distributedInherency ? "OfEach" : "Of") + cleanId;
            rId += (distributedInherency ? "_of_each_" : "_of_") + inherent.getReferenceName();
        }

        if (context != null) {
            Concept other = Services.INSTANCE.getReasoner().context(main);
            // use the version of isCompatible that allows for observations that are
            // compatible with
            // the context's context if the context is an occurrent (e.g. Precipitation of
            // Storm)
            if (other != null && !Services.INSTANCE.getReasoner().contextuallyCompatible(main, context, other)) {
                monitor.error("cannot set the context type of " + main.displayName() + " to " + context.displayName()
                        + " as it already has an incompatible context: " + other.displayName(), declaration);
            }
            cleanId = getCleanId(context);
            cId += "In" + cleanId;
            cDs += "In" + cleanId;
            rId += "_within_" + context.getReferenceName();
            // uId += "In" + cleanId;
        }

        if (compresent != null) {
            Concept other = Services.INSTANCE.getReasoner().compresent(main);
            if (other != null && !Services.INSTANCE.getReasoner().compatible(compresent, other)) {
                monitor.error("cannot set the compresent type of " + main.displayName() + " to " + compresent.displayName()
                        + " as it already has an incompatible compresent type: " + other.displayName(), declaration);
            }
            cleanId = getCleanId(compresent);
            cId += "With" + cleanId;
            cDs += "With" + cleanId;
            rId += "_with_" + compresent.getReferenceName();
        }

        if (goal != null) {
            // TODO transform as necessary
            Concept other = Services.INSTANCE.getReasoner().goal(main);
            if (other != null && !Services.INSTANCE.getReasoner().compatible(goal, other)) {
                monitor.error("cannot set the goal type of " + main.displayName() + " to " + goal.displayName()
                        + " as it already has an incompatible goal type: " + other.displayName(), declaration);
            }
            cleanId = getCleanId(goal);
            cId += "For" + cleanId;
            cDs += "For" + cleanId;
            rId += "_for_" + goal.getReferenceName();
        }

        if (caused != null) {
            Concept other = Services.INSTANCE.getReasoner().caused(main);
            if (other != null && !Services.INSTANCE.getReasoner().compatible(caused, other)) {
                monitor.error("cannot set the caused type of " + main.displayName() + " to " + caused.displayName()
                        + " as it already has an incompatible caused type: " + other.displayName(), declaration);
            }
            cleanId = getCleanId(caused);
            cId += "To" + cleanId;
            cDs += "To" + cleanId;
            rId += "_to_" + caused.getReferenceName();
        }

        if (causant != null) {
            Concept other = Services.INSTANCE.getReasoner().causant(main);
            if (other != null && !Services.INSTANCE.getReasoner().compatible(causant, other)) {
                monitor.error("cannot set the causant type of " + main.displayName() + " to " + causant.displayName()
                        + " as it already has an incompatible causant type: " + other.displayName(), declaration);
            }
            cleanId = getCleanId(causant);
            cId += "From" + cleanId;
            cDs += "From" + cleanId;
            rId += "_from_" + causant.getReferenceName();
        }

        if (adjacent != null) {
            Concept other = Services.INSTANCE.getReasoner().adjacent(main);
            if (other != null && !Services.INSTANCE.getReasoner().compatible(adjacent, other)) {
                monitor.error("cannot set the adjacent type of " + main.displayName() + " to " + adjacent.displayName()
                        + " as it already has an incompatible adjacent type: " + other.displayName(), declaration);
            }
            cleanId = getCleanId(adjacent);
            cId += "AdjacentTo" + cleanId;
            cDs += "AdjacentTo" + cleanId;
            rId += "_adjacent_" + adjacent.getReferenceName();
        }

        if (cooccurrent != null) {
            Concept other = Services.INSTANCE.getReasoner().cooccurrent(main);
            if (other != null && !Services.INSTANCE.getReasoner().compatible(cooccurrent, other)) {
                monitor.error("cannot set the co-occurrent type of " + main.displayName() + " to " + cooccurrent.displayName()
                        + " as it already has an incompatible co-occurrent type: " + other.displayName(), declaration);
            }
            cleanId = getCleanId(cooccurrent);
            cId += "During" + cleanId;
            cDs += "During" + cleanId;
            rId += "_during_" + cooccurrent.getReferenceName();
        }

        if (relationshipSource != null) {
            Concept other = Services.INSTANCE.getReasoner().relationshipSource(main);
            if (other != null && !Services.INSTANCE.getReasoner().compatible(relationshipSource, other)) {
                monitor.error("cannot set the relationship source type of " + main.displayName() + " to "
                        + relationshipSource.displayName() + " as it already has an incompatible source type: "
                        + other.displayName(), declaration);
            }
            Concept other2 = Services.INSTANCE.getReasoner().relationshipTarget(main);
            if (other2 != null && !Services.INSTANCE.getReasoner().compatible(relationshipTarget, other2)) {
                monitor.error("cannot set the relationship target type of " + main.displayName() + " to "
                        + relationshipTarget.displayName() + " as it already has an incompatible target type: "
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
                if (Services.INSTANCE.getReasoner().roles(main).contains(role)) {
                    monitor.error("concept " + main.displayName() + " already has role " + role.displayName(), declaration);
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
         * now that we use the builder to create even a simple concept, the abstract status must be
         * re-evaluated according to the engine's rules. TODO integrate this with the one in
         * KimConcept, which behaves slightly differently.
         */
        evaluateAbstractStatus();

        List<IAxiom> axioms = new ArrayList<>();
        axioms.add(Axiom.ClassAssertion(conceptId, type));
        axioms.add(Axiom.AnnotationAssertion(conceptId, NS.DISPLAY_LABEL_PROPERTY, cDs));
        axioms.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cId));
        axioms.add(Axiom.SubClass(main.getUrn(), conceptId));
        if (distributedInherency) {
            axioms.add(Axiom.AnnotationAssertion(conceptId, NS.INHERENCY_IS_DISTRIBUTED, "true"));
        }

        /*
         * add the core observable concept ID using NS.CORE_OBSERVABLE_PROPERTY
         */
        axioms.add(Axiom.AnnotationAssertion(conceptId, NS.CORE_OBSERVABLE_PROPERTY, main.toString()));
        axioms.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, rId));
        axioms.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, declaration.getUri()));

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
            OWL.INSTANCE.restrict(ret, OWL.INSTANCE.getProperty(NS.HAS_IDENTITY_PROPERTY), LogicalConnector.UNION, identities,
                    ontology);
        }
        if (realms.size() > 0) {
            OWL.INSTANCE.restrict(ret, OWL.INSTANCE.getProperty(NS.HAS_REALM_PROPERTY), LogicalConnector.UNION, realms, ontology);
        }
        if (attributes.size() > 0) {
            OWL.INSTANCE.restrict(ret, OWL.INSTANCE.getProperty(NS.HAS_ATTRIBUTE_PROPERTY), LogicalConnector.UNION, attributes,
                    ontology);
        }
        if (acceptedRoles.size() > 0) {
            OWL.INSTANCE.restrictSome(ret, OWL.INSTANCE.getProperty(NS.HAS_ROLE_PROPERTY), LogicalConnector.UNION, acceptedRoles,
                    ontology);
        }
        if (inherent != null) {
            OWL.INSTANCE.restrictSome(ret, OWL.INSTANCE.getProperty(NS.IS_INHERENT_TO_PROPERTY), inherent, ontology);
        }
        if (context != null) {
            OWL.INSTANCE.restrictSome(ret, OWL.INSTANCE.getProperty(NS.HAS_CONTEXT_PROPERTY), context, ontology);
        }
        if (caused != null) {
            OWL.INSTANCE.restrictSome(ret, OWL.INSTANCE.getProperty(NS.HAS_CAUSED_PROPERTY), caused, ontology);
        }
        if (causant != null) {
            OWL.INSTANCE.restrictSome(ret, OWL.INSTANCE.getProperty(NS.HAS_CAUSANT_PROPERTY), causant, ontology);
        }
        if (compresent != null) {
            OWL.INSTANCE.restrictSome(ret, OWL.INSTANCE.getProperty(NS.HAS_COMPRESENT_PROPERTY), compresent, ontology);
        }
        if (goal != null) {
            OWL.INSTANCE.restrictSome(ret, OWL.INSTANCE.getProperty(NS.HAS_PURPOSE_PROPERTY), goal, ontology);
        }
        if (cooccurrent != null) {
            OWL.INSTANCE.restrictSome(ret, OWL.INSTANCE.getProperty(NS.OCCURS_DURING_PROPERTY), cooccurrent, ontology);
        }
        if (adjacent != null) {
            OWL.INSTANCE.restrictSome(ret, OWL.INSTANCE.getProperty(NS.IS_ADJACENT_TO_PROPERTY), adjacent, ontology);
        }
        if (relationshipSource != null) {
            OWL.INSTANCE.restrictSome(ret, OWL.INSTANCE.getProperty(NS.IMPLIES_SOURCE_PROPERTY), relationshipSource, ontology);
            OWL.INSTANCE.restrictSome(ret, OWL.INSTANCE.getProperty(NS.IMPLIES_DESTINATION_PROPERTY), relationshipTarget,
                    ontology);
        }

        if (monitor != null && !Services.INSTANCE.getReasoner().satisfiable(ret)) {
            monitor.error("this declaration has logical errors and is inconsistent", declaration);
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
                remove = relationshipSource != null && !relationshipSource.isAbstract() && relationshipTarget != null
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
        return OWL.INSTANCE.getTargetOntology(ontology, main, traits, roles, inherent, context, caused, causant, compresent, goal,
                cooccurrent, adjacent);
    }

    public static String getCleanId(Concept main) {
        String id = main.getMetadata().get(IMetadata.DC_LABEL, String.class);
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
    public Collection<Concept> getRemoved() {
        return removed;
    }

    @Override
    public Observable buildObservable() throws KlabValidationException {

        Concept obs = buildConcept();

        if (obs == null) {
            return null;
        }

        ObservableImpl ret = Observable.promote(obs);

        if (currency != null) {
            ret.setCurrency(currency);
            ret.setUrn(ret.getUrn() + " in " + ret.getCurrency());
        } else if (unit != null) {
            ret.setUnit(unit);
            ret.setUrn(ret.getUrn() + " in " + ret.getUnit());
        }

        String opId = "";
        String cdId = "";

        for (Pair<ValueOperator, Object> op : valueOperators) {

            ValueOperator valueOperator = op.getFirst();
            Object valueOperand = op.getSecond();

            ret.setUrn(ret.getUrn() + " " + valueOperator.declaration);

            opId += (opId.isEmpty() ? "" : "_") + valueOperator.textForm;
            cdId += (cdId.isEmpty() ? "" : "_") + valueOperator.textForm;

            /*
             * turn these into their parsed form so we have their properly computed reference name
             */
            if (valueOperand instanceof KimObservable) {
                valueOperand = Services.INSTANCE.getReasoner().declareObservable((KimObservable) valueOperand);
            } else if (valueOperand instanceof KimConcept) {
                valueOperand = Services.INSTANCE.getReasoner().declareConcept((KimConcept) valueOperand);
            }

            if (valueOperand instanceof Concept) {

                ret.setUrn(ret.getUrn() + " " + ((Concept) valueOperand).getUrn());

                opId += (opId.isEmpty() ? "" : "_") + ((Concept) valueOperand).getReferenceName();
                cdId += (cdId.isEmpty() ? "" : "_")
                        + ((Concept) valueOperand).displayName().replaceAll("\\-", "_").replaceAll(" ", "_");

                if (name == null) {
                    ret.setName(ret.getName() + "_"
                            + ((Concept) valueOperand).displayName().replaceAll("\\-", "_").replaceAll(" ", "_"));
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

            ret.getValueOperators().add(Pair.of(valueOperator, valueOperand));

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
        ret.setTargetPredicate(targetPredicate);
        ret.setOptional(this.optional);
        ret.setMustContextualizeAtResolution(mustContextualize);
        ret.getAnnotations().addAll(annotations);
        ret.setDistributedInherency(distributedInherency);
        ret.setTemporalInherent(temporalInherent);
        ret.setDereifiedAttribute(this.dereifiedAttribute);
        ret.setDereified(this.dereified);
        ret.setGeneric(this.generic);
        ret.setResolution(this.resolution);
        ret.setGlobal(this.global);
        ret.setIncarnatedAbstractObservable(this.incarnatedAbstractObservable);
        ret.setDeferredTarget(this.deferredTarget);
        ret.setUrl(this.url);

        // if (Units.INSTANCE.needsUnits(ret) && this.unit == null && this.currency == null) {
        // ret.setFluidUnits(true);
        // }

        if (unitStatement != null) {
            /* TODO CHECK */
            Unit unit = Unit.create(this.unitStatement);
            ret.setUnit(unit);
        }
        if (currencyStatement != null) {
            /* TODO CHECK */
            Currency currency = Currency.create(currencyStatement);
            ret.setCurrency(currency);
        }

        if (this.inlineValue != null) {
            /* TODO CHECK */
            ret.setValue(this.inlineValue);
        }

        if (this.range != null) {
            /* TODO CHECK */
            ret.setRange(this.range);
        }

        return ret;
    }

    private String getCodeForm(Object o, boolean reference) {
        if (o == null) {
            return "empty";
        } else if (o instanceof IKnowledge) {
            return reference ? ((Concept) o).getReferenceName() : ((Concept) o).getCodeName();
        } else if (o instanceof Integer || o instanceof Long) {
            return ("i" + o).replaceAll("-", "_");
        } else if (o instanceof KimConcept) {
            return reference
                    ? Concepts.INSTANCE.declare((IKimConcept) o).getReferenceName()
                    : Concepts.INSTANCE.getCodeName(Concepts.INSTANCE.declare((IKimConcept) o));
        } else if (o instanceof KimObservable) {
            return reference
                    ? Observables.INSTANCE.declare((IKimObservable) o, Klab.INSTANCE.getRootMonitor()).getReferenceName()
                    : Observables.INSTANCE.declare((IKimObservable) o, Klab.INSTANCE.getRootMonitor()).getName();
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
        this.valueOperators.add(Pair.of(operator, operand));
        return this;
    }

    @Override
    public Observable.Builder withoutValueOperators() {
        this.valueOperators.clear();
        return this;
    }

    @Override
    public Observable.Builder withTargetPredicate(Concept targetPredicate) {
        this.targetPredicate = targetPredicate;
        return this;
    }

    @Override
    public Observable.Builder withDereifiedAttribute(String dereifiedAttribute) {
        this.dereifiedAttribute = dereifiedAttribute;
        return this;
    }

    @Override
    public boolean axiomsAdded() {
        return this.axiomsAdded;
    }

    @Override
    public Observable.Builder setDereified() {
        this.dereified = true;
        return this;
    }

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
    public Observable.Builder withRange(Range range) {
        this.range = range;
        return this;
    }

    @Override
    public Observable.Builder generic(boolean generic) {
        this.generic = generic;
        return this;
    }

    @Override
    public Observable.Builder withResolution(Observable.Resolution resolution) {
        this.resolution = resolution;
        return this;
    }

    @Override
    public Observable.Builder fluidUnits(boolean b) {
        this.fluidUnits = b;
        return this;
    }

    @Override
    public Observable.Builder withAnnotation(Annotation annotation) {
        this.annotations.add(annotation);
        return this;
    }

    @Override
    public Observable.Builder global(boolean global) {
        this.global = global;
        return this;
    }

    @Override
    public Observable.Builder withUrl(String uri) {
        this.url = uri;
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

}
