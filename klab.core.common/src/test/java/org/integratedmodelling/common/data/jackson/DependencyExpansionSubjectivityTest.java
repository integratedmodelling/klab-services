package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.SemanticRole;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.junit.jupiter.api.Test;

class DependencyExpansionSubjectivityTest {
  private static KimConceptImpl predicate(String name, boolean subjective) {
    var node = new KimConceptImpl();
    node.setName(name);
    node.setType(EnumSet.of(SemanticType.PREDICATE, SemanticType.ROLE));
    node.setDeclaredSubjective(subjective);
    if (subjective) node.getType().add(SemanticType.SUBJECTIVE);
    return node;
  }

  private static KimObservableImpl observable(KimConceptImpl node) {
    var observable = new KimObservableImpl();
    observable.setSemantics(node);
    observable.setNamespace("owner");
    return observable;
  }

  @Test void replacementUsesCandidateDeclarationInBothDirectionsDespiteStaleAggregate() {
    for (boolean subjective : new boolean[] {false, true}) {
      var source = predicate("test:Source", !subjective);
      source.getType().add(SemanticType.ANY);
      var candidate = predicate("test:Candidate", subjective);
      // Deliberately contradictory cached aggregate: only declaration provenance is authoritative.
      if (subjective) candidate.getType().remove(SemanticType.SUBJECTIVE);
      else candidate.getType().add(SemanticType.SUBJECTIVE);
      var candidateFlags = EnumSet.copyOf(candidate.getType());
      var original = observable(source);
      var result = DependencyExpansion.expand(original, name -> source, request ->
          new DependencyExpansion.Classification(DependencyExpansion.Status.SUCCESS, List.of(candidate))).getFirst();
      var replaced = (KimConceptImpl) result.getSemantics();
      assertEquals(subjective, replaced.getDeclaredSubjective());
      assertEquals(subjective, ExpressionStatus.isSubjective(result));
      replaced.resetDefinition();
      assertEquals(subjective, replaced.is(SemanticType.SUBJECTIVE));
      assertEquals(!subjective, source.getDeclaredSubjective());
      assertEquals("test:Source", source.getName());
      assertTrue(source.is(SemanticType.ANY));
      assertFalse(original.isOptional());
      assertEquals(candidateFlags, candidate.getType());
      assertEquals(subjective, candidate.getDeclaredSubjective());
    }
  }

  @Test void detachedCopyThenTransportThenRemovalRecomputesFromPreservedDeclaration() throws Exception {
    var head = predicate("test:Head", false);
    head.setInherent(predicate("test:View", true));
    head.resetDefinition();
    var original = observable(head);
    var copied = new KimObservableImpl(original);
    var mapper = JacksonConfiguration.newObjectMapper();
    var restored = (KimObservableImpl) mapper.readValue(mapper.writeValueAsString(copied), KimObservable.class);
    var restoredHead = (KimConceptImpl) restored.getSemantics();
    assertEquals(false, restoredHead.getDeclaredSubjective());
    assertEquals(true, ((KimConceptImpl) restoredHead.getInherent()).getDeclaredSubjective());
    assertTrue(ExpressionStatus.isSubjective(restored));
    var removed = restoredHead.removeComponents(SemanticRole.INHERENT);
    assertFalse(ExpressionStatus.isSubjective(removed));
    assertFalse(removed.is(SemanticType.SUBJECTIVE));
    assertTrue(ExpressionStatus.isSubjective(original));
    assertNotSame(head.getInherent(), copied.getSemantics().getInherent());
    assertNotNull(restoredHead.getInherent());
  }

  @Test void sameRoleOccurrencesHaveIndependentSubjectivityAcrossPositionsAndContexts() {
    var identities = new java.util.HashSet<DependencyExpansion.RequestIdentity>();
    for (boolean reverse : new boolean[] {false, true}) {
      var role = predicate("test:Role", false);
      role.getType().add(SemanticType.ANY);
      var bearer = predicate("test:Bearer", false);
      bearer.getRoles().add(role);
      var root = predicate(reverse ? "test:Second" : "test:First", false);
      root.setRelationshipSource(bearer);
      root.setRelationshipTarget(bearer);
      var result = DependencyExpansion.expand(observable(root), name -> predicate(name, false), request -> {
        assertTrue(identities.add(request.identity()));
        boolean subjective = request.identity().path().contains("relationshipSource") != reverse;
        return new DependencyExpansion.Classification(DependencyExpansion.Status.SUCCESS,
            List.of(predicate(subjective ? "test:Private" : "test:Public", subjective)));
      }).getFirst();
      var source = result.getSemantics().getRelationshipSource().getRoles().getFirst();
      var target = result.getSemantics().getRelationshipTarget().getRoles().getFirst();
      assertEquals(!reverse, ExpressionStatus.isSubjective(source));
      assertEquals(reverse, ExpressionStatus.isSubjective(target));
      assertTrue(ExpressionStatus.isSubjective(result));
      assertFalse(ExpressionStatus.isSubjective(root));
      assertEquals("test:Role", role.getName());
    }
    assertEquals(4, identities.size());
  }
}
