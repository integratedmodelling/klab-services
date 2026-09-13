package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.runtime.language.SemanticDocumentation;
import org.semanticweb.owlapi.model.*;

/** Reads actual axioms; never treats a restriction filler or a union operand as a superclass. */
public final class OwlDocumentation {
  private OwlDocumentation() {}

  public static String describe(OWL owl, Concept concept) {
    var target = owl.getOWLClass(concept);
    if (target == null) return "## OWL\n\nNo OWL class is available for this concept.\n\n";
    var ontologies = owl.getOntologies(true).stream().map(Ontology::getOWLOntology).toList();
    String result = describe(target, ontologies);
    var inference = new StringBuilder("## Inferred named superclasses\n\n");
    try {
      var superclasses = owl.getSuperClasses(target, false).getFlattened();
      var names = new TreeSet<String>();
      superclasses.forEach(c -> names.add(SemanticDocumentation.code(c.getIRI())));
      if (names.isEmpty()) inference.append("None.\n\n");
      else names.forEach(n -> inference.append("- ").append(n).append('\n'));
      inference.append("\n### Restrictions supplied by inferred named superclasses\n\n");
      var restrictions = new TreeSet<String>();
      var pending = new ArrayDeque<OWLClass>();
      var visited = new HashSet<OWLClass>();
      pending.addAll(superclasses);
      while (!pending.isEmpty()) {
        var current = pending.removeFirst();
        if (current.equals(target) || !visited.add(current)) continue;
        for (var ontology : ontologies) {
          String origin = " (inherited via inference from " + SemanticDocumentation.code(current.getIRI())
              + ", ontology " + SemanticDocumentation.code(ontology.getOntologyID()) + ")";
          ontology.getSubClassAxiomsForSubClass(current).forEach(a ->
              collect(a.getSuperClass(), restrictions, pending, origin));
          ontology.getEquivalentClassesAxioms(current).forEach(a -> a.getClassExpressionsMinus(current)
              .forEach(e -> collect(e, restrictions, pending, origin)));
        }
      }
      if (restrictions.isEmpty()) inference.append("None recorded.\n");
      else restrictions.forEach(r -> inference.append("- ").append(r).append('\n'));
    } catch (RuntimeException e) {
      inference.append("Inference unavailable (").append(e.getClass().getSimpleName()).append(").\n");
    }
    return result + inference.append('\n');
  }

  static String describe(OWLClass target, Collection<OWLOntology> roots) {
    Set<OWLOntology> ontologies = new HashSet<>();
    roots.forEach(o -> ontologies.addAll(o.getImportsClosure()));
    var asserted = new TreeSet<String>();
    var inherited = new TreeSet<String>();
    var facts = new TreeSet<String>();
    var pending = new ArrayDeque<OWLClass>();
    var visited = new HashSet<OWLClass>();
    pending.add(target);
    while (!pending.isEmpty()) {
      var current = pending.removeFirst();
      if (!visited.add(current)) continue;
      boolean direct = current.equals(target);
      for (var ontology : ontologies) {
        var origin = " (on " + SemanticDocumentation.code(current.getIRI()) + ", ontology "
            + SemanticDocumentation.code(ontology.getOntologyID()) + ")";
        for (var axiom : ontology.getSubClassAxiomsForSubClass(current)) {
          var expression = axiom.getSuperClass();
          collect(expression, direct ? asserted : inherited, pending, origin);
        }
        for (var axiom : ontology.getEquivalentClassesAxioms(current)) {
          for (var expression : axiom.getClassExpressionsMinus(current)) {
            collect(expression, direct ? asserted : inherited, pending, origin + " [equivalent class]");
          }
        }
        if (direct) {
          ontology.getReferencingAxioms(current).forEach(a ->
              facts.add(SemanticDocumentation.code(a) + origin));
          ontology.getAnnotationAssertionAxioms(current.getIRI()).forEach(a ->
              facts.add(SemanticDocumentation.code(a) + origin));
        }
      }
    }
    var out = new StringBuilder("## OWL identity\n\n")
        .append(SemanticDocumentation.code(target.getIRI())).append("\n\n")
        .append("Restrictions below follow asserted superclass and equivalent-class axioms, including "
            + "imports. Inherited means reachable through named superclass/equivalence links. "
            + "This is an axiom provenance report, not a complete enumeration of reasoner entailments. "
            + "k.LAB aliases need not have OWL equivalence axioms.\n\n");
    section(out, "Asserted superclass expressions and restrictions", asserted);
    section(out, "Inherited superclass expressions and restrictions", inherited);
    section(out, "All axioms referencing this class and its annotations", facts);
    return out.toString();
  }

  private static void collect(OWLClassExpression expression, Set<String> entries,
      Deque<OWLClass> pending, String origin) {
    entries.add(SemanticDocumentation.code(expression) + origin);
    if (!expression.isAnonymous()) pending.add(expression.asOWLClass());
    else if (expression instanceof OWLObjectIntersectionOf intersection) {
      // Every conjunct is inherited; nested fillers and union alternatives are not.
      intersection.getOperands().forEach(e -> collect(e, entries, pending, origin));
    }
  }

  private static void section(StringBuilder out, String heading, Set<String> entries) {
    out.append("## ").append(heading).append("\n\n");
    if (entries.isEmpty()) out.append("None recorded.\n");
    else entries.forEach(e -> out.append("- ").append(e).append('\n'));
    out.append('\n');
  }
}
