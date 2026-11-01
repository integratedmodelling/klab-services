package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.junit.jupiter.api.Test;

class AboutIntegrationTest {
  private static KimConceptImpl node(String name, boolean subjective) {
    var ret = new KimConceptImpl(); ret.setName(name);
    ret.setType(EnumSet.of(SemanticType.PREDICATE, SemanticType.ROLE));
    ret.setDeclaredSubjective(subjective);
    if (subjective) ret.getType().add(SemanticType.SUBJECTIVE);
    return ret;
  }
  private static KimObservableImpl observable(KimConceptImpl head) {
    var ret = new KimObservableImpl(); ret.setSemantics(head); ret.setNamespace("owner"); return ret;
  }
  private static DependencyExpansion.Classification success(KimConcept candidate) {
    return new DependencyExpansion.Classification(DependencyExpansion.Status.SUCCESS, List.of(candidate));
  }

  @Test void targetSubjectivitySurvivesCopyAndTransportAndClearsOnlyOnRemoval() throws Exception {
    var target = node("test:Target", true);
    var head = node("test:Head", false); head.getAbout().add(target); head.resetDefinition();
    assertTrue(ExpressionStatus.isSubjective(head));
    var copied = new KimObservableImpl(observable(head));
    assertNotSame(target, copied.getSemantics().getAbout().getFirst());
    var mapper = JacksonConfiguration.newObjectMapper();
    var restored = mapper.readValue(mapper.writeValueAsString(copied), KimObservable.class);
    var restoredHead = (KimConceptImpl) restored.getSemantics();
    assertTrue(ExpressionStatus.isSubjective(restored));
    assertEquals(false, restoredHead.getDeclaredSubjective());
    assertEquals(true, ((KimConceptImpl) restoredHead.getAbout().getFirst()).getDeclaredSubjective());
    restoredHead.getAbout().clear(); restoredHead.resetDefinition();
    assertFalse(ExpressionStatus.isSubjective(restored));
    assertFalse(restoredHead.is(SemanticType.SUBJECTIVE));
    assertTrue(ExpressionStatus.isSubjective(head));
    assertEquals(1, copied.getSemantics().getAbout().size());
  }

  @Test void sharedTargetsAreDetachedPerOccurrenceBeforeCallbacks() {
    var shared = node("test:Shared", false);
    var head = node("test:Head", false); head.getAbout().addAll(List.of(shared, shared));
    var paths = new ArrayList<List<String>>();
    var copy = head.mapSyntax(List.of(), (path, value) -> {
      if (path.contains("about")) { paths.add(path); value.setName("test:Copy" + path.getLast()); }
    });
    assertEquals(List.of(List.of("about", "0"), List.of("about", "1")), paths);
    assertNotSame(copy.getAbout().get(0), copy.getAbout().get(1));
    assertEquals("test:Shared", shared.getName());
    assertSame(shared, head.getAbout().getFirst());
  }

  @Test void expansionVisitsAboutAndRolePositionsIndependentlyAcrossContexts() {
    var identities = new HashSet<DependencyExpansion.RequestIdentity>();
    for (boolean reverse : List.of(false, true)) {
      var shared = node("test:Selector", false); shared.getType().add(SemanticType.ANY);
      var head = node("test:Head", false);
      head.getAbout().addAll(List.of(shared, shared)); head.getRoles().add(shared);
      var result = DependencyExpansion.expand(observable(head), name -> node(name, false), request -> {
        assertTrue(identities.add(request.identity()));
        assertNotSame(shared, request.predicate());
        boolean subjective = request.identity().path().contains("about") != reverse;
        return success(node(subjective ? "test:Private" : "test:Public", subjective));
      }).getFirst();
      assertEquals(!reverse, ExpressionStatus.isSubjective(result.getSemantics().getAbout().get(0)));
      assertEquals(!reverse, ExpressionStatus.isSubjective(result.getSemantics().getAbout().get(1)));
      assertEquals(reverse, ExpressionStatus.isSubjective(result.getSemantics().getRoles().getFirst()));
      assertTrue(ExpressionStatus.isSubjective(result));
      assertEquals("test:Selector", shared.getName()); assertTrue(shared.is(SemanticType.ANY));
      assertFalse(ExpressionStatus.isSubjective(head));
    }
    assertEquals(6, identities.size());
  }

  @Test void aboutSubstitutionRefreshesDeclaredStatusInBothDirections() {
    for (boolean subjective : List.of(false, true)) {
      var source = node("test:Source", !subjective); source.getType().add(SemanticType.ANY);
      var candidate = node("test:Candidate", subjective);
      if (subjective) candidate.getType().remove(SemanticType.SUBJECTIVE);
      else candidate.getType().add(SemanticType.SUBJECTIVE);
      var head = node("test:Head", false); head.getAbout().add(source);
      var result = DependencyExpansion.expand(observable(head), name -> node(name, false), request -> success(candidate)).getFirst();
      assertEquals("test:Candidate", result.getSemantics().getAbout().getFirst().getName());
      assertEquals(subjective, ExpressionStatus.isSubjective(result));
      assertEquals(subjective, ((KimConceptImpl) result.getSemantics().getAbout().getFirst()).getDeclaredSubjective());
      assertEquals(!subjective, source.getDeclaredSubjective());
      assertEquals("test:Source", source.getName());
    }
  }

  @Test void classificationRejectsCandidatesWhoseAboutClauseWouldBeDiscarded() {
    var candidate = node("test:Candidate", false); candidate.getAbout().add(node("test:Target", false));
    assertThrows(IllegalArgumentException.class, () -> success(candidate));
  }
}
