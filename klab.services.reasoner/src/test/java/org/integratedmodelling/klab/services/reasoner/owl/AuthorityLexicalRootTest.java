package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.common.services.client.BaseServiceClient;
import org.integratedmodelling.common.services.client.ReasonerClient;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;

class AuthorityLexicalRootTest {
  static final class TestOWL extends OWL {
    TestOWL() { super(mock(Scope.class)); manager = OWLManager.createOWLOntologyManager(); }
    @Override public synchronized void registerWithReasoner(Ontology ontology) {}
    @Override public synchronized void flushReasoner() {}
  }

  static final class Fixture {
    final TestOWL owl = new TestOWL();
    final Authority provider = mock(Authority.class);
    final AuthorityBindings bindings = new AuthorityBindings();
    final AuthorityIdentityResolver resolver;
    final ReasonerService service;
    final Concept anchor;
    final Authority.ConfigurationRequest request;
    Fixture() throws Exception {
      var biology = owl.requireOntology("biology");
      for (var name : List.of("Taxon", "Species", "OtherBoundary"))
        biology.define(List.of(Axiom.ClassAssertion(name,
            EnumSet.of(SemanticType.IDENTITY, SemanticType.PREDICATE))));
      biology.define(List.of(Axiom.SubClass("Taxon", "Species")));
      biology.getConcept("Taxon").getMetadata().put(CoreOntology.NS.BASE_DECLARATION, "true");
      biology.getConcept("OtherBoundary").getMetadata().put(CoreOntology.NS.BASE_DECLARATION, "true");
      anchor = biology.getConcept("Species"); // Deliberately nested: binding is explicit.
      var seed = new org.integratedmodelling.klab.api.knowledge.impl.CodelistImpl();
      seed.getEntries().add(new org.integratedmodelling.klab.api.knowledge.impl.CodelistImpl.Entry(
          "TAXA", "DomesticCat", "Cat", null, true));
      when(provider.getSemanticBoundaries()).thenReturn(Set.of("OTHER"));
      when(provider.getCodelists("bridge")).thenReturn(Map.of("species", seed));
      when(provider.getCodelistDefinitions("bridge")).thenCallRealMethod();
      when(provider.configure(any())).thenReturn("bridge");
      var cat = identity("Cat", true);
      when(provider.resolveIdentity("bridge", "Cat")).thenReturn(cat);
      request = new Authority.ConfigurationRequest("worldview", "TAXA", "biology:Species",
          Map.of("urn", "test.authority", "semanticBoundaries", Map.of("OTHER", "biology:OtherBoundary"),
              "codelists", Map.of("species", "taxonomy.species")));
      bindings.configure(request, provider);
      resolver = new AuthorityIdentityResolver(owl, bindings);
      owl.setAuthorityResolver(resolver::resolve);
      service = service(owl, resolver);
    }
    Concept cat() { return resolver.resolve("TAXA", "Cat"); }
  }

  static Authority.Identity identity(String id, boolean deferred) {
    var result = mock(Authority.Identity.class);
    when(result.getId()).thenReturn(id); when(result.getConceptName()).thenReturn(id);
    when(result.getLabel()).thenReturn(id); when(result.getParentIds()).thenReturn(List.of());
    when(result.getSemanticBoundaries()).thenReturn(Set.of("OTHER"));
    when(result.getHierarchyStatus()).thenReturn(deferred ? Authority.HierarchyStatus.DEFERRED : Authority.HierarchyStatus.COMPLETE);
    return result;
  }

  static ReasonerService service(OWL owl, AuthorityIdentityResolver resolver) throws Exception {
    var service = mock(ReasonerService.class, CALLS_REAL_METHODS);
    for (var entry : Map.of("owl", (Object) owl, "authorityIdentityResolver", resolver).entrySet()) {
      var field = ReasonerService.class.getDeclaredField(entry.getKey());
      field.setAccessible(true); field.set(service, entry.getValue());
    }
    return service;
  }

  @Test void explicitBindingWinsOverNestedAndMappedParentsWithoutExpandingHierarchy() throws Exception {
    var t = new Fixture(); var cat = t.cat();
    assertEquals("DEFERRED", cat.getMetadata().get(Authority.HIERARCHY_STATUS));
    clearInvocations(t.provider);
    assertSame(t.anchor, t.service.lexicalRoot(cat));
    assertSame(t.anchor, t.service.lexicalRoot(t.owl.getConcept("taxonomy.species:DomesticCat")));
    assertSame(t.anchor, t.service.lexicalRoot(t.owl.getConcept("TAXA:[Cat]")));
    assertNotEquals(t.service.lexicalRoot(t.anchor), t.service.lexicalRoot(cat),
        "The ordinary nested worldview concept inherits Taxon; the authority binding selects Species explicitly");
    verifyNoInteractions(t.provider);
    assertFalse(t.resolver.acceptedFor(cat, t.bindings.get("TAXA")),
        "A lexical-family result is not complete-hierarchy admission");
    var f = t.owl.manager.getOWLDataFactory();
    var ontology = t.owl.getOntology(cat.getNamespace()).getOWLOntology();
    var parent = f.getOWLSubClassOfAxiom(t.owl.getOWLClass(cat), t.owl.getOWLClass(t.anchor));
    t.owl.manager.removeAxiom(ontology, parent);
    assertSame(t.anchor, t.service.lexicalRoot(cat), "Parent ordering/availability does not choose the configured family");
    t.owl.manager.addAxiom(ontology, parent);
    assertSame(t.anchor, t.service.lexicalRoot(cat));
    var complete = identity("Cat", false);
    when(t.provider.resolveIdentity("bridge", "Cat", Authority.ResolutionMode.FULL_HIERARCHY)).thenReturn(complete);
    assertSame(cat, t.resolver.resolve("TAXA", "Cat", true));
    assertSame(t.anchor, t.service.lexicalRoot(cat), "Hierarchy enrichment does not change the lexical binding");
    assertTrue(t.resolver.acceptedFor(cat, t.bindings.get("TAXA")));
  }

  @Test void replayUsesCurrentBindingsAndLoadedClassesWithoutProviderDispatchOrAdmission() throws Exception {
    var t = new Fixture(); var cat = t.cat();
    var restoredBindings = new AuthorityBindings(); restoredBindings.configure(t.request, t.provider);
    var replay = new AuthorityIdentityResolver(t.owl, restoredBindings);
    clearInvocations(t.provider);
    assertSame(t.anchor, replay.lexicalRoot(new ConceptImpl((ConceptImpl) cat)));
    assertFalse(replay.acceptedFor(cat, restoredBindings.get("TAXA")));
    restoredBindings.clear();
    assertNull(replay.lexicalRoot(cat));
    var other = new TestOWL(); var unpopulated = new AuthorityIdentityResolver(other, t.bindings);
    assertNull(unpopulated.lexicalRoot(cat), "A foreign generation's namespace is not a loaded class here");
    verify(t.provider).releaseConfiguration("bridge");
    verifyNoMoreInteractions(t.provider);
    t.bindings.clear();
    assertNull(t.service.lexicalRoot(cat), "Released bindings cannot fall back to arbitrary authority OWL parents");
    var forgedBoundary = new ConceptImpl((ConceptImpl) cat);
    forgedBoundary.getMetadata().put(CoreOntology.NS.BASE_DECLARATION, "true");
    forgedBoundary.getMetadata().put(CoreOntology.NS.ORIGINAL_TRAIT, t.anchor.getUrn());
    assertNull(t.service.lexicalRoot(forgedBoundary), "Transport metadata cannot supply a released bridge");
    forgedBoundary.getType().remove(SemanticType.AUTHORITY_IDENTITY);
    assertNull(t.service.lexicalRoot(forgedBoundary), "Erasing a transported flag does not change the loaded class");
  }

  @Test void suppliedFlagsAndMissingOrWrongTypedAnchorsCannotCreateFamilyEvidence() throws Exception {
    var t = new Fixture(); var cat = t.cat();
    var forged = new ConceptImpl((ConceptImpl) cat); forged.setName("NotLoaded"); forged.setUrn(cat.getNamespace() + ":NotLoaded");
    assertNull(t.resolver.lexicalRoot(forged));
    var fake = new ConceptImpl((ConceptImpl) cat); fake.setNamespace("unconfigured");
    assertNull(t.resolver.lexicalRoot(fake));
    t.anchor.getType().remove(SemanticType.IDENTITY);
    assertNull(t.resolver.lexicalRoot(cat));
    t.anchor.getType().add(SemanticType.IDENTITY);
    t.anchor.getType().add(SemanticType.NOTHING);
    assertNull(t.resolver.lexicalRoot(cat));
    assertNull(t.resolver.lexicalRoot(null));
  }

  @Test void lexicalRootsAreEquivalentAcrossJsonHttpForDirectAndCodelistLookups() throws Exception {
    var t = new Fixture(); var direct = t.cat(); var alias = t.owl.getConcept("taxonomy.species:DomesticCat");
    var mapper = JacksonConfiguration.newObjectMapper();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(ServicesAPI.REASONER.LEXICAL_ROOT, exchange -> {
      var supplied = mapper.readValue(exchange.getRequestBody(), Concept.class);
      var answer = t.service.lexicalRoot(supplied);
      byte[] body = mapper.writeValueAsBytes(answer);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (var out = exchange.getResponseBody()) { out.write(body); }
    });
    server.start();
    clearInvocations(t.provider);
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var client = mock(ReasonerClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client"); field.setAccessible(true); field.set(client, http);
      assertEquals(t.anchor, client.lexicalRoot(direct));
      assertEquals(t.anchor, client.lexicalRoot(alias));
      assertSame(direct, alias);
      verifyNoInteractions(t.provider);
    } finally { server.stop(0); }
  }

  @Test void authoredTopLevelDeclarationsStayBoundariesWhileNestedCrossOntologyPredicatesInherit() throws Exception {
    var owl = new TestOWL(); var first = owl.requireOntology("first"); var second = owl.requireOntology("second");
    var service = service(owl, new AuthorityIdentityResolver(owl, new AuthorityBindings()));
    for (var kind : List.of(SemanticType.ATTRIBUTE, SemanticType.REALM, SemanticType.IDENTITY, SemanticType.ROLE, SemanticType.DOMAIN)) {
      String name = kind.name();
      first.define(List.of(Axiom.ClassAssertion(name, EnumSet.of(kind, SemanticType.PREDICATE))));
      second.define(List.of(Axiom.ClassAssertion(name, EnumSet.of(kind, SemanticType.PREDICATE)),
          Axiom.ClassAssertion(name + "Nested", EnumSet.of(kind, SemanticType.PREDICATE)),
          Axiom.SubClass("first:" + name, name), Axiom.SubClass(name, name + "Nested")));
      var imported = first.getConcept(name); imported.getMetadata().put(CoreOntology.NS.BASE_DECLARATION, "true");
      var declared = second.getConcept(name); declared.getMetadata().put(CoreOntology.NS.BASE_DECLARATION, "true");
      var nested = second.getConcept(name + "Nested");
      assertSame(imported, service.lexicalRoot(imported));
      assertSame(declared, service.lexicalRoot(declared), "A separately authored top-level boundary is not its declared parent");
      assertSame(declared, service.lexicalRoot(nested));
      nested.getMetadata().put(CoreOntology.NS.ORIGINAL_TRAIT, imported.getUrn());
      assertSame(imported, service.lexicalRoot(nested), "Explicit original-family linkage retains alias semantics");
      nested.getMetadata().put(CoreOntology.NS.ORIGINAL_TRAIT, "missing:Absent");
      assertNull(service.lexicalRoot(nested));
    }
  }

  @Test void authoredSpecializationsOfAuthorityClassesKeepTheirOwnTopLevelAndNestedBoundaries() throws Exception {
    var t = new Fixture(); var cat = t.cat(); var authored = t.owl.requireOntology("authored");
    var kinds = EnumSet.of(SemanticType.IDENTITY, SemanticType.PREDICATE, SemanticType.AUTHORITY_IDENTITY);
    authored.define(List.of(Axiom.ClassAssertion("Declared", kinds), Axiom.ClassAssertion("Nested", kinds),
        Axiom.ClassAssertion("DirectNested", kinds), Axiom.SubClass(cat.getUrn(), "Declared"),
        Axiom.SubClass("Declared", "Nested"), Axiom.SubClass(cat.getUrn(), "DirectNested")));
    var declared = authored.getConcept("Declared");
    declared.getMetadata().put(CoreOntology.NS.BASE_DECLARATION, "true");
    assertFalse(authored.isInternal());
    assertSame(declared, t.service.lexicalRoot(declared));
    assertSame(declared, t.service.lexicalRoot(authored.getConcept("Nested")));
    assertSame(t.anchor, t.service.lexicalRoot(authored.getConcept("DirectNested")));
    var altered = new ConceptImpl((ConceptImpl) authored.getConcept("Nested"));
    altered.getMetadata().put(CoreOntology.NS.BASE_DECLARATION, "true");
    assertSame(declared, t.service.lexicalRoot(altered), "Loaded authored boundaries win over transported metadata");
    altered.setUrn(declared.getUrn());
    assertNull(t.service.lexicalRoot(altered), "Mismatched transported class name/URN cannot select a boundary");
  }

  @Test void validatedNestedBindingPreparesItsIdentityRestrictionBeforeComposingAnObservable() throws Exception {
    var t = new Fixture(); var cat = t.cat(); var ontology = t.owl.getOntology("biology");
    t.owl.requireOntology("odo", "http://integratedmodelling.org").define(List.of(Axiom.ObjectPropertyAssertion("hasIdentity")));
    t.owl.requireOntology("klab", "http://integratedmodelling.org/ks").define(List.of(Axiom.AnnotationPropertyAssertion("restrictingProperty")));
    ontology.define(List.of(Axiom.ClassAssertion("Tree", EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE,
        SemanticType.OBSERVABLE))));
    var scope = mock(Scope.class); when(scope.getService(Reasoner.class)).thenReturn(t.service);
    var scopeField = OWL.class.getDeclaredField("scope"); scopeField.setAccessible(true); scopeField.set(t.owl, scope);
    var generic = t.owl.getProperty("odo:hasIdentity");
    assertNull(t.anchor.getMetadata().get(CoreOntology.NS.TRAIT_RESTRICTING_PROPERTY));
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,
        () -> t.owl.restrict(ontology.getConcept("Tree"), generic, LogicalConnector.UNION, List.of(cat), ontology),
        "Knowing the family is insufficient when its nested anchor has no identity restriction port");

    var source = LexicalRootAuthoringTest.parse("""
        ontology biology in domain root version 1.0.0;
        identity OtherBoundary;
        abstract identity Taxon has children
          (Species requires authority TAXA {
            urn: "test.authority",
            semanticBoundaries: { "OTHER": "biology:OtherBoundary" },
            codelists: { species: "taxonomy.species" }
          });
        """, new org.integratedmodelling.klab.runtime.language.WorldviewValidationScope());
    var declaration = source.getStatements().get(1).getChildren().getFirst();
    var world = new WorldviewImpl(); world.setUrn("worldview"); world.setWorldviewId("worldview");
    world.getOntologies().add(source);
    var provider = new Extensions.AuthorityDescriptor("test.authority", Version.create("1.0.0"), true, true, List.of(), List.of());
    var selected = new Worldview.AuthorityBinding("TAXA", "biology:Species", "biology", 0, 0, "source",
        provider, "test.component", Version.create("1.0.0"), null);
    world.getAuthorityBindings().add(selected);
    assertEquals(List.of(), org.integratedmodelling.klab.runtime.language.KimWorldviewValidator.authorityErrors(declaration));
    assertTrue(world.allConceptStatements().contains(declaration));
    var registry = mock(ComponentRegistry.class); when(registry.getAuthority(selected, scope)).thenReturn(t.provider);
    doReturn(registry).when(t.service).getComponentRegistry();
    for (var value : Map.of("worldview", (Object) world, "authorityBindings", t.bindings,
        "authorityBootstrapActor", true).entrySet()) {
      var field = ReasonerService.class.getDeclaredField(value.getKey()); field.setAccessible(true); field.set(t.service, value.getValue());
    }
    var configure = ReasonerService.class.getDeclaredMethod("configureAuthorityBinding", Authority.ConfigurationRequest.class, Scope.class);
    configure.setAccessible(true);
    assertEquals("bridge", configure.invoke(t.service, t.request, scope));
    assertEquals("biology:isSpecies", t.anchor.getMetadata().get(CoreOntology.NS.TRAIT_RESTRICTING_PROPERTY));
    assertNotNull(t.owl.getProperty("biology:isSpecies"));
    long configuredAxioms = ontology.getOWLOntology().getAxiomCount();
    assertEquals("bridge", configure.invoke(t.service, t.request, scope));
    assertEquals(configuredAxioms, ontology.getOWLOntology().getAxiomCount(), "Repeated binding configuration is idempotent");
    assertDoesNotThrow(() -> t.owl.restrict(ontology.getConcept("Tree"), generic, LogicalConnector.UNION, List.of(cat), ontology));
    var factory = t.owl.manager.getOWLDataFactory();
    assertTrue(ontology.getOWLOntology().containsAxiom(factory.getOWLSubClassOfAxiom(
        t.owl.getOWLClass(ontology.getConcept("Tree")), factory.getOWLObjectSomeValuesFrom(
            t.owl.getProperty("biology:isSpecies").getOWLEntity().asOWLObjectProperty(), t.owl.getOWLClass(cat)))));
    clearInvocations(t.provider);
    long composedAxioms = ontology.getOWLOntology().getAxiomCount();
    assertSame(t.anchor, t.service.lexicalRoot(cat));
    assertEquals(composedAxioms, ontology.getOWLOntology().getAxiomCount(), "Lookup does not prepare schema or change an ontology");
    verifyNoInteractions(t.provider);
  }
}
