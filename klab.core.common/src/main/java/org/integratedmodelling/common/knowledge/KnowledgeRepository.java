package org.integratedmodelling.common.knowledge;

import org.apache.commons.collections.MultiMap;
import org.glassfish.tyrus.core.uri.internal.MultivaluedHashMap;
import org.glassfish.tyrus.core.uri.internal.MultivaluedMap;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A singleton that ingests {@link ResourceSet}s intelligently and keeps tabs on loaded knowledge, caching
 * documents to minimize network transfer.
 */
public enum KnowledgeRepository {

    INSTANCE;

    /**
     * We keep all syntactic document we encounter here, in a multimap with different versions.
     */
    MultivaluedMap<String, KlabDocument<?>> namespaceMap = new MultivaluedHashMap<>();

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
    public List<KlabAsset> ingest(ResourceSet resourceSet, Scope scope) {

        if (resourceSet.isEmpty()) {
            return List.of();
        }

        for (var res : Utils.Collections.join(resourceSet.getOntologies(), resourceSet.getNamespaces(),
                resourceSet.getObservationStrategies(), resourceSet.getBehaviors())) {
            if (!ingest(res, scope)) {
                return List.of();
            }
        }

        var ret = new ArrayList<KlabAsset>();
        for (var res : resourceSet.getResults()) {
            KlabDocument<?> doc = getDocumentForResource(res);
            if (doc == null) {
                // this shouldn't happen, would mean the resource set is inconsistent, but for now no
                // exception
                continue;
            }
            if (doc.getUrn().equals(res.getResourceUrn()) && res.getKnowledgeClass() == KlabAsset.classify(doc)) {
                ret.add(doc);
                break;
            } else {
                for (var statement : doc.getStatements()) {
                    if (switch (statement) {
                        case KimModel model -> model.getUrn().equals(res.getResourceUrn());
                        case KimSymbolDefinition model -> model.getUrn().equals(res.getResourceUrn());
                        case KimConceptStatement model -> model.getUrn().equals(res.getResourceUrn());
                        case KimObservationStrategy model -> model.getUrn().equals(res.getResourceUrn());
                        default -> false;
                    }) {
                        ret.add((KlabAsset) statement);
                        break;
                    }
                }
            }
        }

        return ret;
    }

    private KlabDocument<?> getDocumentForResource(ResourceSet.Resource res) {
        return null;
    }

    private boolean ingest(ResourceSet.Resource resource, Scope scope) {

        if (namespaceMap.containsKey(resource.getResourceUrn())) {

            for (var doc : namespaceMap.get(resource.getResourceUrn())) {
                if (doc.getVersion() == null && resource.getResourceVersion() == null || (resource.getResourceVersion() != null && doc.getVersion() != null && doc.getVersion().compatible(resource.getResourceVersion()))) {

                }
            }

            return true;
        }

        return false;
    }

    <T extends Knowledge> T resolve(String urn, Class<T> resultClass) {
        return null;
    }

}
