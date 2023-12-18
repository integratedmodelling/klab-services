package org.integratedmodelling.klab.api.knowledge.organization;


import java.net.URL;
import java.util.List;

/**
 * Abstracts stored access for a project. Being based on input streams it can be mapped on filesystem, online
 * repositories and archive files.
 */
public interface ProjectStorage {

    enum ResourceType {
        WORLDVIEW_NAMESPACE,
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
     * List all contained resources for the passed types. Use {@link #openResource(String, ResourceType)} to
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
}
