package org.integratedmodelling.common.knowledge;

import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;

import java.util.List;

/**
 * A singleton that ingests {@link ResourceSet}s intelligently and keeps tabs on loaded knowledve.
 */
public enum KnowledgeRepository {

    INSTANCE;



    /**
     * Load what necessary from the passed resource set and update any index. After this has returned, the
     * URNs requested will be available to retrieve from the {@link #resolve(String, Class)} method; if the
     * resource set named "result" object, these are returned directly in order of reference.
     *
     * @param resourceSet
     * @param scope
     * @return any resolved knowledge items pointed to by the resourceSet {@link ResourceSet#getResults()}
     * method, or an empty list if the result list was empty or an error occurred.
     */
    public List<Knowledge> ingest(ResourceSet resourceSet, Scope scope) {
        return List.of();
    }

    <T extends Knowledge> T resolve(String urn, Class<T> resultClass) {
        return null;
    }

}
