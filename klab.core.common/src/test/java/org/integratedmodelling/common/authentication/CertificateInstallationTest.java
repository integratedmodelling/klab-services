package org.integratedmodelling.common.authentication;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CertificateInstallationTest {
  @TempDir Path directory;

  @Test void renamedDownloadReplacesCanonicalFileAndRetainsEveryBackup() throws Exception {
    var config = directory.resolve("configuration");
    Files.createDirectories(config);
    var target = Files.writeString(config.resolve("klab.cert"), "original");
    var download = Files.writeString(directory.resolve("klab.cert (1)"), "replacement");
    var first = CertificateInstallation.install(download, config);
    assertEquals("replacement", Files.readString(target));
    assertEquals("original", Files.readString(first.backup()));
    Files.writeString(download, "third");
    var second = CertificateInstallation.install(download, config);
    assertNotEquals(first.backup(), second.backup());
    assertEquals("original", Files.readString(first.backup()));
    assertEquals("replacement", Files.readString(second.backup()));
    second.restore();
    assertEquals("replacement", Files.readString(target));
    assertEquals("third", Files.readString(download));
  }

  @Test void firstInstallAndRestoreWorkWithoutExistingConfiguration() throws Exception {
    var download = Files.writeString(directory.resolve("klab.cert"), "new");
    var installation = CertificateInstallation.install(download, directory.resolve("config"));
    assertNull(installation.backup());
    installation.restore();
    assertFalse(Files.exists(installation.target()));
    assertEquals("new", Files.readString(download));
  }

  @Test void installingCanonicalSourceStillBacksItUp() throws Exception {
    var target = Files.writeString(directory.resolve("klab.cert"), "original");
    var installation = CertificateInstallation.install(target, directory);
    assertEquals("original", Files.readString(target));
    assertEquals("original", Files.readString(installation.backup()));
  }

  @Test void invalidSourceLeavesExistingCertificateAlone() throws Exception {
    var target = Files.writeString(directory.resolve("klab.cert"), "original");
    var wrong = Files.writeString(directory.resolve("node.cert"), "bad");
    assertThrows(IllegalArgumentException.class, () -> CertificateInstallation.install(wrong, directory));
    assertFalse(CertificateInstallation.accepts(directory.resolve("missing")));
    assertFalse(CertificateInstallation.accepts(directory));
    assertEquals("original", Files.readString(target));
  }
}
