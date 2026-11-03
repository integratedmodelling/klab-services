package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

class OdoImportCaptureTest {
  @TempDir Path directory;
  static final String URL = "https://raw.githubusercontent.com/integratedmodelling/odo-im/"
      + "df5980e36d921233cc66b0bb270a2a36f86ccfce/releases/0.2.0/odo.owl";
  private byte[] remote() throws Exception {
    return (new String(CoreOntologyCatalog.bundledOdo(), StandardCharsets.UTF_8)
        + "\n<!-- authoritative publication differs from distribution -->\n").getBytes(StandardCharsets.UTF_8);
  }
  @Test void remoteBytesWinWithoutACompiledPublicationAllowlist() throws Exception {
    var bytes = remote();
    var captured = new OdoImportCapture(directory, uri -> bytes).capture(URL, CoreOntologyCatalog.bundledOdo());
    assertEquals("REMOTE", captured.source());
    assertArrayEquals(bytes, captured.bytes());
    assertNotEquals(CachedAuthority.digest(new String(CoreOntologyCatalog.bundledOdo(), StandardCharsets.UTF_8)), captured.sha256());
  }
  @Test void offlineRestartPrefersLastCapturedRemoteBytes() throws Exception {
    var bytes = remote();
    new OdoImportCapture(directory, uri -> bytes).capture(URL, CoreOntologyCatalog.bundledOdo());
    var offline = new OdoImportCapture(directory, uri -> { throw new IOException("offline"); });
    var captured = offline.capture(URL, CoreOntologyCatalog.bundledOdo());
    assertEquals("CACHED_REMOTE", captured.source());
    assertArrayEquals(bytes, captured.bytes());
  }
  @Test void uncachedOfflineStartupUsesCompatibleBundle() throws Exception {
    var offline = new OdoImportCapture(directory, uri -> { throw new IOException("offline"); });
    var captured = offline.capture(URL, CoreOntologyCatalog.bundledOdo());
    assertEquals("BUNDLED_OFFLINE", captured.source());
    assertArrayEquals(CoreOntologyCatalog.bundledOdo(), captured.bytes());
    assertThrows(IllegalArgumentException.class,
        () -> offline.capture(URL.replace("/0.2.0/", "/9.9.9/"), CoreOntologyCatalog.bundledOdo()));
  }
  @Test void reachableInvalidDocumentDoesNotSilentlyUseCachedOrBundledBytes() throws Exception {
    new OdoImportCapture(directory, uri -> CoreOntologyCatalog.bundledOdo()).capture(URL, CoreOntologyCatalog.bundledOdo());
    var invalid = new OdoImportCapture(directory, uri -> "<not-an-ontology/>".getBytes(StandardCharsets.UTF_8));
    assertThrows(IllegalArgumentException.class, () -> invalid.capture(URL, CoreOntologyCatalog.bundledOdo()));
  }
  @Test void lineEndingsDoNotGateAdmissionButCapturedBytesKeepTheirExactFingerprint() throws Exception {
    var lf = new String(remote(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    var crlf = lf.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
    var first = new OdoImportCapture(directory, uri -> lf.getBytes(StandardCharsets.UTF_8)).capture(URL, CoreOntologyCatalog.bundledOdo());
    var second = new OdoImportCapture(directory, uri -> crlf).capture(URL, CoreOntologyCatalog.bundledOdo());
    assertNotEquals(first.sha256(), second.sha256());
    assertArrayEquals(crlf, second.bytes());
  }
  @Test @EnabledIfSystemProperty(named="klab.test.liveOdo", matches="true")
  void liveDeclaredPublicationIsRetrievedRatherThanUsingFallback() throws Exception {
    var captured = new OdoImportCapture(directory).capture(URL, CoreOntologyCatalog.bundledOdo());
    assertEquals("REMOTE", captured.source());
    assertEquals("5c020dac9bd9d1cd4f9673b8075cf08f75ecc7194c74378bdb8f0498110ddba3", captured.sha256());
  }
}
