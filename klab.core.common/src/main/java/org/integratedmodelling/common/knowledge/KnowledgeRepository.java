package org.integratedmodelling.common.knowledge;

import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;

/**
 * A singleton that ingests {@link ResourceSet}s intelligently and keeps tabs on loaded knowledve.
 */
public enum KnowledgeRepository {

    INSTANCE;


    /**
     * Load what necessary from the passed resource set and update any index. After this has returned true,
     * the URNs requested will be available to retrieve from the {@link #resolve(String, Class)} method.
     *
     * @param resourceSet
     * @param scope
     * @return
     */
    public boolean ingest(ResourceSet resourceSet, Scope scope) {
        return false;
    }

    <T extends Knowledge> T resolve(String urn, Class<T> resultClass) {
        return null;
    }

}
