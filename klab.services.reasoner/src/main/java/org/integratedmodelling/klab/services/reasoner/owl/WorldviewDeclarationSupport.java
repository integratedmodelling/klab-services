package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.*;
import java.util.function.Function;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.*;

/** Translation of declaration clauses whose OWL contract is an existential restriction. */
public final class WorldviewDeclarationSupport {
  static final org.semanticweb.owlapi.model.IRI VALUE_SOURCE =
      org.semanticweb.owlapi.model.IRI.create("urn:klab:validation:description-value-source");
  static final org.semanticweb.owlapi.model.IRI VALUE_STATUS =
      org.semanticweb.owlapi.model.IRI.create("urn:klab:validation:description-value-status");

  private WorldviewDeclarationSupport() {}

  public static boolean hasDeferredValue(KimConceptStatement statement) {
    return statement.getDescriptionValue() != null && !statement.getDescriptionValue().isBlank();
  }

  public static boolean hasDeferredValues(KimOntology ontology) {
    return ontology.getStatements().stream().anyMatch(WorldviewDeclarationSupport::hasDeferredValues);
  }

  private static boolean hasDeferredValues(KimConceptStatement statement) {
    return hasDeferredValue(statement)
        || statement.getChildren().stream().anyMatch(WorldviewDeclarationSupport::hasDeferredValues);
  }

  public static void compile(
      OWL owl,
      Ontology ontology,
      Concept owner,
      KimConceptStatement statement,
      Function<KimConcept, Concept> resolve) {
    // Resolve and validate all operands before writing any of these restrictions.
    // Legal source-language values must not become invalid merely because this transport still
    // carries raw text. Preserve them explicitly as unresolved authoring evidence, not validated
    // value restrictions or computations. Typed normalization remains a separate missing stage.
    if (statement.getObservablesDescribed().stream()
        .anyMatch(d -> d.getSecond() == KimConceptStatement.DescriptionType.DISCRETIZES))
      throw new KlabValidationException("DISCRETIZATION_PARTITION_UNSUPPORTED: discretizes requires "
          + "an applicability range and numeric categories with explicit endpoint ownership to "
          + "validate nonoverlap, contiguity and complete coverage");
    var described = new ArrayList<Concept>();
    var authoredBearer = statement.getDeclaredInherent() == null ? null
        : require(resolve, statement.getDeclaredInherent(), SemanticType.OBSERVABLE);
    var restrictions = new LinkedHashMap<String, List<Concept>>();
    var interpretations = WorldviewDomainSupport.interpretations(owl, owner, statement, resolve);
    if (!interpretations.isEmpty()) restrictions.put("odo:interpretsPhysicalDomain", interpretations);
    WorldviewDomainSupport.validateGrounding(owl, owner, statement);
    if (!statement.getQualitiesAffected().isEmpty()
        && !(owner.is(SemanticType.PROCESS) || owner.is(SemanticType.EVENT)))
      throw new KlabValidationException("affects requires a process or event source");
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
    var inverseRestrictions = new LinkedHashMap<String, List<Concept>>();
    if (!statement.getAffectedBy().isEmpty() && !owner.is(SemanticType.QUALITY))
      throw new KlabValidationException("affected by requires a quality target");
    for (var operand : statement.getAffectedBy()) {
      var source = require(resolve, operand, SemanticType.OBSERVABLE);
      if (!(source.is(SemanticType.PROCESS) || source.is(SemanticType.EVENT)))
        throw new KlabValidationException("affected by requires a process or event source");
      inverseRestrictions.computeIfAbsent("odo:affects", key -> new ArrayList<>()).add(source);
    }
    if (!statement.getCreatedBy().isEmpty() && !owner.is(SemanticType.OBSERVABLE))
      throw new KlabValidationException("created by requires an observable target");
    for (var operand : statement.getCreatedBy())
      inverseRestrictions
          .computeIfAbsent("odo:creates", key -> new ArrayList<>())
          .add(require(resolve, operand, SemanticType.OBSERVABLE));
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
      // The approved Domain -> Configuration branch is a necessary class restriction.
      // Its target is not a quality and contributes no bearer to the describing domain.
      // Complete consistency/satisfiability checks run at the candidate publication boundary.
      if (description.getSecond() == KimConceptStatement.DescriptionType.DESCRIBES) {
        var target = description.getFirst() == null ? null : resolve.apply(description.getFirst());
        if (owner.is(SemanticType.DOMAIN) && target != null && !target.is(SemanticType.NOTHING)
            && target.is(SemanticType.CONFIGURATION)) {
          restrictions.computeIfAbsent("odo:describesConfiguration", key -> new ArrayList<>()).add(target);
          continue;
        }
      }
      boolean validOwner = switch (description.getSecond()) {
        case CLASSIFIES -> owner.is(SemanticType.PREDICATE);
        case DISCRETIZES -> owner.is(SemanticType.ORDERING);
        case MARKS, INCREASES_WITH, DECREASES_WITH -> owner.is(SemanticType.QUALITY);
        case DESCRIBES -> owner.is(SemanticType.QUALITY) || owner.is(SemanticType.PREDICATE);
      };
      if (!validOwner) throw new KlabValidationException("Invalid source for " + description.getSecond());
      String property =
          switch (description.getSecond()) {
            case DESCRIBES -> "odo:describesQuality";
            case INCREASES_WITH -> "odo:increasesWith";
            case DECREASES_WITH -> "odo:decreasesWith";
            case MARKS -> "odo:marksQuality";
            case CLASSIFIES -> "odo:classifiesQuality";
            case DISCRETIZES -> "odo:discretizesQuality";
          };
      // No truth, threshold or value is implied by marks. Keep the existing broad type
      // matrix pending review; all six relations share the bearer contract.
      var target = require(resolve, description.getFirst(),
          description.getSecond() == KimConceptStatement.DescriptionType.MARKS
              ? SemanticType.PRESENCE
              : description.getSecond() == KimConceptStatement.DescriptionType.DISCRETIZES
                  ? SemanticType.QUANTIFIABLE : SemanticType.QUALITY);
      described.add(target);
      restrictions.computeIfAbsent(property, key -> new ArrayList<>()).add(target);
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
    var bearerAxioms = DescriptionBearerSupport.prepare(owl, owner, authoredBearer, described, applicables);
    var propertyNames = new LinkedHashSet<>(restrictions.keySet());
    propertyNames.addAll(inverseRestrictions.keySet());
    if (!applicables.isEmpty()) propertyNames.add("odo:appliesTo");
    for (var name : propertyNames)
      if (owl.getProperty(name) == null)
        throw new KlabValidationException("Missing ontology property " + name);
    // Commit only after every clause has passed validation and property preflight.
    for (var entry : restrictions.entrySet()) {
      var property = owl.getProperty(entry.getKey());
      if (property == null)
        throw new KlabValidationException("Missing ontology property " + entry.getKey());
      for (var target : entry.getValue()) {
        owl.restrictSome(owner, property, target, ontology);
        if (entry.getKey().equals("odo:interpretsPhysicalDomain"))
          WorldviewDomainSupport.markInterpretation(owl, ontology, owner, target);
      }
    }
    for (var entry : inverseRestrictions.entrySet()) {
      var property = owl.getProperty(entry.getKey());
      if (property == null)
        throw new KlabValidationException("Missing ontology property " + entry.getKey());
      for (var source : entry.getValue()) owl.restrictSome(source, property, owner, ontology);
    }
    if (!applicables.isEmpty()) owl.setApplicableObservables(owner, applicables, ontology);
    owl.manager.addAxioms(ontology.getOWLOntology(), new LinkedHashSet<>(bearerAxioms));
    if (hasDeferredValue(statement)) {
      var factory = owl.manager.getOWLDataFactory();
      var source = owl.getOWLClass(owner).getIRI();
      owl.manager.addAxiom(ontology.getOWLOntology(), factory.getOWLAnnotationAssertionAxiom(
          factory.getOWLAnnotationProperty(VALUE_SOURCE), source,
          factory.getOWLLiteral(statement.getDescriptionValue())));
      owl.manager.addAxiom(ontology.getOWLOntology(), factory.getOWLAnnotationAssertionAxiom(
          factory.getOWLAnnotationProperty(VALUE_STATUS), source, factory.getOWLLiteral("UNRESOLVED")));
    }
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
