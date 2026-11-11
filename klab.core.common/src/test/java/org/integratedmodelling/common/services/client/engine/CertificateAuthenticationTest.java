package org.integratedmodelling.common.services.client.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.configuration.*;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CertificateAuthenticationTest {
  @TempDir Path directory;

  static void field(Object target, String name, Object value) throws Exception {
    var f = EngineImpl.class.getDeclaredField(name); f.setAccessible(true); f.set(target, value);
  }

  UserScope user() {
    var scope = mock(UserScope.class);
    var identity = mock(UserIdentity.class);
    when(identity.getData()).thenReturn(Parameters.create());
    when(scope.getIdentity()).thenReturn(identity);
    when(scope.getUser()).thenReturn(identity);
    return scope;
  }

  EngineImpl engine(UserScope old, ServiceMonitor monitor) throws Exception {
    var engine = spy(new EngineImpl(null, null) {
      @Override protected Path certificateDirectory() { return directory; }
    });
    var settings = mock(Settings.class);
    when(settings.get(Setting.LOGIN_ANONYMOUSLY, Boolean.class)).thenReturn(false);
    when(settings.set(eq(Setting.LOGIN_ANONYMOUSLY), anyBoolean())).thenReturn(CompletableFuture.completedFuture(false));
    field(engine, "settings", settings);
    field(engine, "defaultUser", old);
    field(engine, "serviceMonitor", monitor);
    engine.getUsers().add(old);
    return engine;
  }

  Path download() throws Exception {
    return Files.writeString(directory.resolve("klab.cert (1)"), "klab.certificate.type=ENGINE\nklab.certificate=opaque-hub-data\n");
  }

  @Test void localServicesRejectReplacementBeforeTouchingFileOrUser() throws Exception {
    var old = user(); var monitor = mock(ServiceMonitor.class);
    when(monitor.hasActiveLocalServices()).thenReturn(true);
    var engine = engine(old, monitor);
    var target = Files.writeString(directory.resolve("klab.cert"), "original");
    try {
      assertThrows(IllegalStateException.class, () -> engine.installCertificate(download().toFile()));
      assertEquals("original", Files.readString(target));
      verify(old, never()).close();
      assertSame(old, engine.authenticate());
    } finally { engine.shutdown(); }
  }

  @Test void replacementClosesOldIdentityAndRejectsReentryAndLocalStartup() throws Exception {
    var old = user(); var next = user(); var monitor = mock(ServiceMonitor.class);
    var engine = engine(old, monitor);
    var target = Files.writeString(directory.resolve("klab.cert"), "original");
    var download = download();
    var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
    doAnswer(call -> {
      verify(old).close(); verify(monitor).disconnect();
      entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS));
      field(engine, "defaultUser", next); engine.getUsers().add(next); return next;
    }).when(engine).authenticate();
    var installation = CompletableFuture.supplyAsync(() -> engine.installCertificate(download.toFile()));
    try {
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      assertThrows(IllegalStateException.class, () -> engine.installCertificate(download.toFile()));
      assertThrows(IllegalStateException.class, engine::startLocalServices);
      assertThrows(IllegalStateException.class, () -> engine.startAuxiliaryServices());
      release.countDown();
      assertSame(next, installation.get(5, TimeUnit.SECONDS));
      assertEquals(1, engine.getUsers().size());
      assertSame(next, engine.getOwner());
      assertEquals(Files.readString(download), Files.readString(target));
      try (var files = Files.list(directory)) {
        var backup = files.filter(p -> p.getFileName().toString().startsWith("klab.cert.backup-")).findFirst().orElseThrow();
        assertEquals("original", Files.readString(backup));
      }
    } finally { release.countDown(); engine.shutdown(); }
  }

  @Test void authenticationExceptionRestoresPreviousCertificateAndReleasesGuard() throws Exception {
    var old = user(); var recovered = user(); var monitor = mock(ServiceMonitor.class);
    var engine = engine(old, monitor);
    var target = Files.writeString(directory.resolve("klab.cert"), "original");
    var calls = new AtomicInteger();
    doAnswer(call -> {
      if (calls.getAndIncrement() == 0) throw new IllegalStateException("Hub rejected certificate");
      field(engine, "defaultUser", recovered); engine.getUsers().add(recovered); return recovered;
    }).when(engine).authenticate();
    try {
      assertThrows(IllegalStateException.class, () -> engine.installCertificate(download().toFile()));
      assertEquals("original", Files.readString(target));
      assertSame(recovered, engine.getOwner());
      // Validation now runs again instead of reporting an installation in progress.
      assertThrows(IllegalArgumentException.class, () -> engine.installCertificate(directory.resolve("missing").toFile()));
    } finally { engine.shutdown(); }
  }
}
