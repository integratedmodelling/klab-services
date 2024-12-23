package org.integratedmodelling.common.knowledge;

import org.glassfish.tyrus.core.uri.internal.MultivaluedHashMap;
import org.glassfish.tyrus.core.uri.internal.MultivaluedMap;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;

import java.util.*;
import java.util.function.Function;

/**
 * A singleton that ingests {@link ResourceSet}s intelligently and keeps tabs on loaded knowledge, caching
 * documents to minimize network transfer. It can be configured with callbacks to extract derived knowledge
 * assets from documents upon loading, also handling versions.
 * <p>
 * TODO when used with locked services, should subscribe to resource events and reload any changed namespace
 *  when changes are notified.
 */
public enum KnowledgeRepository {

    INSTANCE;

    /**
     * We keep all syntactic document we encounter here, in a multimap with different versions.
     */
    MultivaluedMap<String, KlabDocument<?>> documentMap = new MultivaluedHashMap<>();
    MultivaluedMap<String, Pair<KlabAsset, Version>> assetMap = new MultivaluedHashMap<>();
    //    Map<KlabAsset.KnowledgeClass, Function<KlabDocument<?>, Collection<Knowledge>>> processors =
    //            new HashMap<>();

    //    /**
    //     * @param knowledgeClass
    //     * @param processor
    //     * @param <T>
    //     */
    //    public <T extends KlabDocument<?>> void setProcessor(KlabAsset.KnowledgeClass knowledgeClass,
    //                                                         Function<T, Collection<Knowledge>> processor) {
    //        this.processors.put(knowledgeClass, (Function<KlabDocument<?>, Collection<Knowledge>>)
    //        processor);
    //    }

    /**
     * Knowledge assets can be registered from the outside, normally within the service whenever results are
     * ingested.
     *
     * @param urn
     * @param asset
     * @param version
     */
    public void registerAsset(String urn, Knowledge asset, Version version) {
        assetMap.add(urn, Pair.of(asset, version));
    }

    /**
     * Retrieve any knowledge asset registered by the service. This is meant to hold derived assets such as
     * models, concepts etc. as needed, and is normally used on the service side where services can keep the
     * assets they need.
     *
     * @param urn
     * @param assetClass
     * @param version
     * @param <T>
     * @return
     */
    public <T extends Knowledge> T retrieveAsset(String urn, Class<T> assetClass, Version version) {
        // TODO
        return null;
    }

    /**
     * Load what necessary from the passed resource set and update any index. After this has returned, any
     * results in the resource set are returned directly in order of reference. This only loads documents,
     * which can be further processed by the passed functors to store assets they contain; for components and
     * the like, the extension mechanism must be extended. The functors determine what gets returned and only
     * operates on the resource set's results.
     *
     * @param resourceSet
     * @param scope
     * @return any resolved knowledge items pointed to by the resourceSet {@link ResourceSet#getResults()}
     * method, or an empty list if the result list was empty or an error occurred.
     */
    public <T extends KlabAsset> List<T> ingest(ResourceSet resourceSet, Scope scope, Class<T> resultClass,
                                                Pair<KlabAsset.KnowledgeClass, Function<KlabAsset,
                                                        List<KlabAsset>>>... functors) {

        if (resourceSet.isEmpty()) {
            return List.of();
        }

        for (var res : Utils.Collections.join(resourceSet.getOntologies(), resourceSet.getNamespaces(),
                resourceSet.getObservationStrategies(), resourceSet.getBehaviors())) {
            if (!ingestDocument(res, scope, functors)) {
                return List.of();
            }
        }

        var ret = new ArrayList<T>();
        for (var res : resourceSet.getResults()) {

            if (assetMap.containsKey(res.getResourceUrn())) {
                boolean found = false;
                for (var asset : assetMap.get(res.getResourceUrn())) {
                    //  match versions
                    if ((res.getResourceVersion() == null && asset.getSecond() == null) ||
                            (res.getResourceVersion() != null && asset.getSecond()
                                    != null && asset.getSecond().compatible(res.getResourceVersion()))) {
                        if (resultClass.isAssignableFrom(asset.getFirst().getClass())) {
                            ret.add((T) asset.getFirst());
                            found = true;
                        }
                    }
                }
                if (found) {
                    continue;
                }
            }

            KlabDocument<?> doc = getExistingDocumentForResource(res);
            if (doc == null) {
                // this shouldn't happen, would mean the resource set is inconsistent, but for now no
                // exception
                continue;
            }
            if (doc.getUrn().equals(res.getResourceUrn()) && res.getKnowledgeClass() == KlabAsset.classify(doc)) {
                if (resultClass.isAssignableFrom(doc.getClass())) {
                    ret.add((T) doc);
                    break;
                }
            } else {
                for (var statement : doc.getStatements()) {
                    if (statement instanceof KlabAsset asset && asset.getUrn().equals(res.getResourceUrn())) {
                        if (resultClass.isAssignableFrom(statement.getClass())) {
                            ret.add((T) statement);
                            break;
                        }
                    }
                }
            }
        }

        return ret;
    }

    private KlabDocument<?> getExistingDocumentForResource(ResourceSet.Resource res) {
        String namespace = switch (res.getKnowledgeClass()) {
            case NAMESPACE, BEHAVIOR, SCRIPT, TESTCASE, APPLICATION, COMPONENT, ONTOLOGY,
                 OBSERVATION_STRATEGY_DOCUMENT -> res.getResourceUrn();
            case MODEL, DEFINITION, CONCEPT_STATEMENT -> Utils.Paths.getLeading(res.getResourceUrn(), '.');
            default -> null;
        };

        if (namespace != null && documentMap.containsKey(namespace)) {
            for (var doc : documentMap.get(namespace)) {
                if (doc.getVersion() == null && res.getResourceVersion() == null ||
                        (res.getResourceVersion() != null && doc.getVersion() != null && doc.getVersion().compatible(res.getResourceVersion()))) {
                    return doc;
                }
            }
        }

        return null;
    }

    private boolean ingestDocument(ResourceSet.Resource resource, Scope scope, Pair<KlabAsset.KnowledgeClass,
            Function<KlabAsset, List<KlabAsset>>>... functors) {

        if (documentMap.containsKey(resource.getResourceUrn())) {
            for (var doc : documentMap.get(resource.getResourceUrn())) {
                if (doc.getVersion() == null && resource.getResourceVersion() == null ||
                        (resource.getResourceVersion() != null && doc.getVersion() != null && doc.getVersion().compatible(resource.getResourceVersion()))) {
                    return true;
                }
            }
        }

        // if we get here, we need to resolve the document
        // TODO use broadcast to all services
        // TODO honor version in request
        var resources = scope.getService(ResourcesService.class);
        KlabDocument<?> doc = switch (resource.getKnowledgeClass()) {
            case NAMESPACE -> resources.resolveNamespace(resource.getResourceUrn(), scope);
            case BEHAVIOR, SCRIPT, TESTCASE, APPLICATION, COMPONENT ->
                    resources.resolveBehavior(resource.getResourceUrn(), scope);
            case ONTOLOGY -> resources.resolveOntology(resource.getResourceUrn(), scope);
            case OBSERVATION_STRATEGY_DOCUMENT ->
                    resources.resolveObservationStrategyDocument(resource.getResourceUrn(), scope);
            default -> null;
        };

        if (doc != null) {
            if (functors != null) {
                for (var functor : functors) {
                    if (KlabAsset.classify(doc) == functor.getFirst()) {
                        for (var knowledgeAsset : functor.getSecond().apply(doc)) {
                            assetMap.add(knowledgeAsset.getUrn(), Pair.of(knowledgeAsset, doc.getVersion()));
                        }
                    }
                }
            }
            documentMap.add(resource.getResourceUrn(), doc);

            return true;
        }

        return false;
    }

    //    <T extends Knowledge> T resolve(String urn, Class<T> resultClass) {
    //        return null;
    //    }

}
