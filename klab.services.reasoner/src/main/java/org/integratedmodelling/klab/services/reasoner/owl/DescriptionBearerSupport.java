package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.*;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.semanticweb.owlapi.model.*;

/** Stages local necessary bearer restrictions, preserving imported OWL witness semantics. */
final class DescriptionBearerSupport {
  static final IRI PROVENANCE = IRI.create("urn:klab:validation:bearer-provenance");
  static final IRI COMPATIBILITY = IRI.create("urn:klab:validation:bearer-compatibility");
  static final IRI TARGET_STATUS = IRI.create("urn:klab:validation:description-bearer-target-status");
  private DescriptionBearerSupport() {}

  static List<OWLAxiom> prepare(OWL owl, Concept owner, Concept authoredBearer,
      Collection<Concept> described, Collection<Concept> applicables) {
    return prepare(owl, owner, authoredBearer, described, applicables, List.of());
  }

  static List<OWLAxiom> prepare(OWL owl, Concept owner, Concept authoredBearer,
      Collection<Concept> described, Collection<Concept> applicables,
      Collection<Concept> independentlyCheckedDescriptions) {
    var support = new OWLSemanticClauseSupport(owl);
    var own = new ArrayList<>(support.bearerConstraints(owner));
    var f = owl.manager.getOWLDataFactory();
    var property = owl.getProperty("odo:isInherentTo");
    if (property == null && own.isEmpty() && authoredBearer == null
        && (applicables.isEmpty() || !SemanticType.isDependent(owner.getType()))
        && described.isEmpty() && independentlyCheckedDescriptions.isEmpty()) return List.of();
    if (property == null) throw new KlabValidationException("Missing ontology property odo:isInherentTo");
    var inherent = property.getOWLEntity().asOWLObjectProperty();
    var targetStatuses = new ArrayList<String>();
    for (var target : independentlyCheckedDescriptions) {
      var constraints = support.bearerConstraints(target);
      var semantic = semanticFillers(constraints, inherent);
      validateWitnesses(support, constraints, semantic, inherent, target.getUrn());
      var targetStatus = validateConjunction(owl, support, target, constraints, semantic, inherent);
      if (constraints.stream().anyMatch(b -> !b.semantic()))
        targetStatus = OWLSemanticClauseSupport.Compatibility.UNRESOLVED;
      targetStatuses.add(target.getUrn() + " " + target.getType()
          + " : INDEPENDENT_TARGET_" + targetStatus.name());
    }
    if (authoredBearer != null)
      own.add(authored(f, inherent, owl.getOWLClass(authoredBearer), owner.getUrn() + " within", false));
    if (!applicables.isEmpty() && SemanticType.isDependent(owner.getType())) {
      var fillers = applicables.stream().map(owl::getOWLClass).collect(java.util.stream.Collectors.toSet());
      var applies = owl.getProperty("odo:appliesTo");
      if (applies == null) throw new KlabValidationException("Missing ontology property odo:appliesTo");
      own.add(authored(f, applies.getOWLEntity().asOWLObjectProperty(),
          fillers.size() == 1 ? fillers.iterator().next() : f.getOWLObjectUnionOf(fillers),
          owner.getUrn() + " applies to", true));
    }
    boolean unresolvedTarget = false;
    var evidence = new ArrayList<>(own);
    var derived = new LinkedHashSet<>(semanticFillers(own, inherent));
    for (var target : described) {
      var constraints = support.bearerConstraints(target);
      var semantic = semanticFillers(constraints, inherent);
      // First check the target's own restrictions independently. A substantial
      // characterized directly is itself the describing quality's bearer type;
      // its host (if any) is not substituted for that substantial.
      validateWitnesses(support, constraints, semantic, inherent, target.getUrn());
      validateConjunction(owl, support, target, constraints, semantic, inherent);
      boolean substantial = target.is(SemanticType.SUBJECT) || target.is(SemanticType.AGENT);
      Set<OWLClassExpression> contribution = substantial
          ? Set.of(owl.getOWLClass(target)) : semantic;
      boolean unresolved = contribution.isEmpty();
      unresolvedTarget |= unresolved;
      targetStatuses.add(target.getUrn() + " " + target.getType() + " : "
          + (unresolved ? "UNRESOLVED: no established universal bearer contribution"
              : substantial ? "SUBSTANTIAL_TARGET_BEARER" : "UNIVERSAL_BEARER_CONTRIBUTION"));
      evidence.addAll(constraints);
      derived.addAll(contribution);
    }
    // Check inherited-only declarations as well as local descriptions and applicability.
    validateWitnesses(support, own, derived, inherent, owner.getUrn());
    var status = validateConjunction(owl, support, owner, own, derived, inherent);
    if (evidence.isEmpty() && targetStatuses.isEmpty()) return List.of();
    var source = owl.getOWLClass(owner).getIRI();
    var result = new ArrayList<OWLAxiom>();
    for (var targetStatus : targetStatuses)
      result.add(f.getOWLAnnotationAssertionAxiom(f.getOWLAnnotationProperty(TARGET_STATUS),
          source, f.getOWLLiteral(targetStatus)));
    if (derived.isEmpty()) {
      result.add(f.getOWLAnnotationAssertionAxiom(f.getOWLAnnotationProperty(COMPATIBILITY),
          source, f.getOWLLiteral("UNRESOLVED")));
      return result;
    }
    if (unresolvedTarget) status = OWLSemanticClauseSupport.Compatibility.UNRESOLVED;
    // Imported untagged constraints have not established k.LAB bearer alignment.
    if (evidence.stream().anyMatch(b -> !b.semantic())) status = OWLSemanticClauseSupport.Compatibility.UNRESOLVED;
    var filler = derived.size() == 1 ? derived.iterator().next() : f.getOWLObjectIntersectionOf(derived);
    var annotations = new LinkedHashSet<OWLAnnotation>();
    preserveProvenance(owl, owl.getOWLClass(owner), new HashSet<>(), annotations);
    for (var target : described) preserveProvenance(owl, owl.getOWLClass(target), new HashSet<>(), annotations);
    for (var bound : evidence)
      annotations.add(f.getOWLAnnotation(f.getOWLAnnotationProperty(PROVENANCE), f.getOWLLiteral(
          (bound.inherited() ? "inherited:" : "authored:") + bound.source() + " -> "
              + bound.restriction() + " [klab-universal=" + bound.semantic() + "]")));
    annotations.add(f.getOWLAnnotation(f.getOWLAnnotationProperty(PROVENANCE),
        f.getOWLLiteral("derived:description-bearer-intersection")));
    annotations.add(f.getOWLAnnotation(f.getOWLAnnotationProperty(COMPATIBILITY), f.getOWLLiteral(status.name())));
    annotations.add(f.getOWLAnnotation(f.getOWLAnnotationProperty(OWLSemanticClauseSupport.UNIVERSAL_BEARER), f.getOWLLiteral(true)));
    result.add(f.getOWLSubClassOfAxiom(owl.getOWLClass(owner), f.getOWLObjectSomeValuesFrom(inherent, filler), annotations));
    result.add(f.getOWLSubClassOfAxiom(owl.getOWLClass(owner), f.getOWLObjectAllValuesFrom(inherent, filler), annotations));
    return result;
  }

  private static OWLSemanticClauseSupport.Compatibility validateConjunction(OWL owl,
      OWLSemanticClauseSupport support, Concept owner,
      Collection<OWLSemanticClauseSupport.BearerConstraint> constraints,
      Set<OWLClassExpression> semantic, OWLObjectPropertyExpression inherent) {
    var conjunction = new LinkedHashSet<OWLClassExpression>();
    // Include the owner itself: cardinalities, inherited/equivalent expressions and property
    // axioms remain in scope even when they are not projected as semantic bearer clauses.
    conjunction.add(owl.getOWLClass(owner));
    constraints.forEach(b -> conjunction.add(b.restriction()));
    if (!semantic.isEmpty()) {
      var f = owl.manager.getOWLDataFactory();
      var filler = semantic.size() == 1 ? semantic.iterator().next() : f.getOWLObjectIntersectionOf(semantic);
      conjunction.add(f.getOWLObjectSomeValuesFrom(inherent, filler));
      conjunction.add(f.getOWLObjectAllValuesFrom(inherent, filler));
    }
    var status = support.bearerCompatibility(conjunction);
    if (status == OWLSemanticClauseSupport.Compatibility.IMPOSSIBLE)
      throw new KlabValidationException("DESCRIPTION_BEARER_CONFLICT: " + owner.getUrn()
          + " has a proven impossible complete staged restriction conjunction");
    return status;
  }

  private static OWLSemanticClauseSupport.BearerConstraint authored(OWLDataFactory f,
      OWLObjectPropertyExpression property, OWLClassExpression filler, String source, boolean applicability) {
    return new OWLSemanticClauseSupport.BearerConstraint(filler, source, false, property,
        f.getOWLObjectSomeValuesFrom(property, filler), true, applicability);
  }

  private static Set<OWLClassExpression> semanticFillers(
      Collection<OWLSemanticClauseSupport.BearerConstraint> constraints, OWLObjectPropertyExpression inherent) {
    var result = new LinkedHashSet<OWLClassExpression>();
    for (var bound : constraints)
      if (bound.semantic() && (bound.property().equals(inherent) || bound.applicability())) result.add(bound.filler());
    return result;
  }

  private static void validateWitnesses(OWLSemanticClauseSupport support,
      Collection<OWLSemanticClauseSupport.BearerConstraint> constraints,
      Collection<OWLClassExpression> semantic, OWLObjectPropertyExpression inherent, String source) {
    if (!semantic.isEmpty()) {
      var witness = new ArrayList<>(semantic);
      for (var bound : constraints)
        if (bound.restriction() instanceof OWLObjectAllValuesFrom
            && support.subPropertyOf(inherent, bound.property())) witness.add(bound.filler());
      requirePossible(support, witness, source);
    }
    // Each existential/minimum-cardinality requirement may use a DIFFERENT witness.
    for (var bound : constraints) {
      boolean needsWitness = bound.restriction() instanceof OWLObjectSomeValuesFrom
          || bound.restriction() instanceof OWLObjectMinCardinality min && min.getCardinality() > 0
          || bound.restriction() instanceof OWLObjectExactCardinality exact && exact.getCardinality() > 0;
      if (!needsWitness) continue;
      var witness = new ArrayList<OWLClassExpression>();
      witness.add(bound.filler());
      for (var universal : constraints)
        if (universal.restriction() instanceof OWLObjectAllValuesFrom
            && support.subPropertyOf(bound.property(), universal.property())) witness.add(universal.filler());
      if (support.subPropertyOf(bound.property(), inherent)) witness.addAll(semantic);
      requirePossible(support, witness, source);
    }
  }

  private static void requirePossible(OWLSemanticClauseSupport support,
      Collection<OWLClassExpression> fillers, String source) {
    if (support.bearerCompatibility(fillers) == OWLSemanticClauseSupport.Compatibility.IMPOSSIBLE)
      throw new KlabValidationException("DESCRIPTION_BEARER_CONFLICT: " + source
          + " has proven incompatible constraints for a required bearer: " + fillers
          + "; cross-bearer interpretation requires an explicit relationship");
  }

  private static void preserveProvenance(OWL owl, OWLClassExpression expression,
      Set<OWLClassExpression> visited, Set<OWLAnnotation> annotations) {
    if (!visited.add(expression)) return;
    if (expression instanceof OWLClass named) {
      for (var ontology : owl.manager.getOntologies()) {
        for (var axiom : ontology.getSubClassAxiomsForSubClass(named)) {
          axiom.annotations().filter(a -> a.getProperty().getIRI().equals(PROVENANCE)).forEach(annotations::add);
          preserveProvenance(owl, axiom.getSuperClass(), visited, annotations);
        }
        for (var axiom : ontology.getEquivalentClassesAxioms(named))
          axiom.classExpressions().forEach(c -> preserveProvenance(owl, c, visited, annotations));
      }
    } else if (expression instanceof OWLObjectIntersectionOf intersection)
      intersection.operands().forEach(c -> preserveProvenance(owl, c, visited, annotations));
  }
}
