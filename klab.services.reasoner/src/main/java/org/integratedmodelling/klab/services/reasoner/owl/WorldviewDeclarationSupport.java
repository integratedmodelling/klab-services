package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.*;
import java.util.function.Function;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.*;

/** Translation of declaration clauses whose OWL contract is an existential restriction. */
public final class WorldviewDeclarationSupport {
  private WorldviewDeclarationSupport() {}

  public static void compile(
      OWL owl,
      Ontology ontology,
      Concept owner,
      KimConceptStatement statement,
      Function<KimConcept, Concept> resolve) {
    // Resolve and validate all operands before writing any of these restrictions.
    var restrictions = new LinkedHashMap<String, List<Concept>>();
    add(
        restrictions,
        "odo:affects",
        statement.getQualitiesAffected(),
        SemanticType.QUALITY,
        resolve);
    add(
        restrictions,
        "odo:creates",
        statement.getObservablesCreated(),
        SemanticType.OBSERVABLE,
        resolve);
    add(
        restrictions,
        "odo:requiresIdentity",
        statement.getRequiredIdentities(),
        SemanticType.IDENTITY,
        resolve);
    add(
        restrictions,
        "odo:requiresRealm",
        statement.getRequiredRealms(),
        SemanticType.REALM,
        resolve);
    add(
        restrictions,
        "odo:requiresExtent",
        statement.getRequiredExtents(),
        SemanticType.EXTENT,
        resolve);
    add(
        restrictions,
        "odo:requiresAttribute",
        statement.getRequiredAttributes(),
        SemanticType.ATTRIBUTE,
        resolve);
    add(
        restrictions,
        "odo:impliesObservable",
        statement.getImpliedObservables(),
        SemanticType.OBSERVABLE,
        resolve);
    add(
        restrictions,
        "odo:emergesFrom",
        statement.getEmergenceTriggers(),
        SemanticType.OBSERVABLE,
        resolve);
    for (var description : statement.getObservablesDescribed()) {
      String property =
          switch (description.getSecond()) {
            case DESCRIBES -> "odo:describesQuality";
            case INCREASES_WITH -> "odo:increasesWith";
            case DECREASES_WITH -> "odo:decreasesWith";
            case MARKS -> "odo:marksQuality";
            case CLASSIFIES -> "odo:classifiesQuality";
            case DISCRETIZES -> "odo:discretizesQuality";
          };
      add(
          restrictions,
          property,
          List.of(description.getFirst()),
          description.getSecond() == KimConceptStatement.DescriptionType.MARKS
              ? SemanticType.PRESENCE
              : SemanticType.QUALITY,
          resolve);
    }
    for (var link : statement.getSubjectsLinked()) {
      if (!owner.is(SemanticType.RELATIONSHIP))
        throw new KlabValidationException("links is only valid for a relationship or bond");
      var source = require(resolve, link.getSource(), SemanticType.COUNTABLE);
      var target = require(resolve, link.getTarget(), SemanticType.COUNTABLE);
      var bounds = new OWLSemanticClauseSupport(owl);
      if (!bounds.accepts(owner, SemanticRole.RELATIONSHIP_SOURCE, source)
          || !bounds.accepts(owner, SemanticRole.RELATIONSHIP_TARGET, target))
        throw new KlabValidationException(
            "links endpoints must specialize the inherited links fillers");
      restrictions.computeIfAbsent("odo:impliesSource", k -> new ArrayList<>()).add(source);
      restrictions.computeIfAbsent("odo:impliesDestination", k -> new ArrayList<>()).add(target);
    }
    var applicables =
        statement.getAppliesTo().stream()
            .map(value -> require(resolve, value.getTarget(), SemanticType.OBSERVABLE))
            .toList();
    var inheritedDomain = new OWLSemanticClauseSupport(owl);
    for (var applicable : applicables)
      if (!inheritedDomain.applicableTo(owner, applicable))
        throw new KlabValidationException(
            "applies to must specialize every inherited applicability domain");
    for (var entry : restrictions.entrySet()) {
      var property = owl.getProperty(entry.getKey());
      if (property == null)
        throw new KlabValidationException("Missing ontology property " + entry.getKey());
      for (var target : entry.getValue()) owl.restrictSome(owner, property, target, ontology);
    }
    if (!applicables.isEmpty()) owl.setApplicableObservables(owner, applicables, ontology);
  }

  private static void add(
      Map<String, List<Concept>> restrictions,
      String property,
      List<KimConcept> operands,
      SemanticType expected,
      Function<KimConcept, Concept> resolve) {
    if (!operands.isEmpty()) {
      var targets = restrictions.computeIfAbsent(property, key -> new ArrayList<>());
      for (var operand : operands) targets.add(require(resolve, operand, expected));
    }
  }

  private static Concept require(
      Function<KimConcept, Concept> resolve, KimConcept syntax, SemanticType expected) {
    var concept = syntax == null ? null : resolve.apply(syntax);
    if (concept == null || concept.is(SemanticType.NOTHING) || !concept.is(expected))
      throw new KlabValidationException(
          "Expected a resolved "
              + expected
              + " target: "
              + (syntax == null ? "missing operand" : syntax.getUrn()));
    return concept;
  }
}
