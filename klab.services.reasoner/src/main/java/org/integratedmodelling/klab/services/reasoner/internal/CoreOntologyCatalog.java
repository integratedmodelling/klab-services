package org.integratedmodelling.klab.services.reasoner.internal;

import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.integratedmodelling.klab.api.utils.Utils;

/** Naming and visibility rules shared by core capture and the OWL directory loader. */
public final class CoreOntologyCatalog {
  private CoreOntologyCatalog() {}

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
