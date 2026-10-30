package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.junit.jupiter.api.Test;

class BootstrapCleanupRedactionTest {
  private final ServiceScope owner=mock(ServiceScope.class);
  private final Worldview source;
  BootstrapCleanupRedactionTest() {
    var identity=mock(UserIdentity.class); when(identity.getId()).thenReturn("credential");
    when(identity.getUsername()).thenReturn("owner"); when(identity.getGroups()).thenReturn(List.of());
    when(owner.getIdentity()).thenReturn(identity);
    var ontology=new KimOntologyImpl(); ontology.setUrn("w"); ontology.setSourceCode("saved source");
    var world=new WorldviewImpl(); world.setUrn("w"); world.setWorldviewId("w"); world.getOntologies().add(ontology);
    source=world;
  }
  private AuthorityBootstrap bootstrap(AuthorityBootstrap.Producer producer) {
    return new AuthorityBootstrap((w,s)->new AuthorityBootstrap.Plan(List.of(),new ResourceSet(),"revision"),producer);
  }
  private CandidatePublicationCoordinator.Input<Worldview> input() {
    return new CandidatePublicationCoordinator.Input<>(SemanticEnvironmentFactory.sources(source),
        SemanticEnvironmentFactory.sourceOrder(source),"revision",SemanticEnvironmentFactory.CONFIGURATION_REVISION,
        source,CandidatePublicationCoordinator.BeanStatus.VALID,List.of());
  }
  private void assertSanitized(IllegalStateException failure) {
    assertNull(failure.getCause()); assertEquals(1,failure.getSuppressed().length);
    var marker=failure.getSuppressed()[0];
    assertEquals(AuthorityBootstrap.CLEANUP_FAILURE_CODE,marker.getMessage());
    assertNull(marker.getCause()); assertEquals(0,marker.getSuppressed().length);
    assertFalse(failure.toString().contains("SECRET")); assertFalse(marker.toString().contains("SECRET"));
    assertFalse(marker.toString().contains("https://"));
  }

  @Test void bootstrapAndCoordinatorExposeOnlyFixedCleanupEvidenceIncludingCachedFailure() throws Exception {
    var calls=new AtomicInteger();
    var original=new IllegalStateException("SECRET provider parameter https://private.example/?token=SECRET");
    original.addSuppressed(new IOException("SECRET owned removal C:/private/provider/secret-data"));
    var bootstrap=bootstrap((w,s,p,c)->{calls.incrementAndGet();throw original;});
    for(int attempt=0;attempt<2;attempt++) {
      try(var coordinator=new CandidatePublicationCoordinator<Worldview,AutoCloseable>((input,c)->{
        bootstrap.prepare(input.payload(),owner,AuthorityBootstrap.key(input.payload(),owner),c);
        throw new AssertionError("Expected sanitized bootstrap failure");
      }, input->true)) {
        coordinator.submit(input()); assertTrue(coordinator.awaitIdle(Duration.ofSeconds(5)));
        var status=coordinator.status();
        assertEquals(CandidatePublicationCoordinator.LogicalStatus.FAILED,status.logicalStatus());
        assertEquals(1,status.cleanupFailures().size());
        assertTrue(status.cleanupFailures().getFirst().contains(AuthorityBootstrap.CLEANUP_FAILURE_CODE));
        var visible=status.diagnostics().toString()+status.cleanupFailures();
        assertFalse(visible.contains("SECRET")); assertFalse(visible.contains("https://"));
        assertFalse(visible.contains("private/provider"));
      }
    }
    assertEquals(1,calls.get(),"Memoized failure must preserve the marker without retaining the provider exception");
  }

  @Test void redactedExceptionGraphIsBoundedAndContainsNoProviderCausesOrMessages() throws Exception {
    var original=new IOException("SECRET primary");
    for(int i=0;i<1000;i++) {
      var cleanup=new IllegalStateException("SECRET cleanup "+i,new IOException("SECRET cause"));
      cleanup.addSuppressed(new IOException("SECRET nested")); original.addSuppressed(cleanup);
    }
    var bootstrap=bootstrap((w,s,p,c)->{throw original;});
    for(int attempt=0;attempt<2;attempt++) {
      var failure=assertThrows(IllegalStateException.class,()->bootstrap.prepare(source,owner,
          AuthorityBootstrap.key(source,owner),()->false));
      assertSanitized(failure);
      assertEquals("AUTHORITY_BOOTSTRAP_FAILED: IOException",failure.getMessage());
    }
  }

  @Test void standaloneOwnedCleanupFailureIsClassifiedBeforeRedaction() throws Exception {
    var type=Arrays.stream(AuthorityBootstrap.class.getDeclaredClasses())
        .filter(c->c.getSimpleName().equals("OwnedCleanupFailure")).findFirst().orElseThrow();
    var constructor=type.getDeclaredConstructor(Exception.class); constructor.setAccessible(true);
    var ownedFailure=(Exception)constructor.newInstance(new IOException("SECRET standalone owned cleanup"));
    var bootstrap=bootstrap((w,s,p,c)->{throw ownedFailure;});
    var failure=assertThrows(IllegalStateException.class,()->bootstrap.prepare(source,owner,
        AuthorityBootstrap.key(source,owner),()->false));
    assertSanitized(failure);
  }
}
