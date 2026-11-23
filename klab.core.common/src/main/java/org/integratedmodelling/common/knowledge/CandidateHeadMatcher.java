package org.integratedmodelling.common.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.services.Reasoner;

/** Semantic part of filtering the authored outputs of candidate models. */
public final class CandidateHeadMatcher {
  private CandidateHeadMatcher() {}

  public static Set<Integer> match(
      Reasoner reasoner, Semantics requested, List<? extends List<? extends Semantics>> candidates) {
    var core = reasoner.coreObservable(requested);
    if (core == null) return Set.of();

    var matches = new HashSet<Integer>();
    var heads = new HashSet<String>();
    var headsByCandidate = new ArrayList<Set<String>>(candidates.size());
    for (int index = 0; index < candidates.size(); index++) {
      var candidateHeads = new HashSet<String>();
      for (var output : candidates.get(index)) {
        var candidate = reasoner.coreObservable(output);
        if (candidate == null) continue;
        if (Objects.equals(core.getUrn(), candidate.getUrn())) {
          matches.add(index);
          candidateHeads.clear();
          break;
        }
        if (!disjointCoreTypes(core, candidate)) candidateHeads.add(candidate.getUrn());
      }
      headsByCandidate.add(candidateHeads);
      heads.addAll(candidateHeads);
    }

    if (!heads.isEmpty()) {
      var accepted = reasoner.matchingResolutionHeads(core, heads);
      for (int index = 0; index < headsByCandidate.size(); index++)
        if (!Collections.disjoint(headsByCandidate.get(index), accepted)) matches.add(index);
    }
    return matches;
  }

  /** Reject only disjoint quality/substantial pairs. Unclassified types can still match. */
  public static boolean disjointCoreTypes(Concept first, Concept second) {
    if (first.getType() == null || first.getType().isEmpty()
        || second.getType() == null || second.getType().isEmpty()) return false;
    var a = SemanticType.fundamentalType(first.getType());
    var b = SemanticType.fundamentalType(second.getType());
    var substantial = EnumSet.of(SemanticType.SUBJECT, SemanticType.AGENT, SemanticType.PROCESS,
        SemanticType.EVENT, SemanticType.RELATIONSHIP, SemanticType.CONFIGURATION);
    return a == SemanticType.QUALITY && substantial.contains(b)
        || b == SemanticType.QUALITY && substantial.contains(a);
  }
}
