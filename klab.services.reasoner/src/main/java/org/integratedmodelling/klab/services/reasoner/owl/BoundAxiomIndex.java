package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.*;
import java.util.function.Predicate;
import org.semanticweb.owlapi.model.*;

/**
 * Immutable asserted edges for one bounds computation. The caller holds the OWL monitor and owns
 * the mutation/import/backend fence from before construction until bounds publication. This is
 * scratch space, not a semantic answer cache: every owner retains its own traversal and DL checks.
 */
final class BoundAxiomIndex {
  record Edge(OWLClassExpression parent, boolean semantic) {}

  private final Map<OWLClass, List<Edge>> parents;

  private BoundAxiomIndex(Map<OWLClass, List<Edge>> parents) {
    this.parents = parents;
  }

  static BoundAxiomIndex build(OWLOntologyManager manager, Predicate<OWLAxiom> marked,
      Runnable checkpoint, Runnable verifyUnchanged) {
    checkpoint.run();
    verifyUnchanged.run();
    var next = new HashMap<OWLClass, List<Edge>>();
    // Keep the original manager scope, including assertions outside the reasoner's imports.
    // Do not merge equivalent-class owners or deduplicate an assertion present in two documents.
    for (var ontology : manager.getOntologies()) {
      checkpoint.run();
      for (var axiom : ontology.getAxioms(AxiomType.SUBCLASS_OF)) {
        checkpoint.run();
        if (axiom.getSubClass() instanceof OWLClass named) {
          next.computeIfAbsent(named, key -> new ArrayList<>())
              .add(new Edge(axiom.getSuperClass(), marked.test(axiom)));
        }
      }
      for (var axiom : ontology.getAxioms(AxiomType.EQUIVALENT_CLASSES)) {
        checkpoint.run();
        for (var expression : axiom.getClassExpressions()) {
          checkpoint.run();
          if (expression instanceof OWLClass named) {
            for (var other : axiom.getClassExpressionsMinus(named)) {
              checkpoint.run();
              next.computeIfAbsent(named, key -> new ArrayList<>())
                  .add(new Edge(other, marked.test(axiom)));
            }
          }
        }
      }
    }
    next.replaceAll((key, value) -> {
      checkpoint.run();
      return List.copyOf(value);
    });
    checkpoint.run();
    verifyUnchanged.run();
    return new BoundAxiomIndex(Map.copyOf(next));
  }

  List<Edge> parents(OWLClass named) {
    return parents.getOrDefault(named, List.of());
  }
}
