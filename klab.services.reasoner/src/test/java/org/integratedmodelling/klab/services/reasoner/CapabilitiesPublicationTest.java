package org.integratedmodelling.klab.services.reasoner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.services.configuration.ReasonerConfiguration;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.junit.jupiter.api.Test;

class CapabilitiesPublicationTest {
  private static final String HASH = "a".repeat(64);

  private static void field(Object instance, String name, Object value) throws Exception {
    for (var type = instance.getClass(); type != null; type = type.getSuperclass()) {
      try {
        var field = type.getDeclaredField(name);
        field.setAccessible(true); field.set(instance, value); return;
      } catch (NoSuchFieldException ignored) {}
    }
    throw new NoSuchFieldException(name);
  }

  private static Worldview.AuthorityBinding binding() throws Exception {
    return new Worldview.AuthorityBinding("TAXA", "biology:Species", "biology", 0, 1, HASH,
        new Extensions.AuthorityDescriptor("test.taxa", new Version("0.1.1"), true, true,
            new ArrayList<>(List.of("species")), new ArrayList<>(List.of("taxa"))),
        "test.component", new Version("0.1.1"), new URL("http://localhost:8091/reasoner"));
  }

  private record Fixture(ReasonerService reasoner, SemanticValidationState state,
                         ComponentRegistry registry, AtomicLong revision) {}

  /** Infrastructure is mocked; capabilities and publication are real service methods. */
  private static Fixture fixture() throws Exception {
    var reasoner = mock(ReasonerService.class, CALLS_REAL_METHODS);
    var state = new SemanticValidationState(); state.settle(7, true);
    var revision = new AtomicLong(7);
    field(reasoner, "semanticValidationState", state);
    field(reasoner, "knowledgeRevision", revision);
    field(reasoner, "configuration", new ReasonerConfiguration());
    field(reasoner, "capabilitiesGeneration", 0L);
    field(reasoner, "publishedSemanticCapabilities", SemanticCapabilitiesSnapshot.capture(
        0, "worldview-id", "imod", true, List.of(binding()),
        new WorldviewCommitment(1, "worldview-id", Map.of("biology", HASH))));
    var registry = mock(ComponentRegistry.class);
    when(registry.getInstalledComponents(any())).thenReturn(List.of());
    doReturn(registry).when(reasoner).getComponentRegistry();
    return new Fixture(reasoner, state, registry, revision);
  }

  @Test void discoveryReturnsWhileIngestionOwnsTheServiceMonitor() throws Exception {
    var fixture = fixture();
    // Any accidental live model access would fail, even without the monitor assertion.
    var changing = mock(Worldview.class);
    when(changing.getWorldviewId()).thenThrow(new AssertionError("Live worldview read"));
    field(fixture.reasoner(), "worldview", changing);
    var worker = Executors.newSingleThreadExecutor();
    var entered = new CountDownLatch(1);
    try {
      synchronized (fixture.reasoner()) {
        fixture.state().begin(7);
        field(fixture.reasoner(), "capabilitiesGeneration", 1L);
        var future = worker.submit(() -> {
          entered.countDown(); return fixture.reasoner().capabilities(mock(Scope.class));
        });
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        var capabilities = future.get(2, TimeUnit.SECONDS);
        assertEquals("worldview-id", capabilities.getWorldviewId());
        assertFalse(capabilities.isConsistent());
        assertNull(capabilities.getWorldviewCommitment());
        assertTrue(capabilities.getAuthorityBindings().isEmpty());
      }
      verifyNoInteractions(changing);
      verify(fixture.registry(), never()).getComponents(any());
    } finally { worker.shutdownNow(); }
  }

  @Test void settledStatusCannotExposeThePriorGenerationBeforePublication() throws Exception {
    var fixture = fixture();
    fixture.revision().set(8);
    field(fixture.reasoner(), "capabilitiesGeneration", 1L);
    fixture.state().settle(8, true);
    var hidden = fixture.reasoner().capabilities(mock(Scope.class));
    assertEquals(8, hidden.getKnowledgeRevision());
    assertFalse(hidden.isConsistent());
    assertNull(hidden.getWorldviewCommitment());

    var worldview = new WorldviewImpl(); worldview.setWorldviewId("new-id"); worldview.setUrn("imod");
    field(fixture.reasoner(), "worldview", worldview);
    field(fixture.reasoner(), "knowledgeReady", true);
    field(fixture.reasoner(), "consistent", new AtomicBoolean(true));
    var sources = new HashMap<>(Map.of("new-ontology", "b".repeat(64)));
    field(fixture.reasoner(), "loadedOntologySources", sources);
    field(fixture.reasoner(), "authorityBindings", new AuthorityBindings());
    var publish = ReasonerService.class.getDeclaredMethod("publishSemanticCapabilities");
    publish.setAccessible(true);
    synchronized (fixture.reasoner()) { publish.invoke(fixture.reasoner()); }
    sources.clear(); // Subsequent mutations cannot change the published commitment.
    var current = fixture.reasoner().capabilities(mock(Scope.class));
    assertTrue(current.isConsistent());
    assertEquals("new-id", current.getWorldviewId());
    assertEquals(Map.of("new-ontology", "b".repeat(64)), current.getWorldviewCommitment().ontologies());
  }

  @Test void requestStraddlingASettledUpdateCannotCertifyNewMetadataWithTheOldRevision()
      throws Exception {
    var fixture = fixture();
    var capturedRevision = new CountDownLatch(1);
    var continueRequest = new CountDownLatch(1);
    var firstRead = new AtomicBoolean(true);
    doAnswer(invocation -> {
      long revision = fixture.revision().get();
      if (firstRead.getAndSet(false)) {
        capturedRevision.countDown();
        if (!continueRequest.await(2, TimeUnit.SECONDS))
          throw new AssertionError("Request was not released");
      }
      return revision;
    }).when(fixture.reasoner()).knowledgeRevision();
    var worker = Executors.newSingleThreadExecutor();
    try {
      var request = worker.submit(() -> fixture.reasoner().capabilities(mock(Scope.class)));
      assertTrue(capturedRevision.await(2, TimeUnit.SECONDS));
      // Publish and settle N+1 after this request captured N, before it reads the snapshot.
      synchronized (fixture.reasoner()) {
        fixture.state().begin(7);
        field(fixture.reasoner(), "capabilitiesGeneration", 1L);
        fixture.revision().set(8);
        field(fixture.reasoner(), "publishedSemanticCapabilities", SemanticCapabilitiesSnapshot.capture(
            1, "new-id", "imod", true, List.of(binding()),
            new WorldviewCommitment(1, "new-id", Map.of("new-ontology", "b".repeat(64)))));
        fixture.state().settle(8, true);
      }
      continueRequest.countDown();
      var straddled = request.get(2, TimeUnit.SECONDS);
      assertEquals(7, straddled.getKnowledgeRevision());
      assertFalse(straddled.isConsistent());
      assertNull(straddled.getWorldviewCommitment());
      assertTrue(straddled.getAuthorityBindings().isEmpty());

      var coherent = fixture.reasoner().capabilities(mock(Scope.class));
      assertEquals(8, coherent.getKnowledgeRevision());
      assertTrue(coherent.isConsistent());
      assertEquals("new-id", coherent.getWorldviewCommitment().worldviewId());
      assertFalse(coherent.getAuthorityBindings().isEmpty());
    } finally {
      continueRequest.countDown(); worker.shutdownNow();
    }
  }

  @Test void failedStateSuppressesCommitmentsAndAuthorityEnrichmentKeepsTheRevision() throws Exception {
    var fixture = fixture();
    fixture.state().beginUpdate(7, Set.of("ontology:A"));
    fixture.state().settleUpdate(8, Set.of("ontology:A"), false);
    fixture.revision().set(9); fixture.state().enriched(9);
    var failed = fixture.reasoner().capabilities(mock(Scope.class));
    assertEquals(9, failed.getKnowledgeRevision());
    assertFalse(failed.isConsistent());
    assertNull(failed.getWorldviewCommitment());
    assertTrue(failed.getAuthorityBindings().isEmpty());
    assertTrue(fixture.state().signal().endsWith(":9:failed"));

    fixture.state().settle(9, true);
    fixture.revision().set(10); fixture.state().enriched(10);
    var enriched = fixture.reasoner().capabilities(mock(Scope.class));
    assertEquals(10, enriched.getKnowledgeRevision());
    assertTrue(enriched.isConsistent());
    assertNotNull(enriched.getWorldviewCommitment());
  }

  @Test void eachRequestRetainsItsScopePermissionsAndOwnsMutableAuthorityMetadata() throws Exception {
    var fixture = fixture();
    var admin = mock(ServiceUserScope.class);
    when(admin.getPermissions()).thenReturn(EnumSet.allOf(CRUDOperation.class));
    var authorized = fixture.reasoner().capabilities(admin);
    assertEquals(EnumSet.allOf(CRUDOperation.class), authorized.getPermissions());
    var anonymous = fixture.reasoner().capabilities(mock(Scope.class));
    assertEquals(EnumSet.of(CRUDOperation.READ), anonymous.getPermissions());
    assertEquals(EnumSet.of(CRUDOperation.READ), fixture.reasoner().capabilities(null).getPermissions());
    var binding = authorized.getAuthorityBindings().getFirst();
    binding.componentVersion().setMajor(9);
    binding.provider().version().setMinor(9);
    binding.provider().subAuthorities().clear();
    var again = fixture.reasoner().capabilities(admin).getAuthorityBindings().getFirst();
    assertEquals("0.1.1", again.componentVersion().toString());
    assertEquals("0.1.1", again.provider().version().toString());
    assertEquals(List.of("species"), again.provider().subAuthorities());
    verify(fixture.registry(), times(2)).getInstalledComponents(admin);
  }
}
