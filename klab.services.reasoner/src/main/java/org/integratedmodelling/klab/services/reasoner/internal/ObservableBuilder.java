//package org.integratedmodelling.klab.services.reasoner.internal;
//
//import org.integratedmodelling.common.knowledge.ConceptImpl;
//import org.integratedmodelling.common.knowledge.ObservableImpl;
//import org.integratedmodelling.common.lang.Axiom;
//import org.integratedmodelling.common.lang.kim.KimConceptImpl;
//import org.integratedmodelling.common.utils.Utils;
//import org.integratedmodelling.klab.api.collections.Pair;
//import org.integratedmodelling.klab.api.data.Metadata;
//import org.integratedmodelling.klab.api.data.mediation.Currency;
//import org.integratedmodelling.klab.api.data.mediation.NumericRange;
//import org.integratedmodelling.klab.api.data.mediation.Unit;
//import org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl;
//import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
//import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
//import org.integratedmodelling.klab.api.knowledge.Observable;
//import org.integratedmodelling.klab.api.knowledge.*;
//import org.integratedmodelling.klab.api.lang.Annotation;
//import org.integratedmodelling.klab.api.lang.LogicalConnector;
//import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
//import org.integratedmodelling.klab.api.lang.ValueOperator;
//import org.integratedmodelling.klab.api.lang.kim.KimConcept;
//import org.integratedmodelling.klab.api.lang.kim.KimObservable;
//import org.integratedmodelling.klab.api.scope.Scope;
//import org.integratedmodelling.klab.api.services.ResourcesService;
//import org.integratedmodelling.klab.services.reasoner.ReasonerService;
//import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS;
//import org.integratedmodelling.klab.services.reasoner.owl.Ontology;
//import org.integratedmodelling.klab.services.reasoner.owl.QualifiedName;
//
//import java.util.*;
//
///**
// * The working, server-side builder for observables. Clients will send a parameterized one that will
// * call methods on an instance of this at server side.
// * @deprecated
// */
//public class ObservableBuilder implements Observable.Builder {
//
//  private Scope scope;
//  private Ontology ontology;
//
//  private Concept main;
//  private String mainId;
//  private Set<SemanticType> type = EnumSet.noneOf(SemanticType.class);
//  private Concept inherent;
//  private Concept compresent;
//  private Concept causant;
//  private Concept caused;
//  private Concept goal;
//  private Concept cooccurrent;
//  private Concept adjacent;
//  private Concept comparison;
//  private Concept relationshipSource;
//  private Concept relationshipTarget;
//  private boolean optional;
//  private String name;
//  private Concept temporalInherent;
//  private boolean mustContextualize = false;
//  private String statedName;
//  private String url;
//  private List<Concept> traits = new ArrayList<>();
//  private List<Concept> roles = new ArrayList<>();
//  private List<Concept> removed = new ArrayList<>();
//  private List<Pair<ValueOperator, Object>> valueOperators = new ArrayList<>();
//  //    private List<Notification> notifications = new ArrayList<>();
//  private Unit unit;
//  private Currency currency;
//  private List<Annotation> annotations = new ArrayList<>();
//  //    private boolean isTrivial = true;
//  private KimConcept declaration;
//  private boolean axiomsAdded = false;
//  private String referenceName = null;
//  private String unitStatement;
//  private String currencyStatement;
//  private Object inlineValue;
//  private NumericRange range;
//  private boolean generic;
//  private boolean collective;
//
//  private Object defaultValue = null;
//  private Set<Observable.ResolutionDirective> resolutionDirectives =
//      EnumSet.noneOf(Observable.ResolutionDirective.class);
//  private final ReasonerService reasoner;
//  // this gets set to true if a finished declaration is set using
//  // withDeclaration() and the
//  // builder is merely building it.
//  private boolean declarationIsComplete = false;
//  //  private String urn;
//
//  // marks the observable to build as dereifying for a resolution of inherents TODO check if this is
//  //  still relevant
//  private boolean dereified = false;
//  private boolean hasUnaryOp;
//
//  private Observable incarnatedAbstractObservable;
//
//  private Observable deferredTarget;
////  private DescriptionType descriptionType;
//
//  public static ObservableBuilder getBuilder(
//      Concept concept, Scope scope, ReasonerService reasoner) {
//    return new ObservableBuilder(concept, scope, reasoner);
//  }
//
//  public static ObservableBuilder getBuilder(
//      Observable observable, Scope scope, ReasonerService reasoner) {
//    return new ObservableBuilder(observable, scope, reasoner);
//  }
//
//  public ObservableBuilder(Concept main, Ontology ontology, Scope scope, ReasonerService reasoner) {
//    this.reasoner = reasoner;
//    this.main = main;
//    this.scope = scope;
//    this.ontology = ontology;
//    this.declaration = getDeclaration(main);
//    this.type = main.getType();
//    this.collective = main.isCollective();
//  }
//
//  public ObservableBuilder(Concept main, Scope scope, ReasonerService reasoner) {
//    this.reasoner = reasoner;
//    this.main = main;
//    this.scope = scope;
//    this.ontology = reasoner.owl().getOntology(main.getNamespace());
//    this.declaration = getDeclaration(main);
//    this.type.addAll(main.getType());
//    this.collective = main.isCollective();
//  }
//
//  /**
//   * Copies all info from the first level of specification of the passed observable. Will retain the
//   * original semantics, so it won't separate prefix operators from the original observables: at the
//   * moment it will simply collect the traits, roles, and operands of infix operators.
//   *
//   * @param observable
//   */
//  public ObservableBuilder(Observable observable, Scope scope, ReasonerService reasoner) {
//
//    this.reasoner = reasoner;
//    this.main = reasoner.rawObservable(observable.getSemantics());
//    this.scope = scope;
//    this.type = this.main.getType();
//    this.ontology = reasoner.owl().getOntology(observable.getSemantics().getNamespace());
//    this.adjacent = reasoner.directAdjacent(observable.getSemantics());
//    this.inherent = reasoner.directInherent(observable.getSemantics());
//    this.causant = reasoner.directCausant(observable.getSemantics());
//    this.caused = reasoner.directCaused(observable.getSemantics());
//    this.cooccurrent = reasoner.directCooccurrent(observable.getSemantics());
//    this.goal = reasoner.directGoal(observable.getSemantics());
//    this.compresent = reasoner.directCompresent(observable.getSemantics());
//    this.declaration = getDeclaration(observable.getSemantics());
//    this.annotations.addAll(observable.getAnnotations());
//    this.defaultValue = observable.getDefaultValue();
//    this.resolutionDirectives.addAll(observable.getResolutionDirectives());
//    this.collective = observable.getSemantics().isCollective();
//
//    for (Concept role : reasoner.directRoles(observable.getSemantics())) {
//      this.roles.add(role);
//    }
//    for (Concept trait : reasoner.directTraits(observable.getSemantics())) {
//      this.traits.add(trait);
//    }
//
//    // these are only used if buildObservable() is called
//    this.unit = observable.getUnit();
//    this.currency = observable.getCurrency();
////    this.valueOperators.addAll(observable.getValueOperators());
//    this.scope = scope;
//  }
//
//  private KimConcept getDeclaration(Concept semantics) {
//    return scope.getService(ResourcesService.class).resolveConcept(semantics.getUrn());
//  }
//
//  public ObservableBuilder(ObservableBuilder other) {
//
//    this.main = other.main;
//    this.adjacent = other.adjacent;
//    this.causant = other.causant;
//    this.caused = other.caused;
//    this.comparison = other.comparison;
//    this.compresent = other.compresent;
//    //    this.urn = other.urn;
//    this.inherent = other.inherent;
//    this.cooccurrent = other.cooccurrent;
//    this.goal = other.goal;
//    this.traits.addAll(other.traits);
//    this.roles.addAll(other.roles);
//    this.ontology = other.ontology;
//    this.type = other.type;
//    this.declaration = other.declaration;
//    this.scope = other.scope;
//    this.valueOperators.addAll(other.valueOperators);
//    this.annotations.addAll(other.annotations);
//    this.temporalInherent = other.temporalInherent;
//    this.statedName = other.statedName;
//    this.dereified = other.dereified;
//    this.incarnatedAbstractObservable = other.incarnatedAbstractObservable;
//    this.deferredTarget = other.deferredTarget;
//    this.url = other.url;
//    this.reasoner = other.reasoner;
//    this.defaultValue = other.defaultValue;
//    this.resolutionDirectives.addAll(other.resolutionDirectives);
//    this.collective = other.collective;
//  }
//
//  //    @Override
//  public ObservableBuilder withDeclaration(KimConcept declaration) {
//    this.declaration = (KimConceptImpl) declaration;
//    this.declarationIsComplete = true;
//    //    this.urn = declaration.getUrn();
//    return this;
//  }
//
//  @Override
//  public Observable.Builder of(Concept concept) {
//    this.inherent = concept;
//    if (!declarationIsComplete) {
//      ((KimConceptImpl) this.declaration).setInherent(getDeclaration(concept));
//    }
//    return this;
//  }
//
//  @Override
//  public Observable.Builder optional(boolean optional) {
//    this.optional = optional;
//    return this;
//  }
//
//  @Override
//  public Observable.Builder withTemporalInherent(Concept concept) {
//    this.temporalInherent = concept;
//    return this;
//  }
//
//  @Override
//  public Observable.Builder withCaused(Concept concept) {
//    this.caused = concept;
//    if (!declarationIsComplete) {
//      ((KimConceptImpl) this.declaration).setCaused(getDeclaration(concept));
//    }
//    return this;
//  }
//
//  @Override
//  public Observable.Builder withCausant(Concept concept) {
//    this.causant = concept;
//    if (!declarationIsComplete) {
//      ((KimConceptImpl) this.declaration).setCausant(getDeclaration(concept));
//    }
//    return this;
//  }
//
//  @Override
//  public Observable.Builder with(Concept concept) {
//    this.compresent = concept;
//    if (!declarationIsComplete) {
//      ((KimConceptImpl) this.declaration).setCompresent(getDeclaration(concept));
//    }
//    return this;
//  }
//
//  @Override
//  public Observable.Builder withRole(Concept concept) {
//    if (!concept.is(SemanticType.ROLE)) {
//      scope.error("cannot use concept " + concept + " as a role", getDeclaration(concept));
//    }
//    if (!declarationIsComplete) {
//      this.declaration.getRoles().add(getDeclaration(concept));
//    }
//    this.roles.add(concept);
//    return this;
//  }
//
//  @Override
//  public Observable.Builder withGoal(Concept goal) {
//    this.goal = goal;
//    if (!declarationIsComplete) {
//      ((KimConceptImpl) this.declaration).setGoal(getDeclaration(goal));
//    }
//    return this;
//  }
//
//  @Override
//  public Observable.Builder withCooccurrent(Concept cooccurrent) {
//    this.cooccurrent = cooccurrent;
//    if (!declarationIsComplete) {
//      ((KimConceptImpl) this.declaration).setCooccurrent(getDeclaration(cooccurrent));
//    }
//    return this;
//  }
//
//  @Override
//  public Observable.Builder withAdjacent(Concept adjacent) {
//    this.adjacent = adjacent;
//    if (!declarationIsComplete) {
//      ((KimConceptImpl) this.declaration).setAdjacent(getDeclaration(adjacent));
//    }
//    return this;
//  }
//
//  @Override
//  public Observable.Builder linking(Concept source, Concept target) {
//    this.relationshipSource = source;
//    this.relationshipTarget = target;
//    return this;
//  }
//
//  @Override
//  public Observable.Builder as(UnarySemanticOperator type, Concept... participants)
//      throws KlabValidationException {
//
//    Concept argument = null;
//    if (resolveMain()) {
//      argument = getArgumentBuilder().buildConcept();
//    }
//
//    if (!declarationIsComplete) {
//      ((KimConceptImpl) this.declaration).setSemanticModifier(type);
//    }
//
//    if (participants != null && participants.length > 0) {
//      this.comparison = participants[0];
//      if (!declarationIsComplete) {
//        ((KimConceptImpl) this.declaration).setComparisonConcept(getDeclaration((participants[0])));
//      }
//      if (participants.length > 1) {
//        throw new KlabValidationException(
//            "cannot handle more than one participant concept in semantic operator");
//      }
//    }
//
//    if (incarnatedAbstractObservable != null) {
//      incarnatedAbstractObservable =
//          incarnatedAbstractObservable.builder(scope).as(type, participants).buildObservable();
//    }
//
//    if (argument != null) {
//
//      try {
//        switch (type) {
//          case CHANGE:
//            reset(reasoner.owl().makeChange(argument), type);
//            break;
//          case CHANGED:
//            reset(reasoner.owl().makeChanged(argument), type);
//            break;
//          case COUNT:
//            reset(reasoner.owl().makeCount(argument), type);
//            break;
//          case DISTANCE:
//            reset(reasoner.owl().makeDistance(argument), type);
//            break;
//          case OCCURRENCE:
//            reset(reasoner.owl().makeOccurrence(argument), type);
//            break;
//          case PRESENCE:
//            reset(reasoner.owl().makePresence(argument), type);
//            break;
//          case PROBABILITY:
//            reset(reasoner.owl().makeProbability(argument), type);
//            break;
//          case PROPORTION:
//            reset(reasoner.owl().makeProportion(argument, this.comparison, false), type);
//            break;
//          case PERCENTAGE:
//            reset(reasoner.owl().makeProportion(argument, this.comparison, true), type);
//            break;
//          case RATIO:
//            reset(reasoner.owl().makeRatio(argument, this.comparison), type);
//            break;
//          case RATE:
//            reset(reasoner.owl().makeRate(argument), type);
//            break;
//          case UNCERTAINTY:
//            reset(reasoner.owl().makeUncertainty(argument), type);
//            break;
//          case VALUE:
//          case MONETARY_VALUE:
//            reset(
//                reasoner
//                    .owl()
//                    .makeValue(
//                        argument, this.comparison, type == UnarySemanticOperator.MONETARY_VALUE),
//                type);
//            break;
//          case MAGNITUDE:
//            reset(reasoner.owl().makeMagnitude(argument), type);
//            break;
//          case LEVEL:
//            reset(reasoner.owl().makeLevel(argument), type);
//            break;
//          case TYPE:
//            reset(reasoner.owl().makeType(argument), type);
//            break;
//          default:
//            break;
//        }
//      } catch (KlabValidationException e) {
//        // thrown by the makeXXX functions in case of incompatibility
//        scope.error(e.getMessage(), declaration);
//      }
//    }
//
//    return this;
//  }
//
////  @Override
////  public Observable.Builder as(DescriptionType descriptionType) {
////    this.descriptionType = descriptionType;
////    return this;
////  }
//
//  /**
//   * Copy the builder exactly but revise the declaration so that it does not include the operator.
//   *
//   * @return a new builder for the concept w/o operator
//   */
//  private Observable.Builder getArgumentBuilder() {
//    ObservableBuilder ret = new ObservableBuilder(this);
//    ret.declaration = ((KimConceptImpl) declaration).removeOperator();
//    ret.type = declaration.getType();
//    return ret;
//  }
//
//  private void reset(Concept main, UnarySemanticOperator op) {
//    this.main = main;
//    this.type = main.getType();
//    traits.clear();
//    roles.clear();
//    unit = null;
//    currency = null;
//    hasUnaryOp = true;
//    comparison =
//        /* context= */ inherent = /* classifier = downTo = */ caused = compresent = inherent = null;
//  }
//
//  @Override
//  public Observable.Builder without(Collection<Concept> concepts) {
//    return without(concepts.toArray(new Concept[concepts.size()]));
//  }
//
//  @Override
//  public Observable.Builder without(SemanticRole... roles) {
//
//    Set<SemanticRole> r = EnumSet.noneOf(SemanticRole.class);
//    if (roles != null) {
//      for (SemanticRole role : roles) {
//        r.add(role);
//      }
//    }
//
//    KimConcept newDeclaration = ((KimConceptImpl) this.declaration).removeComponents(roles);
//    ObservableBuilder ret =
//        new ObservableBuilder(reasoner.declareConcept(newDeclaration), scope, reasoner);
//
//    /*
//     * copy the rest unless excluded
//     */
//    if (!r.contains(SemanticRole.UNIT)) {
//      ret.unit = unit;
//    }
//    if (!r.contains(SemanticRole.CURRENCY)) {
//      ret.currency = currency;
//    }
//    if (!r.contains(SemanticRole.VALUE_OPERATOR)) {
//      ret.valueOperators.addAll(valueOperators);
//    }
//
//    /*
//     * for now these have no roles associated
//     */
//    ret.name = name;
//    //        ret.targetPredicate = targetPredicate;
//    ret.optional = this.optional;
//    ret.mustContextualize = mustContextualize;
//    ret.annotations.addAll(annotations);
//    ret.deferredTarget = deferredTarget;
//
//    return ret;
//  }
//
//  @Override
//  public Observable.Builder withoutAny(Collection<Concept> concepts) {
//    return withoutAny(concepts.toArray(new Concept[concepts.size()]));
//  }
//
//  @Override
//  public Observable.Builder without(Concept... concepts) {
//
//    ObservableBuilder ret = new ObservableBuilder(this);
//    List<SemanticRole> removedRoles = new ArrayList<>();
//    for (Concept concept : concepts) {
//      Pair<Collection<Concept>, Collection<Concept>> tdelta = copyWithout(ret.traits, concept);
//      ret.traits = new ArrayList<>(tdelta.getFirst());
//      ret.removed.addAll(tdelta.getSecond());
//      for (int i = 0; i < tdelta.getSecond().size(); i++) {
//        removedRoles.add(SemanticRole.TRAIT);
//      }
//      Pair<Collection<Concept>, Collection<Concept>> rdelta = copyWithout(ret.roles, concept);
//      ret.roles = new ArrayList<>(rdelta.getFirst());
//      ret.removed.addAll(rdelta.getSecond());
//      for (int i = 0; i < rdelta.getSecond().size(); i++) {
//        removedRoles.add(SemanticRole.ROLE);
//      }
//      if (ret.inherent != null && ret.inherent.equals(concept)) {
//        ret.inherent = null;
//        ret.removed.add(concept);
//        removedRoles.add(SemanticRole.INHERENT);
//      }
//      if (ret.adjacent != null && ret.adjacent.equals(concept)) {
//        ret.adjacent = null;
//        ret.removed.add(concept);
//        removedRoles.add(SemanticRole.ADJACENT);
//      }
//      if (ret.caused != null && ret.caused.equals(concept)) {
//        ret.caused = null;
//        ret.removed.add(concept);
//        removedRoles.add(SemanticRole.CAUSED);
//      }
//      if (ret.causant != null && ret.causant.equals(concept)) {
//        ret.causant = null;
//        ret.removed.add(concept);
//        removedRoles.add(SemanticRole.CAUSANT);
//      }
//      if (ret.compresent != null && ret.compresent.equals(concept)) {
//        ret.compresent = null;
//        ret.removed.add(concept);
//        removedRoles.add(SemanticRole.COMPRESENT);
//      }
//      if (ret.goal != null && ret.goal.equals(concept)) {
//        ret.goal = null;
//        ret.removed.add(concept);
//        removedRoles.add(SemanticRole.GOAL);
//      }
//      if (ret.cooccurrent != null && ret.cooccurrent.equals(concept)) {
//        ret.cooccurrent = null;
//        ret.removed.add(concept);
//        removedRoles.add(SemanticRole.COOCCURRENT);
//      }
//    }
//    if (!ret.removed.isEmpty()) {
//      List<String> declarations = new ArrayList<>();
//      for (Concept r : ret.removed) {
//        declarations.add(r.getUrn());
//      }
//      ret.declaration =
//          ((KimConceptImpl) ret.declaration).removeComponents(declarations, removedRoles);
//    }
//    return ret;
//  }
//
//  @Override
//  public Observable.Builder withoutAny(SemanticType... concepts) {
//
//    ObservableBuilder ret = new ObservableBuilder(this);
//    List<SemanticRole> removedRoles = new ArrayList<>();
//    for (SemanticType concept : concepts) {
//      Pair<Collection<Concept>, Collection<Concept>> tdelta = copyWithoutAny(ret.traits, concept);
//      ret.traits = new ArrayList<>(tdelta.getFirst());
//      ret.removed.addAll(tdelta.getSecond());
//      for (int i = 0; i < tdelta.getSecond().size(); i++) {
//        removedRoles.add(SemanticRole.TRAIT);
//      }
//      Pair<Collection<Concept>, Collection<Concept>> rdelta = copyWithoutAny(ret.roles, concept);
//      ret.roles = new ArrayList<>(rdelta.getFirst());
//      ret.removed.addAll(rdelta.getSecond());
//      for (int i = 0; i < tdelta.getSecond().size(); i++) {
//        removedRoles.add(SemanticRole.ROLE);
//      }
//      if (ret.inherent != null && ret.inherent.is(concept)) {
//        ret.removed.add(ret.inherent);
//        ret.inherent = null;
//        removedRoles.add(SemanticRole.INHERENT);
//      }
//      if (ret.adjacent != null && ret.adjacent.is(concept)) {
//        ret.removed.add(ret.adjacent);
//        ret.adjacent = null;
//        removedRoles.add(SemanticRole.ADJACENT);
//      }
//      if (ret.caused != null && ret.caused.is(concept)) {
//        ret.removed.add(ret.caused);
//        ret.caused = null;
//        removedRoles.add(SemanticRole.CAUSED);
//      }
//      if (ret.causant != null && ret.causant.is(concept)) {
//        ret.removed.add(ret.causant);
//        ret.causant = null;
//        removedRoles.add(SemanticRole.CAUSANT);
//      }
//      if (ret.compresent != null && ret.compresent.is(concept)) {
//        ret.removed.add(ret.compresent);
//        ret.compresent = null;
//        removedRoles.add(SemanticRole.COMPRESENT);
//      }
//      if (ret.goal != null && ret.goal.is(concept)) {
//        ret.removed.add(ret.goal);
//        ret.goal = null;
//        removedRoles.add(SemanticRole.GOAL);
//      }
//      if (ret.cooccurrent != null && ret.cooccurrent.is(concept)) {
//        ret.removed.add(ret.cooccurrent);
//        ret.cooccurrent = null;
//        removedRoles.add(SemanticRole.COOCCURRENT);
//      }
//    }
//    if (!ret.removed.isEmpty()) {
//      List<String> declarations = new ArrayList<>();
//      for (Concept r : ret.removed) {
//        declarations.add(r.getUrn());
//      }
//      ret.declaration =
//          ((KimConceptImpl) ret.declaration).removeComponents(declarations, removedRoles);
//    }
//
//    return ret;
//  }
//
//  @Override
//  public Observable.Builder withoutAny(Concept... concepts) {
//
//    ObservableBuilder ret = new ObservableBuilder(this);
//    List<SemanticRole> removedRoles = new ArrayList<>();
//    for (Concept concept : concepts) {
//      Pair<Collection<Concept>, Collection<Concept>> tdelta = copyWithoutAny(ret.traits, concept);
//      ret.traits = new ArrayList<>(tdelta.getFirst());
//      ret.removed.addAll(tdelta.getSecond());
//      for (int i = 0; i < tdelta.getSecond().size(); i++) {
//        removedRoles.add(SemanticRole.TRAIT);
//      }
//      Pair<Collection<Concept>, Collection<Concept>> rdelta = copyWithoutAny(ret.roles, concept);
//      ret.roles = new ArrayList<>(rdelta.getFirst());
//      ret.removed.addAll(rdelta.getSecond());
//      for (int i = 0; i < tdelta.getSecond().size(); i++) {
//        removedRoles.add(SemanticRole.ROLE);
//      }
//      if (ret.inherent != null && reasoner.is(ret.inherent, concept)) {
//        ret.inherent = null;
//        ret.removed.add(concept);
//        removedRoles.add(SemanticRole.INHERENT);
//      }
//      if (ret.adjacent != null && reasoner.is(ret.adjacent, concept)) {
//        ret.adjacent = null;
//        ret.removed.add(concept);
//        removedRoles.add(SemanticRole.ADJACENT);
//      }
//      if (ret.caused != null && reasoner.is(ret.caused, concept)) {
//        ret.caused = null;
//        ret.removed.add(concept);
//        removedRoles.add(SemanticRole.CAUSED);
//      }
//      if (ret.causant != null && reasoner.is(ret.causant, concept)) {
//        ret.causant = null;
//        ret.removed.add(concept);
//        removedRoles.add(SemanticRole.CAUSANT);
//      }
//      if (ret.compresent != null && reasoner.is(ret.compresent, concept)) {
//        ret.compresent = null;
//        ret.removed.add(concept);
//        removedRoles.add(SemanticRole.COMPRESENT);
//      }
//      if (ret.goal != null && reasoner.is(ret.goal, concept)) {
//        ret.goal = null;
//        ret.removed.add(concept);
//        removedRoles.add(SemanticRole.GOAL);
//      }
//      if (ret.cooccurrent != null && reasoner.is(ret.cooccurrent, concept)) {
//        ret.cooccurrent = null;
//        ret.removed.add(concept);
//        removedRoles.add(SemanticRole.COOCCURRENT);
//      }
//    }
//    if (!ret.removed.isEmpty()) {
//      List<String> declarations = new ArrayList<>();
//      for (Concept r : ret.removed) {
//        declarations.add(r.getUrn());
//      }
//      ret.declaration =
//          ((KimConceptImpl) ret.declaration).removeComponents(declarations, removedRoles);
//    }
//
//    return ret;
//  }
//
//  boolean isTrivial() {
//    return causant == null
//        && adjacent == null
//        && caused == null
//        && comparison == null
//        && !collective
//        && compresent == null
//        && inherent == null
//        && cooccurrent == null & goal == null
//        && traits.isEmpty()
//        && roles.isEmpty()
//        && deferredTarget == null
//        && !hasUnaryOp;
//  }
//
//  @Override
//  public Observable.Builder withTrait(Concept... concepts) {
//    for (Concept concept : concepts) {
//      if (!concept.is(SemanticType.TRAIT)) {
//        scope.error("cannot use concept " + concept + " as a trait", declaration);
//      } else {
//        traits.add(concept);
//        if (!declarationIsComplete) {
//          this.declaration
//              .getTraits()
//              .add(reasoner.owl().resources().resolveConcept(concept.getUrn()));
//        }
//      }
//    }
//    return this;
//  }
//
//  @Override
//  public Observable.Builder withTrait(Collection<Concept> concepts) {
//    return withTrait(concepts.toArray(new Concept[concepts.size()]));
//  }
//
//  private boolean resolveMain() {
//
//    if (main != null) {
//      return true;
//    }
//
//    if (ontology == null) {
//      if (mainId != null) {
//        if (mainId.contains(":")) {
//          QualifiedName st = new QualifiedName(mainId);
//          ontology = reasoner.owl().getOntology(st.getNamespace());
//          mainId = st.getName();
//          if ((main = ontology.getConcept(mainId)) != null) {
//            mainId = null;
//          }
//        }
//        if (ontology == null) {
//          scope.error(
//              "cannot create a new concept from an ID if the ontology is not specified",
//              declaration);
//        }
//      }
//    }
//
//    return main != null;
//  }
//
//  @Override
//  public Concept buildConcept() throws KlabValidationException {
//
//    //    // finalize the concept by recomputing its URN
//    //    if (declaration instanceof KimConceptImpl impl) {
//    //      impl.finalizeDefinition();
//    //      this.urn = impl.getUrn();
//    //    }
//
//    if (!resolveMain()) {
//      return null;
//    }
//
//    /*
//     * correctly support trivial case so we can use this without checking.
//     */
//    if (isTrivial()) {
//      return adaptImplementation(main);
//    }
//
//    this.ontology = getTargetOntology();
//
//    /*
//     * retrieve the ID for the declaration; if present, just return the
//     * corresponding concept
//     */
//    String conceptId = this.ontology.getIdForDefinition(declaration.getUrn());
//    if (conceptId != null && this.ontology.getConcept(conceptId) != null) {
//      return this.ontology.getConcept(conceptId);
//    }
//
//    // System.out.println("building " + declaration + " in " + ontology);
//
//    conceptId = this.ontology.createIdForDefinition(declaration.getUrn());
//
//    Set<Concept> identities = new HashSet<>();
//    Set<Concept> attributes = new HashSet<>();
//    Set<Concept> realms = new HashSet<>();
//
//    /*
//     * to ensure traits are not conflicting
//     */
//    Set<Concept> baseTraits = new HashSet<>();
//
//    /*
//     * to ensure we know if we concretized any abstract traits so we can properly
//     * compute our abstract status.
//     */
//    Set<Concept> abstractTraitBases = new HashSet<>();
//
//    Concept ret = main;
//    // display IDs without namespaces
//    ArrayList<String> tids = new ArrayList<>();
//    // reference IDs with namespaces
//    ArrayList<String> refIds = new ArrayList<>();
//
//    /*
//     * preload any base traits we already have. If any of them is abstract, take
//     * notice so we can see if they are all concretized later.
//     */
//    for (Concept c : reasoner.traits(main)) {
//      Concept base = reasoner.baseParentTrait(c);
//      baseTraits.add(base);
//      if (c.isAbstract()) {
//        abstractTraitBases.add(base);
//      }
//    }
//
//    /*
//     * name and display label for the finished concept. NOTE: since 0.10.0 these are
//     * no longer guaranteed unique. The authoritative name is the
//     * ontology-attributed ID.
//     */
//    String cId = "";
//    String cDs = "";
//    /*
//     * reference ID is guaranteed unique since 0.11 and used in all catalogs as the
//     * reference name of the observable
//     */
//    String rId = "";
//
//    //    if (collective) {
//    //      cId = cDs = "Each";
//    //      rId = "each_";
//    //    }
//
//    if (traits != null && traits.size() > 0) {
//
//      for (Concept t : traits) {
//
//        if (t.equals(main)) {
//          continue;
//        }
//
//        if (reasoner.traits(main).contains(t)) {
//          continue;
//          // monitor.error("concept " + Concepts.INSTANCE.getDisplayName(main) + " already
//          // adopts trait "
//          // + Concepts.INSTANCE.getDisplayName(t), declaration);
//        }
//
//        if (t.is(SemanticType.IDENTITY)) {
//          identities.add(t);
//        } else if (t.is(SemanticType.REALM)) {
//          realms.add(t);
//        } else if (!t.is(SemanticType.SUBJECTIVE)) {
//          attributes.add(t);
//        }
//
//        Concept base = reasoner.baseParentTrait(t);
//
//        if (base == null) {
//
//          if (CoreOntology.isCore(t)) {
//            base = t;
//          } else {
//            scope.error("base declaration for trait " + t + " could not be found", declaration);
//          }
//        } else {
//          if (!baseTraits.add(base)) {
//            scope.error(
//                "cannot add trait "
//                    + t.displayName()
//                    + " to concept "
//                    + main
//                    + " as it already adopts a trait of type "
//                    + base.displayName(),
//                declaration);
//          } else {
//            if (t.isAbstract()) {
//              abstractTraitBases.add(base);
//            } else {
//              abstractTraitBases.remove(base);
//            }
//          }
//        }
//
//        tids.add(getCleanId(t));
//        refIds.add(t.getReferenceName());
//      }
//    }
//
//    /*
//     * FIXME using the display name to build an ID is wrong and forces us to use
//     * display names that are legal for concept names. The two should work
//     * independently.
//     */
//    if (!tids.isEmpty()) {
//      Collections.sort(tids);
//      for (String s : tids) {
//        cId += s;
//        cDs += s;
//        // uId += s;
//      }
//    }
//
//    rId += dumpIds(refIds);
//
//    /*
//     * add the main identity to the ID after all traits and before any context
//     */
//    String cleanId = getCleanId(main);
//    cId += cleanId;
//    cDs += cleanId;
//    rId += (rId.isEmpty() ? "" : "_") + main.getReferenceName();
//
//    /*
//     * handle context, inherency etc.
//     */
//    if (inherent != null) {
//      Concept other = reasoner.inherent(main);
//      if (other != null && !reasoner.compatible(inherent, other)) {
//        scope.error(
//            "cannot set the inherent type of "
//                + main.displayName()
//                + " "
//                + "to "
//                + inherent.displayName()
//                + " as it already has an incompatible inherency: "
//                + other.displayName(),
//            declaration);
//      }
//      cleanId = getCleanId(inherent);
//      cId += "Of" + cleanId;
//      cDs += "Of" + cleanId;
//      rId += "_of_" + inherent.getReferenceName();
//    }
//
//    if (compresent != null) {
//      Concept other = reasoner.compresent(main);
//      if (other != null && !reasoner.compatible(compresent, other)) {
//        scope.error(
//            "cannot set the compresent type of "
//                + main.displayName()
//                + " to "
//                + compresent.displayName()
//                + " as it already has an incompatible compresent type: "
//                + other.displayName(),
//            declaration);
//      }
//      cleanId = getCleanId(compresent);
//      cId += "With" + cleanId;
//      cDs += "With" + cleanId;
//      rId += "_with_" + compresent.getReferenceName();
//    }
//
//    if (goal != null) {
//      // TODO transform as necessary
//      Concept other = reasoner.goal(main);
//      if (other != null && !reasoner.compatible(goal, other)) {
//        scope.error(
//            "cannot set the goal type of "
//                + main.displayName()
//                + " to "
//                + goal.displayName()
//                + " as it already has an incompatible goal type: "
//                + other.displayName(),
//            declaration);
//      }
//      cleanId = getCleanId(goal);
//      cId += "For" + cleanId;
//      cDs += "For" + cleanId;
//      rId += "_for_" + goal.getReferenceName();
//    }
//
//    if (caused != null) {
//      Concept other = reasoner.caused(main);
//      if (other != null && !reasoner.compatible(caused, other)) {
//        scope.error(
//            "cannot set the caused type of "
//                + main.displayName()
//                + " to "
//                + caused.displayName()
//                + " as it already has an incompatible caused type: "
//                + other.displayName(),
//            declaration);
//      }
//      cleanId = getCleanId(caused);
//      cId += "To" + cleanId;
//      cDs += "To" + cleanId;
//      rId += "_to_" + caused.getReferenceName();
//    }
//
//    if (causant != null) {
//      Concept other = reasoner.causant(main);
//      if (other != null && !reasoner.compatible(causant, other)) {
//        scope.error(
//            "cannot set the causant type of "
//                + main.displayName()
//                + " to "
//                + causant.displayName()
//                + " as it already has an incompatible causant type: "
//                + other.displayName(),
//            declaration);
//      }
//      cleanId = getCleanId(causant);
//      cId += "From" + cleanId;
//      cDs += "From" + cleanId;
//      rId += "_from_" + causant.getReferenceName();
//    }
//
//    if (adjacent != null) {
//      Concept other = reasoner.adjacent(main);
//      if (other != null && !reasoner.compatible(adjacent, other)) {
//        scope.error(
//            "cannot set the adjacent type of "
//                + main.displayName()
//                + " to "
//                + adjacent.displayName()
//                + " as it already has an incompatible adjacent type: "
//                + other.displayName(),
//            declaration);
//      }
//      cleanId = getCleanId(adjacent);
//      cId += "AdjacentTo" + cleanId;
//      cDs += "AdjacentTo" + cleanId;
//      rId += "_adjacent_" + adjacent.getReferenceName();
//    }
//
//    if (cooccurrent != null) {
//      Concept other = reasoner.cooccurrent(main);
//      if (other != null && !reasoner.compatible(cooccurrent, other)) {
//        scope.error(
//            "cannot set the co-occurrent type of "
//                + main.displayName()
//                + " to "
//                + cooccurrent.displayName()
//                + " as it already has an incompatible co-occurrent type: "
//                + other.displayName(),
//            declaration);
//      }
//      cleanId = getCleanId(cooccurrent);
//      cId += "During" + cleanId;
//      cDs += "During" + cleanId;
//      rId += "_during_" + cooccurrent.getReferenceName();
//    }
//
//    if (relationshipSource != null) {
//      Concept other = reasoner.relationshipSource(main);
//      if (other != null && !reasoner.compatible(relationshipSource, other)) {
//        scope.error(
//            "cannot set the relationship source type of "
//                + main.displayName()
//                + " to "
//                + relationshipSource.displayName()
//                + " as it already has an incompatible source "
//                + "type: "
//                + other.displayName(),
//            declaration);
//      }
//      Concept other2 = reasoner.relationshipTarget(main);
//      if (other2 != null && !reasoner.compatible(relationshipTarget, other2)) {
//        scope.error(
//            "cannot set the relationship target type of "
//                + main.displayName()
//                + " to "
//                + relationshipTarget.displayName()
//                + " as it already has an incompatible target "
//                + "type: "
//                + other2.displayName(),
//            declaration);
//      }
//      cleanId = getCleanId(relationshipSource);
//      cId += "Linking" + cleanId;
//      cDs += "Linking" + cleanId;
//      rId += "_linking_" + relationshipSource.getReferenceName();
//      String cid2 = getCleanId(relationshipTarget);
//      cId += "To" + cid2;
//      cDs += "To" + cid2;
//      rId += "_to_" + relationshipTarget.getReferenceName();
//    }
//
//    String roleIds = "";
//    List<String> rids = new ArrayList<>();
//    Set<Concept> acceptedRoles = new HashSet<>();
//
//    if (roles != null && !roles.isEmpty()) {
//      for (Concept role : roles) {
//        if (reasoner.roles(main).contains(role)) {
//          scope.error(
//              "concept " + main.displayName() + " already has role " + role.displayName(),
//              declaration);
//        }
//        rids.add(role.displayName());
//        refIds.add("_as_" + role.getReferenceName());
//        acceptedRoles.add(role);
//      }
//    }
//
//    if (!rids.isEmpty()) {
//      Collections.sort(rids);
//      for (String s : rids) {
//        roleIds += s;
//      }
//    }
//
//    String rolRefIds = dumpIds(refIds);
//    if (!rolRefIds.isEmpty()) {
//      rId += "_" + rolRefIds;
//    }
//
//    /*
//     * add the main identity to the ID after all traits and before any context
//     */
//    if (!roleIds.isEmpty()) {
//      cId += "As" + roleIds;
//      // only add role names to user description if roles are not from the
//      // root of the worldview
//      // if (!rolesAreFundamental(roles)) {
//      cDs = roleIds + main.displayName();
//      // }
//    }
//
//    /*
//     * now that we use the builder to create even a simple concept, the abstract
//     * status must be re-evaluated according to the engine's rules. TODO integrate
//     * this with the one in KimConcept, which behaves slightly differently.
//     */
//    evaluateAbstractStatus();
//
//    List<Axiom> axioms = new ArrayList<>();
//    axioms.add(Axiom.ClassAssertion(conceptId, type));
//    axioms.add(Axiom.AnnotationAssertion(conceptId, NS.DISPLAY_LABEL_PROPERTY, cDs));
//    axioms.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cId));
//    axioms.add(Axiom.SubClass(main.getNamespace() + ":" + main.getName(), conceptId));
//
//    /*
//     * add the core observable concept ID using NS.CORE_OBSERVABLE_PROPERTY
//     */
//    axioms.add(Axiom.AnnotationAssertion(conceptId, NS.CORE_OBSERVABLE_PROPERTY, main.toString()));
//    axioms.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, rId));
//    axioms.add(
//        Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, declaration.getUrn()));
//
//    if (type.contains(SemanticType.ABSTRACT)) {
//      axioms.add(Axiom.AnnotationAssertion(conceptId, NS.IS_ABSTRACT, "true"));
//    }
//
//    ontology.define(axioms);
//    ret = ontology.getConcept(conceptId);
//    this.axiomsAdded = true;
//
//    /*
//     * restrictions
//     */
//
//    if (!identities.isEmpty()) {
//      reasoner
//          .owl()
//          .restrict(
//              ret,
//              reasoner.owl().getProperty(NS.HAS_IDENTITY_PROPERTY),
//              LogicalConnector.UNION,
//              identities,
//              ontology);
//    }
//    if (!realms.isEmpty()) {
//      reasoner
//          .owl()
//          .restrict(
//              ret,
//              reasoner.owl().getProperty(NS.HAS_REALM_PROPERTY),
//              LogicalConnector.UNION,
//              realms,
//              ontology);
//    }
//    if (!attributes.isEmpty()) {
//      reasoner
//          .owl()
//          .restrict(
//              ret,
//              reasoner.owl().getProperty(NS.HAS_ATTRIBUTE_PROPERTY),
//              LogicalConnector.UNION,
//              attributes,
//              ontology);
//    }
//    if (!acceptedRoles.isEmpty()) {
//      reasoner
//          .owl()
//          .restrictSome(
//              ret,
//              reasoner.owl().getProperty(NS.HAS_ROLE_PROPERTY),
//              LogicalConnector.UNION,
//              acceptedRoles,
//              ontology);
//    }
//    if (inherent != null) {
//      reasoner
//          .owl()
//          .restrictSome(
//              ret, reasoner.owl().getProperty(NS.IS_INHERENT_TO_PROPERTY), inherent, ontology);
//    }
//    if (caused != null) {
//      reasoner
//          .owl()
//          .restrictSome(ret, reasoner.owl().getProperty(NS.HAS_CAUSED_PROPERTY), caused, ontology);
//    }
//    if (causant != null) {
//      reasoner
//          .owl()
//          .restrictSome(
//              ret, reasoner.owl().getProperty(NS.HAS_CAUSANT_PROPERTY), causant, ontology);
//    }
//    if (compresent != null) {
//      reasoner
//          .owl()
//          .restrictSome(
//              ret, reasoner.owl().getProperty(NS.HAS_COMPRESENT_PROPERTY), compresent, ontology);
//    }
//    if (goal != null) {
//      reasoner
//          .owl()
//          .restrictSome(ret, reasoner.owl().getProperty(NS.HAS_PURPOSE_PROPERTY), goal, ontology);
//    }
//    if (cooccurrent != null) {
//      reasoner
//          .owl()
//          .restrictSome(
//              ret, reasoner.owl().getProperty(NS.OCCURS_DURING_PROPERTY), cooccurrent, ontology);
//    }
//    if (adjacent != null) {
//      reasoner
//          .owl()
//          .restrictSome(
//              ret, reasoner.owl().getProperty(NS.IS_ADJACENT_TO_PROPERTY), adjacent, ontology);
//    }
//    if (relationshipSource != null) {
//      reasoner
//          .owl()
//          .restrictSome(
//              ret,
//              reasoner.owl().getProperty(NS.IMPLIES_SOURCE_PROPERTY),
//              relationshipSource,
//              ontology);
//      reasoner
//          .owl()
//          .restrictSome(
//              ret,
//              reasoner.owl().getProperty(NS.IMPLIES_DESTINATION_PROPERTY),
//              relationshipTarget,
//              ontology);
//    }
//
//    if (scope != null && !reasoner.satisfiable(ret)) {
//      scope.error("this declaration has logical errors and is inconsistent", declaration);
//    }
//
//    return adaptImplementation(ret);
//  }
//
//  private Concept adaptImplementation(Concept candidate) {
//
//    if (candidate instanceof ConceptImpl concept) {
//      var ret = collective ? concept.collective() : concept.singular();
//      if (type.contains(SemanticType.ABSTRACT)) {
//        ret.setAbstract(true);
//      }
//      return ret;
//    }
//
//    throw new KlabInternalErrorException("Unexpected Concept implementation");
//  }
//
//  private void evaluateAbstractStatus() {
//
//    if (this.type.contains(SemanticType.ABSTRACT)) {
//      // see if we need to remove it
//      boolean remove = hasUnaryOp;
//      if (!remove) {
//        for (Concept t : traits) {
//          if (t.is(SemanticType.IDENTITY) && !t.isAbstract()) {
//            remove = true;
//            break;
//          }
//        }
//      }
//      if (!remove && inherent != null) {
//        remove = !inherent.isAbstract();
//      }
//      if (this.type.contains(SemanticType.RELATIONSHIP)) {
//        remove =
//            relationshipSource != null
//                && !relationshipSource.isAbstract()
//                && relationshipTarget != null
//                && !relationshipTarget.isAbstract();
//      }
//
//      if (remove) {
//        this.type.remove(SemanticType.ABSTRACT);
//      }
//    }
//  }
//
//  private String dumpIds(ArrayList<String> refIds) {
//    if (refIds.isEmpty()) {
//      return "";
//    }
//    Collections.sort(refIds);
//    String ret = Utils.Strings.join(refIds, "_");
//    refIds.clear();
//    return ret;
//  }
//
//  private Ontology getTargetOntology() {
//    return reasoner
//        .owl()
//        .getTargetOntology(
//            ontology,
//            main,
//            traits,
//            roles,
//            inherent,
//            caused,
//            causant,
//            compresent,
//            goal,
//            cooccurrent,
//            adjacent);
//  }
//
//  public static String getCleanId(Concept main) {
//    String id = main.getMetadata().get(Metadata.DC_LABEL, String.class);
//    if (id == null) {
//      id = main.getName();
//    }
//    return id;
//  }
//
//  public Concept getMainConcept() {
//    return main;
//  }
//
//  @Override
//  public Collection<Concept> removed(Semantics result) {
//    return removed;
//  }
//
//  @Override
//  public Observable buildObservable() throws KlabValidationException {
//
//    Concept obs = buildConcept();
//
//    if (obs == null) {
//      return null;
//    }
//
//    var ret = ObservableImpl.promote(obs, scope);
//
//    if (currency != null) {
//      ret.setCurrency(currency);
//      ret.setUrn(ret.getUrn() + " in " + ret.getCurrency());
//    } else if (unit != null) {
//      ret.setUnit(unit);
//      ret.setUrn(ret.getUrn() + " in " + ret.getUnit());
//    }
//
//    StringBuilder opId = new StringBuilder();
//    StringBuilder cdId = new StringBuilder();
//
//    // TODO move to buildConcept
//    for (Pair<ValueOperator, Object> op : valueOperators) {
//
//      ValueOperator valueOperator = op.getFirst();
//      Object valueOperand = op.getSecond();
//
//      ret.setUrn(ret.getUrn() + " " + valueOperator.declaration);
//
//      opId.append((opId.isEmpty()) ? "" : "_").append(valueOperator.textForm);
//      cdId.append((cdId.isEmpty()) ? "" : "_").append(valueOperator.textForm);
//
//      /*
//       * turn these into their parsed form so we have their properly computed
//       * reference name
//       */
//      if (valueOperand instanceof KimObservable) {
//        valueOperand = reasoner.declareObservable((KimObservable) valueOperand);
//      } else if (valueOperand instanceof KimConcept) {
//        valueOperand = reasoner.declareConcept((KimConcept) valueOperand);
//      }
//
//      if (valueOperand instanceof Concept) {
//
//        ret.setUrn(ret.getUrn() + " " + ((Concept) valueOperand).getUrn());
//
//        opId.append((opId.isEmpty()) ? "" : "_")
//            .append(((Concept) valueOperand).getReferenceName());
//        cdId.append((cdId.isEmpty()) ? "" : "_")
//            .append(
//                ((Concept) valueOperand).displayName().replaceAll("\\-", "_").replaceAll(" ", "_"));
//
//        if (name == null) {
//          ret.setName(
//              ret.getName()
//                  + "_"
//                  + ((Concept) valueOperand)
//                      .displayName()
//                      .replaceAll("\\-", "_")
//                      .replaceAll(" ", "_"));
//        }
//
//      } else if (valueOperand instanceof Observable) {
//
//        ret.setUrn(ret.getUrn() + " (" + ((Observable) valueOperand).getUrn() + ")");
//        opId.append((opId.isEmpty()) ? "" : "_")
//            .append(((Observable) valueOperand).getReferenceName());
//        cdId.append((cdId.isEmpty()) ? "" : "_").append(((Observable) valueOperand).displayName());
//
//      } else {
//
//        if (valueOperand != null) {
//
//          ret.setUrn(ret.getUrn() + " " + valueOperand);
//
//          opId.append((opId.isEmpty()) ? "" : "_").append(getCodeForm(valueOperand, true));
//          cdId.append((cdId.isEmpty()) ? "" : "_").append(getCodeForm(valueOperand, false));
//        }
//      }
//
////      ret.getValueOperators().add(Pair.of(valueOperator, valueOperand));
//    }
//
//    if (!opId.isEmpty()) {
//      ret.setReferenceName(ret.getReferenceName() + "_" + opId);
//    }
//
//    if (!cdId.isEmpty()) {
//      ret.setName(ret.getName() + "_" + cdId);
//    }
//
//    // Override for special purposes.
//    if (referenceName != null) {
//      ret.setReferenceName(referenceName);
//    }
//
//    ret.setStatedName(this.statedName);
//    ret.setOptional(this.optional);
//    ret.getAnnotations().addAll(annotations);
//    ret.setGeneric(this.generic);
//
//    if (unitStatement != null) {
//      /* TODO CHECK */
//      Unit unit = new UnitImpl(this.unitStatement);
//      ret.setUnit(unit);
//      ret.setUrn(ret.getUrn() + " in " + ret.getCurrency());
//    }
//    if (currencyStatement != null) {
//      /* TODO CHECK */
//      Currency currency = Currency.create(currencyStatement);
//      ret.setCurrency(currency);
//      ret.setUrn(ret.getUrn() + " in " + ret.getCurrency());
//    }
//
////    if (this.inlineValue != null) {
////      ret.setValue(this.inlineValue);
////    }
//
//    if (this.range != null) {
//      /* TODO CHECK */
//      ret.setRange(this.range);
//      ret.setUrn(ret.getUrn() + " " + this.range);
//    }
//
//    if (this.optional) {
//      ret.setOptional(true);
//      ret.setUrn(ret.getUrn() + " optional");
//    }
//
////    if (this.descriptionType != null) {
////      // TODO validate
////      ret.setDescriptionType(this.descriptionType);
////    }
//
//    return ret;
//  }
//
//  private String getCodeForm(Object o, boolean reference) {
//    if (o == null) {
//      return "empty";
//    } else if (o instanceof Concept) {
//      return reference ? ((Concept) o).getReferenceName() : ((Concept) o).codeName();
//    } else if (o instanceof Integer || o instanceof Long) {
//      return ("i" + o).replaceAll("-", "_");
//    } else if (o instanceof KimConcept) {
//      return reference
//          ? reasoner.declareConcept((KimConcept) o).getReferenceName()
//          : reasoner.declareConcept((KimConcept) o).getName();
//    } else if (o instanceof KimObservable) {
//      return reference
//          ? reasoner.declareObservable((KimObservable) o).getReferenceName()
//          : reasoner.declareObservable((KimObservable) o).getName();
//    }
//    return ("h" + o.hashCode()).replaceAll("-", "_");
//  }
//
//  @Override
//  public Observable.Builder withUnit(Unit unit) {
//    this.unit = unit;
//    return this;
//  }
//
//  @Override
//  public Observable.Builder withCurrency(Currency currency) {
//    this.currency = currency;
//    return this;
//  }
//
//  @Override
//  public Observable.Builder named(String name) {
//    this.statedName = name;
//    return this;
//  }
//
//  @Override
//  public Observable.Builder withValueOperator(ValueOperator operator, Object operand) {
//    this.valueOperators.add(Pair.of(operator, operand));
//    return this;
//  }
//
//  @Override
//  public Observable.Builder withoutValueOperators() {
//    this.valueOperators.clear();
//    return this;
//  }
//
//  public boolean axiomsAdded() {
//    return this.axiomsAdded;
//  }
//
//  @Override
//  public Observable.Builder named(String name, String referenceName) {
//    this.referenceName = referenceName;
//    return named(name);
//  }
//
//  @Override
//  public Observable.Builder withUnit(String unit) {
//    this.unitStatement = unit;
//    return this;
//  }
//
//  @Override
//  public Observable.Builder withCurrency(String currency) {
//    this.currencyStatement = currency;
//    return this;
//  }
//
////  @Override
////  public Observable.Builder withInlineValue(Object value) {
////    this.inlineValue = value;
////    return this;
////  }
//
//  @Override
//  public Observable.Builder withRange(NumericRange range) {
//    this.range = range;
//    return this;
//  }
//
//  @Override
//  public Observable.Builder withObserverSemantics(Concept observerSemantics) {
//    return null;
//  }
//
////  @Override
////  public Observable.Builder generic(boolean generic) {
////    this.generic = generic;
////    return this;
////  }
//
//  @Override
//  public Observable.Builder collective(boolean collective) {
//    this.collective = collective;
//    return this;
//  }
//
//  @Override
//  public Observable.Builder withAnnotation(Annotation annotation) {
//    this.annotations.add(annotation);
//    return this;
//  }
//
//  @Override
//  public Observable.Builder withReferenceName(String s) {
//    this.referenceName = s;
//    return this;
//  }
//
//  @Override
//  public Observable.Builder withDefaultValue(Object defaultValue) {
//    this.defaultValue = defaultValue;
//    return this;
//  }
//
//  @Override
//  public Observable.Builder withResolutionException(
//      Observable.ResolutionDirective resolutionDirective) {
//    this.resolutionDirectives.add(resolutionDirective);
//    return this;
//  }
//
//  /**
//   * Utility to filter a concept list
//   *
//   * @param concepts
//   * @param concept
//   * @return collection without the concepts and the concepts removed
//   */
//  public Pair<Collection<Concept>, Collection<Concept>> copyWithout(
//      Collection<Concept> concepts, Concept concept) {
//    Set<Concept> ret = new HashSet<>();
//    Set<Concept> rem = new HashSet<>();
//    for (Concept c : concepts) {
//      if (!c.equals(concept)) {
//        ret.add(c);
//      } else {
//        rem.add(c);
//      }
//    }
//    return Pair.of(ret, rem);
//  }
//
//  /**
//   * Utility to filter a concept list
//   *
//   * @param concepts
//   * @param concept
//   * @return
//   */
//  public Pair<Collection<Concept>, Collection<Concept>> copyWithoutAny(
//      Collection<Concept> concepts, Concept concept) {
//    Set<Concept> ret = new HashSet<>();
//    Set<Concept> rem = new HashSet<>();
//    for (Concept c : concepts) {
//      if (!reasoner.is(c, concept)) {
//        ret.add(c);
//      } else {
//        rem.add(c);
//      }
//    }
//    return Pair.of(ret, rem);
//  }
//
//  /**
//   * Utility to filter a concept list
//   *
//   * @param concepts
//   * @param concept
//   * @return
//   */
//  public Pair<Collection<Concept>, Collection<Concept>> copyWithoutAny(
//      Collection<Concept> concepts, SemanticType concept) {
//    Set<Concept> ret = new HashSet<>();
//    Set<Concept> rem = new HashSet<>();
//    for (Concept c : concepts) {
//      if (!c.is(concept)) {
//        ret.add(c);
//      } else {
//        rem.add(c);
//      }
//    }
//    return Pair.of(ret, rem);
//  }
//}
