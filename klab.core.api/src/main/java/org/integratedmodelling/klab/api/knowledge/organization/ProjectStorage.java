package org.integratedmodelling.klab.api.knowledge.organization;

import java.net.URL;
import java.util.List;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.Scope;

/**
 * Abstracts stored access for a project. Being based on input streams it can be mapped on
 * filesystem, online repositories and archive files.
 */
public interface ProjectStorage {

  enum Type {
    /**
     * Project is stored in a directory on the filesystem. So far the only one supported for local
     * projects.
     */
    FILE,
    /** Project is a read-only Jar file that will be served without unpacking. */
    JAR,

    /** Project is read-only online repository using the URL of another resources service. */
    ONLINE
  }

  enum ResourceType {
    ONTOLOGY,
    MODEL_NAMESPACE,
    MANIFEST,
    DOCUMENTATION_NAMESPACE,
    STRATEGY,
    BEHAVIOR,
    APPLICATION,
    SCRIPT,
    TESTCASE,
    BEHAVIOR_COMPONENT,
    RESOURCE,
    RESOURCE_ASSET;

    // FIXME just report language and take the rest from there
    public static ResourceType forExtension(String extension) {
      return switch (extension) {
        case "kwv" -> ONTOLOGY;
        case "kim" -> MODEL_NAMESPACE;
        case "obs" -> STRATEGY;
        case "kactor", "kactors" -> BEHAVIOR;
        default -> null;
      };
    }

    public String getFileExtension() {
      return switch (this) {
        case ONTOLOGY -> "kwv";
        case MODEL_NAMESPACE -> "kim";
        case STRATEGY -> "obs";
        case BEHAVIOR, BEHAVIOR_COMPONENT, TESTCASE, SCRIPT, APPLICATION -> "kactors";
        default ->
            throw new KlabUnimplementedException("file extension for document of class " + this);
      };
    }

    public static ResourceType classify(KlabDocument<?> document) {
      return switch (document) {
        case KimOntology o -> ONTOLOGY;
        case KimNamespace o -> MODEL_NAMESPACE;
        case KimObservationStrategyDocument o -> STRATEGY;
        case KActorsBehavior behavior ->
            switch (behavior.getBehaviorType()) {
              case APP -> APPLICATION;
              case SCRIPT -> SCRIPT;
              case UNITTEST -> TESTCASE;
              default -> BEHAVIOR;
            };
        default -> throw new KlabUnimplementedException("no resource type for " + document);
      };
    }
  }

  Type getType();

  String getProjectName();

  /**
   * URL for the content root.
   *
   * @return
   */
  URL getUrl();

  /**
   * List all contained resources for the passed types. Use {@link URL#openStream()} to access their
   * contents.
   *
   * @param types
   * @return
   */
  List<URL> listResources(ResourceType... types);

  /**
   * Create a project document from an externally submitted one, which must be error-free and
   * contain the document source code. The document type, URN and storage location will be inferred
   * from the document type and content.
   *
   * @param document
   * @param scope
   * @return
   */
  URL create(KlabDocument<?> document, Scope scope);

  /**
   * Create a project resource in the right place with default content. Only supported by some
   * storage types, should throw an exception if creation of the requested assets isn't supported.
   * Must be aware of tracking according to implementation conventions.
   *
   * @param resourceId
   * @param resourceType
   * @param scope the scope describing who is creating the resource. If a UserScope and the file is
   *     tracked, this should add authorship information to the commit.
   * @return
   */
  URL create(String resourceId, ResourceType resourceType, String contents, Scope scope);

  /**
   * Read-only status may depend on the storage medium (online, protected JAR) and/or on signature
   * or permissions.
   *
   * @return
   */
  boolean isReadOnly();

  /**
   * A filesystem-based project may be editable and all its assets must be available as files on the
   * filesystem. All URLs returned by {@link #listResources(ResourceType...)} must have file
   * protocol.
   *
   * @return
   */
  boolean isFilesystemBased();

  static Pair<ResourceType, String> getDocumentData(String relativeFilePath) {
    return getDocumentData(relativeFilePath, "/");
  }

  /**
   * Reverse inference of document type and URN from a relative file path with the passed separator.
   * Results will be returned irrespective of whether the document exists or not.
   *
   * @param relativeFilePath
   * @param separator
   * @return the pair including the type and URN for the document, or null. The document may or may
   *     not exist.
   */
  static Pair<ResourceType, String> getDocumentData(String relativeFilePath, String separator) {

    ResourceType type = null;
    String urn = null;

    if (relativeFilePath == null || !relativeFilePath.contains(".")) {
      return null;
    }

    var extension = relativeFilePath.substring(relativeFilePath.lastIndexOf(".") + 1);
    var path = relativeFilePath.substring(0, relativeFilePath.lastIndexOf("."));
    boolean behaviorExtension = "kactor".equals(extension) || "kactors".equals(extension);

    if ("json".equals(extension)) {
      // TODO manifest, docs, resource.
      if (relativeFilePath.startsWith("resources" + separator)) {
        return null;
      }
    } else {
      if (relativeFilePath.startsWith("src" + separator)) {
        type = ResourceType.forExtension(extension);
        urn = path.substring("src".length() + 1).replace(separator, ".");
      } else if (relativeFilePath.startsWith("scripts" + separator) && behaviorExtension) {
        type = ResourceType.SCRIPT;
        urn = path.substring("scripts".length() + 1).replace(separator, ".");
      } else if (relativeFilePath.startsWith("testcases" + separator)
          && behaviorExtension) {
        type = ResourceType.TESTCASE;
        urn = path.substring("testcases".length() + 1).replace(separator, ".");
      } else if (relativeFilePath.startsWith("apps" + separator) && behaviorExtension) {
        type = ResourceType.APPLICATION;
        urn = path.substring("apps".length() + 1).replace(separator, ".");
      } else if (relativeFilePath.startsWith("behaviors" + separator) && behaviorExtension) {
        type = ResourceType.BEHAVIOR;
        urn = path.substring("behaviors".length() + 1).replace(separator, ".");
      } else if (relativeFilePath.startsWith("strategies" + separator) && "obs".equals(extension)) {
        type = ResourceType.STRATEGY;
        urn = path.substring("strategies".length() + 1);
      }
    }
    return type == null ? null : Pair.of(type, urn);
  }

  /**
   * Relative file path for resource without assumptions about existence. Uses canonical separator
   * ('/') suitable for Git commands.
   *
   * @param urn
   * @param type
   * @return
   */
  static String getRelativeFilePath(String urn, ResourceType type) {
    return getRelativeFilePath(urn, type, "/");
  }

  /**
   * Return a slash-separated relative, canonical file path to the passed document file, including
   * the expected extension. This is a static method and does not assume that a correspondent file
   * exists in any project.
   *
   * @return
   */
  static String getRelativeFilePath(String urn, ResourceType type, String separator) {
    return switch (type) {
      case SCRIPT ->
          "scripts"
              + separator
              + urn.replace('.', separator.charAt(0))
              + "."
              + type.getFileExtension();
      case TESTCASE ->
          "testcases"
              + separator
              + urn.replace('.', separator.charAt(0))
              + "."
              + type.getFileExtension();
      case APPLICATION ->
          "apps"
              + separator
              + urn.replace('.', separator.charAt(0))
              + "."
              + type.getFileExtension();
      case BEHAVIOR, BEHAVIOR_COMPONENT ->
          "behaviors"
              + separator
              + urn.replace('.', separator.charAt(0))
              + "."
              + type.getFileExtension();
      case ONTOLOGY, MODEL_NAMESPACE ->
          "src" + separator + urn.replace('.', separator.charAt(0)) + "." + type.getFileExtension();
      case STRATEGY -> "strategies" + separator + urn + "." + type.getFileExtension();
      default -> null;
    };
  }
}
