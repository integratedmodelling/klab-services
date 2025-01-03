package org.integratedmodelling.klab.services.reasoner.internal;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.lang.kim.KimConceptImpl;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.mediation.Currency;
import org.integratedmodelling.klab.api.data.mediation.NumericRange;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

  private Concept buildConcept(KimConcept kimConcept) {

    var ret = new ConceptImpl();

    ret.setUrn(kimConcept.getUrn());

    return ret;
  }

  @Override
  public Concept buildConcept() throws KlabValidationException {
    return buildConcept(syntax);
  }

  @Override
  public Observable buildObservable() {
    return null;
  }
}
