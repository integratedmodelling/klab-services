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

  /** Empty publication fixtures still traverse the real declaration-staging primitive. */
  private static OWL stagingOwl() throws Exception {
    var owl = mock(OWL.class);
    var manager = OWLManager.createOWLOntologyManager();
    field(owl, "manager", manager);
    var property = manager.getOWLDataFactory().getOWLObjectProperty(
        org.semanticweb.owlapi.model.IRI.create("http://integratedmodelling.org/ks/odo#isInherentTo"));
    when(owl.getProperty("odo:isInherentTo")).thenReturn(new Property(property, "odo"));
    when(owl.compilationMetrics()).thenReturn(Map.of(
        "reasonerCreations", 0L, "actualFlushes", 0L,
        "satisfiabilityQueries", 0L, "entailmentQueries", 0L));
    return owl;
  }

  @Test void unrelatedOntologyAndStrategySuccessCannotPublishMissingKnowledgeAsReady() throws Exception {
    var owl = stagingOwl();
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
    var worldview = new org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl();
    worldview.setUrn("worldview");
    field(reasoner, "worldview", worldview);
    when(resources.retrieve("worldview", Worldview.class, scope)).thenReturn(worldview);
    when(resources.retrieve("A", KimOntology.class, scope)).thenThrow(new IllegalStateException("A unavailable"));
    when(resources.retrieve("B", KimOntology.class, scope)).thenReturn(ontology("B"));
    var first = reasoner.updateKnowledge(ontologyChange("A"), scope);
    verify(owl, never()).releaseOntology(releasedA);
    assertFalse(first.getNotifications().isEmpty());
    assertTrue(signal(reasoner).endsWith(":7:failed"));
    reasoner.updateKnowledge(ontologyChange("B"), scope);
    assertTrue(signal(reasoner).endsWith(":8:failed"));
    assertFalse(((AtomicBoolean) field(reasoner, "consistent")).get());
    assertFalse(((Map<?, ?>) field(reasoner, "loadedOntologySources")).containsKey("A"));
    assertTrue(((Map<?, ?>) field(reasoner, "loadedOntologySources")).containsKey("B"));

    var strategies = new ResourceSet();
    var strategy = new KimObservationStrategiesImpl(); strategy.setUrn("policy");
    strategies.getObservationStrategies().add(new ResourceSet.Resource("resources", "policy", "project",
        null, KlabAsset.KnowledgeClass.classify(KimObservationStrategyDocument.class), 0, false));
    when(resources.retrieve("policy", KimObservationStrategyDocument.class, scope)).thenReturn(strategy);
    field(reasoner, "observationReasoner", mock(ObservationReasoner.class));
    reasoner.updateKnowledge(strategies, scope);
    assertTrue(signal(reasoner).endsWith(":9:failed"));

    doReturn(ontology("A")).when(resources).retrieve("A", KimOntology.class, scope);
    reasoner.updateKnowledge(ontologyChange("A"), scope);
    assertTrue(signal(reasoner).endsWith(":10:ready"));
    assertTrue(((AtomicBoolean) field(reasoner, "consistent")).get());
    assertTrue(((Map<?, ?>) field(reasoner, "loadedOntologySources")).containsKey("A"));
  }

  @Test void sharedBeanErrorsRejectLoadBeforeOwlInitializationEmissionOrReasoning() throws Exception {
    var owl = mock(OWL.class);
    var reasoner = reasoner(owl, new AuthorityBindings());
    var previous = new org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl();
    previous.setUrn("previous"); field(reasoner, "worldview", previous);
    var saved = ontology("imod");
    var alias = new KimConceptStatementImpl(); alias.setUrn("InvalidAlias"); alias.setAlias(true);
    alias.getChildren().add(new KimConceptStatementImpl()); // Existing shared bean rule, no supplied diagnostic.
    saved.getStatements().add(alias);
    var candidate = new org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl();
    candidate.setOntologies(List.of(saved)); candidate.setEmpty(false);
    var rejected = reasoner.loadKnowledge(candidate, mock(Scope.class));
    assertTrue(rejected.getNotifications().stream().anyMatch(n -> n.getMessage().contains("alias")));
    verifyNoInteractions(owl);
    verify(reasoner, never()).initializeService();
    assertSame(previous, field(reasoner, "worldview"));
    assertEquals(7, reasoner.knowledgeRevision());
  }

  @Test void suppliedBeanErrorRejectsMixedBatchWithoutReleasingPriorKnowledgeOrMetadata() throws Exception {
    for (var failure : List.of("bean error", "missing import", "unavailable bean")) {
    var owl = mock(OWL.class);
    var bindings = new AuthorityBindings();
    var reasoner = reasoner(owl, bindings);
    var resources = mock(ResourcesService.class); var scope = mock(UserScope.class);
    var capabilities = mock(ResourcesService.Capabilities.class);
    when(capabilities.getServiceId()).thenReturn("resources");
    when(resources.capabilities(scope)).thenReturn(capabilities);
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    var manager = mock(ScopeManager.class);
    when(manager.collectMessagePayload(eq(scope), eq(Notification.class), any())).thenReturn(scope);
    doReturn(manager).when(reasoner).getScopeManager();
    var previous = new org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl();
    previous.setUrn("worldview"); previous.setOntologies(List.of(ontology("A"), ontology("B")));
    field(reasoner, "worldview", previous);
    when(resources.retrieve("worldview", Worldview.class, scope)).thenReturn(previous);
    var valid = ontology("A"); var invalid = ontology("B");
    if (failure.equals("bean error")) invalid.getNotifications().add(Notification.error("Authoritative bean error"));
    if (failure.equals("missing import")) invalid.setImportedOntologies(List.of("missing"));
    when(resources.retrieve("A", KimOntology.class, scope)).thenReturn(valid);
    when(resources.retrieve("B", KimOntology.class, scope)).thenReturn(failure.equals("unavailable bean") ? null : invalid);
    var sources = (Map<String, String>) field(reasoner, "loadedOntologySources");
    var hashA = org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.sourceHash("previous A");
    var hashB = org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.sourceHash("previous B");
    sources.put("A", hashA); sources.put("B", hashB);
    var domains = (Map<String, Concept>) field(reasoner, "declarationDomains");
    var domain = mock(Concept.class); domains.put("A", domain);
    var changes = ontologyChange("A"); changes.getOntologies().addAll(ontologyChange("B").getOntologies());
    var rejected = reasoner.updateKnowledge(changes, scope);
    assertFalse(rejected.getNotifications().isEmpty(), failure);
    if (failure.equals("bean error")) assertEquals(1, rejected.getNotifications().stream()
        .filter(n -> n.getMessage().contains("Authoritative bean error")).count());
    verifyNoInteractions(owl);
    assertSame(previous, field(reasoner, "worldview"));
    assertSame(bindings, field(reasoner, "authorityBindings"));
    assertEquals(Map.of("A", hashA, "B", hashB), sources);
    assertSame(domain, domains.get("A"));
    assertEquals(7, reasoner.knowledgeRevision());
    assertTrue(signal(reasoner).endsWith(":7:failed"));
    }
  }

  @Test void freshRepairIgnoresOldAggregateErrorsAndCompilesTheFrozenValidatedBean() throws Exception {
    var owl = stagingOwl(); var reasoner = reasoner(owl, new AuthorityBindings());
    var resources = mock(ResourcesService.class); var scope = mock(UserScope.class);
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    var capabilities = mock(ResourcesService.Capabilities.class);
    when(capabilities.getServiceId()).thenReturn("resources");
    when(resources.capabilities(scope)).thenReturn(capabilities);
    var parsing = mock(UserScope.class); var captured = new AtomicReference<List<Notification>>();
    var manager = mock(ScopeManager.class);
    when(manager.collectMessagePayload(eq(scope), eq(Notification.class), any())).thenAnswer(call -> {
      captured.set(call.getArgument(2)); return parsing;
    });
    doReturn(manager).when(reasoner).getScopeManager();
    var previous = new org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl();
    previous.setUrn("worldview"); previous.setEmpty(true);
    previous.getNotifications().add(Notification.error("old aggregate error"));
    var old = ontology("imod"); old.getNotifications().add(Notification.error("old source error"));
    previous.setOntologies(List.of(old)); field(reasoner, "worldview", previous);
    when(resources.retrieve(eq("worldview"), eq(Worldview.class), any())).thenAnswer(call -> {
      if (call.getArgument(2) == parsing) captured.get().add(Notification.error("unrelated aggregate channel error"));
      return previous;
    });
    var fresh = ontology("imod"); fresh.setSourceCode("saved repaired source");
    when(resources.retrieve("imod", KimOntology.class, parsing)).thenReturn(fresh);
    var retained = mock(Ontology.class); when(owl.getOntology("imod")).thenReturn(retained);
    doAnswer(call -> {
      // Edit races with compilation after preflight. It cannot replace the validated source.
      fresh.setSourceCode("later unvalidated source");
      fresh.getNotifications().add(Notification.error("later source error"));
      return null;
    }).when(owl).releaseOntology(retained);
    var updated = reasoner.updateKnowledge(ontologyChange("imod"), scope);
    assertTrue(updated.getNotifications().isEmpty());
    assertTrue(signal(reasoner).endsWith(":8:ready"));
    assertEquals(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.sourceHash("saved repaired source"),
        ((Map<?, ?>) field(reasoner, "loadedOntologySources")).get("imod"));
    verify(resources, times(1)).retrieve("imod", KimOntology.class, parsing);
    verify(owl).releaseOntology(retained);
  }

  private static KimObservationStrategiesImpl strategyDocument(String reference) {
    var document = new KimObservationStrategiesImpl(); document.setUrn("policy"); document.setSourceCode("strategies policy");
    var concept = new KimConceptImpl(); concept.setName(reference);
    var observable = new KimObservableImpl(); observable.setSemantics(concept);
    var alternative = new KimObservationPlanImpl.MatchAlternativeImpl(); alternative.setObservable(observable);
    var selection = new KimObservationPlanImpl.StrategySelectionImpl(); selection.setAlternatives(List.of(alternative));
    var strategy = new KimObservationStrategyImpl(); strategy.setUrn("observe"); strategy.setSelection(selection);
    document.getStatements().add(strategy); return document;
  }

  @Test void reviewGapMixedBatchesNeverReleaseOwlStrategiesOrConfiguredAuthorities() throws Exception {
    for (var failure : List.of("strategy missing", "dependency error", "nested error", "authority missing", "authority conflict", "authority boundary")) {
      var owl = mock(OWL.class); var observation = mock(ObservationReasoner.class);
      var bindings = new AuthorityBindings(); var provider = mock(Authority.class);
      when(provider.configure(any())).thenReturn("previous-configuration");
      bindings.configure(new Authority.ConfigurationRequest("worldview", "TAXA", "alpha:Taxon", Map.of("urn", "provider.taxa")), provider);
      var retainedBindings = bindings.snapshot();
      var reasoner = reasoner(owl, bindings); field(reasoner, "observationReasoner", observation);
      var scope = mock(UserScope.class); var resources = mock(ResourcesService.class);
      when(scope.getService(ResourcesService.class)).thenReturn(resources);
      var capabilities = mock(ResourcesService.Capabilities.class); when(capabilities.getServiceId()).thenReturn("resources");
      when(resources.capabilities(scope)).thenReturn(capabilities);
      var manager = mock(ScopeManager.class);
      when(manager.collectMessagePayload(eq(scope), eq(Notification.class), any())).thenReturn(scope);
      doReturn(manager).when(reasoner).getScopeManager();
      var previous = new org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl(); previous.setUrn("worldview");
      field(reasoner, "worldview", previous);
      var sources = (Map<String, String>) field(reasoner, "loadedOntologySources");
      var oldHash = org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.sourceHash("previous source");
      sources.put("alpha", oldHash);
      var alpha = ontology("alpha"); var beta = ontology("beta");
      var known = new KimConceptStatementImpl(); known.setUrn("Known"); beta.getStatements().add(known);
      var strategy = strategyDocument(failure.equals("strategy missing") ? "missing:Thing" : "beta:Known");
      if (failure.equals("dependency error")) beta.getNotifications().add(Notification.error("current dependency error"));
      if (failure.equals("nested error")) {
        var nested = new KimConceptImpl(); nested.setName("beta:Known");
        nested.getNotifications().add(Notification.create("nested supplied error", Notification.Level.SystemError));
        known.setDeclaredInherent(nested);
      }
      var sourceWorld = new org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl(); sourceWorld.setUrn("worldview");
      sourceWorld.setOntologies(List.of(alpha, beta));
      if (failure.startsWith("authority")) {
        var root = new KimConceptStatementImpl(); root.setUrn("Taxon"); root.setNamespace("alpha");
        root.getType().add(SemanticType.IDENTITY); root.setAuthorityRequired("TAXA");
        root.getAuthorityParameters().put("urn", "provider.taxa"); alpha.getStatements().add(root);
        String message = "Missing authority provider descriptor";
        String owner = "alpha:Taxon";
        if (failure.equals("authority conflict")) {
          var other = new KimConceptStatementImpl(); other.setUrn("Other"); other.setNamespace("beta");
          other.getType().add(SemanticType.IDENTITY); other.setAuthorityRequired("TAXA");
          other.getAuthorityParameters().put("urn", "provider.taxa"); beta.getStatements().add(other);
          message = "Conflicting local authority name TAXA"; owner = "beta:Other";
        }
        if (failure.equals("authority boundary")) {
          root.getAuthorityParameters().put("semanticBoundaries", Map.of("SPECIES", "beta:Missing"));
          message = "Semantic boundary must name a valid worldview identity: beta:Missing";
        }
        sourceWorld.getNotifications().add(Notification.error("Authority TAXA at " + owner + ": " + message));
      }
      when(resources.retrieve("alpha", KimOntology.class, scope)).thenReturn(alpha);
      when(resources.retrieve("policy", KimObservationStrategyDocument.class, scope)).thenReturn(strategy);
      when(resources.retrieve("worldview", Worldview.class, scope)).thenReturn(sourceWorld);
      var changes = ontologyChange("alpha");
      changes.getObservationStrategies().add(new ResourceSet.Resource("resources", "policy", "project", null,
          KlabAsset.KnowledgeClass.classify(KimObservationStrategyDocument.class), 0, false));
      var rejected = reasoner.updateKnowledge(changes, scope);
      assertFalse(rejected.getNotifications().isEmpty(), failure);
      if (failure.equals("authority conflict")) assertTrue(rejected.getNotifications().stream()
          .anyMatch(n -> n.getMessage().contains("Conflicting local authority name")));
      verifyNoInteractions(owl, observation);
      verify(provider, never()).releaseConfiguration(anyString());
      assertEquals(retainedBindings, bindings.snapshot());
      assertSame(previous, field(reasoner, "worldview"));
      assertEquals(Map.of("alpha", oldHash), sources);
      assertEquals(7, reasoner.knowledgeRevision());
    }
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
