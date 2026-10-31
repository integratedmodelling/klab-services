package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.lang.reflect.*;
import org.junit.jupiter.api.Test;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.HermiT.hierarchy.*;
import org.semanticweb.HermiT.model.AtomicConcept;
import org.semanticweb.HermiT.tableau.Tableau;

/**
 * Differential oracle retains the former full-hierarchy lookup, including complete bound records.
 */
class TargetedBoundDiscoveryTest {

  public static class CounterReasoner extends org.semanticweb.HermiT.Reasoner {

    int classifications, reloads, globalQueries, entailments;

    CounterReasoner(OWLOntology root) {
      super(new org.semanticweb.HermiT.Configuration(), root);
    }

    @Override
    protected void loadOntology() {
      reloads++;
      super.loadOntology();
    }

    @Override
    protected Hierarchy<AtomicConcept> classifyAtomicConcepts(Tableau t, ClassificationProgressMonitor p, AtomicConcept a, AtomicConcept b, Set<AtomicConcept> c, boolean q) {
      classifications++;
      return super.classifyAtomicConcepts(t, p, a, b, c, q);
    }

    @Override
    public NodeSet<OWLClass> getSuperClasses(OWLClassExpression x, boolean d) {
      globalQueries++;
      return super.getSuperClasses(x, d);
    }

    @Override
    public Node<OWLClass> getEquivalentClasses(OWLClassExpression x) {
      globalQueries++;
      return super.getEquivalentClasses(x);
    }

    @Override
    public boolean isEntailed(OWLAxiom x) {
      entailments++;
      return super.isEntailed(x);
    }

    Map<String, Integer> metrics() {
      return Map.of("classifications", classifications, "reloads", reloads, "globalQueries", globalQueries, "entailments", entailments);
    }
  }

  static class Fixture implements AutoCloseable {

    final OWL owl;

    final Ontology domain;

    final OWLDataFactory f;

    final OWLOntology root;

    final CounterReasoner dl;

    Fixture() throws Exception {
      var scope = (Scope) Proxy.newProxyInstance(Scope.class.getClassLoader(), new Class<?>[] { Scope.class }, (o, m, a) -> null);
      owl = new OWL(scope);
      owl.manager = OWLManager.createOWLOntologyManager();
      f = owl.manager.getOWLDataFactory();
      domain = owl.requireOntology("audit");
      var declarations = new ArrayList<Axiom>();
      for (String n : List.of("Trait", "Parent", "Marker", "Inferred", "Alias", "UnionOwner")) declarations.add(Axiom.ClassAssertion(n, EnumSet.of(SemanticType.IDENTITY, SemanticType.PREDICATE)));
      for (String n : List.of("Tree", "Oak", "Pine", "Rock", "Plant")) declarations.add(Axiom.ClassAssertion(n, EnumSet.of(SemanticType.SUBJECT, SemanticType.OBSERVABLE)));
      domain.define(declarations);
      owl.requireOntology("odo").define(List.of(Axiom.ObjectPropertyAssertion("appliesTo"), Axiom.ObjectPropertyAssertion("isInherentTo"), Axiom.ObjectPropertyAssertion("hasPurpose")));
      add(f.getOWLSubClassOfAxiom(c("Trait"), c("Parent")));
      add(f.getOWLSubClassOfAxiom(c("Trait"), c("Marker")));
      add(f.getOWLSubClassOfAxiom(c("Tree"), c("Plant")));
      add(f.getOWLSubClassOfAxiom(c("Oak"), c("Tree")));
      add(f.getOWLSubClassOfAxiom(c("Pine"), c("Tree")));
      add(f.getOWLDisjointClassesAxiom(c("Tree"), c("Rock")));
      add(f.getOWLEquivalentClassesAxiom(c("Inferred"), f.getOWLObjectIntersectionOf(c("Trait"), c("Marker"))));
      add(f.getOWLEquivalentClassesAxiom(c("Alias"), c("Trait")));
      var mark = f.getOWLAnnotation(f.getOWLAnnotationProperty(OWLSemanticClauseSupport.UNIVERSAL_BEARER), f.getOWLLiteral(true));
      add(f.getOWLSubClassOfAxiom(c("Parent"), f.getOWLObjectSomeValuesFrom(p("appliesTo"), c("Tree")), Set.of(mark)));
      add(f.getOWLSubClassOfAxiom(c("Parent"), f.getOWLObjectSomeValuesFrom(p("appliesTo"), f.getOWLObjectUnionOf(c("Tree"), c("Rock")))));
      add(f.getOWLSubClassOfAxiom(c("Inferred"), f.getOWLObjectSomeValuesFrom(p("appliesTo"), f.getOWLObjectUnionOf(c("Oak"), c("Rock")))));
      add(f.getOWLSubClassOfAxiom(c("Parent"), f.getOWLObjectSomeValuesFrom(p("isInherentTo"), c("Plant"))));
      add(f.getOWLSubClassOfAxiom(c("UnionOwner"), f.getOWLObjectUnionOf(c("Parent"), c("Marker"))));
      root = owl.manager.createOntology(IRI.create("urn:offline:merged"));
      for (var o : new ArrayList<>(owl.manager.getOntologies())) if (o != root)
        owl.manager.applyChange(new AddImport(root, f.getOWLImportsDeclaration(o.getOntologyID().getOntologyIRI().orElseThrow())));
      dl = new CounterReasoner(root);
      var field = OWL.class.getDeclaredField("reasoner");
      field.setAccessible(true);
      field.set(owl, dl);
    }

    OWLClass c(String n) {
      return owl.getOWLClass(domain.getConcept(n));
    }

    OWLObjectProperty p(String n) {
      return owl.getProperty("odo:" + n).getOWLEntity().asOWLObjectProperty();
    }

    void add(OWLAxiom x) {
      owl.manager.addAxiom(domain.getOWLOntology(), x);
    }

    Object support(boolean targeted) {
      return new OWLSemanticClauseSupport(owl);
    }

    List<String> bounds(Object s, String n) throws Exception {
      var m = s.getClass().getDeclaredMethod("bounds", Concept.class);
      m.setAccessible(true);
      return ((List<?>) m.invoke(s, domain.getConcept(n))).stream().map(Object::toString).sorted().toList();
    }

    boolean applicable(Object s, String target) throws Exception {
      return (boolean) s.getClass().getMethod("applicableTo", Concept.class, Concept.class).invoke(s, domain.getConcept("Trait"), domain.getConcept(target));
    }

    void trial(int i, String base) {
      domain.define(List.of(Axiom.ClassAssertion("Trial" + i, EnumSet.of(SemanticType.SUBJECT, SemanticType.OBSERVABLE)), Axiom.SubClass(base, "Trial" + i)));
      add(f.getOWLSubClassOfAxiom(c("Trial" + i), f.getOWLObjectSomeValuesFrom(p("hasPurpose"), c("Marker"))));
    }

    void mutate() {
      add(f.getOWLSubClassOfAxiom(c("Trait"), f.getOWLObjectSomeValuesFrom(p("appliesTo"), c("Pine"))));
    }

    void external(boolean importIt) throws Exception {
      var imported = owl.manager.createOntology(IRI.create("urn:offline:external"));
      var owner = f.getOWLClass(IRI.create("urn:external:Owner"));
      var prop = f.getOWLObjectProperty(IRI.create("urn:external:applies"));
      owl.manager.addAxiom(imported, f.getOWLSubObjectPropertyOfAxiom(prop, p("appliesTo")));
      owl.manager.addAxiom(imported, f.getOWLEquivalentClassesAxiom(owner, f.getOWLObjectIntersectionOf(c("Trait"), c("Marker"))));
      owl.manager.addAxiom(imported, f.getOWLSubClassOfAxiom(owner, f.getOWLObjectAllValuesFrom(prop, f.getOWLObjectUnionOf(c("Oak"), c("Pine")))));
      if (importIt)
        owl.manager.applyChange(new AddImport(root, f.getOWLImportsDeclaration(IRI.create("urn:offline:external"))));
    }

    void removeExternal() {
      owl.manager.applyChange(new RemoveImport(root, f.getOWLImportsDeclaration(IRI.create("urn:offline:external"))));
    }

    public void close() {
      dl.dispose();
    }
  }

  @FunctionalInterface
  interface Mutation {

    void apply(Fixture fixture) throws Exception;
  }

  static List<?> legacyBounds(OWL owl, OWLSemanticClauseSupport support, Concept concept) throws Exception {
    // This oracle varies the DL inheritance algorithm. Asserted-index equivalence to the former
    // manager scan is checked independently in BoundAxiomIndexTest.
    var slotField = OWLSemanticClauseSupport.class.getDeclaredField("assertedAxioms");
    slotField.setAccessible(true);
    var slot = (ThreadLocal<BoundAxiomIndex>) slotField.get(support);
    var previous = slot.get();
    slot.set(BoundAxiomIndex.build(owl.manager, axiom -> axiom.annotations().anyMatch(a ->
        a.getProperty().getIRI().equals(OWLSemanticClauseSupport.UNIVERSAL_BEARER)
            && a.getValue() instanceof OWLLiteral value && value.isBoolean() && value.parseBoolean()),
        () -> {}, () -> {}));
    try {
    var result = new ArrayList<>();
    var visit = OWLSemanticClauseSupport.class.getDeclaredMethod("visit", OWLClassExpression.class, boolean.class, Set.class, List.class, Runnable.class);
    visit.setAccessible(true);
    var root = owl.getOWLClass(concept);
    visit.invoke(support, root, false, new HashSet<>(), result, OWLSemanticClauseSupport.NO_CHECKPOINT);
    if (owl.hasReasoner()) {
      owl.flushReasoner();
      for (var parent : owl.getSuperClasses(root, false).getFlattened()) if (!parent.isOWLThing() && !parent.equals(root))
        visit.invoke(support, parent, true, new HashSet<>(), result, OWLSemanticClauseSupport.NO_CHECKPOINT);
      for (var equivalent : owl.getEquivalentClasses(root).getEntities()) if (!equivalent.isOWLThing() && !equivalent.equals(root))
        visit.invoke(support, equivalent, true, new HashSet<>(), result, OWLSemanticClauseSupport.NO_CHECKPOINT);
    }
    var complete = result.stream().distinct().toList();
    var cache = OWLSemanticClauseSupport.class.getDeclaredField("cache");
    cache.setAccessible(true);
    ((Map) cache.get(support)).put(concept.getUrn(), complete);
    return complete;
    } finally {
      if (previous == null) slot.remove(); else slot.set(previous);
    }
  }

  static List<String> actualBounds(OWLSemanticClauseSupport support, Concept concept) throws Exception {
    var bounds = OWLSemanticClauseSupport.class.getDeclaredMethod("bounds", Concept.class);
    bounds.setAccessible(true);
    return normalize((List<?>) bounds.invoke(support, concept));
  }

  static List<String> normalize(List<?> values) {
    return values.stream().map(Object::toString).sorted().toList();
  }

  private void compare(Mutation mutation) throws Exception {
    try (var reference = new Fixture();
      var candidate = new Fixture()) {
      mutation.apply(reference);
      mutation.apply(candidate);
      var original = new OWLSemanticClauseSupport(reference.owl);
      var actual = new OWLSemanticClauseSupport(candidate.owl);
      for (String name : List.of("Trait", "Parent", "Inferred", "Alias", "UnionOwner")) assertEquals(normalize(legacyBounds(reference.owl, original, reference.domain.getConcept(name))), actualBounds(actual, candidate.domain.getConcept(name)), name);
      for (String head : List.of("Oak", "Pine", "Rock", "Tree")) assertEquals(original.applicableTo(reference.domain.getConcept("Trait"), reference.domain.getConcept(head)), actual.applicableTo(candidate.domain.getConcept("Trait"), candidate.domain.getConcept(head)), head);
      assertEquals(0, candidate.dl.classifications, "Bound discovery must not classify the whole ontology");
      assertEquals(0, candidate.dl.globalQueries, "No global hierarchy API is needed for bounds");
    }
  }

  @Test
  void inferredEquivalentUnionConjunctiveDisjointAndProvenance() throws Exception {
    compare(f -> {
    });
  }

  @Test
  void firstSpeculativeClass() throws Exception {
    compare(f -> f.trial(0, "Oak"));
  }

  @Test
  void secondSpeculativeClass() throws Exception {
    compare(f -> {
      f.trial(0, "Oak");
      f.trial(1, "Pine");
    });
  }

  @Test
  void multipleSpeculativeClasses() throws Exception {
    compare(f -> {
      f.trial(0, "Oak");
      f.trial(1, "Pine");
      f.trial(2, "Rock");
    });
  }

  @Test
  void importedExternalSubpropertyAndFullFiller() throws Exception {
    compare(f -> f.external(true));
  }

  @Test
  void removedImport() throws Exception {
    compare(f -> {
      f.external(true);
      f.removeExternal();
    });
  }

  @Test
  void changedApplicability() throws Exception {
    compare(Fixture::mutate);
  }

  @Test
  void unrelatedManagerOntologyIsNotAReasonerOwner() throws Exception {
    compare(f -> f.external(false));
  }

  @Test
  void unsatisfiableRootCannotAdoptOutOfImportBounds() throws Exception {
    compare(f -> {
      f.external(false);
      f.add(f.f.getOWLSubClassOfAxiom(f.c("Trait"), f.f.getOWLNothing()));
    });
  }

  @Test
  void inferredUnsatisfiableRootRetainsNothingBounds() throws Exception {
    compare(f -> {
      f.add(f.f.getOWLSubClassOfAxiom(f.c("Trait"), f.c("Tree")));
      f.add(f.f.getOWLSubClassOfAxiom(f.c("Trait"), f.c("Rock")));
      f.add(f.f.getOWLSubClassOfAxiom(f.f.getOWLNothing(), f.f.getOWLObjectSomeValuesFrom(f.p("appliesTo"), f.c("Rock"))));
    });
  }

  @Test
  void reusesClassifiedHierarchyButDoesNotReclassifyAfterTrialMutation() throws Exception {
    try (var fixture = new Fixture()) {
      fixture.dl.precomputeInferences(org.semanticweb.owlapi.reasoner.InferenceType.CLASS_HIERARCHY);
      int classifications = fixture.dl.classifications;
      var actual = new OWLSemanticClauseSupport(fixture.owl);
      var first = actualBounds(actual, fixture.domain.getConcept("Trait"));
      assertTrue(fixture.dl.globalQueries > 0, "A ready hierarchy should be reused");
      assertEquals(classifications, fixture.dl.classifications);
      fixture.trial(80, "Oak");
      int queries = fixture.dl.globalQueries;
      var afterMutation = actualBounds(new OWLSemanticClauseSupport(fixture.owl), fixture.domain.getConcept("Trait"));
      assertEquals(first, afterMutation);
      assertEquals(classifications, fixture.dl.classifications, "A new trial must not force classification");
      assertEquals(queries, fixture.dl.globalQueries, "The invalidated hierarchy must use targeted proofs");
    }
  }
}
