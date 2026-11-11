package org.integratedmodelling.common.authentication;

import java.io.IOException;
import java.nio.file.*;

/** Stages certificate writes on the destination filesystem and retains every previous certificate. */
public record CertificateInstallation(Path target, Path backup) {
  public static boolean accepts(Path source) {
    return source != null && source.getFileName() != null
        && source.getFileName().toString().startsWith("klab.cert")
        && Files.isRegularFile(source) && Files.isReadable(source);
  }

  public static CertificateInstallation install(Path source, Path directory) throws IOException {
    if (!accepts(source)) throw new IllegalArgumentException("Drop one readable file named klab.cert or starting with klab.cert");
    Files.createDirectories(directory);
    var target = directory.resolve("klab.cert");
    var staged = Files.createTempFile(directory, ".klab-certificate-", ".tmp");
    Path backup = null;
    try {
      Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING);
      if (Files.exists(target)) {
        backup = Files.createTempFile(directory, "klab.cert.backup-", ".bak");
        Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
      }
      replace(staged, target);
      return new CertificateInstallation(target, backup);
    } finally {
      Files.deleteIfExists(staged);
    }
  }

  public void restore() throws IOException {
    if (backup == null) {
      Files.deleteIfExists(target);
    } else {
      var staged = Files.createTempFile(target.getParent(), ".klab-certificate-", ".tmp");
      try {
        Files.copy(backup, staged, StandardCopyOption.REPLACE_EXISTING);
        replace(staged, target);
      } finally {
        Files.deleteIfExists(staged);
      }
    }
  }

  private static void replace(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
