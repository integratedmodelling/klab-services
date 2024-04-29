package org.integratedmodelling.klab.api.knowledge.organization;


import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.utils.Utils;

import java.io.File;
import java.net.URL;
import java.util.List;

/**
 * Abstracts stored access for a project. Being based on input streams it can be mapped on filesystem, online
 * repositories and archive files.
 */
public interface ProjectStorage {

    enum Type {
        /**
         * Project is stored in a directory on the filesystem. So far the only one supported for local
         * projects.
         */
        FILE,
        /**
         * Project is a read-only Jar file that will be served without unpacking.
         */
        JAR,

        /**
         * Project is read-only online repository using the URL of another resources service.
         */
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

        public static ResourceType forExtension(String extension) {
            return switch (extension) {
                case "kwv" -> ONTOLOGY;
                case "kim" -> MODEL_NAMESPACE;
                case "obs" -> STRATEGY;
                case "kactors" -> BEHAVIOR;
                default -> null;
            };
        }

        public String getFileExtension() {
            return switch (this) {
                case ONTOLOGY -> "kwv";
                case MODEL_NAMESPACE -> "kim";
                case STRATEGY -> "obs";
                case BEHAVIOR, TESTCASE, SCRIPT -> "kactors";
                default ->
                        throw new KlabUnimplementedException("file extension for document of class " + this);
            };
        }

        public static ResourceType classify(KlabDocument<?> document) {
            return switch (document) {
                case KimOntology o -> ONTOLOGY;
                case KimNamespace o -> MODEL_NAMESPACE;
                case KimObservationStrategyDocument o -> STRATEGY;
                case KActorsBehavior o -> BEHAVIOR; // TODO check type
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
     * Create a project resource in the right place with default content. Only supported by some storage
     * types, should throw an exception if creation of the requested assets isn't supported.
     *
     * @param resourceId
     * @param resourceType
     * @return
     */
    URL create(String resourceId, ResourceType resourceType);

    /**
     * Read-only status may depend on the storage medium (online, protected JAR) and/or on signature or
     * permissions.
     *
     * @return
     */
    boolean isReadOnly();

    /**
     * A filesystem-based project may be editable and all its assets must be available as files on the
     * filesystem. All URLs returned by {@link #listResources(ResourceType...)} must have file protocol.
     *
     * @return
     */
    boolean isFilesystemBased();


    public static Pair<ResourceType, String> getDocumentData(String relativeFilePath) {
        return getDocumentData(relativeFilePath, "/");
    }

    /**
     * Reverse inference of document type and URN from a relative file path with the passed separator. Results
     * will be returned irrespective of whether the document exists or not.
     *
     * @param relativeFilePath
     * @param separator
     * @return the pair including the type and URN for the document, or null. The document may or may not
     * exist.
     */
    public static Pair<ResourceType, String> getDocumentData(String relativeFilePath, String separator) {

        ResourceType type = null;
        String urn = null;

        var extension = relativeFilePath.substring(relativeFilePath.lastIndexOf(".") + 1);
        var path = relativeFilePath.substring(0, relativeFilePath.lastIndexOf("."));

        if ("json".equals(extension)) {
            // TODO manifest, docs, resource.
            if (relativeFilePath.startsWith("resources" + separator)) {
                throw new KlabUnimplementedException("KAAAAAAAAKKKKKKKKKKKKKKKKKKK");
            }
        } else {
            if (relativeFilePath.startsWith("src" + separator)) {
                type = ResourceType.forExtension(extension);
                urn = path.substring("src".length() + 1).replace(separator, ".");
            } else if (relativeFilePath.startsWith("scripts" + separator)) {
                throw new KlabUnimplementedException("POOOOOO");
            } else if (relativeFilePath.startsWith("tests" + separator)) {
                throw new KlabUnimplementedException("PAAAAAAA");
            } else if (relativeFilePath.startsWith("apps" + separator) && "apps".equals(extension)) {
                type = ResourceType.APPLICATION;
                urn = path.substring("apps".length() + 1);
            } else if (relativeFilePath.startsWith("strategies" + separator) && "obs".equals(extension)) {
                type = ResourceType.STRATEGY;
                urn = path.substring("strategies".length() + 1);
            } else {
                throw new KlabUnimplementedException("PUUUUUU");
            }
        }
        return (type == null || urn == null) ? null : Pair.of(type, urn);
    }

    public static String getRelativeFilePath(String urn, ResourceType type) {
        return getRelativeFilePath(urn, type, File.separator);
    }

    /**
     * Return a slash-separated relative, canonical file path to the passed document file, including the
     * expected extension. This is a static method and does not assume that a correspondent file exists in any
     * project.
     *
     * @return
     */
    public static String getRelativeFilePath(String urn, ResourceType type, String separator) {
        return switch (type) {
            case SCRIPT -> "scripts" + separator + urn + "." + type.getFileExtension();
            case TESTCASE -> "tests" + separator + urn + "." + type.getFileExtension();
            case APPLICATION -> "apps" + separator + urn + "." + type.getFileExtension();
            case ONTOLOGY, MODEL_NAMESPACE, BEHAVIOR ->
                    "src" + separator + urn.replace('.', separator.charAt(0)) + "." + type.getFileExtension();
            case STRATEGY -> "strategies" + separator + urn + "." + type.getFileExtension();
            default -> null;
        };
    }
}
