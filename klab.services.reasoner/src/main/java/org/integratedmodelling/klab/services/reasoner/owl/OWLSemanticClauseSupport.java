package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.indexing.SemanticClauseSupport;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;

/** Preserves complete OWL fillers: a union is one bound, multiple restrictions are conjunctive. */
public final class OWLSemanticClauseSupport extends SemanticClauseSupport {
  private final OWL owl;

  private record Bound(
      SemanticRole role, OWLClassExpression filler, boolean inherited, boolean applicability) {}

  private final Map<String, List<Bound>> cache =
      new LinkedHashMap<>(64, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Bound>> entry) {
          return size() > 512;
        }
      };
  private static final Map<SemanticRole, String> PROPERTIES =
      Map.ofEntries(
          Map.entry(SemanticRole.INHERENT, NS.IS_INHERENT_TO_PROPERTY),
          Map.entry(SemanticRole.GOAL, NS.HAS_PURPOSE_PROPERTY),
          Map.entry(SemanticRole.COMPRESENT, NS.HAS_COMPRESENT_PROPERTY),
          Map.entry(SemanticRole.CAUSANT, NS.HAS_CAUSANT_PROPERTY),
          Map.entry(SemanticRole.CAUSED, NS.HAS_CAUSED_PROPERTY),
          Map.entry(SemanticRole.ADJACENT, NS.IS_ADJACENT_TO_PROPERTY),
          Map.entry(SemanticRole.COOCCURRENT, NS.OCCURS_DURING_PROPERTY),
          Map.entry(SemanticRole.RELATIONSHIP_SOURCE, NS.IMPLIES_SOURCE_PROPERTY),
          Map.entry(SemanticRole.RELATIONSHIP_TARGET, NS.IMPLIES_DESTINATION_PROPERTY));

  public OWLSemanticClauseSupport(OWL owl) {
    super(owl.reasoner());
    this.owl = owl;
  }

  @Override
  public boolean predicatesCompatible(Collection<Concept> predicates) {
    if (predicates.size() < 2) return true;
    var classes = predicates.stream().map(owl::getOWLClass).toList();
    if (classes.stream().anyMatch(Objects::isNull)) return false;
    if (owl.hasReasoner()) {
      owl.flushReasoner();
      return owl.isSatisfiable(
          owl.manager.getOWLDataFactory().getOWLObjectIntersectionOf(new HashSet<>(classes)));
    }
    // Also support asserted disjointness during bootstrap, before the DL reasoner exists.
    for (var ontology : owl.manager.getOntologies()) {
      for (var axiom : ontology.getAxioms(AxiomType.DISJOINT_CLASSES)) {
        int matched = 0;
        for (var alternative : axiom.getClassExpressions())
          if (classes.stream()
              .anyMatch(candidate -> specializes(candidate, alternative, new HashSet<>())))
            matched++;
        if (matched > 1) return false;
      }
    }
    return true;
  }

  private List<Bound> bounds(Concept concept) {
    synchronized (owl) {
      return cache.computeIfAbsent(
          concept.getUrn(),
          key -> {
            var result = new ArrayList<Bound>();
            var root = owl.getOWLClass(concept);
            if (root == null) throw new IllegalArgumentException("No ontology class for " + key);
            visit(root, false, new HashSet<>(), result);
            // Include inferred superclasses as well as asserted/equivalent class definitions.
            if (owl.hasReasoner()) {
              owl.flushReasoner();
              for (var parent : owl.getSuperClasses(root, false).getFlattened())
                if (!parent.isOWLThing() && !parent.equals(root))
                  visit(parent, true, new HashSet<>(), result);
            }
            return result.stream()
                .distinct()
                .sorted(
                    Comparator.comparing((Bound b) -> b.role().name())
                        .thenComparing(Bound::inherited)
                        .thenComparing(b -> b.filler().toString()))
                .toList();
          });
    }
  }

  private void visit(
      OWLClassExpression expression, boolean inherited, Set<OWLClass> visited, List<Bound> result) {
    if (expression instanceof OWLClass named) {
      if (!visited.add(named)) return;
      var supers = EntitySearcher.getSuperClasses(named, owl.manager.ontologies()).toList();
      var equivalents =
          EntitySearcher.getEquivalentClasses(named, owl.manager.ontologies()).toList();
      for (var parent : supers)
        visit(parent, inherited || parent instanceof OWLClass, visited, result);
      for (var equivalent : equivalents)
        visit(equivalent, inherited || equivalent instanceof OWLClass, visited, result);
    } else if (expression instanceof OWLObjectIntersectionOf intersection) {
      for (var operand : intersection.getOperands())
        visit(operand, inherited || operand instanceof OWLClass, visited, result);
    } else if (expression instanceof OWLQuantifiedRestriction<?> restriction
        && restriction.getFiller() instanceof OWLClassExpression filler) {
      if (!(restriction.getProperty() instanceof OWLObjectPropertyExpression property)) return;
      // Imported vocabularies (e.g. PROV) need not have a k.LAB namespace. Match OWL
      // properties directly, retaining semantic subproperties without resolving unrelated IRIs.
      var properties = new HashSet<OWLObjectPropertyExpression>();
      collectSuperProperties(property, properties);
      for (var entry : PROPERTIES.entrySet())
        if (matchesProperty(properties, entry.getValue()))
          result.add(new Bound(entry.getKey(), filler, inherited, false));
      if (matchesProperty(properties, NS.APPLIES_TO_PROPERTY))
        result.add(new Bound(SemanticRole.INHERENT, filler, inherited, true));
    }
    // A union in the superclass hierarchy does not assert either branch individually.
  }

  private void collectSuperProperties(
      OWLObjectPropertyExpression property, Set<OWLObjectPropertyExpression> properties) {
    if (!properties.add(property)) return;
    EntitySearcher.getSuperProperties(property, owl.manager.ontologies())
        .forEach(parent -> collectSuperProperties(parent, properties));
  }

  private boolean matchesProperty(Set<OWLObjectPropertyExpression> properties, String name) {
    var semanticProperty = owl.getProperty(name);
    return semanticProperty != null && properties.contains(semanticProperty.getOWLEntity());
  }

  @Override
  public boolean hasBounds(Concept owner, SemanticRole role) {
    return bounds(owner).stream().anyMatch(b -> constrains(b, owner, role));
  }

  private boolean constrains(Bound bound, Concept owner, SemanticRole role) {
    return bound.role() == role
        && (!bound.applicability() || SemanticType.isDependent(owner.getType()));
  }

  @Override
  public boolean applicableTo(Concept concept, Concept target) {
    var candidate = owl.getOWLClass(target.isCollective() ? target.singular() : target);
    return candidate != null
        && bounds(concept).stream()
            .filter(Bound::applicability)
            .allMatch(bound -> specializes(candidate, bound.filler(), new HashSet<>()));
  }

  @Override
  public boolean accepts(Concept owner, SemanticRole role, Concept operand) {
    var candidate = owl.getOWLClass(operand.isCollective() ? operand.singular() : operand);
    if (candidate == null) return false;
    return bounds(owner).stream()
        .filter(b -> constrains(b, owner, role))
        .allMatch(b -> specializes(candidate, b.filler(), new HashSet<>()));
  }

  private boolean specializes(
      OWLClassExpression candidate, OWLClassExpression bound, Set<OWLClassExpression> visited) {
    if (candidate.equals(bound) || bound.isOWLThing()) return true;
    if (owl.hasReasoner()
        && owl.isEntailed(owl.manager.getOWLDataFactory().getOWLSubClassOfAxiom(candidate, bound)))
      return true;
    // During worldview loading, the DL reasoner may not yet exist. Check asserted
    // inheritance without weakening unions or multiple independent restrictions.
    if (bound instanceof OWLObjectUnionOf union)
      return union
          .operands()
          .anyMatch(value -> specializes(candidate, value, new HashSet<>(visited)));
    if (bound instanceof OWLObjectIntersectionOf intersection)
      return intersection
          .operands()
          .allMatch(value -> specializes(candidate, value, new HashSet<>(visited)));
    if (!visited.add(candidate)) return false;
    if (candidate instanceof OWLClass named)
      return java.util.stream.Stream.concat(
              EntitySearcher.getSuperClasses(named, owl.manager.ontologies()),
              EntitySearcher.getEquivalentClasses(named, owl.manager.ontologies()))
          .anyMatch(value -> specializes(value, bound, new HashSet<>(visited)));
    if (candidate instanceof OWLObjectIntersectionOf intersection)
      return intersection
          .operands()
          .anyMatch(value -> specializes(value, bound, new HashSet<>(visited)));
    if (candidate instanceof OWLObjectUnionOf union)
      return union.operands().allMatch(value -> specializes(value, bound, new HashSet<>(visited)));
    return false;
  }

  @Override
  public List<SemanticClauseRestriction> clauses(Concept concept) {
    var result = new ArrayList<SemanticClauseRestriction>();
    for (var bound : bounds(concept)) {
      if (bound.applicability() && !SemanticType.isDependent(concept.getType())) continue;
      var clause =
          new SemanticClauseRestriction(
              bound.role(),
              bound.filler() instanceof OWLClass named ? owl.getConceptFor(named) : null,
              bound.inherited());
      var code = new ArrayList<StyledKimToken>();
      if (bound.applicability()) {
        var token = new StyledKimToken();
        token.setValue("applies to");
        code.add(token);
      } else
        Arrays.stream(SemanticLexicalElement.values())
            .filter(m -> m.role == bound.role())
            .findFirst()
            .ifPresent(m -> code.add(StyledKimToken.create(m)));
      style(bound.filler(), code);
      clause.setCode(code);
      result.add(clause);
    }
    return result;
  }

  private void style(OWLClassExpression expression, List<StyledKimToken> code) {
    if (expression instanceof OWLClass named && owl.getConceptFor(named) != null) {
      code.add(StyledKimToken.create(owl.getConceptFor(named)));
    } else if (expression instanceof OWLNaryBooleanClassExpression group) {
      code.add(StyledKimToken.create("("));
      boolean first = true;
      for (var operand : group.operands().sorted(Comparator.comparing(Object::toString)).toList()) {
        if (!first)
          code.add(
              StyledKimToken.create(
                  expression instanceof OWLObjectUnionOf
                      ? BinarySemanticOperator.UNION
                      : BinarySemanticOperator.INTERSECTION));
        style(operand, code);
        first = false;
      }
      code.add(StyledKimToken.create(")"));
    } else {
      var token = new StyledKimToken();
      token.setValue(expression.toString());
      code.add(token);
    }
  }
}
