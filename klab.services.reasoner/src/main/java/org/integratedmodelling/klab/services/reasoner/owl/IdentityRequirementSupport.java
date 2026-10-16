package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.*;
import java.util.function.BiPredicate;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.ExpressionStatus;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.semanticweb.owlapi.model.*;

/** Resolution eligibility, not OWL class abstractness or creation of an observation.
 * The canonical isAbstract annotation continues to mean independently authored abstractness.
 * Necessary identity restrictions add dischargeable abstractness only on detached projections.
 */
public final class IdentityRequirementSupport {
  private final OWL owl;
  private final BiPredicate<Concept, Concept> eligible;

  /** Authority identities require the backend's captured binding admission check. */
  public IdentityRequirementSupport(OWL owl) {
    this(owl, (root, identity) -> !identity.is(SemanticType.AUTHORITY_IDENTITY));
  }

  public IdentityRequirementSupport(OWL owl, BiPredicate<Concept, Concept> eligible) {
    this.owl = Objects.requireNonNull(owl);
    this.eligible = Objects.requireNonNull(eligible);
  }

  public ConceptImpl project(ConceptImpl concept, KimConcept syntax) {
    return concept.withExpressionStatus(syntax, owl::getConcept, this::abstractHead);
  }

  /** Atomic resolution has the same status as an unqualified expression. */
  public ConceptImpl project(ConceptImpl concept) {
    var syntax = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();
    syntax.setName(concept.getNamespace() + ":" + concept.getName());
    syntax.setType(concept.getType());
    var projected = project(concept, syntax);
    // Unchanged atomic views may be live core aliases; keep their refresh behavior.
    return projected.isAbstract() == concept.isAbstract() ? concept : projected;
  }

  private boolean abstractHead(KimConcept syntax) {
    var identities = new LinkedHashSet<Concept>();
    var current = syntax;
    while (true) {
      if (current.getSemanticModifier() != null) return false; // Existing unary boundary.
      for (var trait : current.getTraits()) {
        var identity = trait.getSemanticModifier() == null && trait.getObservable() == null
            && trait.getTraits().isEmpty() && trait.getOperands().isEmpty()
            ? resolve(trait) : trait.getUrn() == null ? null : owl.getConcept(trait.getUrn());
        if (!trait.is(SemanticType.ANY) && !trait.is(SemanticType.ALL) && !trait.is(SemanticType.NONE)
            && !trait.isPattern() && identity != null && identity.is(SemanticType.IDENTITY)
            && !ExpressionStatus.evaluate(trait, ExpressionStatus::declarationTypes, this::abstractHead)
                .contains(SemanticType.ABSTRACT)) identities.add(identity);
        if ((trait.is(SemanticType.ATTRIBUTE) || identity != null && identity.is(SemanticType.ATTRIBUTE))
            && ExpressionStatus.evaluate(trait, ExpressionStatus::declarationTypes, this::abstractHead)
                .contains(SemanticType.ABSTRACT)) return true;
      }
      if (current.getObservable() == null) break;
      current = current.getObservable();
    }
    var head = resolve(current);
    if (head == null) return ExpressionStatus.declarationTypes(current).contains(SemanticType.ABSTRACT);
    // This is the canonical annotation, never the effective status of a cached projection.
    if (head.isAbstract()) return true;
    if (!head.is(SemanticType.OBSERVABLE)) return false;
    var requirements = new LinkedHashSet<OWLClassExpression>();
    var inheritedIdentities = new LinkedHashSet<OWLClassExpression>();
    var visited = new HashSet<OWLClass>();
    var headClass = owl.getOWLClass(head);
    collect(headClass, requirements, inheritedIdentities, visited);
    // An asserted walk cannot discover a necessary superclass inferred from, e.g.,
    // two independently inherited conditions. Never certify absence without closure.
    if (hasIdentityRequirements()) {
      synchronized (owl) {
        if (!owl.hasReasoner()) return true;
        try {
          var necessary = new LinkedHashSet<>(owl.getSuperClasses(headClass, false).getFlattened());
          necessary.addAll(owl.getEquivalentClasses(headClass).getEntities());
          for (var parent : necessary) if (!parent.isOWLThing() && !parent.isOWLNothing())
            collect(parent, requirements, inheritedIdentities, visited);
        } catch (RuntimeException unavailable) { return true; }
      }
    }
    if (requirements.isEmpty()) return false;
    for (var inherited : inheritedIdentities) {
      // A named, concrete inherited identity is evidence. A union of possible fillers
      // or an anonymous existential does not supply a concrete named identity.
      if (inherited instanceof OWLClass named) {
        var identity = owl.getConceptFor(named);
        if (identity != null) identities.add(identity);
      }
    }
    return requirements.stream().anyMatch(required -> identities.stream()
        .noneMatch(identity -> concrete(identity) && satisfies(identity, required)));
  }

  private Concept resolve(KimConcept syntax) {
    if (syntax.getName() != null) return owl.getConcept(syntax.getName());
    return syntax.getUrn() == null ? null : owl.getConcept(syntax.getUrn());
  }

  private boolean concrete(Concept identity) {
    if (!identity.is(SemanticType.IDENTITY) || identity.isAbstract()
        || identity.is(SemanticType.NOTHING) || identity.is(SemanticType.ANY)) return false;
    try { return owl.isSatisfiable(owl.getOWLClass(identity)); }
    catch (RuntimeException unavailable) { return false; }
  }

  private boolean satisfies(Concept identity, OWLClassExpression required) {
    // Prove compatibility with the entire filler before checking admission. In
    // particular C <= (A or B) need not imply either C <= A or C <= B separately.
    return proves(identity, required) && admitted(identity, required);
  }

  private boolean admitted(Concept identity, OWLClassExpression required) {
    if (required instanceof OWLObjectUnionOf union)
      return union.getOperands().stream().anyMatch(branch -> satisfies(identity, branch))
          // With no proved branch, every alternative must permit this identity.
          // An ineligible authority branch cannot be bypassed by an unrelated one.
          || union.getOperands().stream().allMatch(branch -> admitted(identity, branch));
    if (required instanceof OWLObjectIntersectionOf intersection)
      return intersection.getOperands().stream().allMatch(branch -> admitted(identity, branch));
    if (!(required instanceof OWLClass named) || named.isOWLThing() || named.isOWLNothing()) return false;
    var root = owl.getConceptFor(named);
    if (root == null || !root.is(SemanticType.IDENTITY)) return false;
    return eligible.test(root, identity);
  }

  /** No namespace matching, and no vacuous subsumption of an inconsistent identity. */
  public boolean proves(Concept child, Concept parent) {
    return proves(child, owl.getOWLClass(parent));
  }

  private boolean proves(Concept child, OWLClassExpression parent) {
    try {
      if (!owl.hasReasoner()) return false;
      return owl.isEntailed(owl.manager.getOWLDataFactory().getOWLSubClassOfAxiom(
          owl.getOWLClass(child), parent));
    } catch (RuntimeException unavailable) { return false; }
  }

  private boolean hasIdentityRequirements() {
    return owl.manager.ontologies().flatMap(ontology -> ontology.axioms())
        .flatMap(axiom -> axiom.getNestedClassExpressions().stream())
        .anyMatch(expression -> expression instanceof OWLObjectSomeValuesFrom some
            && !some.getProperty().isAnonymous()
            && some.getProperty().asOWLObjectProperty().getIRI().toString()
                .equals("http://integratedmodelling.org/odo#requiresIdentity"));
  }

  private void collect(OWLClassExpression expression, Set<OWLClassExpression> required,
      Set<OWLClassExpression> identities, Set<OWLClass> visited) {
    if (expression instanceof OWLClass named) {
      if (!visited.add(named)) return;
      for (var ontology : owl.manager.getOntologies()) {
        for (var axiom : ontology.getSubClassAxiomsForSubClass(named))
          collect(axiom.getSuperClass(), required, identities, visited);
        for (var axiom : ontology.getEquivalentClassesAxioms(named))
          for (var equivalent : axiom.getClassExpressions())
            if (!equivalent.equals(named)) collect(equivalent, required, identities, visited);
      }
    } else if (expression instanceof OWLObjectIntersectionOf intersection) {
      for (var branch : intersection.getOperands()) collect(branch, required, identities, visited);
    } else if (expression instanceof OWLObjectSomeValuesFrom some && !some.getProperty().isAnonymous()) {
      var property = some.getProperty().asOWLObjectProperty().getIRI().toString();
      if (property.equals("http://integratedmodelling.org/odo#requiresIdentity")) required.add(some.getFiller());
      if (property.equals("http://integratedmodelling.org/odo#hasIdentity")) identities.add(some.getFiller());
    } else if (expression instanceof OWLObjectUnionOf union) {
      // Preserve disjunctive superclass scope. A branch-dependent requirement cannot
      // be discharged by flattening all branches or choosing one without evidence.
      var branchRequirements = new LinkedHashSet<OWLClassExpression>();
      for (var branch : union.getOperands())
        collect(branch, branchRequirements, new LinkedHashSet<>(), new HashSet<>(visited));
      if (!branchRequirements.isEmpty()) required.add(owl.manager.getOWLDataFactory().getOWLNothing());
    }
  }
}
