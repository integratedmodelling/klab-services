package org.integratedmodelling.klab.services.reasoner;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.lang.SemanticClause;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.services.ResourcesService;

import java.util.concurrent.ExecutionException;

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

    private LoadingCache<Pair<Semantics, Semantics>, Boolean> matchCache =
            CacheBuilder.newBuilder()
                        .concurrencyLevel(20)
                        .maximumSize(400) // TODO configure
                        .build(new CacheLoader<>() {
                            @Override
                            public Boolean load(Pair<Semantics, Semantics> key) throws Exception {
                                return doMatch(key.getFirst(), key.getSecond());
                            }
                        });

    public SyntacticMatcher(ReasonerService reasonerService, ResourcesService resourcesService) {
        this.reasonerService = reasonerService;
        this.resourcesService = resourcesService;
    }

    public boolean match(Semantics candidate, Semantics pattern) {
        try {
            return matchCache.get(Pair.of(candidate, pattern));
        } catch (ExecutionException e) {
            Logging.INSTANCE.error(e);
            return false;
        }
    }

    public boolean doMatch(Semantics candidate, Semantics pattern) {

        if (candidate == null || pattern == null || candidate.is(SemanticType.NOTHING) || pattern.is(SemanticType.NOTHING)) {
            // null doesn't match null
            return false;
        }

        if (isAtomic(pattern.getUrn())) {
            return reasonerService.subsumes(candidate, pattern);
        }

        KimObservable oCandidateObservable = null;
        KimObservable pCandidateObservable = null;
        KimConcept oCandidate = null;
        KimConcept pCandidate = null;

        try {
            oCandidateObservable = conceptCache.getUnchecked(candidate.getUrn());
            oCandidate = oCandidateObservable.getSemantics();
            pCandidateObservable = conceptCache.getUnchecked(pattern.getUrn());
            pCandidate = pCandidateObservable.getSemantics();
        } catch (Throwable t) {
            Logging.INSTANCE.error(t);
            return false;
        }

        if (pCandidate == null || oCandidate == null) {
            return false;
        }

        return matchConcepts(oCandidate, pCandidate, oCandidateObservable, pCandidateObservable,
                candidate.asConcept(), pattern.asConcept());
    }

    private boolean matchConcepts(KimConcept candidate, KimConcept pattern,
                                  KimObservable candidateObservable, KimObservable patternObservable,
                                  Concept candidateConcept,
                                  Concept patternConcept) {

        if (candidate == null || pattern == null) {
            // null doesn't match null
            return false;
        }

        if (pattern.is(SemanticType.UNION) || pattern.is(SemanticType.INTERSECTION)) {
            // pattern should have at most two arguments; we operate on a <tail, rest> basis.
            if (pattern.getOperands().size() != 2) {
                throw new KlabIllegalStateException("Patterns in AND or OR should have at most two operands");
            }

            var type = pattern.is(SemanticType.UNION) ? SemanticType.UNION : SemanticType.INTERSECTION;

            /*
              candidate must have at least two operands; extract the head and the tail as concept
             */
            if (!candidate.is(type)) {
                return false;
            }

            StringBuffer buffer = new StringBuffer();
            var headSyntax = candidate.getOperands().getFirst();
            candidate.getOperands().stream().skip(1).map(c -> buffer.append(buffer.isEmpty() ? "" : " ").append(c.getUrn()));

            if (buffer.isEmpty()) {
                return false;
            }

            /* Match the FIRST operand and connect the remaining, then match the two pieces */

            var head = reasonerService.declareConcept(headSyntax);
            var tail = reasonerService.resolveConcept(buffer.toString());

            return match(head, reasonerService.declareConcept(pattern.getOperands().getFirst())) &&
                    match(tail, reasonerService.declareConcept(pattern.getOperands().get(1)));
        }

        if (pattern.isCollective() != candidate.isCollective()) {
            return false;
        }

        if (pattern.isNegated() != candidate.isNegated()) {
            return false;
        }

        if (pattern.getSemanticModifier() != null) {

            if (pattern.getSemanticModifier() != candidate.getSemanticModifier()) {
                return false;
            }

            var pMod = pattern.semanticOperation();
            var oMod = candidate.semanticOperation();

            if (!match(reasonerService.declareConcept(candidate.semanticOperation().getSecond()),
                    reasonerService.declareConcept(pMod.getSecond()))) {
                return false;
            }

            if (pMod.getThird() != null) {

                if (oMod.getThird() == null) {
                    return false;
                }

                // match the comparison
                if (!match(reasonerService.declareConcept(oMod.getThird()),
                        reasonerService.declareConcept(pMod.getThird()))) {
                    return false;
                }
            }
        }

        for (var clause : SemanticClause.values()) {
            var target = pattern.semanticClause(clause);
            // for all the modifiers, use the reasoner on the candidate
            if (target != null) {
                var operand = candidate.semanticClause(clause);
                if (operand == null || !match(reasonerService.declareConcept(operand),
                        reasonerService.declareConcept(target))) {
                    return false;
                }
            }
        }


        for (var valueOperator : patternObservable.getValueOperators()) {
            // TODO match the corresponding value operator. Must enable both value equality and generic
            //  value classifier
        }

        // all checks passed

        return true;
    }

    private boolean isAtomic(String urn) {
        // TODO we should use a more intelligent check, although this one should work in all circumstances
        //  given that the URN is normalized.
        return !urn.contains(" ");
    }

    /**
     * Call this after any changes to the worldview!
     */
    public void resetCaches() {
        this.conceptCache.invalidateAll();
        this.matchCache.invalidateAll();
    }

}
