package org.integratedmodelling.klab.services.reasoner.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse;

/** Reasoner-owned, bounded records of successful static validation. All inputs are rechecked on read. */
final class SourceValidationRecords {
  private static final int MAX_RECORD = 1024 * 1024, MAX_RECORDS = 256, MAX_BYTES = 16 * 1024 * 1024;
  private static final ObjectMapper JSON = new ObjectMapper();
  private final Path directory;
  private final java.util.concurrent.atomic.LongAdder writes = new java.util.concurrent.atomic.LongAdder();
  private record Record(String key, String fileHash, String checksum, byte[] result, SemanticDependencyWitness witness) {}
  long writes() { return writes.sum(); }
  private record FileAge(Path path, long modified, long size) {}

  SourceValidationRecords(Path directory) {
    this.directory = directory;
  }

  // The same SNAPSHOT version can contain different validator implementations. Hash the
  // actual loaded artifacts once per process, including normal IDE class directories.
  private static final class Implementation {
    static final String HASH = implementationHash();
  }

  static String key(String service, String worldview, String principal, String syntax) {
    if (service == null || worldview == null || principal == null || syntax == null || Implementation.HASH == null)
      return null;
    return SemanticContentFingerprint.sha256(
        SemanticContentFingerprint.canonical(List.of("document-static-v1", Implementation.HASH, service, worldview, principal, syntax)));
  }

  /** User identity IDs are renewable credentials. The authenticated account and authority realm
   * survive login, while membership changes must partition its saved source proofs. */
  static String principal(Identity identity) {
    if (identity == null || identity.getId() == null) return null;
    if (!(identity instanceof UserIdentity user))
      return SemanticContentFingerprint.sha256(SemanticContentFingerprint.canonical(
          Arrays.asList("credential", identity.getIdentityType(), identity.getId())));
    if (user.isAnonymous() || user.getUsername() == null || user.getUsername().isBlank()) return null;
    try {
      var order = Comparator.nullsFirst(Comparator.<String>naturalOrder());
      var groups = user.getGroups().stream()
          .sorted(Comparator.comparing(Group::getId, order).thenComparing(Group::getName, order))
          .map(g -> Arrays.asList(g.getId(), g.getName())).toList();
      var federation = Klab.INSTANCE.getFederationData(user);
      return SemanticContentFingerprint.sha256(SemanticContentFingerprint.canonical(
          Arrays.asList("user", user.getServerURL(), federation == null ? null : federation.getId(),
              user.getUsername(), groups)));
    } catch (RuntimeException unavailable) { return null; }
  }

  private static String implementationHash() {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var seen = new HashSet<Path>();
      for (var type : List.of(DocumentSemanticValidator.class,
          org.integratedmodelling.klab.api.lang.kim.KimNamespace.class,
          org.integratedmodelling.common.data.jackson.JacksonConfiguration.class,
          org.integratedmodelling.klab.runtime.language.ModelValidationGate.class,
          org.semanticweb.HermiT.Reasoner.class, org.semanticweb.owlapi.model.OWLOntology.class,
          org.semanticweb.owlapi.apibinding.OWLManager.class)) {
        var path = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!seen.add(path)) continue;
        if (Files.isRegularFile(path)) {
          // Ignore archive dates and compression. Repackaging identical classes preserves the record.
          try (var jar = new java.util.jar.JarFile(path.toFile())) {
            var entries = jar.stream().filter(e -> e.getName().endsWith(".class"))
                .sorted(Comparator.comparing(java.util.jar.JarEntry::getName)).toList();
            for (var entry : entries) {
              digest.update(entry.getName().getBytes(StandardCharsets.UTF_8));
              try (var in = new java.security.DigestInputStream(jar.getInputStream(entry), digest)) {
                in.transferTo(java.io.OutputStream.nullOutputStream());
              }
            }
          }
        }
        else {
          try (var files = Files.walk(path)) {
            for (var file : files.filter(f -> f.toString().endsWith(".class")).sorted().toList()) {
              digest.update(path.relativize(file).toString().replace('\\','/').getBytes(StandardCharsets.UTF_8));
              hashFile(digest, file);
            }
          }
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (Exception unavailable) { return null; }
  }

  private static void hashFile(MessageDigest digest, Path file) throws IOException {
    try (var in = new java.security.DigestInputStream(Files.newInputStream(file), digest)) {
      in.transferTo(java.io.OutputStream.nullOutputStream());
    }
  }

  SourceValidationProofCache.Hit read(String key, String fileHash,
      Function<List<String>, SemanticDependencyWitness> verify) {
    if (key == null || directory == null) return null;
    SemanticValidationResponse saved;
    Record stored;
    try (InputStream input = Files.newInputStream(directory.resolve(key + ".json"))) {
      var bytes = input.readNBytes(MAX_RECORD + 1);
      if (bytes.length > MAX_RECORD) return null;
      stored = JSON.readValue(bytes, Record.class);
      if (!key.equals(stored.key()) || !fileHash.equals(stored.fileHash()) || stored.result() == null
          || !SemanticContentFingerprint.sha256(stored.result()).equals(stored.checksum())) return null;
      saved = SourceValidationProofCache.decode(stored.result());
      if (!saved.valid() || !saved.isSourceSemanticsComplete() || !fileHash.equals(saved.getSourceHash())) return null;
    } catch (IOException | RuntimeException unavailable) { return null; }
    var current = verify.apply(saved.getSemanticObligations());
    return SemanticDependencyWitness.revalidates(stored.witness(), current)
        ? new SourceValidationProofCache.Hit(saved, current) : null;
  }

  synchronized void write(String key, SemanticValidationResponse result, SemanticDependencyWitness witness) {
    if (key == null || directory == null || !result.valid() || !result.isSourceSemanticsComplete()
        || result.getSourceHash() == null || witness == null || !witness.completeAt(System.currentTimeMillis())) return;
    Path temp = null;
    try {
      byte[] encoded = SourceValidationProofCache.encode(result);
      byte[] bytes = JSON.writeValueAsBytes(new Record(key, result.getSourceHash(),
          SemanticContentFingerprint.sha256(encoded), encoded, witness));
      if (bytes.length > MAX_RECORD) return;
      Files.createDirectories(directory);
      temp = Files.createTempFile(directory, "writing-", ".tmp");
      Files.write(temp, bytes);
      Files.move(temp, directory.resolve(key + ".json"), StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
      writes.increment();
      trim();
    } catch (IOException | RuntimeException unavailable) {
      // Disk records are optional. The validation just completed against the live actor.
    } finally {
      if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
    }
  }

  void retainForCaller(String key, SemanticValidationResponse result, SemanticDependencyWitness witness) {
    if (key != null && directory != null && Files.notExists(directory.resolve(key + ".json")))
      write(key, result, witness);
  }

  private void trim() throws IOException {
    var records = new ArrayList<FileAge>();
    long total = 0;
    try (var files = Files.newDirectoryStream(directory, "*.json")) {
      for (var path : files) {
        if (!path.getFileName().toString().matches("[0-9a-f]{64}\\.json")) continue;
        var size = Files.size(path);
        records.add(new FileAge(path, Files.getLastModifiedTime(path).toMillis(), size));
        total += size;
      }
    }
    if (records.size() <= MAX_RECORDS && total <= MAX_BYTES) return;
    records.sort(Comparator.comparingLong(FileAge::modified));
    for (int i = 0; records.size() - i > MAX_RECORDS || total > MAX_BYTES; i++) {
      var entry = records.get(i);
      Files.deleteIfExists(entry.path());
      total -= entry.size();
    }
  }
}
