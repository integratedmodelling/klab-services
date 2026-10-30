package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.junit.jupiter.api.Test;

class BootstrapFailureDiagnosticsTest {
  @Test void serverLoggerReceivesThrowableOverloadWithRedactedOriginalTypeAndCause() {
    var logger=mock(org.slf4j.Logger.class);
    var original=new IllegalStateException("SECRET",new IOException("SECRET cause"));
    try(var factory=mockStatic(org.slf4j.LoggerFactory.class)) {
      factory.when(()->org.slf4j.LoggerFactory.getLogger(
          org.integratedmodelling.klab.components.OwnedBootstrapDiagnostics.class)).thenReturn(logger);
      org.integratedmodelling.klab.components.OwnedBootstrapDiagnostics.report(
          org.integratedmodelling.klab.components.OwnedBootstrapDiagnostics.Phase.COMPONENT_EXPORT,
          "fixture.component@2.0.0",
          org.integratedmodelling.klab.components.OwnedBootstrapDiagnostics.Reason.EXCEPTION,original);
      var text=org.mockito.ArgumentCaptor.forClass(String.class);
      var failure=org.mockito.ArgumentCaptor.forClass(Throwable.class);
      verify(logger).error(text.capture(),failure.capture());
      assertTrue(text.getValue().contains("component=fixture.component@2.0.0"));
      assertTrue(failure.getValue().getMessage().contains(IllegalStateException.class.getName()));
      assertTrue(failure.getValue().getCause().getMessage().contains(IOException.class.getName()));
      assertFalse(failure.getValue().getMessage().contains("SECRET"));
      assertFalse(failure.getValue().getCause().getMessage().contains("SECRET"));
      assertArrayEquals(original.getStackTrace(),failure.getValue().getStackTrace());
      assertNotSame(original,failure.getValue());
    }
  }

  @Test void onlyReadableCanonicalComponentCoordinatesAreAllowedInServerContext() {
    assertEquals("org.example.provider@1.0.0-SNAPSHOT",
        org.integratedmodelling.klab.components.OwnedBootstrapDiagnostics.identifier("org.example.provider@1.0.0-SNAPSHOT"));
    assertEquals("[redacted]",org.integratedmodelling.klab.components.OwnedBootstrapDiagnostics.identifier(
        "https://private.example/?token=SECRET"));
    assertEquals("[redacted]",org.integratedmodelling.klab.components.OwnedBootstrapDiagnostics.identifier("provider\nSECRET"));
  }
  @Test void originalCauseAndCleanupLocationsSurviveWithoutMessagesOrMutation() {
    var cause = new IOException("SECRET https://private.example/?token=SECRET");
    var original = new IllegalStateException("SECRET provider response", cause);
    var cleanup = new IOException("SECRET cleanup path");
    original.addSuppressed(cleanup);
    var diagnostic = BootstrapFailureDiagnostics.snapshot(original);
    assertArrayEquals(original.getStackTrace(), diagnostic.getStackTrace());
    assertArrayEquals(cause.getStackTrace(), diagnostic.getCause().getStackTrace());
    assertArrayEquals(cleanup.getStackTrace(), diagnostic.getSuppressed()[0].getStackTrace());
    assertTrue(diagnostic.toString().startsWith(IllegalStateException.class.getName()));
    assertTrue(diagnostic.getMessage().contains(IllegalStateException.class.getName()),
        "logging backends may render getClass/getMessage instead of toString");
    assertTrue(diagnostic.getCause().toString().startsWith(IOException.class.getName()));
    assertTrue(diagnostic.getCause().getMessage().contains(IOException.class.getName()));
    var text = new StringWriter(); diagnostic.printStackTrace(new PrintWriter(text));
    assertTrue(text.toString().contains("Caused by:"));
    assertTrue(text.toString().contains("Suppressed:"));
    assertFalse(text.toString().contains("SECRET"));
    assertFalse(text.toString().contains("https://"));
    assertSame(cause, original.getCause());
    assertSame(cleanup, original.getSuppressed()[0]);
    assertEquals("SECRET provider response", original.getMessage());
  }

  @Test void cyclicAndLargeExceptionGraphsAreBounded() {
    var a = new IOException("SECRET"); var b = new IOException("SECRET");
    a.initCause(b); b.initCause(a);
    for (int i=0;i<1000;i++) a.addSuppressed(new IOException("SECRET"));
    var copy = BootstrapFailureDiagnostics.snapshot(a);
    assertSame(copy, copy.getCause().getCause());
    assertEquals(16, copy.getSuppressed().length);
    var text = new StringWriter(); copy.printStackTrace(new PrintWriter(text));
    assertFalse(text.toString().contains("SECRET"));
    assertTrue(text.toString().contains("CIRCULAR REFERENCE"));
  }

  @Test void reportGetsOriginalExactlyOnceWhileBothPublicAttemptsStaySanitized() throws Exception {
    var world = new WorldviewImpl(); world.setUrn("w"); world.setWorldviewId("w");
    var ontology = new KimOntologyImpl(); ontology.setUrn("w"); ontology.setSourceCode("saved source");
    world.getOntologies().add(ontology);
    var owner = mock(ServiceScope.class);
    var original = new IllegalStateException("SECRET", new IOException("SECRET cause"));
    var observed = new AtomicReference<Throwable>();
    var context = new AtomicReference<BootstrapFailureDiagnostics.Context>();
    var calls = new AtomicInteger();
    var bootstrap = new AuthorityBootstrap(
        (w,s)->new AuthorityBootstrap.Plan(List.of(),new ResourceSet(),"revision"),
        (w,s,p,c)->{throw original;},null,
        (ctx,error)->{calls.incrementAndGet();context.set(ctx);observed.set(error);});
    for (int attempt=0;attempt<2;attempt++) {
      var failure = assertThrows(IllegalStateException.class,()->bootstrap.prepare(world,owner,
          AuthorityBootstrap.key(world,owner),()->false));
      assertEquals("AUTHORITY_BOOTSTRAP_FAILED: IllegalStateException", failure.getMessage());
      assertNull(failure.getCause());
    }
    assertSame(original, observed.get());
    assertEquals(1,calls.get(),"cached failures must not log again");
    assertEquals(BootstrapFailureDiagnostics.Phase.PRODUCE,context.get().phase());
    assertTrue(context.get().inputHash().matches("[0-9a-f]{64}"));
    assertTrue(context.get().planHash().matches("[0-9a-f]{64}"));
    assertTrue(context.get().bindingsHash().matches("[0-9a-f]{64}"));
  }

  @Test void failedReporterCannotReplaceOriginalFailure() {
    var world = new WorldviewImpl(); world.setUrn("w"); world.setWorldviewId("w");
    var owner = mock(ServiceScope.class);
    var bootstrap = new AuthorityBootstrap((w,s)->{throw new IllegalStateException("SECRET");},
        (w,s,p,c)->{throw new AssertionError("unreachable");},null,
        (ctx,error)->{throw new IllegalArgumentException("logger unavailable");});
    var failure = assertThrows(IllegalStateException.class,()->bootstrap.prepare(world,owner,
        AuthorityBootstrap.key(world,owner),()->false));
    assertEquals("AUTHORITY_BOOTSTRAP_FAILED: IllegalStateException",failure.getMessage());
    assertNull(failure.getCause());
  }
}
