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
    final StructuralClosure closure;
    try {
      synchronized (owl) {
        closure = owl.hasReasoner()
            ? owl.identityRequirementCache().resolve(owl, owl.getOWLClass(head),
                () -> structuralClosure(owl.getOWLClass(head)))
            : structuralClosure(owl.getOWLClass(head));
      }
    } catch (RuntimeException unavailable) { return true; }
    var requirements = closure.requirements();
    var inheritedIdentities = closure.identities();
    if (requirements.isEmpty()) return false;
    if (SemanticType.isDependent(head.getType()))
      return !bearerSatisfies(head, syntax, requirements);
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

  /** Pure OWL structure: no concept status, provider answer or eligibility decision is retained. */
  record StructuralClosure(Set<OWLClassExpression> requirements, Set<OWLClassExpression> identities) {
    StructuralClosure { requirements = Set.copyOf(requirements); identities = Set.copyOf(identities); }
  }

  private StructuralClosure structuralClosure(OWLClass head) {
    var required = new LinkedHashSet<OWLClassExpression>();
    var identities = new LinkedHashSet<OWLClassExpression>();
    var visited = new HashSet<OWLClass>();
    collect(head, required, identities, visited);
    if (hasIdentityRequirements()) {
      if (!owl.hasReasoner()) throw new IllegalStateException("Identity hierarchy unavailable");
      if (owl.isPrecomputed(org.semanticweb.owlapi.reasoner.InferenceType.CLASS_HIERARCHY)) {
        var parents = new LinkedHashSet<>(owl.getSuperClasses(head, false).getFlattened());
        parents.addAll(owl.getEquivalentClasses(head).getEntities());
        for (var parent : parents) if (!parent.isOWLThing() && !parent.isOWLNothing())
          collect(parent, required, identities, visited);
      } else {
        // A new generated class invalidates global classification. Prove only inheritance from
        // owners capable of contributing a requirement or a concrete inherited identity.
        for (var owner : owl.reasonerClassSignature()) {
          if (owner.isOWLThing() || owner.isOWLNothing() || owner.equals(head) || visited.contains(owner)) continue;
          var ownerRequirements = new LinkedHashSet<OWLClassExpression>();
          var ownerIdentities = new LinkedHashSet<OWLClassExpression>();
          collect(owner, ownerRequirements, ownerIdentities, new HashSet<>());
          if ((!ownerRequirements.isEmpty() || !ownerIdentities.isEmpty())
              && owl.isEntailed(owl.manager.getOWLDataFactory().getOWLSubClassOfAxiom(head, owner))) {
            // Keep the root walk's visited set: independently merging an owner's walk can turn
            // a harmless cyclic union into a new branch-dependent requirement.
            collect(owner, required, identities, visited);
          }
        }
      }
    }
    return new StructuralClosure(required, identities);
  }

  /** Owned by one OWL actor, fenced by the same mutation/flush boundaries as structural proofs. */
  static final class Cache {
    static final int CAPACITY = 512;
    private final java.util.concurrent.atomic.AtomicLong revision = new java.util.concurrent.atomic.AtomicLong();
    private final OWLOntologyChangeListener listener = changes -> changed();
    private OWLOntologyManager manager;
    private Object reasoner;
    private OWLOntology root;
    private Set<OWLOntology> ontologies;
    private long retained = -1, hits, computations;
    private final Map<OWLClass, StructuralClosure> answers = new LinkedHashMap<>(32, .75f, true) {
      @Override protected boolean removeEldestEntry(Map.Entry<OWLClass, StructuralClosure> entry) {
        return size() > CAPACITY;
      }
    };
    void changed() { revision.incrementAndGet(); }
    void reset() {
      if (manager != null) manager.removeOntologyChangeListener(listener);
      manager = null; reasoner = null; root = null; ontologies = null;
      answers.clear(); retained = -1; changed();
    }
    private long prepare(OWL owl) {
      if (manager != owl.manager) { reset(); manager = owl.manager; manager.addOntologyChangeListener(listener); }
      var current = manager.getOntologies();
      var backend = owl.structuralReasonerIdentity();
      var currentRoot = backend instanceof org.semanticweb.owlapi.reasoner.OWLReasoner dl ? dl.getRootOntology() : null;
      if (ontologies == null || current.size() != ontologies.size()
          || current.stream().anyMatch(ontology -> !ontologies.contains(ontology))
          || reasoner != backend || root != currentRoot) {
        var owned = Collections.newSetFromMap(new IdentityHashMap<OWLOntology, Boolean>());
        owned.addAll(current); ontologies = owned; reasoner = backend; root = currentRoot; changed();
      }
      long now = revision.get();
      if (retained != now) { answers.clear(); retained = now; }
      return now;
    }
    StructuralClosure resolve(OWL owl, OWLClass head, java.util.function.Supplier<StructuralClosure> compute) {
      if (!Thread.holdsLock(owl)) throw new IllegalStateException("Identity cache requires the OWL monitor");
      owl.prepareIdentityHierarchy(); // Flush pending changes before capturing the cache generation.
      long before = prepare(owl);
      var cached = answers.get(head);
      if (cached != null && before == prepare(owl)) { owl.requireAvailableClosure(); hits++; return cached; }
      computations++;
      var result = compute.get();
      if (before != prepare(owl)) throw new ConcurrentModificationException("Identity hierarchy changed during projection");
      owl.requireAvailableClosure();
      if (result.requirements().size() + result.identities().size() <= 4096) answers.put(head, result);
      return result;
    }
    Map<String, Long> metrics() { return Map.of("entries", (long) answers.size(), "hits", hits, "computations", computations); }
  }

  private boolean bearerSatisfies(Concept head, KimConcept syntax,
      Set<OWLClassExpression> requirements) {
    try {
      if (!owl.hasReasoner()) return false;
      var aligned = new LinkedHashSet<OWLClassExpression>();
      var domains = new LinkedHashSet<OWLClassExpression>();
      var universals = new ArrayList<OWLSemanticClauseSupport.BearerConstraint>();
      var independent = new ArrayList<OWLSemanticClauseSupport.BearerConstraint>();
      var support = new OWLSemanticClauseSupport(owl);
      boolean alignedWitness = false;
      for (var bound : support.bearerConstraints(head)) {
        domains.add(bound.filler());
        if (bound.applicability()) continue;
        if (bound.semantic()) {
          aligned.add(bound.filler());
          alignedWitness |= needsWitness(bound.restriction());
        } else if (bound.restriction() instanceof OWLObjectAllValuesFrom) {
          universals.add(bound);
        } else if (needsWitness(bound.restriction())) {
          // Imported existentials may name DIFFERENT witnesses. They do not
          // establish one bearer specializing the intersection of their fillers.
          independent.add(bound);
        }
      }
      var explicitBearers = new LinkedHashSet<OWLClassExpression>();
      // Explicit inherency may be on a wrapper around the atomic observable.
      var current = syntax;
      while (current != null) {
        if (current.getInherent() != null) {
          var explicit = bearerExpression(current.getInherent());
          if (explicit == null) return false;
          explicitBearers.add(explicit);
        }
        current = current.getObservable();
      }
      if (!explicitBearers.isEmpty()) {
        var explicit = intersection(explicitBearers);
        if (domains.stream().anyMatch(bound -> !entailed(explicit, bound))) return false;
        aligned.add(explicit);
        alignedWitness = true;
      }
      var inherent = owl.manager.getOWLDataFactory().getOWLObjectProperty(
          IRI.create("http://integratedmodelling.org/odo#isInherentTo"));
      boolean checked = false;
      if (alignedWitness || independent.isEmpty() && (!aligned.isEmpty() || !universals.isEmpty())) {
        var candidate = new LinkedHashSet<>(aligned);
        for (var universal : universals)
          if (support.subPropertyOf(inherent, universal.property())) candidate.add(universal.filler());
        if (candidate.isEmpty() || !satisfiesBearer(intersection(candidate), requirements)) return false;
        checked = true;
      }
      for (var witness : independent) {
        var candidate = new LinkedHashSet<>(aligned);
        candidate.add(witness.filler());
        for (var universal : universals)
          if (support.subPropertyOf(witness.property(), universal.property())) candidate.add(universal.filler());
        if (!satisfiesBearer(intersection(candidate), requirements)) return false;
        checked = true;
      }
      return checked;
    } catch (RuntimeException unavailable) { return false; }
  }

  private boolean needsWitness(OWLClassExpression restriction) {
    return restriction instanceof OWLObjectSomeValuesFrom
        || restriction instanceof OWLObjectMinCardinality minimum && minimum.getCardinality() > 0
        || restriction instanceof OWLObjectExactCardinality exact && exact.getCardinality() > 0;
  }

  private OWLClassExpression intersection(Collection<OWLClassExpression> expressions) {
    return expressions.size() == 1 ? expressions.iterator().next()
        : owl.manager.getOWLDataFactory().getOWLObjectIntersectionOf(new HashSet<>(expressions));
  }

  private OWLClassExpression bearerExpression(KimConcept syntax) {
    if (syntax.getSemanticModifier() != null || syntax.isPattern()
        || syntax.is(SemanticType.ANY) || syntax.is(SemanticType.ALL) || syntax.is(SemanticType.NONE))
      return null;
    // Use the entire compiled expression, including boolean alternatives, roles,
    // attributes and other restrictions. Never replace it with its atomic head.
    var materialized = owl.getMaterializedClass(syntax.getUrn());
    if (materialized != null) return materialized;
    if (syntax.getUrn() != null) for (var ontology : owl.getOntologies(true)) {
      var id = ontology.getIdForDefinition(syntax.getUrn());
      if (id != null) return owl.getOWLClass(ontology.getConcept(id));
    }
    if (!syntax.getOperands().isEmpty() || !syntax.getRoles().isEmpty()
        || !syntax.getModifiers().isEmpty() || !syntax.getValueOperators().isEmpty()) return null;
    var resolved = syntax.getObservable() == null ? resolve(syntax) : null;
    var base = syntax.getObservable() == null
        ? resolved == null ? null : owl.getOWLClass(resolved)
        : bearerExpression(syntax.getObservable());
    if (base == null) return null;
    var terms = new LinkedHashSet<OWLClassExpression>(); terms.add(base);
    var f = owl.manager.getOWLDataFactory();
    var identities = new LinkedHashSet<OWLClassExpression>();
    for (var trait : syntax.getTraits()) {
      // Only untransformed, asserted identity traits are bearer evidence.
      var identity = trait.getSemanticModifier() == null && trait.getObservable() == null
          && trait.getTraits().isEmpty() && trait.getOperands().isEmpty() ? resolve(trait) : null;
      if (identity != null && identity.is(SemanticType.IDENTITY) && !trait.isPattern()
          && !trait.is(SemanticType.ANY) && !trait.is(SemanticType.ALL) && !trait.is(SemanticType.NONE))
        identities.add(owl.getOWLClass(identity));
      else return null; // Unmaterialized complex evidence must remain unresolved.
    }
    if (!identities.isEmpty()) terms.add(f.getOWLObjectSomeValuesFrom(identityProperty(),
        identities.size() == 1 ? identities.iterator().next() : f.getOWLObjectUnionOf(identities)));
    return intersection(terms);
  }

  private OWLObjectProperty identityProperty() {
    return owl.manager.getOWLDataFactory().getOWLObjectProperty(
        IRI.create("http://integratedmodelling.org/odo#hasIdentity"));
  }

  private boolean entailed(OWLClassExpression child, OWLClassExpression parent) {
    return owl.isEntailed(owl.manager.getOWLDataFactory().getOWLSubClassOfAxiom(child, parent));
  }

  private boolean satisfiesBearer(OWLClassExpression bearer, Set<OWLClassExpression> requirements) {
    // Prevent vacuous subclass proofs from inconsistent bearer intersections.
    if (!owl.isSatisfiable(bearer)) return false;
    var evidence = new LinkedHashSet<OWLClassExpression>();
    var visited = new HashSet<OWLClass>();
    collectIdentityEvidence(bearer, evidence, visited);
    var necessary = new LinkedHashSet<>(owl.getSuperClasses(bearer, false).getFlattened());
    necessary.addAll(owl.getEquivalentClasses(bearer).getEntities());
    for (var parent : necessary) if (!parent.isOWLThing() && !parent.isOWLNothing())
      collectIdentityEvidence(parent, evidence, visited);
    var categories = new LinkedHashSet<Concept>();
    for (var filler : evidence) for (var named : filler.getClassesInSignature()) {
      var category = owl.getConceptFor(named);
      if (category != null && category.is(SemanticType.IDENTITY)
          && !category.is(SemanticType.NOTHING) && !category.is(SemanticType.ANY)) categories.add(category);
    }
    var f = owl.manager.getOWLDataFactory();
    for (var required : requirements) {
      var admittedCategories = categories.stream().filter(identity -> admitted(identity, required))
          .map(owl::getOWLClass).collect(java.util.stream.Collectors.toSet());
      if (admittedCategories.isEmpty()) return false;
      var admitted = admittedCategories.size() == 1 ? admittedCategories.iterator().next()
          : f.getOWLObjectUnionOf(admittedCategories);
      // One witness must establish both the full requirement and admission. This
      // preserves OR, AND, and authority checks without flattening witness scope.
      var filler = f.getOWLObjectIntersectionOf(required, admitted);
      if (!entailed(bearer, f.getOWLObjectSomeValuesFrom(identityProperty(), filler))) return false;
    }
    return true;
  }

  private void collectIdentityEvidence(OWLClassExpression expression,
      Set<OWLClassExpression> evidence, Set<OWLClass> visited) {
    if (expression instanceof OWLClass named) {
      if (!visited.add(named)) return;
      for (var ontology : owl.manager.getOntologies()) {
        for (var axiom : ontology.getSubClassAxiomsForSubClass(named))
          collectIdentityEvidence(axiom.getSuperClass(), evidence, visited);
        for (var axiom : ontology.getEquivalentClassesAxioms(named))
          for (var equivalent : axiom.getClassExpressionsMinus(named))
            collectIdentityEvidence(equivalent, evidence, visited);
      }
    } else if (expression instanceof OWLNaryBooleanClassExpression logical) {
      // These are candidate admission categories, not flattened proof. The final
      // existential entailment must still hold for the COMPLETE bearer expression.
      for (var branch : logical.getOperands()) collectIdentityEvidence(branch, evidence, visited);
    } else if (expression instanceof OWLObjectSomeValuesFrom some
        && (some.getProperty().equals(identityProperty())
            || new OWLSemanticClauseSupport(owl).subPropertyOf(some.getProperty(), identityProperty())))
      evidence.add(some.getFiller());
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
