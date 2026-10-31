package org.integratedmodelling.klab.runtime.language;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Only the locally built reviewed fixture is loaded; Home's supplied bytecode is never used. */
class AuthoredSemanticInventoryTest {
  @TempDir Path temp;
  private static final String CATEGORY = "org.integratedmodelling.generators.library.RandomContextualizers";
  private static final String ADAPTER = "org.integratedmodelling.generators.adapters.RandomGeneratorAdapter";
  private static final String POM = "META-INF/maven/org.integratedmodelling/klab.component.generators/pom.xml";

  private LinkedHashMap<String, byte[]> fixture() throws Exception {
    var result = new LinkedHashMap<String, byte[]>();
    try (var input = new ZipInputStream(getClass().getResourceAsStream("/semantic-footprints/generators-authored-audited.jar"))) {
      for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry())
        if (!entry.isDirectory()) result.put(entry.getName(), input.readAllBytes());
    }
    return result;
  }

  private Path jar(Map<String, byte[]> entries, boolean reverse, boolean stored) throws Exception {
    var path = temp.resolve("reviewed " + UUID.randomUUID() + ".jar");
    var names = new ArrayList<>(entries.keySet()); if (reverse) Collections.reverse(names);
    try (var output = new JarOutputStream(Files.newOutputStream(path))) {
      for (var name : names) {
        var bytes = entries.get(name); var entry = new JarEntry(name); entry.setTime(1700000000000L);
        if (stored) {
          var crc = new CRC32(); crc.update(bytes); entry.setMethod(ZipEntry.STORED);
          entry.setSize(bytes.length); entry.setCompressedSize(bytes.length); entry.setCrc(crc.getValue());
        }
        output.putNextEntry(entry); output.write(bytes); output.closeEntry();
      }
    }
    return path;
  }

  private Extensions.ComponentDescriptor component(Path path) {
    return new Extensions.ComponentDescriptor("klab.component.generators", Version.create("1.0-SNAPSHOT"),
        "reviewed fixture", path.toFile(), "not trusted", null, ResourcePrivileges.PUBLIC,
        List.of(), List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), "test", 0, null, null, 0);
  }

  private URLClassLoader loader(Path path) throws Exception {
    return loader(path, null);
  }

  private URLClassLoader loader(Path path, ClassLoader helperOwner) throws Exception {
    return new URLClassLoader(new java.net.URL[]{path.toUri().toURL()}, getClass().getClassLoader()) {
      @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (helperOwner != null && name.equals("org.integratedmodelling.generators.utils.RandomShapes"))
          return helperOwner.loadClass(name);
        if (!name.startsWith("org.integratedmodelling.generators.")) return super.loadClass(name, resolve);
        synchronized (getClassLoadingLock(name)) {
          var loaded = findLoadedClass(name); if (loaded == null) loaded = findClass(name);
          if (resolve) resolveClass(loaded); return loaded;
        }
      }
      @Override public java.net.URL getResource(String name) {
        return name.startsWith("org/integratedmodelling/generators/") ? findResource(name) : super.getResource(name);
      }
    };
  }

  private Optional<AuditedSemanticFootprints.Proof> category(Path path) throws Exception {
    try (var loader = loader(path)) {
      var type = Class.forName(CATEGORY, false, loader);
      return AuditedSemanticFootprints.forFunction(component(path),
          type.getMethod("generateConcept", Observable.class, ServiceCall.class, Scope.class),
          "klab.generators.random.categories");
    }
  }

  @Test void reviewedAuthoredImplementationHasItsOwnContractAndObservedArchiveHash() throws Exception {
    var path = jar(fixture(), false, false); var proof = category(path).orElseThrow();
    assertEquals(AuditedSemanticFootprints.Kind.AUTHORED_CATEGORY_CLOSURE, proof.kind());
    assertEquals("audited-semantic-footprint-v2", proof.contractVersion());
    assertEquals("6b460cf115f9ec6f7c8a53cb57d18c7cbf46b4b02e43bdc499530eed7cccf5e2", proof.implementationHashes().get(CATEGORY));
    assertEquals(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))), proof.archiveSha256());
    try (var loader = loader(path)) {
      var type = Class.forName(ADAPTER, false, loader);
      var method = type.getMethod("encode", Urn.class, Storage.DoubleScanner.class, Data.Builder.class,
          Geometry.class, Observable.class, Scope.class);
      var adapter = AuditedSemanticFootprints.forAdapter(component(path), type, method,
          "klab:random:objects:polygons").orElseThrow();
      assertEquals(proof.contentInventorySha256(), adapter.contentInventorySha256());
      assertEquals(AuditedSemanticFootprints.Kind.DECLARED_OBSERVABLE, adapter.kind());
    }
  }

  @Test void rebuildMetadataPomLineEndingsOrderAndCompressionDoNotChangeContentIdentity() throws Exception {
    var original = category(jar(fixture(), false, false)).orElseThrow();
    var entries = fixture();
    var manifest = new Manifest(new ByteArrayInputStream(entries.get("META-INF/MANIFEST.MF")));
    manifest.getMainAttributes().putValue("Built-By", "another builder");
    manifest.getMainAttributes().putValue("Built-Date", "2026-10-31T10:00:00Z");
    var bytes = new ByteArrayOutputStream(); manifest.write(bytes); entries.put("META-INF/MANIFEST.MF", bytes.toByteArray());
    var pom = new String(entries.get(POM), StandardCharsets.UTF_8).replace("\r\n", "\n");
    entries.put(POM, pom.getBytes(StandardCharsets.UTF_8));
    var rebuilt = category(jar(entries, true, true)).orElseThrow();
    assertEquals(original.contentInventorySha256(), rebuilt.contentInventorySha256());
    assertNotEquals(original.archiveSha256(), rebuilt.archiveSha256());
    // Also accept mixed CRLF/LF, without normalizing spaces or XML content.
    entries.put(POM, pom.replaceFirst("\n", "\r\n").getBytes(StandardCharsets.UTF_8));
    assertEquals(original.contentInventorySha256(), category(jar(entries, false, false)).orElseThrow().contentInventorySha256());
  }

  @Test void changedClassesHelpersResourcesAndPomContentsRemainUnknown() throws Exception {
    for (var name : List.of("org/integratedmodelling/generators/adapters/RandomGeneratorAdapter.class",
        "org/integratedmodelling/generators/utils/RandomShapes.class",
        "org/integratedmodelling/generators/GeneratorsPlugin.class", "META-INF/extensions.idx", POM)) {
      var entries = fixture(); var changed = entries.get(name).clone(); changed[changed.length - 1] ^= 1;
      entries.put(name, changed); assertTrue(category(jar(entries, false, false)).isEmpty(), name);
    }
    var proof = category(jar(fixture(), false, false)).orElseThrow();
    assertTrue(AuditedSemanticFootprints.attest(new AuditedSemanticFootprints.Evidence(proof.componentId(),
        Version.create(proof.componentVersion()), proof.archiveSha256(), "0".repeat(64),
        proof.methodSignature(), proof.implementationHashes()), proof.kind()).isEmpty());
  }

  @Test void entrySetIsExactAndDuplicateNamesAreRejected() throws Exception {
    var missing = fixture(); missing.remove("META-INF/extensions.idx");
    assertTrue(category(jar(missing, false, false)).isEmpty());
    var extra = fixture(); extra.put("unreviewed.txt", new byte[]{42});
    assertTrue(category(jar(extra, false, false)).isEmpty());
    var duplicate = fixture(); duplicate.put("META-INF/extensions.idy", duplicate.get("META-INF/extensions.idx"));
    var path = jar(duplicate, false, false); var bytes = Files.readAllBytes(path);
    var old = "META-INF/extensions.idy".getBytes(StandardCharsets.UTF_8);
    for (int i = 0; i <= bytes.length - old.length; i++) {
      boolean match = true; for (int j = 0; j < old.length; j++) if (bytes[i + j] != old[j]) { match = false; break; }
      if (match) bytes[i + old.length - 1] = 'x';
    }
    Files.write(path, bytes); assertTrue(category(path).isEmpty());
  }

  @Test void functionalManifestChangesDuplicateAttributesAndNamedSectionsAreRejected() throws Exception {
    for (var changed : List.of("Plugin-Class: other.Plugin", "Plugin-Version: 1.0.1-SNAPSHOT",
        "Plugin-License: private", "Class-Path: injected.jar")) {
      var entries = fixture();
      var manifest = new Manifest(new ByteArrayInputStream(entries.get("META-INF/MANIFEST.MF")));
      int colon = changed.indexOf(':'); manifest.getMainAttributes().putValue(changed.substring(0, colon), changed.substring(colon + 2));
      var bytes = new ByteArrayOutputStream(); manifest.write(bytes); entries.put("META-INF/MANIFEST.MF", bytes.toByteArray());
      assertTrue(category(jar(entries, false, false)).isEmpty(), changed);
    }
    for (var suffix : List.of("Plugin-Version: 1.0-SNAPSHOT\r\n", "\r\nName: arbitrary\r\nSealed: true\r\n")) {
      var entries = fixture(); var text = new String(entries.get("META-INF/MANIFEST.MF"), StandardCharsets.UTF_8).stripTrailing();
      entries.put("META-INF/MANIFEST.MF", (text + "\r\n" + suffix + "\r\n").getBytes(StandardCharsets.UTF_8));
      assertTrue(category(jar(entries, false, false)).isEmpty());
    }
  }

  @Test void malformedManifestLinesCannotHideInCanonicalProvenance() throws Exception {
    var parser = AuditedSemanticFootprints.class.getDeclaredMethod("contentInventory", Path.class);
    parser.setAccessible(true);
    var entries = fixture();
    var text = new String(entries.get("META-INF/MANIFEST.MF"), StandardCharsets.UTF_8)
        .replace("Plugin-Class: org.integratedmodelling.generators.GeneratorsPlugin\r\n", "").stripTrailing();
    entries.put("META-INF/MANIFEST.MF", (text + "\r\nPlugin-Class: org.integratedmodelling.generators.GeneratorsPlugin")
        .getBytes(StandardCharsets.UTF_8));
    var unterminated = jar(entries, false, false);
    assertInstanceOf(java.io.IOException.class,
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> parser.invoke(null, unterminated)).getCause());
    entries = fixture();
    text = new String(entries.get("META-INF/MANIFEST.MF"), StandardCharsets.UTF_8)
        .replaceAll("(?m)^Built-Date: [^\\r\\n]*\\r?\\n", "").stripTrailing();
    entries.put("META-INF/MANIFEST.MF", (text + "\r\nBuilt-Date: " + "x".repeat(600) + "\r\n\r\n")
        .getBytes(StandardCharsets.UTF_8));
    var oversized = jar(entries, false, false);
    assertInstanceOf(java.io.IOException.class,
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> parser.invoke(null, oversized)).getCause());
  }

  @Test void oversizedReviewedEntryIsRejectedBeforeUnboundedInflation() throws Exception {
    var entries = fixture(); entries.put("META-INF/extensions.idx", new byte[1024 * 1024 + 1]);
    assertTrue(category(jar(entries, false, false)).isEmpty());
  }

  @Test void differentDescriptorArchiveCannotBorrowLoadedClassIdentity() throws Exception {
    var first = jar(fixture(), false, false); var second = jar(fixture(), true, true);
    try (var loader = loader(first)) {
      var type = Class.forName(CATEGORY, false, loader);
      assertTrue(AuditedSemanticFootprints.forFunction(component(second),
          type.getMethod("generateConcept", Observable.class, ServiceCall.class, Scope.class),
          "klab.generators.random.categories").isEmpty());
    }
  }

  @Test void helperFromAnotherLoaderCannotBorrowMatchingBytes() throws Exception {
    var first = jar(fixture(), false, false); var second = jar(fixture(), true, true);
    try (var helpers = loader(second); var selected = loader(first, helpers)) {
      var type = Class.forName(ADAPTER, false, selected);
      var method = type.getMethod("encode", Urn.class, Storage.DoubleScanner.class, Data.Builder.class,
          Geometry.class, Observable.class, Scope.class);
      assertTrue(AuditedSemanticFootprints.forAdapter(component(first), type, method,
          "klab:random:objects:polygons").isEmpty());
    }
  }

  @Test void replacementDuringClassInspectionIsRejectedEvenWhenBothInventoriesAreReviewed() throws Exception {
    var entries = fixture(); var original = jar(entries, false, false);
    var replacement = Files.readAllBytes(jar(entries, true, true));
    var resourceName = CATEGORY.replace('.', '/') + ".class";
    class ReplacingLoader extends URLClassLoader {
      boolean armed;
      ReplacingLoader() throws Exception {
        super(new java.net.URL[]{original.toUri().toURL()}, AuthoredSemanticInventoryTest.class.getClassLoader());
      }
      @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!name.equals(CATEGORY)) return super.loadClass(name, resolve);
        var type = findLoadedClass(name); if (type == null) type = findClass(name);
        if (resolve) resolveClass(type); return type;
      }
      @Override public InputStream getResourceAsStream(String name) {
        if (!name.equals(resourceName)) return super.getResourceAsStream(name);
        if (armed) {
          armed = false;
          try { Files.write(original, replacement); } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
        return new ByteArrayInputStream(entries.get(resourceName));
      }
    }
    try (var selected = new ReplacingLoader()) {
      var type = Class.forName(CATEGORY, false, selected);
      var method = type.getMethod("generateConcept", Observable.class, ServiceCall.class, Scope.class);
      // Release URLClassLoader's JAR handle before the controlled Windows file replacement.
      selected.close(); selected.armed = true;
      assertTrue(AuditedSemanticFootprints.forFunction(component(original), method,
          "klab.generators.random.categories").isEmpty());
      assertArrayEquals(replacement, Files.readAllBytes(original), "Replacement hook must actually execute");
    }
  }
}
