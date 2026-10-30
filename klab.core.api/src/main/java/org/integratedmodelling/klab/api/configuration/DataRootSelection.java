package org.integratedmodelling.klab.api.configuration;

import java.nio.file.Path;
import java.util.Properties;

/** Data-root selection without filesystem access or configuration-singleton initialization. */
public final class DataRootSelection {
  public static final String DATA_DIRECTORY = "klab.data.directory";
  public static final String WORK_DIRECTORY = "klab.work.directory";

  private DataRootSelection() {}

  public static Path select(Properties properties) {
    String explicit = properties.getProperty(DATA_DIRECTORY);
    if (explicit != null) return absolute(explicit, DATA_DIRECTORY);
    String home = properties.getProperty("user.home");
    if (home == null || home.isBlank()) throw new IllegalArgumentException("Missing user.home");
    String relative = properties.getProperty(WORK_DIRECTORY, ".klab");
    if (relative.isBlank() || Path.of(relative).isAbsolute()) {
      throw new IllegalArgumentException(WORK_DIRECTORY + " must be a nonempty relative path");
    }
    return Path.of(home).resolve(relative).toAbsolutePath().normalize();
  }

  /** A CLI root and an explicit JVM root must agree; reject ambiguity before any write. */
  public static Path selectForService(Properties properties, Path cliRoot) {
    if (cliRoot == null) return select(properties);
    Path requested = cliRoot.toAbsolutePath().normalize();
    if (properties.containsKey(DATA_DIRECTORY) && !select(properties).equals(requested)) {
      throw new IllegalArgumentException("CLI and JVM data roots disagree");
    }
    return requested;
  }

  /** Pure preflight for a private harness: every potentially writable root must be confined. */
  public static void requirePrivateRoots(Path boundary, Path... roots) {
    Path allowed = boundary.toAbsolutePath().normalize();
    for (Path root : roots) {
      if (root == null || !root.toAbsolutePath().normalize().startsWith(allowed)) {
        throw new IllegalArgumentException("Writable root escapes the private harness boundary");
      }
    }
  }

  private static Path absolute(String value, String name) {
    if (value.isBlank() || !Path.of(value).isAbsolute()) {
      throw new IllegalArgumentException(name + " must be a nonempty absolute path");
    }
    return Path.of(value).normalize();
  }
}
