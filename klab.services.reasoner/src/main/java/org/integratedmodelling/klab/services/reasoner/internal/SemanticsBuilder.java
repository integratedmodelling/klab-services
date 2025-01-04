package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.*;

import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.lang.kim.KimConceptImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.mediation.Currency;
import org.integratedmodelling.klab.api.data.mediation.NumericRange;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.owl.Ontology;

/**
 * Actual builder of concepts and observables. Uses the syntactic form for any transformations,
 * leaving the URN computation and everything else having to do with syntax in there and only
 * bridging to the ontology manager. Efficient if caching is implemented in both services and
 * clients.
 */
public class SemanticsBuilder implements Observable.Builder {

  KimConceptImpl syntax;
  KimConceptImpl observerSyntax;
  boolean dirty;
  ReasonerService reasoner;
  Unit unit;
  Currency currency;
  NumericRange numericRange;
  String statedName;
  boolean optional;
  boolean generic;
  List<Annotation> annotations = new ArrayList<>();

  public static SemanticsBuilder create(KimConcept concept, ReasonerService reasoner) {
    var ret = new SemanticsBuilder();
    ret.reasoner = reasoner;
    if (concept instanceof KimConceptImpl kimConcept) {
      ret.syntax = kimConcept;
      return ret;
    }
    throw new KlabInternalErrorException("Unexpected concept syntax implementation");
  }

  public static SemanticsBuilder create(Concept concept, ReasonerService reasoner) {
    var ret = new SemanticsBuilder();
    ret.reasoner = reasoner;
    var syntax =
        reasoner.serviceScope().getService(ResourcesService.class).resolveConcept(concept.getUrn());
    if (syntax instanceof KimConceptImpl kimConcept) {
      ret.syntax = kimConcept;
      return ret;
    }
    throw new KlabInternalErrorException("Unexpected concept syntax implementation");
  }

  public static SemanticsBuilder create(Observable observable, ReasonerService reasoner) {
    var ret = new SemanticsBuilder();
    ret.reasoner = reasoner;
    var syntax =
        reasoner
            .serviceScope()
            .getService(ResourcesService.class)
            .resolveConcept(observable.getSemantics().getUrn());
    if (syntax instanceof KimConceptImpl kimConcept) {
      ret.syntax = kimConcept;
      ret.unit = observable.getUnit();
      ret.currency = observable.getCurrency();
      ret.statedName = observable.getStatedName();
      ret.optional = observable.isOptional();
      return ret;
    }
    throw new KlabInternalErrorException("Unexpected concept syntax implementation");
  }

  @Override
  public Observable.Builder of(Concept inherent) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder with(Concept compresent) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder withGoal(Concept goal) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder from(Concept causant) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder to(Concept caused) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder withRole(Concept role) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder as(UnarySemanticOperator type, Concept... participants)
      throws KlabValidationException {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder withTrait(Concept... concepts) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder withTrait(Collection<Concept> concepts) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder without(Collection<Concept> concepts) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder without(Concept... concepts) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder withCooccurrent(Concept cooccurrent) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder withAdjacent(Concept adjacent) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder withoutAny(Collection<Concept> concepts) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder withoutAny(SemanticType... type) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder withoutAny(Concept... concepts) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder withUnit(Unit unit) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder withCurrency(Currency currency) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder withValueOperator(ValueOperator operator, Object valueOperand) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Collection<Concept> removed(Semantics result) {
    // TODO
    this.dirty = true;
    return List.of();
  }

  @Override
  public Observable.Builder linking(Concept source, Concept target) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder named(String name) {
    // TODO
    this.statedName = name;
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder withoutValueOperators() {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder optional(boolean optional) {
    // TODO
    this.dirty = true;
    return this;
  }

  @Override
  public Observable.Builder without(SemanticRole... roles) {
    return null;
  }

  @Override
  public Observable.Builder withTemporalInherent(Concept concept) {
    return null;
  }

  @Override
  public Observable.Builder named(String name, String referenceName) {
    return null;
  }

  @Override
  public Observable.Builder withUnit(String unit) {
    return null;
  }

  @Override
  public Observable.Builder withCurrency(String currency) {
    return null;
  }

  @Override
  public Observable.Builder withInlineValue(Object value) {
    return null;
  }

  @Override
  public Observable.Builder withDefaultValue(Object defaultValue) {
    return null;
  }

  @Override
  public Observable.Builder withResolutionException(
      Observable.ResolutionDirective resolutionDirective) {
    return null;
  }

  @Override
  public Observable.Builder withRange(NumericRange range) {
    return null;
  }

  @Override
  public Observable.Builder withObserverSemantics(Concept observerSemantics) {
    return null;
  }

  @Override
  public Observable.Builder collective(boolean collective) {
    if (syntax.isCollective() != collective) {
      syntax.setCollective(collective);
      this.dirty = true;
    }
    return this;
  }

  @Override
  public Observable.Builder withAnnotation(Annotation annotation) {
    return null;
  }

  @Override
  public Observable.Builder withReferenceName(String s) {
    return null;
  }

  // TODO use a cache
  private Concept buildConcept(KimConcept kimConcept) {

    if (kimConcept == null) {
      return null;
    }

    var ret =
        (ConceptImpl)
            (kimConcept.getName() != null
                ? reasoner.resolveConcept(kimConcept.getName())
                : buildConcept(kimConcept.getObservable()));

    if (ret.is(SemanticType.NOTHING)) {
      return ret;
    }

    // transform with the unary op if any
    if (kimConcept.getSemanticModifier() != null) {

      // TODO all these must make the concept NOTHING on error
      ret =
          (ConceptImpl)
              switch (kimConcept.getSemanticModifier()) {
                case NOT -> reasoner.owl().makeNegation(ret);
                case PRESENCE -> reasoner.owl().makePresence(ret);
                case PROPORTION ->
                    reasoner
                        .owl()
                        .makeProportion(
                            ret, buildConcept(kimConcept.getComparisonConcept()), false);
                case PERCENTAGE ->
                    reasoner
                        .owl()
                        .makeProportion(ret, buildConcept(kimConcept.getComparisonConcept()), true);
                case RATIO ->
                    reasoner.owl().makeRatio(ret, buildConcept(kimConcept.getComparisonConcept()));
                case DISTANCE -> reasoner.owl().makeDistance(ret);
                case PROBABILITY -> reasoner.owl().makeProbability(ret);
                case UNCERTAINTY -> reasoner.owl().makeUncertainty(ret);
                case COUNT -> reasoner.owl().makeCount(ret);
                case VALUE ->
                    reasoner
                        .owl()
                        .makeValue(ret, buildConcept(kimConcept.getComparisonConcept()), false);
                case MONETARY_VALUE ->
                    reasoner
                        .owl()
                        .makeValue(ret, buildConcept(kimConcept.getComparisonConcept()), true);
                case OCCURRENCE -> reasoner.owl().makeOccurrence(ret);
                case CHANGE -> reasoner.owl().makeChange(ret);
                case CHANGED -> reasoner.owl().makeChanged(ret);
                case RATE -> reasoner.owl().makeRate(ret);
                case MAGNITUDE -> reasoner.owl().makeMagnitude(ret);
                case LEVEL -> reasoner.owl().makeLevel(ret);
                case TYPE -> reasoner.owl().makeType(ret);
              };

      if (ret.is(SemanticType.NOTHING)) {
        return ret;
      }
    }

    // restrict with traits and roles. Up to here we have no need for subclassing, but if we have
    // modifiers and
    // value operators now we need a subclass.
    var modifiers = kimConcept.getModifiers();
    var valueOperators = kimConcept.getValueOperators();
    var traits = kimConcept.getTraits();
    var roles = kimConcept.getRoles();
    var ontology = reasoner.owl().getOntology(ret.getNamespace());

    var attributes = new ArrayList<Concept>();
    var realms = new ArrayList<Concept>();
    var identities = new ArrayList<Concept>();
    var acceptedRoles = new ArrayList<Concept>();

    if (valueOperators.size() + modifiers.size() + traits.size() + roles.size() > 0) {

      var parent = ret;
      ret = (ConceptImpl) reasoner.owl().makeSubclass(ret, kimConcept.getUrn());

      Set<Concept> baseTraits = new HashSet<>();
      // add traits and the proper axioms for each (move restrictions to OWL, handle the base trait)
      for (var trait : Utils.Collections.join(roles, traits)) {

        var traitConcept = buildConcept(trait);

        if (traitConcept.is(SemanticType.NOTHING)) {
          ret.error("predicate " + traitConcept.getUrn() + " is inconsistent");
          continue;
        }

        var baseTrait = reasoner.baseParentTrait(traitConcept);
        if (baseTrait == null) {
          ret.error(
              "cannot add predicate "
                  + trait.getUrn()
                  + " because a base trait for it cannot be established");
          continue;
        } else if (!baseTraits.add(baseTrait)) {
          ret.error(
              "cannot add predicate "
                  + trait.getUrn()
                  + " in expression "
                  + kimConcept.getUrn()
                  + " as it adopts more than one predicates of type "
                  + baseTrait.getUrn());
          continue;
        }

        if (traitConcept.is(SemanticType.IDENTITY)) {
          identities.add(traitConcept);
        } else if (traitConcept.is(SemanticType.REALM)) {
          realms.add(traitConcept);
        } else if (traitConcept.is(SemanticType.ATTRIBUTE)) {
          attributes.add(traitConcept);
        } else if (traitConcept.is(SemanticType.ROLE)) {
          acceptedRoles.add(traitConcept);
        }
      }

      if (!identities.isEmpty()) {
        reasoner
            .owl()
            .restrict(
                ret,
                reasoner.owl().getProperty(CoreOntology.NS.HAS_IDENTITY_PROPERTY),
                LogicalConnector.UNION,
                identities,
                ontology);
      }
      if (!realms.isEmpty()) {
        reasoner
            .owl()
            .restrict(
                ret,
                reasoner.owl().getProperty(CoreOntology.NS.HAS_REALM_PROPERTY),
                LogicalConnector.UNION,
                realms,
                ontology);
      }
      if (!attributes.isEmpty()) {
        reasoner
            .owl()
            .restrict(
                ret,
                reasoner.owl().getProperty(CoreOntology.NS.HAS_ATTRIBUTE_PROPERTY),
                LogicalConnector.UNION,
                attributes,
                ontology);
      }
      if (!acceptedRoles.isEmpty()) {
        reasoner
            .owl()
            .restrictSome(
                ret,
                reasoner.owl().getProperty(CoreOntology.NS.HAS_ROLE_PROPERTY),
                LogicalConnector.UNION,
                acceptedRoles,
                ontology);
      }

      // restrict with modifiers
      for (var modifier : modifiers) {

        // validate with parent class first!
        var modifying = buildConcept(modifier.getSecond());
        var inherited =
            switch (modifier.getFirst()) {
              case INHERENT -> reasoner.inherent(ret);
              case ADJACENT -> reasoner.adjacent(ret);
              case CAUSED -> reasoner.caused(ret);
              case CAUSANT -> reasoner.causant(ret);
              case COMPRESENT -> reasoner.compresent(ret);
              case GOAL -> reasoner.goal(ret);
              case COOCCURRENT -> reasoner.cooccurrent(ret);
              case RELATIONSHIP_SOURCE -> reasoner.relationshipSource(ret);
              case RELATIONSHIP_TARGET -> reasoner.relationshipTarget(ret);
              default ->
                  throw new KlabInternalErrorException("Unexpected modifier in semantic builder");
            };

        if (inherited != null && !reasoner.compatible(modifying.singular(), inherited)) {
          ret.error(
              "cannot set concept "
                  + modifying.getUrn()
                  + " in role "
                  + modifier.getFirst().name()
                  + " within "
                  + kimConcept.getUrn()
                  + " because it is incompatible with inherited "
                  + inherited.getUrn()
                  + " in the same role");
          continue;
        } else if (modifying.is(SemanticType.NOTHING)) {
          ret.error("definition is inconsistent because of modifier " + modifying.getUrn());
          continue;
        }

        reasoner
            .owl()
            .restrictSome(
                ret,
                reasoner
                    .owl()
                    .getProperty(
                        switch (modifier.getFirst()) {
                          case INHERENT -> CoreOntology.NS.IS_INHERENT_TO_PROPERTY;
                          case ADJACENT -> CoreOntology.NS.IS_ADJACENT_TO_PROPERTY;
                          case CAUSED -> CoreOntology.NS.HAS_CAUSED_PROPERTY;
                          case CAUSANT -> CoreOntology.NS.HAS_CAUSANT_PROPERTY;
                          case COMPRESENT -> CoreOntology.NS.HAS_COMPRESENT_PROPERTY;
                          case GOAL -> CoreOntology.NS.HAS_PURPOSE_PROPERTY;
                          case COOCCURRENT -> CoreOntology.NS.OCCURS_DURING_PROPERTY;
                          case RELATIONSHIP_SOURCE -> CoreOntology.NS.IMPLIES_SOURCE_PROPERTY;
                          case RELATIONSHIP_TARGET -> CoreOntology.NS.IMPLIES_DESTINATION_PROPERTY;
                          default ->
                              throw new KlabInternalErrorException(
                                  "Unexpected modifier in semantic builder");
                        }),
                modifying,
                ontology);
      }

      // and value ops
      for (var valueOperator : valueOperators) {
        // TODO
      }

      reasoner.owl().finalizeConcept(ret);
    }

    // set collective and abstract
    if (kimConcept.isCollective()) {
      ret = ret.collective();
    } else {
      ret = ret.singular();
    }

    // set other metadata

    ret.setUrn(kimConcept.getUrn());

    return ret;
  }

  @Override
  public Concept buildConcept() throws KlabValidationException {

    if (syntax.isPattern()) {
      throw new KlabIllegalStateException("Cannot build a concept pattern: " + syntax.getUrn());
    }
    return buildConcept(syntax);
  }

  @Override
  public Observable buildObservable() {
    return null;
  }
}
