package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.junit.jupiter.api.Test;

/** Actual OWL/HermiT and declaration compilation; no services or external worldview. */
class AuthoredClosureTest {
  static KimConceptStatementImpl declaration(String name) {
    var s = new KimConceptStatementImpl(); s.setUrn(name); s.setNamespace("audit");
    s.setType(EnumSet.of(SemanticType.ATTRIBUTE, SemanticType.PREDICATE)); return s;
  }
  static WorldviewImpl source(KimConceptStatement... statements) {
    var ontology = new KimOntologyImpl(); ontology.setUrn("audit");
    ontology.setServiceId("source-provider"); ontology.getStatements().addAll(List.of(statements));
    var world = new WorldviewImpl(); world.setUrn("test"); world.setWorldviewId("test");
    world.setOntologies(new ArrayList<>(List.of(ontology))); return world;
  }
  static void source(MonotoneRefinementTest.Fixture t, Worldview world) throws Exception {
    ClassifierNativeAdmissionTest.field(t.reasoner, ReasonerService.class, "worldview", world);
  }
  static Set<String> urns(Collection<Concept> values) {
    var ret = new HashSet<String>(); values.forEach(c -> ret.add(c.getUrn())); return ret;
  }
  static void flush(MonotoneRefinementTest.Fixture t) {
    for (var o : t.owl.getOntologies(true)) if (o != t.ontology) t.owl.registerWithReasoner(o);
    t.dl.flush();
  }
  @Test void runtimeScopedDescendantsCannotContaminateAuthoredCandidates() throws Exception {
    try (var t = new MonotoneRefinementTest.Fixture()) {
      source(t, source(declaration("Category"), declaration("Concrete")));
      var initial = urns(t.reasoner.authoredClosure(t.concept("Category")));
      var expression = t.leaf("Concrete"); expression.setInherent(t.leaf("Region")); t.register(expression);
      var generated = t.reasoner.declareConcept(expression); flush(t);
      assertTrue(urns(t.reasoner.closure(t.concept("Category"))).contains(generated.getUrn()));
      assertFalse(initial.contains(generated.getUrn()));
      assertEquals(initial, urns(t.reasoner.authoredClosure(t.concept("Category"))));
      assertTrue(initial.contains("audit:Concrete"));
    }
  }
  @Test void authoredScopedAliasRetainsCanonicalMeaningWithoutProviderOrNamespaceHeuristics() throws Exception {
    try (var t = new MonotoneRefinementTest.Fixture()) {
      var expression = t.leaf("Concrete"); expression.setInherent(t.leaf("Region")); t.register(expression);
      var canonical = t.reasoner.declareConcept(expression);
      var alias = declaration("AuthoredScoped"); alias.setAlias(true); alias.setDeclaredParent(expression);
      var install = ReasonerService.class.getDeclaredMethod("installAlias", KimConceptStatement.class, Ontology.class, Scope.class);
      install.setAccessible(true); install.invoke(t.reasoner, alias, t.ontology, t.scope);
      source(t, source(declaration("Category"), declaration("Concrete"), alias)); flush(t);
      var result = t.reasoner.authoredClosure(t.concept("Category"));
      var retained = result.stream().filter(c -> c.getUrn().equals(canonical.getUrn())).findFirst().orElseThrow();
      assertEquals("audit:Concrete of audit:Region", retained.getUrn());
      assertNull(canonical.getServiceId());
      assertEquals(1, result.stream().filter(c -> c.getUrn().equals(canonical.getUrn())).count());
    }
  }
  @Test void unregisteredNamedDescendantsAreExcludedEvenInAuthoredNamespace() throws Exception {
    try (var t = new MonotoneRefinementTest.Fixture()) {
      source(t, source(declaration("Category"), declaration("Concrete")));
      t.attribute("RuntimeNamed", "Category"); flush(t);
      assertTrue(urns(t.reasoner.closure(t.concept("Category"))).contains("audit:RuntimeNamed"));
      assertFalse(urns(t.reasoner.authoredClosure(t.concept("Category"))).contains("audit:RuntimeNamed"));
    }
  }
  @Test void sourceReplacementAndRevisionChangeInvalidateMembership() throws Exception {
    try (var t = new MonotoneRefinementTest.Fixture()) {
      var old = source(declaration("Category"), declaration("Concrete")); source(t, old);
      assertTrue(urns(t.reasoner.authoredClosure(t.concept("Category"))).contains("audit:Concrete"));
      var replacement = source(declaration("Category"));
      ((KimOntologyImpl) replacement.getOntologies().getFirst()).setServiceId("replacement-provider"); source(t, replacement);
      assertFalse(urns(t.reasoner.authoredClosure(t.concept("Category"))).contains("audit:Concrete"));
      replacement.getOntologies().getFirst().getStatements().add(declaration("Concrete"));
      var revision = ReasonerService.class.getDeclaredField("knowledgeRevision"); revision.setAccessible(true);
      ((AtomicLong) revision.get(t.reasoner)).incrementAndGet();
      assertTrue(urns(t.reasoner.authoredClosure(t.concept("Category"))).contains("audit:Concrete"));
    }
  }
  @Test void deniableNamesResolveToAuthoredCanonicalNegation() throws Exception {
    try (var t = new MonotoneRefinementTest.Fixture()) {
      var owner = declaration("Concrete"); owner.getType().add(SemanticType.DENIABLE); owner.setDeniedAlias("Opposite");
      WorldviewDeclarationSupport.compileDeniability(t.owl, t.ontology, t.concept("Concrete"), owner);
      source(t, source(owner)); flush(t);
      var negated = t.owl.getConcept("audit:NotConcrete");
      assertNotNull(negated);
      assertEquals(negated.getUrn(), t.owl.getConcept("audit:Opposite").getUrn());
      assertTrue(urns(t.reasoner.authoredClosure(negated)).contains(negated.getUrn()));
    }
  }
  @Test void missingAuthoredDeclarationFailsInsteadOfReturningIncompleteCandidates() throws Exception {
    try (var t = new MonotoneRefinementTest.Fixture()) {
      source(t, source(declaration("Unavailable")));
      assertThrows(IllegalStateException.class, () -> t.reasoner.authoredClosure(t.concept("Category")));
    }
  }
  @Test void deniableAliasDoesNotInventUncompiledSyntheticSourceNames() throws Exception {
    try (var t = new MonotoneRefinementTest.Fixture()) {
      var alias = declaration("Alias"); alias.setAlias(true);
      alias.getType().add(SemanticType.DENIABLE); alias.setDeclaredParent(t.leaf("Concrete"));
      var install = ReasonerService.class.getDeclaredMethod("installAlias", KimConceptStatement.class, Ontology.class, Scope.class);
      install.setAccessible(true); install.invoke(t.reasoner, alias, t.ontology, t.scope);
      source(t, source(alias)); flush(t);
      assertNull(t.owl.getConcept("audit:NotAlias"));
      assertEquals(Set.of("audit:Concrete"), urns(t.reasoner.authoredClosure(t.concept("Category"))));
    }
  }
}
