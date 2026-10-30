package org.integratedmodelling.klab.api.lang.kim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl;
import org.junit.jupiter.api.Test;

class DependencyExpansionTest {
  private static KimConceptImpl concept(String name, SemanticType... types) {
    var ret = new KimConceptImpl();
    ret.setName(name);
    ret.setType(types.length == 0 ? EnumSet.noneOf(SemanticType.class)
        : EnumSet.copyOf(List.of(types)));
    return ret;
  }

  private static KimConceptImpl predicate(String name) {
    return concept(name, SemanticType.PREDICATE, SemanticType.ATTRIBUTE);
  }

  private static KimObservableImpl observable(KimConceptImpl syntax) {
    var ret = new KimObservableImpl();
    ret.setSemantics(syntax);
    ret.setUrn("authored observable in m");
    ret.setUnit("m");
    ret.setNamespace("authored");
    ret.setProjectName("project");
    ret.setServiceId("source-service");
    ret.setOffsetInDocument(17);
    ret.setLength(42);
    ret.setFormalName("input");
    return ret;
  }

  private static DependencyExpansion.Classification success(KimConcept... candidates) {
    return new DependencyExpansion.Classification(DependencyExpansion.Status.SUCCESS, List.of(candidates));
  }

  @Test void cartesianProductIncludesConcreteSelfAndNestedPredicatesWithoutChangingSource() {
    var a = predicate("ns:A");
    a.getType().add(SemanticType.ANY);
    var b = predicate("ns:B");
    var abstractB = predicate("ns:B");
    abstractB.getType().add(SemanticType.ABSTRACT);
    var subject = concept("ns:Subject", SemanticType.SUBJECT, SemanticType.ALL);
    subject.getTraits().add(b);
    var root = concept("ns:Quality", SemanticType.QUALITY);
    root.getTraits().add(a);
    root.setInherent(subject);
    var original = observable(root);
    var declarations = Map.of("ns:A", predicate("ns:A"), "ns:B", abstractB,
        "ns:Subject", subject, "ns:Quality", root);
    var requests = new ArrayList<DependencyExpansion.Request>();
    var result = DependencyExpansion.expand(original, declarations::get, request -> {
      requests.add(request);
      assertEquals(Contextualization.CLASSIFICATION, request.contextualization());
      return request.declaration().getName().equals("ns:A")
          ? success(predicate("ns:A"), predicate("ns:A1"))
          : success(predicate("ns:B1"), predicate("ns:B2"));
    });
    assertEquals(2, requests.size());
    assertEquals(4, result.size());
    assertFalse(requests.stream().filter(r -> r.declaration().getName().equals("ns:A"))
        .findFirst().orElseThrow().declaration().is(SemanticType.ABSTRACT));
    var combinations = new java.util.HashSet<String>();
    for (var expanded : result) {
      assertTrue(expanded.isOptional());
      assertNull(expanded.getUrn());
      assertEquals("m", expanded.getUnit());
      assertEquals("authored", expanded.getNamespace());
      assertEquals("project", expanded.getProjectName());
      assertEquals("source-service", expanded.getServiceId());
      assertEquals(17, expanded.getOffsetInDocument());
      assertEquals(42, expanded.getLength());
      assertEquals("input", expanded.getFormalName());
      var syntax = expanded.getSemantics();
      assertTrue(syntax.getInherent().is(SemanticType.ALL));
      assertFalse(syntax.getTraits().getFirst().is(SemanticType.ANY));
      assertNotSame(subject, syntax.getInherent());
      combinations.add(syntax.getTraits().getFirst().getName() + "/"
          + syntax.getInherent().getTraits().getFirst().getName());
    }
    assertEquals(java.util.Set.of("ns:A/ns:B1", "ns:A/ns:B2", "ns:A1/ns:B1", "ns:A1/ns:B2"), combinations);
    assertFalse(original.isOptional());
    assertEquals("authored observable in m", original.getUrn());
    assertTrue(a.is(SemanticType.ANY));
    assertEquals("ns:B", b.getName());
  }

  @Test void successfulEmptyIsEmptyButDoesNotMaskFailedOrUnknownFactors() {
    var root = concept("ns:Subject", SemanticType.SUBJECT);
    for (var name : List.of("ns:A", "ns:B")) {
      var p = predicate(name); p.getType().add(SemanticType.ANY); root.getTraits().add(p);
    }
    var original = observable(root);
    assertTrue(DependencyExpansion.expand(original, name -> name.equals("ns:Subject") ? root : predicate(name),
        request -> success()).isEmpty());
    for (var status : List.of(DependencyExpansion.Status.FAILED, DependencyExpansion.Status.UNKNOWN)) {
      assertThrows(IllegalStateException.class, () -> DependencyExpansion.expand(original,
          name -> name.equals("ns:Subject") ? root : predicate(name),
          request -> request.predicate().getName().equals("ns:A") ? success()
              : new DependencyExpansion.Classification(status, List.of())));
    }
    assertThrows(NullPointerException.class, () -> DependencyExpansion.expand(original,
        name -> name.equals("ns:Subject") ? root : predicate(name), request -> null));
  }

  @Test void noExpansionPreservesRequiredDependencyAndDoesNotClassify() {
    var root = concept("ns:Subject", SemanticType.SUBJECT);
    var original = observable(root);
    assertSame(original, DependencyExpansion.expand(original, name -> root,
        request -> { throw new AssertionError(); }).getFirst());
    assertFalse(original.isOptional());
  }

  @Test void invalidClassificationCannotMasqueradeAsConcreteClosure() {
    var abstractCandidate = predicate("ns:Abstract");
    abstractCandidate.getType().add(SemanticType.ABSTRACT);
    assertThrows(IllegalArgumentException.class, () -> success(abstractCandidate));
    assertThrows(IllegalArgumentException.class, () -> new DependencyExpansion.Classification(
        DependencyExpansion.Status.FAILED, List.of(predicate("ns:X"))));
  }

  @Test void candidateNamespaceDoesNotReplaceAuthoredProvenanceOrMutateCanonicalConcept() {
    var source = predicate("ns:A"); source.getType().add(SemanticType.ANY);
    source.setNamespace("authored");
    var candidate = predicate("ns:Concrete"); candidate.setNamespace("private-subjective");
    candidate.getType().add(SemanticType.SUBJECTIVE);
    var original = observable(source);
    var expanded = DependencyExpansion.expand(original, name -> predicate(name), request -> success(candidate)).getFirst();
    assertEquals("authored", expanded.getSemantics().getNamespace());
    assertEquals("authored", expanded.getNamespace());
    assertEquals("private-subjective", candidate.getNamespace());
    assertFalse(candidate.is(SemanticType.ANY));
    assertEquals("ns:A", source.getName());
  }

  @Test void duplicateClosureEntriesDoNotDuplicateDependencies() {
    var source = predicate("ns:A"); source.getType().add(SemanticType.ANY);
    assertEquals(1, DependencyExpansion.expand(observable(source), name -> predicate(name),
        request -> success(predicate("ns:A"), predicate("ns:A"))).size());
  }

  @Test void everyClauseAndValueOperatorSurvivesSubstitution() {
    var p = predicate("ns:A"); p.getType().add(SemanticType.ANY);
    var root = concept("ns:Quality", SemanticType.QUALITY);
    root.setInherent(p); root.setGoal(p); root.setCausant(p); root.setCaused(p);
    root.setCompresent(p); root.setComparisonConcept(p); root.setRelationshipSource(p);
    root.setRelationshipTarget(p); root.setCooccurrent(p); root.setAdjacent(p);
    root.getRoles().add(p); root.getOperands().add(p);
    root.setObservable(p);
    root.getValueOperators().add(org.integratedmodelling.klab.api.collections.Pair.of(
        org.integratedmodelling.klab.api.lang.ValueOperator.BY, p));
    var original = observable(root);
    original.getValueOperators().add(org.integratedmodelling.klab.api.collections.Pair.of(
        org.integratedmodelling.klab.api.lang.ValueOperator.WHERE, p));
    int[] calls = {0};
    var expanded = DependencyExpansion.expand(original,
        name -> name.equals("ns:Quality") ? root : predicate(name), request -> {
          calls[0]++; return success(predicate("ns:Concrete"));
        }).getFirst();
    assertEquals(15, calls[0]);
    var s = expanded.getSemantics();
    for (var clause : List.of(s.getInherent(), s.getGoal(), s.getCausant(), s.getCaused(),
        s.getCompresent(), s.getComparisonConcept(), s.getRelationshipSource(),
        s.getRelationshipTarget(), s.getCooccurrent(), s.getAdjacent(), s.getObservable(),
        s.getRoles().getFirst(), s.getOperands().getFirst(),
        (KimConcept) s.getValueOperators().getFirst().getSecond(),
        (KimConcept) expanded.getValueOperators().getFirst().getSecond())) {
      assertEquals("ns:Concrete", clause.getName());
      assertNotSame(p, clause);
    }
    assertEquals("ns:A", p.getName());
    assertSame(p, original.getValueOperators().getFirst().getSecond());
  }

  @Test void completeProductIsNotTruncated() {
    var root = concept("ns:Subject", SemanticType.SUBJECT);
    for (var name : List.of("ns:A", "ns:B")) {
      var p = predicate(name); p.getType().add(SemanticType.ANY); root.getTraits().add(p);
    }
    var candidates = new ArrayList<KimConcept>();
    for (int i = 0; i < 33; i++) candidates.add(predicate("ns:C" + i));
    assertEquals(1089, DependencyExpansion.expand(observable(root),
        name -> name.equals("ns:Subject") ? root : predicate(name),
        request -> new DependencyExpansion.Classification(DependencyExpansion.Status.SUCCESS, candidates)).size());
  }

  private static KimObservableImpl relationshipWithRepeatedRole() {
    var role = concept("ns:Participant", SemanticType.PREDICATE, SemanticType.ROLE, SemanticType.ABSTRACT);
    var bearer = concept("ns:Patch", SemanticType.SUBJECT);
    bearer.getRoles().add(role);
    var relationship = concept("ns:Connection", SemanticType.RELATIONSHIP);
    // Deliberately share the entire endpoint syntax: object identity must not couple the choices.
    relationship.setRelationshipSource(bearer); relationship.setRelationshipTarget(bearer);
    return observable(relationship);
  }

  private static KimConcept declaration(String name) {
    return name.equals("ns:Participant")
        ? concept(name, SemanticType.PREDICATE, SemanticType.ROLE, SemanticType.ABSTRACT)
        : concept(name, name.equals("ns:Patch") ? SemanticType.SUBJECT : SemanticType.RELATIONSHIP);
  }

  @Test void repeatedRoleAtTwoBearerPositionsProducesAllFourCombinations() {
    var original = relationshipWithRepeatedRole();
    var requests = new ArrayList<DependencyExpansion.Request>();
    var result = DependencyExpansion.expand(original, DependencyExpansionTest::declaration, request -> {
      requests.add(request);
      return success(concept("ns:R1", SemanticType.PREDICATE, SemanticType.ROLE),
          concept("ns:R2", SemanticType.PREDICATE, SemanticType.ROLE));
    });
    var combinations = new java.util.HashSet<String>();
    for (var output : result) combinations.add(
        output.getSemantics().getRelationshipSource().getRoles().getFirst().getName() + "/"
            + output.getSemantics().getRelationshipTarget().getRoles().getFirst().getName());
    assertEquals(java.util.Set.of("ns:R1/ns:R1", "ns:R1/ns:R2", "ns:R2/ns:R1", "ns:R2/ns:R2"), combinations);
    assertEquals(2, requests.size());
    assertEquals(List.of("semantics", "relationshipSource", "roles", "0"), requests.getFirst().identity().path());
    assertEquals(List.of("semantics", "relationshipTarget", "roles", "0"), requests.getLast().identity().path());
    assertNotEquals(requests.getFirst().identity(), requests.getLast().identity());
    assertEquals(requests.getFirst().identity().attemptId(), requests.getLast().identity().attemptId());
    assertThrows(UnsupportedOperationException.class, () -> requests.getFirst().identity().path().clear());
    assertNotSame(original, requests.getFirst().originalObservable());
    assertNotSame(requests.getFirst().originalObservable(), requests.getLast().originalObservable());
    assertEquals("m", requests.getFirst().originalObservable().getUnit());
  }

  @Test void classificationUsesEndpointContextAndCannotReuseAnotherAttemptsResult() {
    var cache = new java.util.HashMap<DependencyExpansion.RequestIdentity, DependencyExpansion.Classification>();
    for (var context : List.of("ns:FirstContext", "ns:SecondContext")) {
      var original = relationshipWithRepeatedRole();
      ((KimConceptImpl) original.getSemantics()).setName(context);
      var result = DependencyExpansion.expand(original, DependencyExpansionTest::declaration, request ->
          cache.computeIfAbsent(request.identity(), key -> {
            assertEquals("ns:Patch", request.originalObservable().getSemantics().getRelationshipSource().getName());
            assertEquals("ns:Patch", request.originalObservable().getSemantics().getRelationshipTarget().getName());
            var endpoint = key.path().contains("relationshipSource") ? "Source" : "Target";
            return success(concept(request.originalObservable().getSemantics().getName() + endpoint,
                SemanticType.PREDICATE, SemanticType.ROLE));
          }));
      assertEquals(1, result.size());
      assertEquals(context + "Source", result.getFirst().getSemantics().getRelationshipSource().getRoles().getFirst().getName());
      assertEquals(context + "Target", result.getFirst().getSemantics().getRelationshipTarget().getRoles().getFirst().getName());
    }
    assertEquals(4, cache.size());
  }
}
