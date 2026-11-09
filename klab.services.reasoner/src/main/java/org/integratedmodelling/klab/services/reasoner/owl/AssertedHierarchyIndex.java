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
  private int queryDepth;
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
    // A complete search checks the manager identity set on entry and exit. Its recursive
    // edges need only observe the change listener, avoiding a manager-wide copy per edge.
    if (queryDepth > 0 && parents != null) return;
    var current = manager.getOntologies();
    // Ontology creation/removal need not produce an OWLOntologyChange event.
    if (ontologies != null && (ontologies.size() != current.size()
        || current.stream().anyMatch(ontology -> !ontologies.contains(ontology)))) invalidate();
    if (parents != null) return;
    // Compilation may add a generated declaration between queries. Rebuilding the entire
    // worldview each time is wasteful: OWLAPI already indexes assertions by named owner.
    parents = new LinkedHashMap<>();
    // OWLOntology.equals compares ontology IDs; replacing one with the same ID
    // must still invalidate assertions from the previous object.
    ontologies = Collections.newSetFromMap(new IdentityHashMap<>());
    ontologies.addAll(current);
  }

  <T> T query(java.util.function.Supplier<T> compute, Runnable checkpoint) {
    prepare(checkpoint);
    long initial = revision;
    queryDepth++;
    try {
      T answer = compute.get();
      checkpoint.run();
      return answer;
    } finally {
      queryDepth--;
      if (queryDepth == 0) {
        prepare(checkpoint);
        if (initial != revision)
          throw new ConcurrentModificationException("Ontology changed during private hierarchy search");
      }
    }
  }

  List<OWLClassExpression> parents(OWLClass named, Runnable checkpoint) {
    prepare(checkpoint);
    var known = parents.get(named);
    if (known != null) return known;
    long initial = revision;
    var next = new ArrayList<OWLClassExpression>();
    for (var ontology : ontologies) {
      checkpoint.run();
      for (var axiom : ontology.getSubClassAxiomsForSubClass(named)) {
        checkpoint.run();
        next.add(axiom.getSuperClass());
      }
      for (var axiom : ontology.getEquivalentClassesAxioms(named)) {
        checkpoint.run();
        next.addAll(axiom.getClassExpressionsMinus(named));
      }
    }
    checkpoint.run();
    prepare(checkpoint);
    if (initial != revision)
      throw new ConcurrentModificationException("Ontology changed while indexing private hierarchy");
    var saved = List.copyOf(next);
    parents.put(named, saved);
    return saved;
  }

  List<List<OWLClassExpression>> disjoint(Runnable checkpoint) {
    prepare(checkpoint);
    if (disjoint == null) {
      long initial = revision;
      var next = new ArrayList<List<OWLClassExpression>>();
      for (var ontology : ontologies) {
        checkpoint.run();
        for (var axiom : ontology.getAxioms(AxiomType.DISJOINT_CLASSES)) {
          checkpoint.run();
          next.add(axiom.classExpressions().toList());
        }
      }
      checkpoint.run();
      prepare(checkpoint);
      if (initial != revision)
        throw new ConcurrentModificationException("Ontology changed while indexing private disjointness");
      disjoint = List.copyOf(next);
    }
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
