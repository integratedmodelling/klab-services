package org.integratedmodelling.klab.runtime.language;

import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;

/**
 * Finite, reviewed semantic footprints of actual component implementations. A function name is
 * never an attestation. These contracts account only for the selected implementation's own
 * semantic behavior; framework object creation, input provenance, observers and returned meanings
 * must still be accounted for by the execution witness.
 *
 * <p>Complete content inventories are reviewed against generator revisions dc6b7e7 and d782839.
 * ZIP packaging, manifest Built-Date/Built-By and POM CRLF/LF are not executable identity.
 * Every other entry and manifest attribute remains checked. Unknown content is not independence.
 */
public final class AuditedSemanticFootprints {
  private AuditedSemanticFootprints() {}

  public enum Kind {
    /** Remove INHERENT from the directive, build that predicate, enumerate its entire closure. */
    CATEGORY_CLOSURE,
    /** Same directive transformation, selecting only the accepted authored vocabulary. */
    AUTHORED_CATEGORY_CLOSURE,
    /** Preserve the declared input semantics, changing only collective to singular. */
    DECLARED_OBSERVABLE
  }

  public record Proof(String contractVersion, Kind kind, String componentId, String componentVersion,
      String archiveSha256, String contentInventorySha256, String methodSignature,
      Map<String, String> implementationHashes)
      implements java.io.Serializable {
    public Proof { implementationHashes = Map.copyOf(implementationHashes); }
  }

  private static final String COMPONENT = "klab.component.generators";
  private static final Version COMPONENT_VERSION = Version.create("1.0-SNAPSHOT");
  private static final int MAX_ARCHIVE_BYTES = 64 * 1024 * 1024;
  private static final int MAX_ENTRY_BYTES = 1024 * 1024;
  private static final String CATEGORIES = "org.integratedmodelling.generators.library.RandomContextualizers";
  private static final String ADAPTER = "org.integratedmodelling.generators.adapters.RandomGeneratorAdapter";
  private static final String SHAPES = "org.integratedmodelling.generators.utils.RandomShapes";
  private static final String CATEGORY_SIGNATURE = CATEGORIES + "#generateConcept("
      + "org.integratedmodelling.klab.api.knowledge.Observable,"
      + "org.integratedmodelling.klab.api.lang.ServiceCall,"
      + "org.integratedmodelling.klab.api.scope.Scope):org.integratedmodelling.klab.api.knowledge.Concept:static";
  private static final String ENCODER_SIGNATURE = ADAPTER + "#encode("
      + "org.integratedmodelling.klab.api.knowledge.Urn,"
      + "org.integratedmodelling.klab.api.data.Storage$DoubleScanner,"
      + "org.integratedmodelling.klab.api.data.Data$Builder,"
      + "org.integratedmodelling.klab.api.geometry.Geometry,"
      + "org.integratedmodelling.klab.api.knowledge.Observable,"
      + "org.integratedmodelling.klab.api.scope.Scope):void:instance";
  private static final Map<String, String> CATEGORY_HASHES = Map.of(CATEGORIES,
      "bcc317fab6a41ec38d9a309e8e99115abc0b2f3d2df64e78772ab010e77ba4fc");
  private static final Map<String, String> AUTHORED_CATEGORY_HASHES = Map.of(CATEGORIES,
      "6b460cf115f9ec6f7c8a53cb57d18c7cbf46b4b02e43bdc499530eed7cccf5e2");
  private static final Map<String, String> ADAPTER_HASHES = Map.of(ADAPTER,
      "304e12a41c431a698a312066516bec2bd5ec8edd162a4a83ed81c3ee7b9c8a54", SHAPES,
      "8db0982a1ddeb596171f2a3a21870d0748bb524d32707709c4e7539b6ea78904");

  private static final String POM = "META-INF/maven/org.integratedmodelling/klab.component.generators/pom.xml";
  private static final Map<String, String> MANIFEST = Map.ofEntries(
      Map.entry("manifest-version", "1.0"),
      Map.entry("created-by", "Maven JAR Plugin 3.3.0"),
      Map.entry("build-jdk-spec", "21"),
      Map.entry("implementation-title", COMPONENT),
      Map.entry("implementation-version", "1.0-SNAPSHOT"),
      Map.entry("implementation-vendor-id", "org.integratedmodelling"),
      Map.entry("plugin-id", COMPONENT), Map.entry("plugin-version", "1.0-SNAPSHOT"),
      Map.entry("plugin-klab-version", "1.0.0-SNAPSHOT"),
      Map.entry("plugin-class", "org.integratedmodelling.generators.GeneratorsPlugin"),
      Map.entry("plugin-description", "Self-contained observation generator resources and contextualizers for"
          + " ".repeat(31) + "testing"),
      Map.entry("plugin-provider", "Integrated Modelling Partnership"),
      Map.entry("plugin-license", "*"), Map.entry("plugin-license-name", "GNU Affero GPL 3.0"),
      Map.entry("plugin-license-url", "No license URL"),
      Map.entry("plugin-vendor-id", "org.integratedmodelling"),
      Map.entry("plugin-vendor-url", "No vendor URL"),
      Map.entry("plugin-vendor-email", "info@integratedmodelling.org"));

  private static Map<String, String> inventory(Map<String, String> category) {
    var files = new TreeMap<String, String>();
    files.put("META-INF/MANIFEST.MF", mapDigest(MANIFEST));
    files.put("META-INF/extensions.idx", "d1338b0de25c011e6ec770b9a17fc38759b409a0e965269a5ad728db47a45145");
    files.put(POM, "c879ac7f859bbfa8cebafd6a3b5520c555ffdaee91c948b84b0cc6101e430793");
    files.put("META-INF/maven/org.integratedmodelling/klab.component.generators/pom.properties",
        "9108c7450ea56f1b4d1c6ccf03e67cb211d3880f9e63a15b601674099120e736");
    var classes = new HashMap<>(ADAPTER_HASHES); classes.putAll(category);
    classes.put("org.integratedmodelling.generators.GeneratorsPlugin",
        "150b9b231b4932b6d45ec2dfd8919dd4885614234552aa40d5bce6a0df0c023f");
    classes.put("org.integratedmodelling.generators.library.TerrainGenerators",
        "b13719df1d48d6b322685da8e94f93d520dbc146afd34515c97e5a6af822b83f");
    classes.put("org.integratedmodelling.generators.terrain.Terrain",
        "e062b3e32dde0087493c264b4529a0e26a560332d8ecf7a737795bdc7d780351");
    classes.put("org.integratedmodelling.generators.utils.RandomRelationships",
        "0daeb0cb3f7c65f972d7f89ed94b85f8ca9a33c9755257726e90e70137594e20");
    classes.forEach((name, hash) -> files.put(name.replace('.', '/') + ".class", hash));
    return Map.copyOf(files);
  }

  private static final Map<String, String> LEGACY_FILES = inventory(CATEGORY_HASHES);
  private static final String LEGACY_INVENTORY = mapDigest(LEGACY_FILES);
  private static final String AUTHORED_INVENTORY = mapDigest(inventory(AUTHORED_CATEGORY_HASHES));

  public static Optional<Proof> forFunction(Extensions.ComponentDescriptor component,
      Method actualMethod, String functionUrn) {
    if (!"klab.generators.random.categories".equals(functionUrn)) return Optional.empty();
    return inspect(component, actualMethod, Kind.CATEGORY_CLOSURE);
  }

  public static Optional<Proof> forAdapter(Extensions.ComponentDescriptor component,
      Class<?> actualImplementation, Method actualEncoder, String resourceUrn) {
    if (actualEncoder == null || actualImplementation != actualEncoder.getDeclaringClass()
        || !objectShapeResource(resourceUrn)) return Optional.empty();
    return inspect(component, actualEncoder, Kind.DECLARED_OBSERVABLE);
  }

  private static boolean objectShapeResource(String text) {
    try {
      var urn = Urn.of(text);
      return "klab".equals(urn.getNodeName()) && "random".equals(urn.getCatalog())
          && "objects".equals(urn.getNamespace())
          && Set.of("polygons", "points", "lines").contains(urn.getResourceId());
    } catch (RuntimeException malformed) { return false; }
  }

  private static Optional<Proof> inspect(Extensions.ComponentDescriptor component, Method method, Kind kind) {
    if (component == null || method == null || component.sourceArchive() == null
        || !COMPONENT.equals(component.id()) || !COMPONENT_VERSION.equals(component.version()))
      return Optional.empty();
    String expectedSignature = kind == Kind.DECLARED_OBSERVABLE ? ENCODER_SIGNATURE : CATEGORY_SIGNATURE;
    if (!expectedSignature.equals(signature(method))) return Optional.empty();
    try {
      Path archive = component.sourceArchive().toPath().toRealPath();
      if (!Files.isRegularFile(archive)) return Optional.empty();
      String before = archiveDigest(archive);
      String inventory = contentInventory(archive);
      var category = AUTHORED_INVENTORY.equals(inventory) ? AUTHORED_CATEGORY_HASHES : CATEGORY_HASHES;
      if (!LEGACY_INVENTORY.equals(inventory) && !AUTHORED_INVENTORY.equals(inventory))
        return Optional.empty();
      var expected = kind == Kind.DECLARED_OBSERVABLE ? ADAPTER_HASHES : category;
      var hashes = new TreeMap<String, String>();
      var selected = method.getDeclaringClass();
      for (var name : expected.keySet()) {
        var implementation = name.equals(selected.getName()) ? selected
            : Class.forName(name, false, selected.getClassLoader());
        if (implementation.getClassLoader() != selected.getClassLoader()
            || implementation.getProtectionDomain() == null
            || implementation.getProtectionDomain().getCodeSource() == null
            || !Files.isSameFile(archive, Path.of(implementation.getProtectionDomain().getCodeSource()
                .getLocation().toURI()))) return Optional.empty();
        try (InputStream input = implementation.getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
          if (input == null) return Optional.empty();
          String hash = digest(bounded(input, MAX_ENTRY_BYTES));
          if (!expected.get(name).equals(hash)) return Optional.empty();
          hashes.put(name, hash);
        }
      }
      // Never attest a mixture of class/helper bytes and an archive replaced during inspection.
      String after = archiveDigest(archive);
      if (!before.equals(after)) return Optional.empty();
      return attest(new Evidence(component.id(), component.version(), before, inventory, signature(method), hashes), kind);
    } catch (Exception | LinkageError unavailable) {
      // Failure to inspect is unknown execution coverage, not absence of authority use.
      return Optional.empty();
    }
  }

  static record Evidence(String componentId, Version version, String archiveHash, String inventoryHash,
      String methodSignature, Map<String, String> classHashes) {}

  /** Pure policy boundary; package visibility permits mutation/negative tests without installing plugins. */
  static Optional<Proof> attest(Evidence evidence, Kind kind) {
    if (evidence == null || kind == null || !COMPONENT.equals(evidence.componentId())
        || !COMPONENT_VERSION.equals(evidence.version()) || evidence.archiveHash() == null
        || !evidence.archiveHash().matches("[a-f0-9]{64}"))
      return Optional.empty();
    boolean authored = AUTHORED_INVENTORY.equals(evidence.inventoryHash());
    if (!authored && !LEGACY_INVENTORY.equals(evidence.inventoryHash())) return Optional.empty();
    var hashes = kind == Kind.DECLARED_OBSERVABLE ? ADAPTER_HASHES
        : authored ? AUTHORED_CATEGORY_HASHES : CATEGORY_HASHES;
    var signature = kind == Kind.DECLARED_OBSERVABLE ? ENCODER_SIGNATURE : CATEGORY_SIGNATURE;
    if (!signature.equals(evidence.methodSignature()) || !hashes.equals(evidence.classHashes()))
      return Optional.empty();
    var actualKind = kind == Kind.DECLARED_OBSERVABLE ? kind
        : authored ? Kind.AUTHORED_CATEGORY_CLOSURE : Kind.CATEGORY_CLOSURE;
    return Optional.of(new Proof("audited-semantic-footprint-v2", actualKind, COMPONENT,
        COMPONENT_VERSION.toString(), evidence.archiveHash(), evidence.inventoryHash(), signature, hashes));
  }

  static String signature(Method method) {
    return method.getDeclaringClass().getName() + "#" + method.getName() + "("
        + String.join(",", Arrays.stream(method.getParameterTypes()).map(Class::getName).toList())
        + "):" + method.getReturnType().getName() + ":"
        + (Modifier.isStatic(method.getModifiers()) ? "static" : "instance");
  }

  private static String archiveDigest(Path archive) throws Exception {
    if (Files.size(archive) > MAX_ARCHIVE_BYTES) throw new IOException("Component archive too large");
    try (var input = Files.newInputStream(archive)) { return digest(bounded(input, MAX_ARCHIVE_BYTES)); }
  }

  private static byte[] bounded(InputStream input, int limit) throws IOException {
    var bytes = input.readNBytes(limit + 1);
    if (bytes.length > limit) throw new IOException("Component entry exceeds audit bound");
    return bytes;
  }

  /** Hash exactly the reviewed content surface, not ZIP timestamps/order/compression. */
  private static String contentInventory(Path archive) throws Exception {
    var directories = new HashSet<String>();
    for (var name : LEGACY_FILES.keySet()) for (int slash = name.indexOf('/'); slash >= 0;
        slash = name.indexOf('/', slash + 1)) directories.add(name.substring(0, slash + 1));
    var names = new HashSet<String>(); var files = new TreeMap<String, String>();
    try (var zip = new ZipFile(archive.toFile())) {
      var entries = zip.entries();
      while (entries.hasMoreElements()) {
        var entry = entries.nextElement(); var name = entry.getName();
        if (!names.add(name)) throw new IOException("Duplicate component entry");
        if (entry.isDirectory()) {
          if (!directories.contains(name)) throw new IOException("Unexpected component directory");
          continue;
        }
        if (!LEGACY_FILES.containsKey(name)) throw new IOException("Unexpected component content");
        try (var input = zip.getInputStream(entry)) {
          var bytes = bounded(input, MAX_ENTRY_BYTES);
          if (name.equals("META-INF/MANIFEST.MF")) files.put(name, mapDigest(manifest(bytes)));
          else if (name.equals(POM)) {
            // Only CRLF/LF is normalized: bare CR, spaces, XML and every other byte remain significant.
            var normalized = new java.io.ByteArrayOutputStream(bytes.length);
            for (int i = 0; i < bytes.length; i++)
              if (bytes[i] != '\r' || i + 1 == bytes.length || bytes[i + 1] != '\n') normalized.write(bytes[i]);
            files.put(name, digest(normalized.toByteArray()));
          } else files.put(name, digest(bytes));
        }
      }
    }
    if (!files.keySet().equals(LEGACY_FILES.keySet())) throw new IOException("Missing component content");
    return mapDigest(files);
  }

  /** Strict main-section manifest parsing; duplicate attributes and named sections stay UNKNOWN. */
  private static Map<String, String> manifest(byte[] bytes) throws Exception {
    // Require the same syntactic validity as the JVM before canonicalizing provenance.
    new java.util.jar.Manifest(new java.io.ByteArrayInputStream(bytes));
    var text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        .replace("\r\n", "\n");
    if (!text.endsWith("\n")) throw new IOException("Unterminated manifest line");
    if (text.indexOf('\r') >= 0) throw new IOException("Malformed manifest line ending");
    var result = new TreeMap<String, String>(); String current = null; boolean ended = false;
    for (var line : text.split("\n", -1)) {
      if (line.isEmpty()) { ended = true; current = null; continue; }
      if (ended) throw new IOException("Unexpected manifest section");
      if (line.startsWith(" ")) {
        if (current == null) throw new IOException("Orphan manifest continuation");
        result.put(current, result.get(current) + line.substring(1));
      } else {
        int colon = line.indexOf(':');
        if (colon < 1 || colon + 1 >= line.length() || line.charAt(colon + 1) != ' ')
          throw new IOException("Malformed manifest attribute");
        current = line.substring(0, colon).toLowerCase(Locale.ROOT);
        if (!current.matches("[a-z0-9_-]+") || result.putIfAbsent(current, line.substring(colon + 2)) != null)
          throw new IOException("Duplicate or malformed manifest attribute");
      }
    }
    result.remove("built-date"); result.remove("built-by");
    return result;
  }

  private static String mapDigest(Map<String, String> values) {
    var text = new StringBuilder();
    new TreeMap<>(values).forEach((key, value) -> text.append(key.length()).append(':').append(key)
        .append(value.length()).append(':').append(value));
    return digest(text.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }
}
