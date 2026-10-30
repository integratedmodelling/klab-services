package org.integratedmodelling.klab.runtime.language;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;

/**
 * Finite, reviewed semantic footprints of actual component implementations. A function name is
 * never an attestation. These contracts account only for the selected implementation's own
 * semantic behavior; framework object creation, input provenance, observers and returned meanings
 * must still be accounted for by the execution witness.
 *
 * <p>The class/helper hashes were audited against the installed generators component and its
 * clean source revision dc6b7e7bb92545a6e196b30ea2ee5749fafe227e. Any changed implementation,
 * component version, helper or archive is UNKNOWN until separately reviewed.
 */
public final class AuditedSemanticFootprints {
  private AuditedSemanticFootprints() {}

  public enum Kind {
    /** Remove INHERENT from the directive, build that predicate, enumerate its entire closure. */
    CATEGORY_CLOSURE,
    /** Preserve the declared input semantics, changing only collective to singular. */
    DECLARED_OBSERVABLE
  }

  public record Proof(String contractVersion, Kind kind, String componentId, String componentVersion,
      String archiveSha256, String methodSignature, Map<String, String> implementationHashes)
      implements java.io.Serializable {
    public Proof { implementationHashes = Map.copyOf(implementationHashes); }
  }

  private static final String COMPONENT = "klab.component.generators";
  private static final Version COMPONENT_VERSION = Version.create("1.0-SNAPSHOT");
  private static final String ARCHIVE = "cd5e7a3d3140e234c8ab6a51a624dbcdac4e073d4a466dc78bf9a5176e79475d";
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
  private static final Map<String, String> ADAPTER_HASHES = Map.of(ADAPTER,
      "304e12a41c431a698a312066516bec2bd5ec8edd162a4a83ed81c3ee7b9c8a54", SHAPES,
      "8db0982a1ddeb596171f2a3a21870d0748bb524d32707709c4e7539b6ea78904");

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
    var expected = kind == Kind.CATEGORY_CLOSURE ? CATEGORY_HASHES : ADAPTER_HASHES;
    String expectedSignature = kind == Kind.CATEGORY_CLOSURE ? CATEGORY_SIGNATURE : ENCODER_SIGNATURE;
    if (!expectedSignature.equals(signature(method))) return Optional.empty();
    try {
      Path archive = component.sourceArchive().toPath().toRealPath();
      if (!Files.isRegularFile(archive) || Files.size(archive) > 64L * 1024 * 1024) return Optional.empty();
      String before = digest(Files.readAllBytes(archive));
      if (!ARCHIVE.equals(before)) return Optional.empty();
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
          String hash = digest(input.readAllBytes());
          if (!expected.get(name).equals(hash)) return Optional.empty();
          hashes.put(name, hash);
        }
      }
      // Never attest a mixture of class/helper bytes and an archive replaced during inspection.
      String after = digest(Files.readAllBytes(archive));
      if (!before.equals(after)) return Optional.empty();
      return attest(new Evidence(component.id(), component.version(), before, signature(method), hashes), kind);
    } catch (Exception | LinkageError unavailable) {
      // Failure to inspect is unknown execution coverage, not absence of authority use.
      return Optional.empty();
    }
  }

  static record Evidence(String componentId, Version version, String archiveHash,
      String methodSignature, Map<String, String> classHashes) {}

  /** Pure policy boundary; package visibility permits mutation/negative tests without installing plugins. */
  static Optional<Proof> attest(Evidence evidence, Kind kind) {
    if (evidence == null || kind == null || !COMPONENT.equals(evidence.componentId())
        || !COMPONENT_VERSION.equals(evidence.version()) || !ARCHIVE.equals(evidence.archiveHash()))
      return Optional.empty();
    var hashes = kind == Kind.CATEGORY_CLOSURE ? CATEGORY_HASHES : ADAPTER_HASHES;
    var signature = kind == Kind.CATEGORY_CLOSURE ? CATEGORY_SIGNATURE : ENCODER_SIGNATURE;
    if (!signature.equals(evidence.methodSignature()) || !hashes.equals(evidence.classHashes()))
      return Optional.empty();
    return Optional.of(new Proof("audited-semantic-footprint-v1", kind, COMPONENT,
        COMPONENT_VERSION.toString(), ARCHIVE, signature, hashes));
  }

  static String signature(Method method) {
    return method.getDeclaringClass().getName() + "#" + method.getName() + "("
        + String.join(",", Arrays.stream(method.getParameterTypes()).map(Class::getName).toList())
        + "):" + method.getReturnType().getName() + ":"
        + (Modifier.isStatic(method.getModifiers()) ? "static" : "instance");
  }

  private static String digest(byte[] bytes) throws java.security.NoSuchAlgorithmException {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }
}
