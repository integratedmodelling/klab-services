package org.integratedmodelling.klab.services.reasoner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.ArrayList;
import org.integratedmodelling.klab.api.services.Authority;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.scope.Scope;
import org.junit.jupiter.api.Test;

class SemanticMatcherTest {

  @Test
  void assertedDistanceUsesShortestPathAndTerminatesOnCycles() {
    var graph = new FakeOperations();
    Concept root = concept("Root");
    Concept left = concept("Left");
    Concept right = concept("Right");
    Concept leaf = concept("Leaf");
    graph.parents.put(left, List.of(root));
    graph.parents.put(right, List.of(root));
    graph.parents.put(leaf, List.of(left, right));

    var matcher = new SemanticMatcher(graph);
    assertEquals(0, matcher.assertedDistance(leaf, leaf));
    assertEquals(1, matcher.assertedDistance(leaf, left));
    assertEquals(2, matcher.assertedDistance(leaf, root));
    assertEquals(-1, matcher.assertedDistance(root, leaf));
    assertEquals(-1, matcher.assertedDistance(left, right));

    graph.parents.put(root, List.of(leaf));
    graph.revision++;
    assertEquals(3, matcher.assertedDistance(left, right));
  }

  @Test
  void semanticDistanceIsDirectionalAndMonotonic() {
    var graph = new FakeOperations();
    Concept root = predicate("Root");
    Concept child = predicate("Child");
    Concept grandchild = predicate("Grandchild");
    Concept sibling = predicate("Sibling");
    graph.parents.put(child, List.of(root));
    graph.parents.put(grandchild, List.of(child));
    graph.parents.put(sibling, List.of(root));

    var matcher = new SemanticMatcher(graph);
    assertEquals(0, matcher.semanticDistance(root, root));
    int direct = matcher.semanticDistance(root, child);
    int indirect = matcher.semanticDistance(root, grandchild);
    assertTrue(direct > 0);
    assertTrue(indirect > direct);
    assertTrue(matcher.semanticDistance(child, root) < 0);
    assertTrue(matcher.semanticDistance(child, sibling) < 0);
  }

  @Test
  void contextualDistanceDoesNotReuseAnotherContextsResult() {
    var graph = new FakeOperations();
    Concept target = predicate("Target");
    Concept candidate = predicate("Candidate");
    Concept expectedContext = concept("ExpectedContext");
    Concept wrongContext = concept("WrongContext");
    graph.parents.put(candidate, List.of(target));
    graph.inherent.put(candidate, expectedContext);

    var matcher = new SemanticMatcher(graph);
    assertTrue(matcher.semanticDistance(target, candidate, expectedContext) >= 0);
    assertTrue(matcher.semanticDistance(target, candidate, wrongContext) < 0);
    assertTrue(matcher.semanticDistance(target, candidate) < 0);
  }

  @Test
  void inheritedInherencyAcceptsOnlyCandidateSpecializations() {
    var graph = new FakeOperations();
    Concept target = predicate("Target");
    Concept candidate = predicate("Candidate");
    Concept broadInherent = concept("BroadInherent");
    Concept narrowInherent = concept("NarrowInherent");
    graph.parents.put(candidate, List.of(target));
    graph.parents.put(narrowInherent, List.of(broadInherent));
    graph.inherent.put(target, broadInherent);
    graph.inherent.put(candidate, narrowInherent);

    var matcher = new SemanticMatcher(graph);
    assertTrue(matcher.semanticDistance(target, candidate) >= 0);
    assertTrue(matcher.semanticDistance(candidate, target) < 0);
  }

  @Test
  void modifiedPredicatesCompareHeadsBeforeTheirInherentFillers() {
    var graph = new FakeOperations();
    Concept normalized = predicate("Normalized");
    Concept quality = concept("Quality");
    ((ConceptImpl) quality).setUrn("odo:Quality"); // Canonical result of the im:Quality alias.
    Concept elevation = concept("Elevation");
    Concept region = concept("TerrestrialRegion");
    Concept general = predicate("Normalized of odo:Quality");
    Concept specific = predicate("Normalized of geography:Elevation");
    Concept incompatible = predicate("Normalized of earth:Region");
    graph.parents.put(elevation, List.of(quality));
    for (Concept expression : List.of(general, specific, incompatible)) {
      graph.parents.put(expression, List.of(normalized));
      graph.heads.put(expression, normalized);
    }
    graph.directInherent.put(general, quality);
    graph.directInherent.put(specific, elevation);
    graph.directInherent.put(incompatible, region);
    graph.inherent.putAll(graph.directInherent);

    var matcher = new SemanticMatcher(graph);
    assertEquals(-1, matcher.assertedDistance(specific, general));
    assertTrue(matcher.semanticDistance(general, specific) >= 0);
    assertTrue(matcher.semanticDistance(general, specific, region) >= 0);
    assertTrue(matcher.semanticDistance(specific, general, region) < 0);
    assertTrue(matcher.semanticDistance(general, incompatible, region) < 0);
  }

  @Test
  void differentObservableHeadsRemainIncompatibleDespiteSubsumption() {
    var graph = new FakeOperations();
    Concept quality = concept("Quality");
    Concept elevation = concept("Elevation");
    graph.parents.put(elevation, List.of(quality));
    assertTrue(new SemanticMatcher(graph).semanticDistance(quality, elevation) < 0);
  }

  @Test
  void nonPredicateHeadsStillRankByInherentDistance() {
    var graph = new FakeOperations();
    var head = concept("Elevation");
    var broad = concept("Elevation of Region");
    var exact = concept("Elevation of Basin");
    var region = concept("Region"); var basin = concept("Basin");
    graph.heads.put(broad, head); graph.heads.put(exact, head);
    graph.parents.put(basin, List.of(region));
    graph.directInherent.put(broad, region); graph.directInherent.put(exact, basin);
    graph.inherent.putAll(graph.directInherent);
    var matcher = new SemanticMatcher(graph);
    assertEquals(0, matcher.semanticDistance(exact, exact));
    assertTrue(matcher.semanticDistance(broad, exact) > 0);
    assertTrue(matcher.semanticDistance(exact, broad) < 0);
  }

  @Test
  void distributedAndIndividualPredicateOperationsCannotResolveEachOther() {
    var graph = new FakeOperations();
    var classification = predicate("P of each S");
    var characterization = predicate("P of S");
    ((ConceptImpl) classification).setDescriptionType(
        org.integratedmodelling.klab.api.knowledge.Contextualization.CLASSIFICATION);
    ((ConceptImpl) characterization).setDescriptionType(
        org.integratedmodelling.klab.api.knowledge.Contextualization.CHARACTERIZATION);
    graph.heads.put(classification, predicate("P"));
    graph.heads.put(characterization, graph.heads.get(classification));
    var matcher = new SemanticMatcher(graph);
    assertTrue(matcher.semanticDistance(classification, characterization) < 0);
    assertTrue(matcher.semanticDistance(characterization, classification) < 0);
  }

  @Test void authorityDistanceIsDirectionalAndUnknownDoesNotFallBackToParentsOrCaches() {
    var graph = new FakeOperations(); var target = predicate("AuthorityTarget"); var candidate = predicate("AuthorityCandidate");
    graph.parents.put(candidate,List.of(target));
    graph.delegated.put(Pair.of(target,candidate), new org.integratedmodelling.klab.api.services.Authority.SemanticDistance(
        org.integratedmodelling.klab.api.services.Authority.DistanceStatus.COMPATIBLE,2,List.of()));
    var matcher = new SemanticMatcher(graph);
    assertEquals(100,matcher.semanticDistance(target,candidate));
    graph.delegated.put(Pair.of(target,candidate),org.integratedmodelling.klab.api.services.Authority.SemanticDistance.unknown("Policy changed"));
    assertTrue(matcher.semanticDistance(target,candidate)<0);
    graph.delegated.put(Pair.of(candidate,target),org.integratedmodelling.klab.api.services.Authority.SemanticDistance.unknown("Not a refinement"));
    assertTrue(matcher.semanticDistance(candidate,target)<0);
    graph.delegated.clear(); assertTrue(matcher.semanticDistance(target,candidate)>0);
  }

  @Test void authorityTraitsMediateWithoutSkippingOrdinaryHeadAndContextChecks() {
    var graph = new FakeOperations(); var a=predicate("PatternA"); var b=predicate("PatternB");
    var target=concept("TreeA"); var candidate=concept("TreeB"); var head=concept("Tree");
    graph.heads.put(target,head); graph.heads.put(candidate,head);
    graph.traits.put(target,List.of(a)); graph.traits.put(candidate,List.of(b));
    graph.delegated.put(Pair.of(a,b),new org.integratedmodelling.klab.api.services.Authority.SemanticDistance(
        org.integratedmodelling.klab.api.services.Authority.DistanceStatus.COMPATIBLE,1,List.of()));
    var matcher=new SemanticMatcher(graph); assertEquals(1,matcher.semanticDistance(target,candidate));
    graph.heads.put(candidate,concept("WrongHead")); assertTrue(matcher.semanticDistance(target,candidate)<0);
    graph.heads.put(candidate,head); graph.inherent.put(candidate,concept("WrongContext"));
    assertTrue(matcher.semanticDistance(target,candidate,concept("Context"))<0);
  }

  @Test void identicalAuthorityTraitMustRemainReflexiveWhenProviderIsUnknown() {
    var g=new FakeOperations(); var a=predicate("A"); var target=concept("Target"); var candidate=concept("Candidate"); var head=concept("Head");
    g.heads.put(target,head);g.heads.put(candidate,head);g.traits.put(target,List.of(a));g.traits.put(candidate,List.of(a));
    g.delegated.put(Pair.of(a,a),org.integratedmodelling.klab.api.services.Authority.SemanticDistance.unknown("Replay-only evidence unavailable"));
    assertEquals(0,new SemanticMatcher(g).semanticDistance(target,candidate));
  }
  @Test void unrelatedCrossBindingUnknownMustNotVetoAnUnsupportedExactTrait() {
    var g=new FakeOperations(); var a=predicate("LegacyA");var b=predicate("OtherBindingB");var target=concept("Target");var candidate=concept("Candidate");var head=concept("Head");
    g.heads.put(target,head);g.heads.put(candidate,head);g.traits.put(target,List.of(a,b));g.traits.put(candidate,List.of(a,b));
    g.delegated.put(Pair.of(a,b),org.integratedmodelling.klab.api.services.Authority.SemanticDistance.unknown("Different authority configurations"));
    g.delegated.put(Pair.of(b,a),org.integratedmodelling.klab.api.services.Authority.SemanticDistance.unknown("Different authority configurations"));
    assertEquals(0,new SemanticMatcher(g).semanticDistance(target,candidate));
  }


  @Test void unknownNestedAuthorityTraitMustBlockInherentFillerHierarchyFallback() {
    var g=new FakeOperations();var a=predicate("PatternA");var b=predicate("PatternB");var target=concept("Target");var candidate=concept("Candidate");var head=concept("Head");var targetFiller=concept("TargetFiller");var candidateFiller=concept("CandidateFiller");
    g.heads.put(target,head);g.heads.put(candidate,head);g.directInherent.put(target,targetFiller);g.directInherent.put(candidate,candidateFiller);
    g.parents.put(candidateFiller,List.of(targetFiller));g.traits.put(targetFiller,List.of(a));g.traits.put(candidateFiller,List.of(b));g.parents.put(b,List.of(a));
    g.delegated.put(Pair.of(a,b),org.integratedmodelling.klab.api.services.Authority.SemanticDistance.unknown("Policy cannot admit candidate"));
    assertTrue(new SemanticMatcher(g).semanticDistance(target,candidate)<0,"UNKNOWN nested identity must not fall back to OWL traits");
  }

  @Test void exactAuthorityHeadsAndTraitsDoNotAskTheProviderForReflexivity() {
    var g = new FakeOperations(); var a = predicate("A");
    g.delegated.put(Pair.of(a, a), Authority.SemanticDistance.unknown("Replay only"));
    var matcher = new SemanticMatcher(g);
    assertEquals(0, matcher.semanticDistance(a, a));
    var target = concept("Target"); var candidate = concept("Candidate"); var head = concept("Head");
    g.heads.put(target, head); g.heads.put(candidate, head);
    g.traits.put(target, List.of(a)); g.traits.put(candidate, List.of(a));
    assertEquals(0, matcher.semanticDistance(target, candidate));
    assertTrue(g.distanceCalls.isEmpty());
  }

  @Test void onlyUnsupportedFallsBackToOrdinaryAuthorityHeadHierarchy() {
    var g = new FakeOperations(); var a = predicate("A"); var b = predicate("B");
    g.parents.put(b, List.of(a)); var matcher = new SemanticMatcher(g);
    for (var status : List.of(Authority.DistanceStatus.UNKNOWN, Authority.DistanceStatus.INCOMPATIBLE,
        Authority.DistanceStatus.UNSUPPORTED)) {
      g.delegated.put(Pair.of(a, b), new Authority.SemanticDistance(status, -1, List.of()));
      int actual = matcher.semanticDistance(a, b);
      if (status == Authority.DistanceStatus.UNSUPPORTED) assertEquals(50, actual);
      else assertTrue(actual < 0, status.toString());
    }
  }

  @Test void crossBindingAlternativeOrderDoesNotChangeLegacyExactMatches() {
    var g = new FakeOperations(); var a = predicate("LegacyA"); var b = predicate("OtherBindingB");
    var target = concept("Target"); var candidate = concept("Candidate"); var head = concept("Head");
    g.heads.put(target, head); g.heads.put(candidate, head); g.traits.put(target, List.of(a, b));
    g.delegated.put(Pair.of(a, b), Authority.SemanticDistance.unknown("Different bindings"));
    g.delegated.put(Pair.of(b, a), Authority.SemanticDistance.unknown("Different bindings"));
    var matcher = new SemanticMatcher(g);
    for (var order : List.of(List.of(a, b), List.of(b, a))) {
      g.traits.put(candidate, order); assertEquals(0, matcher.semanticDistance(target, candidate));
    }
  }

  @Test void providerAlternativesChooseTheLowestCompatibleDistance() {
    var g = new FakeOperations(); var a = predicate("A"); var b = predicate("B"); var c = predicate("C");
    var target = concept("Target"); var candidate = concept("Candidate"); var head = concept("Head");
    g.heads.put(target, head); g.heads.put(candidate, head);
    g.traits.put(target, List.of(a, b, c));
    g.delegated.put(Pair.of(a, b), new Authority.SemanticDistance(Authority.DistanceStatus.COMPATIBLE, 4, List.of()));
    g.delegated.put(Pair.of(a, c), new Authority.SemanticDistance(Authority.DistanceStatus.COMPATIBLE, 1, List.of()));
    var matcher = new SemanticMatcher(g);
    for (var order : List.of(List.of(b, c), List.of(c, b))) {
      g.traits.put(candidate, order); assertEquals(1, matcher.semanticDistance(target, candidate));
    }
  }

  @Test void ordinaryTraitSpecializationsAndExtraTraitsKeepTheirExistingRestrictions() {
    var g = new FakeOperations(); var a = predicate("A"); var b = predicate("B");
    var target = concept("Target"); var candidate = concept("Candidate"); var head = concept("Head");
    g.heads.put(target, head); g.heads.put(candidate, head); g.traits.put(target, List.of(a));
    g.parents.put(b, List.of(a)); g.traits.put(candidate, List.of(b));
    var matcher = new SemanticMatcher(g);
    assertTrue(matcher.semanticDistance(target, candidate) < 0);
    g.delegated.put(Pair.of(a, b), new Authority.SemanticDistance(Authority.DistanceStatus.COMPATIBLE, 1, List.of()));
    assertEquals(1, matcher.semanticDistance(target, candidate));
    g.traits.put(candidate, List.of(a, b));
    assertTrue(matcher.semanticDistance(target, candidate) < 0);
  }

  @Test void everyFillerPathHonorsProviderPolicyAndRetainsUnsupportedHierarchyScores() {
    for (String path : List.of("directInherent", "inherent", "goal", "cooccurrent", "causant", "caused",
        "adjacent", "compresent", "relativeTo")) {
      var g = new FakeOperations(); var a = predicate("A"); var b = predicate("B");
      var target = concept("Target"); var candidate = concept("Candidate"); var head = concept("Head");
      var targetFiller = concept("TargetFiller"); var candidateFiller = concept("CandidateFiller");
      g.heads.put(target, head); g.heads.put(candidate, head);
      setFiller(g, path, target, targetFiller); setFiller(g, path, candidate, candidateFiller);
      g.parents.put(candidateFiller, List.of(targetFiller)); g.parents.put(b, List.of(a));
      g.traits.put(targetFiller, List.of(a)); g.traits.put(candidateFiller, List.of(b));
      var matcher = new SemanticMatcher(g);
      for (var status : Authority.DistanceStatus.values()) {
        g.delegated.put(Pair.of(a, b), new Authority.SemanticDistance(status,
            status == Authority.DistanceStatus.COMPATIBLE ? 4 : -1, List.of()));
        int actual = matcher.semanticDistance(target, candidate);
        switch (status) {
          case COMPATIBLE -> assertEquals(5, actual, path);
          case UNSUPPORTED -> assertEquals(11, actual, path);
          case UNKNOWN, INCOMPATIBLE -> assertTrue(actual < 0, path + ": " + status);
        }
      }
    }
  }

  @Test void identicalFillerTraitsAndUnrelatedUnknownAlternativesRemainUsable() {
    var g = new FakeOperations(); var a = predicate("A"); var b = predicate("B");
    var target = concept("Target"); var candidate = concept("Candidate"); var head = concept("Head");
    var targetFiller = concept("TargetFiller"); var candidateFiller = concept("CandidateFiller");
    g.heads.put(target, head); g.heads.put(candidate, head);
    g.inherent.put(target, targetFiller); g.inherent.put(candidate, candidateFiller);
    g.parents.put(candidateFiller, List.of(targetFiller));
    g.traits.put(targetFiller, List.of(a, b)); g.traits.put(candidateFiller, List.of(b, a));
    g.delegated.put(Pair.of(a, a), Authority.SemanticDistance.unknown("Replay only"));
    g.delegated.put(Pair.of(a, b), Authority.SemanticDistance.unknown("Different bindings"));
    g.delegated.put(Pair.of(b, a), Authority.SemanticDistance.unknown("Different bindings"));
    assertEquals(1, new SemanticMatcher(g).semanticDistance(target, candidate));
  }

  @Test void changingTraitPolicyCannotReuseAnEarlierCompatibleResult() {
    var g = new FakeOperations(); var a = predicate("A"); var b = predicate("B");
    var target = concept("Target"); var candidate = concept("Candidate"); var head = concept("Head");
    g.heads.put(target, head); g.heads.put(candidate, head);
    g.traits.put(target, List.of(a)); g.traits.put(candidate, List.of(b));
    g.delegated.put(Pair.of(a, b), new Authority.SemanticDistance(Authority.DistanceStatus.COMPATIBLE, 2, List.of()));
    var matcher = new SemanticMatcher(g); assertEquals(2, matcher.semanticDistance(target, candidate));
    g.delegated.put(Pair.of(a, b), Authority.SemanticDistance.unknown("Evidence changed"));
    assertTrue(matcher.semanticDistance(target, candidate) < 0);
  }

  @Test void compatibleMediationPrecedesAnUnsupportedHierarchyAlternative() {
    var g = new FakeOperations(); var a = predicate("A"); var b = predicate("B"); var c = predicate("C");
    var target = concept("Target"); var candidate = concept("Candidate"); var head = concept("Head");
    g.heads.put(target, head); g.heads.put(candidate, head);
    g.traits.put(target, List.of(a, b)); g.parents.put(b, List.of(a));
    g.delegated.put(Pair.of(a, c), new Authority.SemanticDistance(Authority.DistanceStatus.COMPATIBLE, 4, List.of()));
    g.delegated.put(Pair.of(b, c), Authority.SemanticDistance.unknown("Different bindings"));
    var matcher = new SemanticMatcher(g);
    for (var order : List.of(List.of(b, c), List.of(c, b))) {
      g.traits.put(candidate, order); assertEquals(4, matcher.semanticDistance(target, candidate));
    }
  }

  @Test void exactCanonicalIdentityHasStablePrecedenceOverRedundantEquivalentExtras() {
    for (String path : tiePaths()) {
      var f = tieFixture(path); var g = f.graph(); var a = predicate("A"); var b = predicate("B");
      g.traits.put(f.targetOwner(), List.of(a));
      g.delegated.put(Pair.of(a, b), compatible(0));
      var matcher = new SemanticMatcher(g);
      for (var order : permutations(List.of(a, b))) {
        g.traits.put(f.candidateOwner(), order);
        int actual = matcher.semanticDistance(f.target(), f.candidate());
        if (path.equals("traits")) assertEquals(-50, actual, path);
        else assertEquals(11, actual, path);
      }
    }
  }

  @Test void equalDistanceAlternativesChooseSemanticallyValidCoverageInEveryPath() {
    for (String path : tiePaths()) {
      var f = tieFixture(path); var g = f.graph();
      var a = predicate("A"); var b = predicate("B"); var c = predicate("C");
      g.traits.put(f.targetOwner(), List.of(a));
      // B is already admitted by the reverse hierarchy check. C must receive mediation.
      g.parents.put(a, List.of(b));
      g.delegated.put(Pair.of(a, b), compatible(2)); g.delegated.put(Pair.of(a, c), compatible(2));
      var matcher = new SemanticMatcher(g);
      for (var order : permutations(List.of(b, c))) {
        g.traits.put(f.candidateOwner(), order);
        assertEquals(path.equals("traits") ? 2 : 3,
            matcher.semanticDistance(f.target(), f.candidate()), path + order);
      }
    }
  }

  @Test void equalDistanceAssignmentsReconsiderEarlierChoicesInEveryPath() {
    for (String path : tiePaths()) {
      var f = tieFixture(path); var g = f.graph();
      var a = predicate("A"); var d = predicate("D"); var e = predicate("E");
      var b = predicate("B"); var c = predicate("C"); var last = predicate("F");
      g.delegated.put(Pair.of(a, b), compatible(2)); g.delegated.put(Pair.of(a, c), compatible(2));
      g.delegated.put(Pair.of(a, last), compatible(2));
      g.delegated.put(Pair.of(d, b), compatible(1)); g.delegated.put(Pair.of(d, c), compatible(1));
      g.delegated.put(Pair.of(d, last), Authority.SemanticDistance.unknown("Different binding"));
      g.delegated.put(Pair.of(e, b), compatible(3));
      g.delegated.put(Pair.of(e, c), Authority.SemanticDistance.unknown("Unavailable"));
      g.delegated.put(Pair.of(e, last), new Authority.SemanticDistance(Authority.DistanceStatus.INCOMPATIBLE, -1, List.of()));
      var matcher = new SemanticMatcher(g);
      for (var targetOrder : permutations(List.of(a, d, e))) {
        g.traits.put(f.targetOwner(), targetOrder);
        for (var candidateOrder : permutations(List.of(b, c, last))) {
          g.traits.put(f.candidateOwner(), candidateOrder);
          assertEquals(path.equals("traits") ? 6 : 7,
              matcher.semanticDistance(f.target(), f.candidate()), path + targetOrder + candidateOrder);
        }
      }
    }
  }

  @Test void equalDistanceAlternativesCannotCollectivelyAuthorizeTooManyExtraTraits() {
    for (String path : tiePaths()) {
      var f = tieFixture(path); var g = f.graph();
      var a = predicate("A"); var b = predicate("B"); var c = predicate("C");
      g.traits.put(f.targetOwner(), List.of(a));
      g.delegated.put(Pair.of(a, b), compatible(2)); g.delegated.put(Pair.of(a, c), compatible(2));
      var matcher = new SemanticMatcher(g);
      for (var order : permutations(List.of(b, c))) {
        g.traits.put(f.candidateOwner(), order);
        int actual = matcher.semanticDistance(f.target(), f.candidate());
        if (path.equals("traits")) assertTrue(actual < 0, path);
        else assertEquals(13, actual, path);
      }
    }
  }

  @Test void rejectedOrUnsupportedEdgesCannotCoverAnExtraAlongsideACompatibleAlternative() {
    for (String path : tiePaths()) {
      for (var status : List.of(Authority.DistanceStatus.UNKNOWN, Authority.DistanceStatus.INCOMPATIBLE,
          Authority.DistanceStatus.UNSUPPORTED)) {
        var f = tieFixture(path); var g = f.graph();
        var a = predicate("A"); var b = predicate("B"); var c = predicate("C");
        g.traits.put(f.targetOwner(), List.of(a)); g.parents.put(c, List.of(a));
        g.delegated.put(Pair.of(a, b), compatible(2));
        g.delegated.put(Pair.of(a, c), new Authority.SemanticDistance(status, -1, List.of()));
        var matcher = new SemanticMatcher(g);
        for (var order : permutations(List.of(b, c))) {
          g.traits.put(f.candidateOwner(), order);
          int actual = matcher.semanticDistance(f.target(), f.candidate());
          if (path.equals("traits")) assertTrue(actual < 0, path + status);
          else assertEquals(13, actual, path + status);
        }
      }
    }
  }

  @Test void unsupportedTiesPreserveOrdinaryReverseTraitRestrictionsInEveryPath() {
    for (String path : tiePaths()) {
      var f = tieFixture(path); var g = f.graph();
      var a = predicate("A"); var b = predicate("B"); var c = predicate("C");
      g.traits.put(f.targetOwner(), List.of(a));
      g.parents.put(b, List.of(a)); g.parents.put(c, List.of(a));
      g.delegated.put(Pair.of(a, b), Authority.SemanticDistance.unsupported());
      var matcher = new SemanticMatcher(g);
      for (var order : permutations(List.of(b, c))) {
        g.traits.put(f.candidateOwner(), order);
        int actual = matcher.semanticDistance(f.target(), f.candidate());
        if (path.equals("traits")) assertTrue(actual < 0, path);
        else assertEquals(21, actual, path);
      }
    }
  }

  @Test void missingRequiredEvidenceCannotBeMaskedByTiesForAnotherRequirement() {
    for (String path : tiePaths()) {
      var f = tieFixture(path); var g = f.graph();
      var a = predicate("A"); var d = predicate("D"); var b = predicate("B"); var c = predicate("C");
      g.delegated.put(Pair.of(a, b), compatible(2)); g.delegated.put(Pair.of(a, c), compatible(2));
      g.delegated.put(Pair.of(d, b), Authority.SemanticDistance.unknown("Unavailable"));
      g.delegated.put(Pair.of(d, c), new Authority.SemanticDistance(Authority.DistanceStatus.INCOMPATIBLE, -1, List.of()));
      var matcher = new SemanticMatcher(g);
      for (var targetOrder : permutations(List.of(a, d))) {
        g.traits.put(f.targetOwner(), targetOrder);
        for (var candidateOrder : permutations(List.of(b, c))) {
          g.traits.put(f.candidateOwner(), candidateOrder);
          assertTrue(matcher.semanticDistance(f.target(), f.candidate()) < 0, path);
        }
      }
    }
  }

  private record TieFixture(FakeOperations graph, Concept target, Concept candidate,
      Concept targetOwner, Concept candidateOwner) {}

  private static TieFixture tieFixture(String path) {
    var g = new FakeOperations(); var target = concept("Target"); var candidate = concept("Candidate");
    var head = concept("Head"); g.heads.put(target, head); g.heads.put(candidate, head);
    if (path.equals("traits")) return new TieFixture(g, target, candidate, target, candidate);
    var tf = concept("TargetFiller"); var cf = concept("CandidateFiller");
    setFiller(g, path, target, tf); setFiller(g, path, candidate, cf); g.parents.put(cf, List.of(tf));
    return new TieFixture(g, target, candidate, tf, cf);
  }

  private static List<String> tiePaths() {
    return List.of("traits", "directInherent", "inherent", "goal", "cooccurrent", "causant", "caused",
        "adjacent", "compresent", "relativeTo");
  }

  private static Authority.SemanticDistance compatible(int distance) {
    return new Authority.SemanticDistance(Authority.DistanceStatus.COMPATIBLE, distance, List.of());
  }

  private static <T> List<List<T>> permutations(List<T> values) {
    if (values.isEmpty()) return List.of(List.of());
    var result = new ArrayList<List<T>>();
    for (int i = 0; i < values.size(); i++) {
      var remainder = new ArrayList<>(values); var first = remainder.remove(i);
      for (var tail : permutations(remainder)) {
        var order = new ArrayList<T>(); order.add(first); order.addAll(tail); result.add(order);
      }
    }
    return result;
  }

  private static void setFiller(FakeOperations g, String path, Concept owner, Concept filler) {
    if (path.equals("directInherent")) g.directInherent.put(owner, filler);
    else if (path.equals("inherent")) g.inherent.put(owner, filler);
    else g.fillers.computeIfAbsent(path, ignored -> new HashMap<>()).put(owner, filler);
  }

  private static Concept concept(String name) {
    var ret = new ConceptImpl();
    ret.setUrn("test:" + name);
    ret.setNamespace("test");
    ret.setName(name);
    ret.setReferenceName(name.toLowerCase());
    return ret;
  }

  private static Concept predicate(String name) {
    Concept ret = concept(name);
    ret.getType().add(SemanticType.PREDICATE);
    return ret;
  }

  private static final class FakeOperations implements SemanticMatcher.Operations {
    private final Map<Concept, Collection<Concept>> parents = new HashMap<>();
    private final Map<Concept, Concept> inherent = new HashMap<>();
    private final Map<Concept, Concept> directInherent = new HashMap<>();
    private final Map<Concept, Concept> heads = new HashMap<>();
    private long revision;
    private final Map<String, Map<Concept, Concept>> fillers = new HashMap<>();
    private final List<Pair<Concept, Concept>> distanceCalls = new ArrayList<>();
    private final Map<Concept,Collection<Concept>> traits = new HashMap<>();
    private final Map<Pair<Concept,Concept>,org.integratedmodelling.klab.api.services.Authority.SemanticDistance> delegated = new HashMap<>();
    @Override public boolean hasConfiguredAuthorities() { return !delegated.isEmpty(); }
    @Override public org.integratedmodelling.klab.api.services.Authority.SemanticDistance authorityDistance(Concept target,Concept candidate) {
      distanceCalls.add(Pair.of(target, candidate));
      return delegated.getOrDefault(Pair.of(target,candidate),org.integratedmodelling.klab.api.services.Authority.SemanticDistance.unsupported());
    }

    @Override
    public Collection<Concept> traits(Semantics concept) {
      return traits.getOrDefault(concept.asConcept(),List.of());
    }

    @Override
    public boolean hasTrait(Semantics concept, Concept trait) {
      return traits(concept).stream().anyMatch(candidate -> is(candidate, trait));
    }

    @Override
    public Collection<Concept> roles(Semantics concept) {
      return List.of();
    }

    @Override
    public boolean hasRole(Semantics concept, Concept role) {
      return false;
    }

    @Override
    public Concept directInherent(Semantics concept) {
      return directInherent.get(concept.asConcept());
    }

    @Override
    public Concept inherent(Semantics concept) {
      return inherent.get(concept.asConcept());
    }

    @Override
    public Concept goal(Semantics concept) {
      return fillers.getOrDefault("goal", Map.of()).get(concept.asConcept());
    }

    @Override
    public Concept cooccurrent(Semantics concept) {
      return fillers.getOrDefault("cooccurrent", Map.of()).get(concept.asConcept());
    }

    @Override
    public Concept causant(Semantics concept) {
      return fillers.getOrDefault("causant", Map.of()).get(concept.asConcept());
    }

    @Override
    public Concept caused(Semantics concept) {
      return fillers.getOrDefault("caused", Map.of()).get(concept.asConcept());
    }

    @Override
    public Concept adjacent(Semantics concept) {
      return fillers.getOrDefault("adjacent", Map.of()).get(concept.asConcept());
    }

    @Override
    public Concept compresent(Semantics concept) {
      return fillers.getOrDefault("compresent", Map.of()).get(concept.asConcept());
    }

    @Override
    public Concept relativeTo(Semantics concept) {
      return fillers.getOrDefault("relativeTo", Map.of()).get(concept.asConcept());
    }

    @Override
    public Pair<Concept, List<SemanticType>> splitOperators(Semantics concept) {
      return Pair.of(concept.asConcept(), List.of());
    }

    @Override
    public Concept withoutModifiers(Concept concept, Scope scope) {
      return heads.getOrDefault(concept, concept);
    }

    @Override
    public boolean is(Semantics concept, Semantics other) {
      return SemanticMatcher.shortestAssertedDistance(concept, other, this::parents) >= 0;
    }

    @Override
    public Collection<Concept> parents(Semantics concept) {
      return parents.getOrDefault(concept.asConcept(), List.of());
    }

    @Override
    public Scope serviceScope() {
      return null;
    }

    @Override
    public long knowledgeRevision() {
      return revision;
    }
  }
  @Test void independentEquivalentAlternativeTieCannotChangeTopLevelAcceptanceByOrder() {
    var g=new FakeOperations();var a=predicate("A");var b=predicate("B");
    var target=concept("Target");var candidate=concept("Candidate");var head=concept("Head");
    g.heads.put(target,head);g.heads.put(candidate,head);g.traits.put(target,List.of(a));
    g.delegated.put(Pair.of(a,b),new Authority.SemanticDistance(Authority.DistanceStatus.COMPATIBLE,0,List.of("Equivalent")));
    var matcher=new SemanticMatcher(g);
    g.traits.put(candidate,List.of(a,b));int first=matcher.semanticDistance(target,candidate);
    g.traits.put(candidate,List.of(b,a));int second=matcher.semanticDistance(target,candidate);
    assertEquals(first,second,"Reordering the same candidate identity set must not change compatibility");
  }

  @Test void independentEquivalentAlternativeTieCannotChangeAnyFillerScoreByOrder() {
    var firstScores=new java.util.LinkedHashMap<String,Integer>();
    var secondScores=new java.util.LinkedHashMap<String,Integer>();
    for(var path:List.of("directInherent","inherent","goal","cooccurrent","causant","caused","adjacent","compresent","relativeTo")) {
      var g=new FakeOperations();var a=predicate("A");var b=predicate("B");
      var target=concept("Target");var candidate=concept("Candidate");var head=concept("Head");
      var tf=concept("TargetFiller");var cf=concept("CandidateFiller");
      g.heads.put(target,head);g.heads.put(candidate,head);setFiller(g,path,target,tf);setFiller(g,path,candidate,cf);
      g.parents.put(cf,List.of(tf));g.traits.put(tf,List.of(a));
      g.delegated.put(Pair.of(a,b),new Authority.SemanticDistance(Authority.DistanceStatus.COMPATIBLE,0,List.of("Equivalent")));
      var matcher=new SemanticMatcher(g);g.traits.put(cf,List.of(a,b));int first=matcher.semanticDistance(target,candidate);
      g.traits.put(cf,List.of(b,a));int second=matcher.semanticDistance(target,candidate);
      firstScores.put(path,first);secondScores.put(path,second);
    }
    assertEquals(firstScores,secondScores,"All nine paths must be independent of identity iteration order");
  }
}
