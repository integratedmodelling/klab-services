package org.integratedmodelling.klab.services.reasoner;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.services.ResourcesService;

import java.util.concurrent.TimeUnit;

/**
 * Match two concept using one as a syntactic pattern for the other. Used in the rule system to filter
 * resolution strategies.
 * <p>
 * Keeps syntactic objects in a cache to minimize traffic to the resources service.
 */
public class SyntacticMatcher {

    private ReasonerService reasonerService;
    private ResourcesService resourcesService;
    private LoadingCache<String, KimObservable> conceptCache =
            CacheBuilder.newBuilder()
                        .concurrencyLevel(20)
                        .maximumSize(400) // TODO configure
                        .build(new CacheLoader<>() {
                            @Override
                            public KimObservable load(String key) throws Exception {
                                return resourcesService.resolveObservable(key);
                            }
                        });

    public SyntacticMatcher(ReasonerService reasonerService, ResourcesService resourcesService) {
        this.reasonerService = reasonerService;
        this.resourcesService = resourcesService;
    }

    public boolean match(Semantics candidate, Semantics pattern) {

        if (isAtomic(pattern.getUrn())) {
            return reasonerService.subsumes(candidate, pattern);
        }

        KimConcept oCandidate = null;
        KimConcept pCandidate = null;

        try {
            oCandidate = conceptCache.getUnchecked(candidate.getUrn()).getSemantics();
            pCandidate = conceptCache.getUnchecked(pattern.getUrn()).getSemantics();
        } catch (Throwable t) {
            //
        }

        if (pCandidate == null || oCandidate == null) {
            return false;
        }

        return matchConcepts(oCandidate, pCandidate);
    }

    private boolean matchConcepts(KimConcept candidate, KimConcept pattern) {


        if (pattern.is(SemanticType.UNION) || pattern.is(SemanticType.INTERSECTION)) {
            // must have same type and same number of arguments
            // TODO this applies also to all ops
        }

        int narg = pattern.getOperands().size();
        // NO - if pattern is X or Y it should match X or Y or Z with matching Y = Y or Z
        if (candidate.getOperands().size() != narg) {
            return false;
        }


        return false;

    }

    private boolean isAtomic(String urn) {
        // TODO we should use a more intelligent check, although this one should work in all circumstances
        //  given that the URN is normalized.
        return !urn.contains(" ");
    }

}
