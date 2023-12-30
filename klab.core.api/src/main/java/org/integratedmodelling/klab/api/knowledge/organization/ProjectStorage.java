package org.integratedmodelling.klab.api.knowledge.organization;


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
        RESOURCE_ASSET
    }

    /**
     * URL for the content root.
     *
     * @return
     */
    URL getUrl();

    /**
     * List all contained resources for the passed types. Use {@link URL#openStream()} to
     * access their contents.
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
}
