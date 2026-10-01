package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.reasoner.internal.AuthorityBindings;
import org.integratedmodelling.klab.services.reasoner.internal.AuthorityIdentityResolver;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;

class AuthorityIdentityResolverTest {
  private static class TestOWL extends OWL {
    TestOWL() {
      super(mock(Scope.class));
      manager = OWLManager.createOWLOntologyManager();
    }
    @Override public synchronized void registerWithReasoner(Ontology ontology) {}
    @Override public synchronized void flushReasoner() {}
  }

  @Test void owlDispatchPreservesBracketPayloadAndEmbeddedColons() {
    var owl = new TestOWL();
    var seen = new java.util.concurrent.atomic.AtomicReference<String>();
    var concept = mock(org.integratedmodelling.klab.api.knowledge.Concept.class);
    owl.setAuthorityResolver((authority, code) -> { assertEquals("TAXA", authority); seen.set(code); return concept; });
    for (String code : List.of("A:B", "x] of test:Other", "x" + (char)92 + "y", "a b")) {
      var token = org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityIdentitySyntax.encode("TAXA", code);
      assertSame(concept, owl.getConcept(token)); assertEquals(code, seen.get());
    }
  }

  private Authority.Identity identity(String id, String base, String... parents) {
    var identity = mock(Authority.Identity.class);
    when(identity.getId()).thenReturn(id);
    when(identity.getConceptName()).thenReturn(id);
    when(identity.getBaseIdentity()).thenReturn(base);
    when(identity.getParentIds()).thenReturn(List.of(parents));
    when(identity.getLabel()).thenReturn(id);
    return identity;
  }

  @Test
  void baseIdentityCarriesTheWorldviewRootAndRecursionStopsAtKnownParents() {
    var owl = new TestOWL();
    var roots = owl.requireOntology("biology");
    roots.define(List.of(Axiom.ClassAssertion("Species", EnumSet.of(SemanticType.IDENTITY))));
    var provider = mock(Authority.class);
    var request = new Authority.ConfigurationRequest("worldview", "TAXA", "biology:Species",
        Map.of("urn", "test.authority"));
    when(provider.configure(request)).thenReturn("bridge");
    var baseIdentity = identity("Base", null);
    var parentIdentity = identity("Parent", "Base");
    var leafIdentity = identity("Leaf", "Base", "Parent");
    when(provider.resolveIdentity("bridge", "Base")).thenReturn(baseIdentity);
    when(provider.resolveIdentity("bridge", "Parent")).thenReturn(parentIdentity);
    when(provider.resolveIdentity("bridge", "Leaf")).thenReturn(leafIdentity);
    var bindings = new AuthorityBindings();
    bindings.configure(request, provider);
    var resolver = new AuthorityIdentityResolver(owl, bindings);
    var parent = resolver.resolve("TAXA", "Parent");
    clearInvocations(provider);
    var leaf = resolver.resolve("TAXA", "Leaf");
    assertNotNull(leaf);
    verify(provider).resolveIdentity("bridge", "Leaf");
    verify(provider, never()).resolveIdentity("bridge", "Parent");
    verify(provider, never()).resolveIdentity("bridge", "Base");
    var factory = owl.manager.getOWLDataFactory();
    var ontology = owl.getOntology(leaf.getNamespace()).getOWLOntology();
    assertTrue(ontology.containsAxiom(factory.getOWLSubClassOfAxiom(
        owl.getOWLClass(leaf), owl.getOWLClass(parent))));
    assertFalse(ontology.containsAxiom(factory.getOWLSubClassOfAxiom(
        owl.getOWLClass(leaf), owl.getOWLClass(roots.getConcept("Species")))));
    var base = owl.getOntology(leaf.getNamespace()).getConcept("Base");
    assertTrue(ontology.containsAxiom(factory.getOWLSubClassOfAxiom(
        owl.getOWLClass(base), owl.getOWLClass(roots.getConcept("Species")))));
    assertSame(leaf, resolver.resolve("TAXA", "Leaf"));
  }

  @Test
  void aKnownWorldviewBaseIdentityDoesNotCallTheProvider() {
    var owl = new TestOWL();
    var roots = owl.requireOntology("biology");
    roots.define(List.of(Axiom.ClassAssertion("Species", EnumSet.of(SemanticType.IDENTITY))));
    var provider = mock(Authority.class);
    var request = new Authority.ConfigurationRequest("worldview", "TAXA", "biology:Species",
        Map.of("urn", "test.authority"));
    when(provider.configure(request)).thenReturn("bridge");
    var leafIdentity = identity("Leaf", "biology:Species");
    when(provider.resolveIdentity("bridge", "Leaf"))
        .thenReturn(leafIdentity);
    var bindings = new AuthorityBindings();
    bindings.configure(request, provider);
    var leaf = new AuthorityIdentityResolver(owl, bindings).resolve("TAXA", "Leaf");
    assertNotNull(leaf);
    verify(provider, never()).resolveIdentity("bridge", "biology:Species");
  }

  @Test
  void searchOnlyRankSuffixUsesTheSameBindingAndCanonicalConcept() {
    var owl = new TestOWL();
    var roots = owl.requireOntology("biology");
    roots.define(List.of(Axiom.ClassAssertion("Species", EnumSet.of(SemanticType.IDENTITY))));
    var provider = mock(Authority.class);
    var capabilities = mock(Authority.Capabilities.class);
    when(provider.getCapabilities()).thenReturn(capabilities);
    when(capabilities.areSubAuthoritiesSearchFilters()).thenReturn(true);
    when(capabilities.getSubAuthorities()).thenReturn(List.of(
        org.integratedmodelling.klab.api.collections.Pair.of("SPECIES", "Species")));
    var request = new Authority.ConfigurationRequest("worldview", "TAXA", "biology:Species",
        Map.of("urn", "test.authority"));
    when(provider.configure(request)).thenReturn("bridge");
    var leafIdentity = identity("Leaf", "biology:Species");
    when(provider.resolveIdentity("bridge", "Leaf")).thenReturn(leafIdentity);
    var bindings = new AuthorityBindings();
    bindings.configure(request, provider);
    var resolver = new AuthorityIdentityResolver(owl, bindings);
    owl.setAuthorityResolver(resolver::resolve);
    var ranked = owl.getConcept("TAXA.SPECIES:Leaf");
    assertNotNull(ranked);
    assertSame(ranked, resolver.resolve("TAXA", "Leaf"));
    assertNull(resolver.resolve("TAXA.UNKNOWN", "Leaf"));
    verify(provider, times(1)).resolveIdentity("bridge", "Leaf");
    when(capabilities.areSubAuthoritiesSearchFilters()).thenReturn(false);
    assertNull(resolver.resolve("TAXA.SPECIES", "Leaf"));
  }

  @Test
  void providerErrorsPreventMaterializingTheRequestedGraph() {
    var owl = new TestOWL();
    var provider = mock(Authority.class);
    var request = new Authority.ConfigurationRequest("worldview", "TAXA", "biology:Species",
        Map.of("urn", "test.authority"));
    when(provider.configure(request)).thenReturn("bridge");
    var leafIdentity = identity("Leaf", "Bad");
    when(provider.resolveIdentity("bridge", "Leaf")).thenReturn(leafIdentity);
    var bad = identity("Bad", null);
    when(bad.getNotifications()).thenReturn(List.of(Notification.error("Unknown identity")));
    when(provider.resolveIdentity("bridge", "Bad")).thenReturn(bad);
    var bindings = new AuthorityBindings();
    bindings.configure(request, provider);
    assertThrows(KlabValidationException.class,
        () -> new AuthorityIdentityResolver(owl, bindings).resolve("TAXA", "Leaf"));
    assertNull(owl.getOntology(AuthorityBindings.ontologyId(bindings.get("TAXA"))));
  }
}
