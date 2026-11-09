package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;

class ConceptSyntaxCacheTest {
  @Test void repeatedQualifiedCoreLookupsReuseSyntaxButStillProjectCurrentEligibility() {
    var reasoner = mock(org.integratedmodelling.klab.services.reasoner.ReasonerService.class, CALLS_REAL_METHODS);
    var resources = mock(ResourcesService.class);
    var scope = mock(org.integratedmodelling.klab.api.scope.ServiceScope.class);
    var cache = new ConceptSyntaxCache();
    doReturn(scope).when(reasoner).serviceScope();
    doReturn(cache).when(reasoner).conceptSyntaxCache();
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    var concept = new org.integratedmodelling.common.knowledge.ConceptImpl();
    concept.setUrn("test:Green test:Tree"); concept.setType(EnumSet.of(SemanticType.SUBJECT, SemanticType.OBSERVABLE));
    when(resources.declareConcept(concept.getUrn())).thenReturn(syntax("test:Tree"));
    doAnswer(call -> call.getArgument(0)).when(reasoner).projectExpressionStatus(any(), any());
    for (int i = 0; i < 20; i++) assertSame(concept, reasoner.coreObservable(concept));
    verify(resources).declareConcept(concept.getUrn());
    verify(reasoner, times(20)).projectExpressionStatus(eq(concept), any());
    cache.clear(); reasoner.coreObservable(concept);
    verify(resources, times(2)).declareConcept(concept.getUrn());
  }
  @Test void separateBuildersShareTheReasonerCache() {
    var resources = mock(ResourcesService.class);
    var reasoner = mock(org.integratedmodelling.klab.services.reasoner.ReasonerService.class);
    var scope = mock(org.integratedmodelling.klab.api.scope.Scope.class);
    var head = mock(org.integratedmodelling.klab.api.knowledge.Concept.class);
    var operand = mock(org.integratedmodelling.klab.api.knowledge.Concept.class);
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    when(reasoner.conceptSyntaxCache()).thenReturn(new ConceptSyntaxCache());
    when(head.getUrn()).thenReturn("test:Tree");
    when(operand.getUrn()).thenReturn("test:Forest");
    when(resources.declareConcept("test:Tree")).thenReturn(syntax("test:Tree"));
    when(resources.declareConcept("test:Forest")).thenReturn(syntax("test:Forest"));
    for (int i = 0; i < 20; i++) SemanticsBuilder.create(head, reasoner, scope).of(operand);
    verify(resources).declareConcept("test:Tree");
    verify(resources).declareConcept("test:Forest");
    verifyNoMoreInteractions(resources);
  }

  @Test void invalidationDuringAnInflightParseCannotRepopulateTheCurrentRevision() throws Exception {
    var resources = mock(ResourcesService.class);
    var entered = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    when(resources.declareConcept("tree")).thenAnswer(call -> {
      entered.countDown();
      assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
      return syntax("test:Old");
    }).thenReturn(syntax("test:New"));
    var cache = new ConceptSyntaxCache();
    var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    try {
      var pending = worker.submit(() -> cache.get(resources, "tree"));
      assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
      cache.clear();
      release.countDown();
      pending.get(5, java.util.concurrent.TimeUnit.SECONDS);
      assertEquals("test:New", cache.get(resources, "tree").getName());
      verify(resources, times(2)).declareConcept("tree");
    } finally { release.countDown(); worker.shutdownNow(); }
  }

  private KimConceptImpl syntax(String name) {
    var result = new KimConceptImpl();
    result.setName(name);
    result.setType(EnumSet.of(SemanticType.SUBJECT, SemanticType.OBSERVABLE,
        SemanticType.COUNTABLE));
    return result;
  }

  @Test void repeatedBuildersReuseParsingButCannotMutateCachedChildren() {
    var resources = mock(ResourcesService.class);
    var original = syntax("test:Tree");
    original.setInherent(syntax("test:Forest"));
    when(resources.declareConcept("tree")).thenReturn(original);
    var cache = new ConceptSyntaxCache();
    var first = (KimConceptImpl) cache.get(resources, "tree");
    ((KimConceptImpl) first.getInherent()).setName("test:Changed");
    first.getType().clear();
    var second = cache.get(resources, "tree");
    assertEquals("test:Forest", second.getInherent().getName());
    assertTrue(second.is(SemanticType.SUBJECT));
    verify(resources).declareConcept("tree");
    cache.clear();
    cache.get(resources, "tree");
    verify(resources, times(2)).declareConcept("tree");
  }

  @Test void missingAndDiagnosticResultsAreRetriedAndServicesAreIsolated() {
    var resources = mock(ResourcesService.class);
    var other = mock(ResourcesService.class);
    var diagnostic = syntax("test:Tree");
    diagnostic.getNotifications().add(Notification.warning("Not ready"));
    var clean = syntax("test:Tree");
    when(resources.declareConcept("tree")).thenReturn(null, diagnostic, clean);
    when(other.declareConcept("tree")).thenReturn(syntax("other:Tree"));
    var cache = new ConceptSyntaxCache();
    assertNull(cache.get(resources, "tree"));
    assertSame(diagnostic, cache.get(resources, "tree"));
    assertEquals("test:Tree", cache.get(resources, "tree").getName());
    assertEquals("test:Tree", cache.get(resources, "tree").getName());
    assertEquals("other:Tree", cache.get(other, "tree").getName());
    verify(resources, times(3)).declareConcept("tree");
  }
}
