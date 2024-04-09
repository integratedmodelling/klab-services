package org.integratedmodelling.klab.api.knowledge.organization;


import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.*;

import java.io.File;
import java.net.URL;
import java.util.List;

/**
 * Abstracts stored access for a project. Being based on input streams it can be mapped on filesystem, online
 * repositories and archive files.
 */
public interface ProjectStorage {

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
        return switch(type) {
            case SCRIPT -> "scripts" + separator + urn + "." + type.getFileExtension();
            case TESTCASE -> "tests" + separator + urn + "." + type.getFileExtension();
            case APPLICATION ->  "apps" + separator +  urn + "." + type.getFileExtension();
            case ONTOLOGY, MODEL_NAMESPACE, BEHAVIOR ->  "src" + separator + urn.replace('.', separator.charAt(0)) + "." + type.getFileExtension();
            case STRATEGY -> "strategies" + separator + urn + "." + type.getFileExtension();
            default -> null;
        };
    }
}
