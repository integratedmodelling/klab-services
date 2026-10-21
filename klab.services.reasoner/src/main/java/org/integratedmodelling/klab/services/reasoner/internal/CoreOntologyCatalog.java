package org.integratedmodelling.klab.services.reasoner.internal;

import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.integratedmodelling.klab.api.utils.Utils;

/** Naming and visibility rules shared by core capture and the OWL directory loader. */
public final class CoreOntologyCatalog {
  private static final java.util.List<String> BUNDLED_DOCUMENTS =
      java.util.List.of("odo.owl", "dc.owl", "klab.owl");
  // Verified official publication, not a general permission to resolve GitHub URLs.
  // Keep the published LF hash and the exact Windows CRLF distribution hash separate;
  // admission never rewrites bytes or treats arbitrary same-version documents as this release.
  private static final String VERIFIED_ODO_RELEASE =
      "https://raw.githubusercontent.com/integratedmodelling/odo-im/"
          + "9cfe20e306e39747a0474bf6754c24e49d99df71/releases/0.1.1/odo.owl";
  private static final java.util.Set<String> VERIFIED_ODO_HASHES = java.util.Set.of(
      "d8771dc5456a48e0d1d64f7ba1c92f8e6a9f89ddddfd1fa6cabf1c93e363ea52",
      "6851a4a34aa66191687232e9c37c2dc12e8b779b73e1a10bdf5abd1dd31ab038");
  // Locally prepared immutable 0.2.0 source; publication is a separate operation.
  private static final String VERIFIED_ODO_020_RELEASE =
      "https://raw.githubusercontent.com/integratedmodelling/odo-im/08e5a29abbbd32ca8c1b933fc157a1762ba3ee5c/releases/0.2.0/odo.owl";
  private static final String VERIFIED_ODO_020_SHA256 =
      "496bc6634392d7e2eb2a6749bca6a1570333ea607b9b8e52ffaf5b619bcf2a57";
  private CoreOntologyCatalog() {}

  /** Only the distribution's three root documents are protected; extensions remain local. */
  public static boolean isBundledDocument(Path relativeDocument) {
    return relativeDocument.getNameCount() == 1
        && BUNDLED_DOCUMENTS.stream().anyMatch(name -> name.equalsIgnoreCase(relativeDocument.toString()));
  }

  /** Fresh bytes from this reasoner distribution, without installed-catalog overrides. */
  public static java.util.Map<String, byte[]> bundledCore() throws java.io.IOException {
    var documents = new java.util.LinkedHashMap<String, byte[]>();
    for (var name : BUNDLED_DOCUMENTS) documents.put(name, bundledDocument(name));
    return documents;
  }

  private static byte[] bundledDocument(String name) throws java.io.IOException {
    try (var input = CoreOntologyCatalog.class.getResourceAsStream("/knowledge/" + name)) {
      if (input == null) throw new java.io.IOException("Bundled knowledge/" + name + " is unavailable");
      return input.readAllBytes();
    }
  }

  /** ODO is part of the reasoner distribution, never an installed-catalog override. */
  public static byte[] bundledOdo() throws java.io.IOException {
    return bundledDocument("odo.owl");
  }

  /** A release locator names captured bytes; neither master nor verified immutable URLs are fetched. */
  static String odoReleaseIdentity(String locator, String capturedSha256) {
    if (locator == null) return null;
    if (VERIFIED_ODO_020_RELEASE.equals(locator))
      return VERIFIED_ODO_020_SHA256.equals(capturedSha256)
          ? "http://integratedmodelling.org/odo/0.2.0" : null;
    if (VERIFIED_ODO_RELEASE.equals(locator))
      return capturedSha256 != null && VERIFIED_ODO_HASHES.contains(capturedSha256)
          ? "http://integratedmodelling.org/odo/0.1.1" : null;
    var match = java.util.regex.Pattern.compile(
        "https://raw\\.githubusercontent\\.com/integratedmodelling/odo-im/master/releases/([0-9]+\\.[0-9]+\\.[0-9]+)/odo\\.owl")
        .matcher(locator);
    return match.matches() ? "http://integratedmodelling.org/odo/" + match.group(1) : null;
  }

  public static void synchronizeBundledOdo(Path catalog) throws java.io.IOException {
    synchronize(catalog.resolve("odo.owl"), bundledOdo());
  }

  public static void synchronizeBundledCore(Path catalog) throws java.io.IOException {
    for (var entry : bundledCore().entrySet())
      synchronize(catalog.resolve(entry.getKey()), entry.getValue());
  }

  private static void synchronize(Path target, byte[] bytes) throws java.io.IOException {
    if (java.nio.file.Files.isRegularFile(target)
        && java.util.Arrays.equals(bytes, java.nio.file.Files.readAllBytes(target))) return;
    java.nio.file.Files.createDirectories(target.getParent());
    java.nio.file.Files.write(target, bytes);
  }

  public static boolean isVisible(Path relativeDocument) {
    for (int i = 0; i < relativeDocument.getNameCount() - 1; i++) {
      if (relativeDocument.getName(i).toString().startsWith(".")) return false;
    }
    return true;
  }

  public static String segment(String name) {
    return Utils.CamelCase.toLowerCase(Utils.Files.getFileBaseName(name), '-');
  }

  public static String alias(Path relativeDocument) {
    return StreamSupport.stream(relativeDocument.spliterator(), false)
        .map(part -> segment(part.toString())).collect(Collectors.joining("."));
  }
}
