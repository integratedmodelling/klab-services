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
  private record Bound(SemanticRole role, OWLClassExpression filler, boolean inherited) {}
  private final Map<String, List<Bound>> cache = new LinkedHashMap<>(64, .75f, true) {
    @Override protected boolean removeEldestEntry(Map.Entry<String, List<Bound>> entry) {
      return size() > 512;
    }
  };
  private static final Map<SemanticRole, String> PROPERTIES = Map.ofEntries(
      Map.entry(SemanticRole.INHERENT, NS.IS_INHERENT_TO_PROPERTY),
      Map.entry(SemanticRole.GOAL, NS.HAS_PURPOSE_PROPERTY),
      Map.entry(SemanticRole.COMPRESENT, NS.HAS_COMPRESENT_PROPERTY),
      Map.entry(SemanticRole.CAUSANT, NS.HAS_CAUSANT_PROPERTY),
      Map.entry(SemanticRole.CAUSED, NS.HAS_CAUSED_PROPERTY),
      Map.entry(SemanticRole.ADJACENT, NS.IS_ADJACENT_TO_PROPERTY),
      Map.entry(SemanticRole.COOCCURRENT, NS.OCCURS_DURING_PROPERTY),
      Map.entry(SemanticRole.RELATIONSHIP_SOURCE, NS.IMPLIES_SOURCE_PROPERTY),
      Map.entry(SemanticRole.RELATIONSHIP_TARGET, NS.IMPLIES_DESTINATION_PROPERTY));
  public OWLSemanticClauseSupport(OWL owl) { super(owl.reasoner()); this.owl = owl; }

  private List<Bound> bounds(Concept concept) {
    return cache.computeIfAbsent(concept.getUrn(), key -> {
      var result = new ArrayList<Bound>();
      var root = owl.getOWLClass(concept);
      if (root == null) throw new IllegalArgumentException("No ontology class for " + key);
      visit(root, false, new HashSet<>(), result);
      // Include inferred superclasses as well as asserted/equivalent class definitions.
      for (var parent : owl.getSuperClasses(root, false).getFlattened())
        if (!parent.isOWLThing() && !parent.equals(root)) visit(parent, true, new HashSet<>(), result);
      return result.stream().distinct().sorted(Comparator.comparing((Bound b) -> b.role().name())
          .thenComparing(Bound::inherited).thenComparing(b -> b.filler().toString())).toList();
    });
  }
  private void visit(OWLClassExpression expression, boolean inherited, Set<OWLClass> visited, List<Bound> result) {
    if (expression instanceof OWLClass named) {
      if (!visited.add(named)) return;
      var supers = EntitySearcher.getSuperClasses(named, owl.manager.ontologies()).toList();
      var equivalents = EntitySearcher.getEquivalentClasses(named, owl.manager.ontologies()).toList();
      for (var parent : supers) visit(parent, inherited || parent instanceof OWLClass, visited, result);
      for (var equivalent : equivalents) visit(equivalent, inherited || equivalent instanceof OWLClass, visited, result);
    } else if (expression instanceof OWLObjectIntersectionOf intersection) {
      for (var operand : intersection.getOperands()) visit(operand, inherited || operand instanceof OWLClass, visited, result);
    } else if (expression instanceof OWLQuantifiedRestriction<?> restriction
        && restriction.getFiller() instanceof OWLClassExpression filler) {
      var property = owl.getPropertyFor(restriction.getProperty());
      for (var entry : PROPERTIES.entrySet())
        if (property != null && property.is(owl.getProperty(entry.getValue()), owl))
          result.add(new Bound(entry.getKey(), filler, inherited));
    }
    // A union in the superclass hierarchy does not assert either branch individually.
  }
  @Override public boolean hasBounds(Concept owner, SemanticRole role) {
    return bounds(owner).stream().anyMatch(b -> b.role() == role);
  }
  @Override public boolean accepts(Concept owner, SemanticRole role, Concept operand) {
    var candidate = owl.getOWLClass(operand.isCollective() ? operand.singular() : operand);
    if (candidate == null) return false;
    return bounds(owner).stream().filter(b -> b.role() == role).allMatch(b ->
        candidate.equals(b.filler()) || owl.isEntailed(
            owl.manager.getOWLDataFactory().getOWLSubClassOfAxiom(candidate, b.filler())));
  }
  @Override public List<SemanticClauseRestriction> clauses(Concept concept) {
    var result = new ArrayList<SemanticClauseRestriction>();
    for (var bound : bounds(concept)) {
      var clause = new SemanticClauseRestriction(bound.role(),
          bound.filler() instanceof OWLClass named ? owl.getConceptFor(named) : null, bound.inherited());
      var code = new ArrayList<StyledKimToken>();
      Arrays.stream(SemanticLexicalElement.values()).filter(m -> m.role == bound.role()).findFirst()
          .ifPresent(m -> code.add(StyledKimToken.create(m)));
      style(bound.filler(), code);
      clause.setCode(code); result.add(clause);
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
        if (!first) code.add(StyledKimToken.create(expression instanceof OWLObjectUnionOf
            ? BinarySemanticOperator.UNION : BinarySemanticOperator.INTERSECTION));
        style(operand, code); first = false;
      }
      code.add(StyledKimToken.create(")"));
    } else {
      var token = new StyledKimToken(); token.setValue(expression.toString()); code.add(token);
    }
  }
}
