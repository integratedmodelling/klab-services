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

  /** Install the same canonical delegate used by an equals declaration. */
  public static void compileDeniability(OWL owl, Ontology ontology, Concept owner,
      KimConceptStatement statement) {
    var alias = statement.deniedAlias();
    if (!statement.deniable()) return;
    if (!owner.is(SemanticType.ATTRIBUTE))
      throw new KlabValidationException("deniable requires an attribute");
    owner.getType().add(SemanticType.DENIABLE);
    if (alias != null) {
      var existing = ontology.getConcept(alias);
      if (existing != null && !owner.getUrn().equals(
          existing.getMetadata().get(
              org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS.IS_NEGATION_OF,
              String.class)))
        throw new KlabValidationException("Denial alias already in use: " + ontology.getName() + ":" + alias);
    }
    var negation = owl.makeNegation(owner);
    ontology.define(List.of(org.integratedmodelling.common.lang.Axiom.AnnotationAssertion(
        owner.getNamespace() + ":" + owner.getName(),
        org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS.DENIABILITY_PROPERTY,
        "true")));
    if (alias != null && (!alias.equals(negation.getName())
        || !ontology.getName().equals(negation.getNamespace())))
      ontology.addDelegateConcept(alias, ontology.getName(), negation);
  }

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
    compile(owl,ontology,owner,statement,resolve,false);
  }

  public static void compileAuthored(OWL owl, Ontology ontology, Concept owner,
      KimConceptStatement statement, Function<KimConcept, Concept> resolve) {
    compile(owl,ontology,owner,statement,resolve,true);
  }

  private static void compile(OWL owl, Ontology ontology, Concept owner,
      KimConceptStatement statement, Function<KimConcept, Concept> resolve, boolean staged) {
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
    var independentlyCheckedDescriptions = new ArrayList<Concept>();
    var authoredBearer = statement.getDeclaredInherent() == null ? null
        : require(resolve, statement.getDeclaredInherent(), SemanticType.OBSERVABLE);
    var restrictions = new LinkedHashMap<String, List<Concept>>();
    var interpretations = WorldviewDomainSupport.interpretations(owl, owner, statement, resolve, !staged);
    if (!interpretations.isEmpty()) restrictions.put("odo:interpretsPhysicalDomain", interpretations);
    if (!staged) WorldviewDomainSupport.validateGrounding(owl, owner, statement);
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
    var identityRequirements = statement.getRequiredIdentities().stream()
        .map(syntax -> identityRequirement(owl, syntax, resolve)).toList();
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
      boolean validOwner = switch (description.getSecond()) {
        case CLASSIFIES -> owner.is(SemanticType.PREDICATE);
        case DISCRETIZES -> owner.is(SemanticType.ORDERING);
        case MARKS, INCREASES_WITH, DECREASES_WITH -> owner.is(SemanticType.QUALITY);
        case DESCRIBES -> owner.is(SemanticType.QUALITY) || owner.is(SemanticType.PREDICATE);
      };
      if (!validOwner) throw new KlabValidationException("Invalid source for " + description.getSecond());
      String property =
          switch (description.getSecond()) {
            case DESCRIBES -> "odo:describes";
            case INCREASES_WITH -> "odo:increasesWith";
            case DECREASES_WITH -> "odo:decreasesWith";
            case MARKS -> "odo:marksQuality";
            case CLASSIFIES -> "odo:classifiesQuality";
            case DISCRETIZES -> "odo:discretizesQuality";
          };
      // Plain describes admits any Observable. Specialized clauses retain their bounds;
      // neither relation asserts values, computations, or particular bearer identity.
      var target = description.getSecond() == KimConceptStatement.DescriptionType.DESCRIBES
          ? requireObservableDescription(resolve, description.getFirst())
          : require(resolve, description.getFirst(),
          description.getSecond() == KimConceptStatement.DescriptionType.MARKS
              ? SemanticType.PRESENCE
              : description.getSecond() == KimConceptStatement.DescriptionType.DISCRETIZES
                  ? SemanticType.QUANTIFIABLE : SemanticType.QUALITY);
      // Domain -> Configuration validates the target independently, without
      // transferring the configuration's bearer constraints to a Domain.
      if (description.getSecond() == KimConceptStatement.DescriptionType.DESCRIBES
          && owner.is(SemanticType.DOMAIN) && target.is(SemanticType.CONFIGURATION))
        independentlyCheckedDescriptions.add(target);
      else described.add(target);
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
    // Staging validates the complete bearer conjunction once, after closure.
    // Local authored bounds are seeds in that closure, not partially reconciled OWL.
    var bearerAxioms = staged ? List.<org.semanticweb.owlapi.model.OWLAxiom>of()
        : DescriptionBearerSupport.prepare(owl, owner, authoredBearer,
            described, applicables, independentlyCheckedDescriptions);
    var propertyNames = new LinkedHashSet<>(restrictions.keySet());
    if (!identityRequirements.isEmpty()) propertyNames.add("odo:requiresIdentity");
    propertyNames.addAll(inverseRestrictions.keySet());
    if (!applicables.isEmpty()) propertyNames.add("odo:appliesTo");
    for (var name : propertyNames)
      if (owl.getProperty(name) == null)
        throw new KlabValidationException("Missing ontology property " + name);
    // Commit only after every clause has passed validation and property preflight.
    for (var requirement : identityRequirements) {
      var f = owl.manager.getOWLDataFactory();
      owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLSubClassOfAxiom(owl.getOWLClass(owner),
          f.getOWLObjectSomeValuesFrom(owl.getProperty("odo:requiresIdentity")
              .getOWLEntity().asOWLObjectProperty(), requirement)));
    }
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
    if (authoredBearer != null)
      OWLSemanticClauseSupport.markAuthoredBearer(owl, owner, authoredBearer, ontology);
    if (hasDeferredValue(statement)) {
      var factory = owl.manager.getOWLDataFactory();
      var source = owl.getOWLClass(owner).getIRI();
      owl.manager.addAxiom(ontology.getOWLOntology(), factory.getOWLAnnotationAssertionAxiom(
          factory.getOWLAnnotationProperty(VALUE_SOURCE), source,
          factory.getOWLLiteral(statement.getDescriptionValue())));
      owl.manager.addAxiom(ontology.getOWLOntology(), factory.getOWLAnnotationAssertionAxiom(
          factory.getOWLAnnotationProperty(VALUE_STATUS), source, factory.getOWLLiteral("UNRESOLVED")));
    }
    compileDeniability(owl, ontology, owner, statement);
  }

  /** Preserve source boolean scope, without equating arbitrary named identity categories. */
  private static org.semanticweb.owlapi.model.OWLClassExpression identityRequirement(
      OWL owl, KimConcept syntax, Function<KimConcept, Concept> resolve) {
    if (syntax.getOperands().isEmpty()) {
      if (syntax.getObservable() != null && syntax.getSemanticModifier() == null
          && syntax.getTraits().isEmpty() && syntax.getRoles().isEmpty()
          && syntax.getModifiers().isEmpty() && syntax.getAbout().isEmpty())
        return identityRequirement(owl, syntax.getObservable(), resolve);
      return owl.getOWLClass(require(resolve, syntax, SemanticType.IDENTITY));
    }
    // General generated union classes currently carry one-way subclass axioms.
    // A requirement denotes the source boolean expression itself, not a demand
    // for membership in that narrower implementation class.
    if (!(syntax instanceof org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl implementation))
      throw new KlabValidationException("Cannot preserve boolean identity requirement syntax");
    var first = (org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl) implementation.removeComponents();
    first.setOperands(new ArrayList<>());
    first.setExpressionType(KimConcept.Expression.SINGLETON);
    first.getType().remove(SemanticType.UNION);
    first.getType().remove(SemanticType.INTERSECTION);
    first.resetDefinition();
    var operands = new LinkedHashSet<org.semanticweb.owlapi.model.OWLClassExpression>();
    operands.add(identityRequirement(owl, first, resolve));
    for (var operand : syntax.getOperands()) operands.add(identityRequirement(owl, operand, resolve));
    var f = owl.manager.getOWLDataFactory();
    return switch (syntax.getExpressionType()) {
      case INTERSECTION -> f.getOWLObjectIntersectionOf(operands);
      case UNION -> f.getOWLObjectUnionOf(operands);
      default -> throw new KlabValidationException("Missing boolean identity requirement grouping");
    };
  }

  /** Unsettled bearer contributions are durable evidence, not successful alignment. */
  public static List<String> unresolvedDescriptionBearers(OWL owl, Concept owner) {
    if (owl == null || owl.manager == null || owner == null || owner.getUrn() == null) return List.of();
    var type = owl.getOWLClass(owner);
    if (type == null) return List.of();
    return owl.manager.ontologies().flatMap(o -> o.getAnnotationAssertionAxioms(type.getIRI()).stream())
        .filter(a -> a.getProperty().getIRI().equals(DescriptionBearerSupport.TARGET_STATUS))
        .map(a -> a.getValue()).filter(org.semanticweb.owlapi.model.OWLLiteral.class::isInstance)
        .map(org.semanticweb.owlapi.model.OWLLiteral.class::cast).map(v -> v.getLiteral())
        .filter(v -> v.contains("UNRESOLVED")).distinct().sorted()
        .map(v -> "DESCRIPTION_BEARER_UNRESOLVED: " + owner.getUrn() + " describes " + v).toList();
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

  /** Observable subtype flags need not carry the redundant OBSERVABLE bit. */
  static boolean isObservableDescriptionTarget(Concept concept) {
    return concept != null && !concept.is(SemanticType.NOTHING)
        && (concept.is(SemanticType.OBSERVABLE) || concept.is(SemanticType.CONFIGURATION)
            || concept.is(SemanticType.EXTENT)
            || SemanticType.BASE_OBSERVABLE_TYPES.stream().anyMatch(concept::is));
  }

  private static Concept requireObservableDescription(
      Function<KimConcept, Concept> resolve, KimConcept syntax) {
    var concept = syntax == null ? null : resolve.apply(syntax);
    if (!isObservableDescriptionTarget(concept))
      throw new KlabValidationException("Expected a resolved OBSERVABLE target: "
          + (syntax == null ? "missing operand" : syntax.getUrn()));
    return concept;
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
