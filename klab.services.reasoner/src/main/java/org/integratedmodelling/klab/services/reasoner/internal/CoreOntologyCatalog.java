package org.integratedmodelling.klab.services.reasoner.internal;

import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.integratedmodelling.klab.api.utils.Utils;

/** Naming and visibility rules shared by core capture and the OWL directory loader. */
public final class CoreOntologyCatalog {
  private CoreOntologyCatalog() {}

  /** ODO is part of the reasoner distribution, never an installed-catalog override. */
  public static byte[] bundledOdo() throws java.io.IOException {
    try (var input = CoreOntologyCatalog.class.getResourceAsStream("/knowledge/odo.owl")) {
      if (input == null) throw new java.io.IOException("Bundled knowledge/odo.owl is unavailable");
      return input.readAllBytes();
    }
  }

  /** A release document URL names the matching captured ODO version; it is never fetched. */
  static String odoReleaseIdentity(String locator) {
    if (locator == null) return null;
    var match = java.util.regex.Pattern.compile(
        "https://raw\\.githubusercontent\\.com/integratedmodelling/odo-im/master/releases/([0-9]+\\.[0-9]+\\.[0-9]+)/odo\\.owl")
        .matcher(locator);
    return match.matches() ? "http://integratedmodelling.org/odo/" + match.group(1) : null;
  }

  public static void synchronizeBundledOdo(Path catalog) throws java.io.IOException {
    var bytes = bundledOdo();
    var target = catalog.resolve("odo.owl");
    if (java.nio.file.Files.isRegularFile(target)
        && java.util.Arrays.equals(bytes, java.nio.file.Files.readAllBytes(target))) return;
    java.nio.file.Files.createDirectories(catalog);
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
