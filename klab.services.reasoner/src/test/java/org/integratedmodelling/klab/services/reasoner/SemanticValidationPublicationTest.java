package org.integratedmodelling.klab.services.reasoner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import com.github.benmanes.caffeine.cache.Cache;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.integratedmodelling.klab.services.reasoner.owl.*;
import org.integratedmodelling.klab.services.scopes.ScopeManager;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;

class SemanticValidationPublicationTest {
  private static void field(Object instance, String name, Object value) throws Exception {
    for (var type = instance.getClass(); type != null; type = type.getSuperclass()) {
      try {
        var field = type.getDeclaredField(name);
        field.setAccessible(true); field.set(instance, value); return;
      } catch (NoSuchFieldException ignored) {}
    }
    throw new NoSuchFieldException(name);
  }

  private static Object field(Object instance, String name) throws Exception {
    var field = ReasonerService.class.getDeclaredField(name);
    field.setAccessible(true); return field.get(instance);
  }

  /** Real service methods, with infrastructure injected instead of starting/configuring a service. */
  private static ReasonerService reasoner(OWL owl, AuthorityBindings bindings) throws Exception {
    var reasoner = mock(ReasonerService.class, CALLS_REAL_METHODS);
    var state = new SemanticValidationState(); state.settle(7, true);
    field(reasoner, "semanticValidationState", state);
    field(reasoner, "knowledgeRevision", new AtomicLong(7));
    field(reasoner, "knowledgeReady", true);
    field(reasoner, "consistent", new AtomicBoolean(true));
    field(reasoner, "authorityBindings", bindings);
    field(reasoner, "owl", owl);
    field(reasoner, "advisories", new ArrayList<Notification>());
    field(reasoner, "worldviewLoadDiagnostics", List.of());
    field(reasoner, "loadedOntologySources", new HashMap<String, String>());
    field(reasoner, "declarationDomains", new HashMap<String, Concept>());
    field(reasoner, "loadedOntologyDiagnostics", new HashMap<String, List<Notification>>());
    field(reasoner, "conceptSyntax", new ConceptSyntaxCache());
    for (var cache : List.of("semanticExpressions", "concepts", "observables", "subsumption"))
      field(reasoner, cache, mock(Cache.class));
    field(reasoner, "cachedLoadPercentage", new AtomicInteger());
    field(reasoner, "serviceNotifications", List.of());
    var scope = mock(ServiceScope.class);
    doReturn(scope).when(reasoner).serviceScope();
    doReturn("reasoner").when(reasoner).serviceId();
    doReturn(KlabService.Type.REASONER).when(reasoner).serviceType();
    return reasoner;
  }

  private static ResourceSet ontologyChange(String urn) {
    var changes = new ResourceSet();
    changes.getOntologies().add(new ResourceSet.Resource(
        "resources", urn, "project", null, KlabAsset.KnowledgeClass.ONTOLOGY, 0, false));
    return changes;
  }

  private static KimOntologyImpl ontology(String urn) {
    var ontology = new KimOntologyImpl(); ontology.setUrn(urn);
    ontology.setSourceCode("ontology " + urn); ontology.setDomain(KimOntology.rootDomain);
    return ontology;
  }

  private static String signal(ReasonerService reasoner) {
    return (String) reasoner.status().getMetadata().get(SemanticValidationState.METADATA_KEY);
  }

  @Test void unrelatedOntologyAndStrategySuccessCannotPublishMissingKnowledgeAsReady() throws Exception {
    var owl = mock(OWL.class);
    var releasedA = mock(Ontology.class); when(owl.getOntology("A")).thenReturn(releasedA);
    var reasoner = reasoner(owl, new AuthorityBindings());
    var scope = mock(UserScope.class); var resources = mock(ResourcesService.class);
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    var capabilities = mock(ResourcesService.Capabilities.class);
    when(capabilities.getServiceId()).thenReturn("resources");
    when(resources.capabilities(scope)).thenReturn(capabilities);
    var manager = mock(ScopeManager.class);
    when(manager.collectMessagePayload(eq(scope), eq(Notification.class), any())).thenReturn(scope);
    doReturn(manager).when(reasoner).getScopeManager();
    var worldview = mock(Worldview.class); when(worldview.getUrn()).thenReturn("worldview");
    field(reasoner, "worldview", worldview);
    when(resources.retrieve("worldview", Worldview.class, scope)).thenReturn(worldview);
    when(resources.retrieve("A", KimOntology.class, scope)).thenThrow(new IllegalStateException("A unavailable"));
    when(resources.retrieve("B", KimOntology.class, scope)).thenReturn(ontology("B"));
    var first = reasoner.updateKnowledge(ontologyChange("A"), scope);
    verify(owl).releaseOntology(releasedA);
    assertFalse(first.getNotifications().isEmpty());
    assertTrue(signal(reasoner).endsWith(":8:failed"));
    reasoner.updateKnowledge(ontologyChange("B"), scope);
    assertTrue(signal(reasoner).endsWith(":9:failed"));
    assertFalse(((AtomicBoolean) field(reasoner, "consistent")).get());
    assertFalse(((Map<?, ?>) field(reasoner, "loadedOntologySources")).containsKey("A"));
    assertTrue(((Map<?, ?>) field(reasoner, "loadedOntologySources")).containsKey("B"));

    var strategies = new ResourceSet();
    var strategy = mock(KimObservationStrategyDocument.class);
    when(strategy.getUrn()).thenReturn("policy"); when(strategy.getStatements()).thenReturn(List.of());
    strategies.getObservationStrategies().add(new ResourceSet.Resource("resources", "policy", "project",
        null, KlabAsset.KnowledgeClass.classify(KimObservationStrategyDocument.class), 0, false));
    when(resources.retrieve("policy", KimObservationStrategyDocument.class, scope)).thenReturn(strategy);
    field(reasoner, "observationReasoner", mock(ObservationReasoner.class));
    reasoner.updateKnowledge(strategies, scope);
    assertTrue(signal(reasoner).endsWith(":10:failed"));

    doReturn(ontology("A")).when(resources).retrieve("A", KimOntology.class, scope);
    reasoner.updateKnowledge(ontologyChange("A"), scope);
    assertTrue(signal(reasoner).endsWith(":11:ready"));
    assertTrue(((AtomicBoolean) field(reasoner, "consistent")).get());
    assertTrue(((Map<?, ?>) field(reasoner, "loadedOntologySources")).containsKey("A"));
  }

  private static class TestOWL extends OWL {
    TestOWL() throws Exception {
      super(mock(Scope.class));
      var manager = OWL.class.getDeclaredField("manager"); manager.setAccessible(true);
      manager.set(this, OWLManager.createOWLOntologyManager());
    }
    @Override public synchronized void registerWithReasoner(Ontology ontology) {}
    @Override public synchronized void flushReasoner() {}
  }

  @Test void actualAuthorityEnrichmentCallbackPublishesItsRevisionWithoutClearingFailureOrLoading() throws Exception {
    for (var phase : List.of("ready", "failed", "loading")) {
      var owl = new TestOWL();
      owl.requireOntology("biology").define(List.of(
          Axiom.ClassAssertion("Taxon", EnumSet.of(SemanticType.IDENTITY)),
          Axiom.ClassAssertion("Species", EnumSet.of(SemanticType.IDENTITY))));
      var provider = mock(Authority.class);
      when(provider.getSemanticBoundaries()).thenReturn(Set.of("SPECIES"));
      var request = new Authority.ConfigurationRequest("worldview", "TAXA", "biology:Taxon",
          Map.of("urn", "test.authority", "semanticBoundaries", Map.of("SPECIES", "biology:Species")));
      when(provider.configure(request)).thenReturn("bridge");
      var leaf = mock(Authority.Identity.class);
      when(leaf.getId()).thenReturn("Leaf"); when(leaf.getConceptName()).thenReturn("Leaf");
      when(leaf.getHierarchyStatus()).thenReturn(Authority.HierarchyStatus.DEFERRED);
      when(leaf.getSemanticBoundaries()).thenReturn(Set.of("SPECIES"));
      when(provider.resolveIdentity("bridge", "Leaf")).thenReturn(leaf);
      var full = mock(Authority.Identity.class);
      when(full.getId()).thenReturn("Leaf"); when(full.getConceptName()).thenReturn("Leaf");
      when(full.getHierarchyStatus()).thenReturn(Authority.HierarchyStatus.COMPLETE);
      when(full.getSemanticBoundaries()).thenReturn(Set.of("SPECIES"));
      when(provider.resolveIdentity("bridge", "Leaf", Authority.ResolutionMode.FULL_HIERARCHY)).thenReturn(full);
      var bindings = new AuthorityBindings(); bindings.configure(request, provider);
      var reasoner = reasoner(owl, bindings);
      var state = (SemanticValidationState) field(reasoner, "semanticValidationState");
      if (phase.equals("failed")) {
        state.beginUpdate(7, Set.of("ontology:A")); state.settleUpdate(7, Set.of("ontology:A"), false);
      } else if (phase.equals("loading")) state.beginUpdate(7, Set.of("ontology:A"));
      reasoner.initializeAuthorityIdentityResolver();
      var resolver = (AuthorityIdentityResolver) field(reasoner, "authorityIdentityResolver");
      var concept = resolver.resolve("TAXA", "Leaf");
      assertEquals(7, reasoner.knowledgeRevision());
      assertSame(concept, resolver.resolve("TAXA", "Leaf", true));
      assertEquals(8, reasoner.knowledgeRevision());
      assertTrue(signal(reasoner).endsWith(phase.equals("loading") ? ":7:loading" : ":8:" + phase));
      resolver.ensureHierarchy(concept);
      assertEquals(8, reasoner.knowledgeRevision());
    }
  }
}
