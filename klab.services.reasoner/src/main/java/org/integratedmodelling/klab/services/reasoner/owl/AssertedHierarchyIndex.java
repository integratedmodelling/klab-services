package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.*;
import org.semanticweb.owlapi.model.*;

/**
 * Private-compilation scratch index. Never retained by an accepted semantic generation.
 * Any manager change discards both indexed axioms and root-search answers. In particular,
 * resolving a previously unseen expression during description preparation may add axioms.
 */
final class AssertedHierarchyIndex implements AutoCloseable {
  private final OWL owl;
  private final OWLOntologyManager manager;
  private final OWLOntologyChangeListener listener = changes -> invalidate();
  private Map<OWLClass, List<OWLClassExpression>> parents;
  private List<List<OWLClassExpression>> disjoint;
  private Set<OWLOntology> ontologies;
  private final Map<Pair, Boolean> roots = new LinkedHashMap<>(128, .75f, true) {
    @Override protected boolean removeEldestEntry(Map.Entry<Pair, Boolean> entry) {
      return size() > 65536;
    }
  };
  private record Pair(OWLClassExpression candidate, OWLClassExpression bound) {}
  private long revision;
  private boolean closed;

  AssertedHierarchyIndex(OWL owl) {
    this.owl = owl;
    this.manager = owl.manager;
    manager.addOntologyChangeListener(listener);
  }

  private void invalidate() {
    revision++;
    parents = null;
    disjoint = null;
    ontologies = null;
    roots.clear();
  }

  boolean usable(OWL owner) {
    // DL answers/flushes have a separate lifetime; this index never memoizes them.
    return !closed && owner == owl && owner.manager == manager && !owl.hasReasoner();
  }

  private void prepare(Runnable checkpoint) {
    checkpoint.run();
    var current = manager.getOntologies();
    // Ontology creation/removal need not produce an OWLOntologyChange event.
    if (ontologies != null && (ontologies.size() != current.size()
        || current.stream().anyMatch(ontology -> !ontologies.contains(ontology)))) invalidate();
    if (parents != null) return;
    long initial = revision;
    var nextParents = new LinkedHashMap<OWLClass, List<OWLClassExpression>>();
    var nextDisjoint = new ArrayList<List<OWLClassExpression>>();
    for (var ontology : current) {
      checkpoint.run();
      for (var axiom : ontology.getAxioms(AxiomType.SUBCLASS_OF)) {
        checkpoint.run();
        if (axiom.getSubClass() instanceof OWLClass named)
          nextParents.computeIfAbsent(named, key -> new ArrayList<>()).add(axiom.getSuperClass());
      }
      for (var axiom : ontology.getAxioms(AxiomType.EQUIVALENT_CLASSES)) {
        checkpoint.run();
        var expressions = axiom.classExpressions().toList();
        for (var expression : expressions) {
          checkpoint.run();
          if (expression instanceof OWLClass named)
            for (var other : expressions) {
              checkpoint.run();
              if (!other.equals(named))
                nextParents.computeIfAbsent(named, key -> new ArrayList<>()).add(other);
            }
        }
      }
      for (var axiom : ontology.getAxioms(AxiomType.DISJOINT_CLASSES)) {
        checkpoint.run();
        nextDisjoint.add(axiom.classExpressions().toList());
      }
    }
    checkpoint.run();
    if (initial != revision)
      throw new ConcurrentModificationException("Ontology changed while indexing private hierarchy");
    // Cancellation/failure above cannot expose a partially built index.
    parents = nextParents;
    disjoint = nextDisjoint;
    // OWLOntology.equals compares ontology IDs; replacing one with the same ID
    // must still invalidate assertions from the previous object.
    ontologies = Collections.newSetFromMap(new IdentityHashMap<>());
    ontologies.addAll(current);
  }

  List<OWLClassExpression> parents(OWLClass named, Runnable checkpoint) {
    prepare(checkpoint);
    return parents.getOrDefault(named, List.of());
  }

  List<List<OWLClassExpression>> disjoint(Runnable checkpoint) {
    prepare(checkpoint);
    return disjoint;
  }

  Boolean root(OWLClassExpression candidate, OWLClassExpression bound, Runnable checkpoint) {
    prepare(checkpoint);
    return roots.get(new Pair(candidate, bound));
  }

  void remember(OWLClassExpression candidate, OWLClassExpression bound, boolean answer, long atRevision) {
    if (!closed && revision == atRevision) roots.put(new Pair(candidate, bound), answer);
  }

  long revision() { return revision; }

  @Override public void close() {
    manager.removeOntologyChangeListener(listener);
    closed = true;
    invalidate();
  }
}
