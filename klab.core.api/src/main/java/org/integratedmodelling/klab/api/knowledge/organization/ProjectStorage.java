package org.integratedmodelling.klab.api.knowledge.organization;


import java.io.InputStream;
import java.util.List;

/**
 * Abstracts stored access for a project. Being based on input streams can also be mapped on online
 * repositories and archive files.
 */
public interface ProjectStorage {

    enum ResourceType {
        WORLDVIEW,
        MODELFILE,
        MANIFEST,
        DOCUMENTATION,
        STRATEGY,
        BEHAVIOR,
        APPLICATION,
        SCRIPT,
        TESTCASE,
        COMPONENT,
        RESOURCE,
        RESOURCE_CONTENT
    }

    /**
     * List all contained resources for the passed types. Use {@link #openResource(String)} to access their
     * contents.
     *
     * @param types
     * @return
     */
    List<String> listResources(ResourceType... types);

    /**
     * @param resourceId
     * @return
     */
    InputStream openResource(String resourceId);
}
