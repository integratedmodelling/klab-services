package org.integratedmodelling.common.knowledge;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.kim.ExpressionStatus;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.junit.jupiter.api.Test;

class ExpressionStatusTest {
  @Test
  void directAttributesPropagateButClauseFillersDoNot() {
    for (var flag : EnumSet.of(SemanticType.ABSTRACT, SemanticType.SUBJECTIVE)) {
      var attribute = leaf("Attribute", SemanticType.ATTRIBUTE, flag);
      var bearer = leaf("Thing", SemanticType.SUBJECT);
      bearer.getTraits().add(attribute);
      assertTrue(ExpressionStatus.evaluate(bearer).contains(flag));
      bearer.getTraits().clear();
      bearer.setInherent(attribute);
      bearer.setGoal(attribute);
      bearer.setCompresent(attribute);
      bearer.setCausant(attribute);
      bearer.setCaused(attribute);
      bearer.setCooccurrent(attribute);
      bearer.setAdjacent(attribute);
      bearer.setRelationshipSource(attribute);
      bearer.setRelationshipTarget(attribute);
      assertFalse(ExpressionStatus.evaluate(bearer).contains(flag));
      // A head's own declared status is retained even when it has clauses.
      bearer.getType().add(flag);
      assertTrue(ExpressionStatus.evaluate(bearer).contains(flag));
    }
  }

  @Test
  void unaryBoundaryStopsOperandStatusButAllowsOuterAttributes() {
    for (var flag : EnumSet.of(SemanticType.ABSTRACT, SemanticType.SUBJECTIVE)) {
      for (var operator : UnarySemanticOperator.values()) {
        var operand = leaf("Thing", SemanticType.SUBJECT, flag);
        var transformed = new KimConceptImpl();
        transformed.setObservable(operand);
        transformed.setSemanticModifier(operator);
        transformed.setComparisonConcept(operand);
        assertFalse(ExpressionStatus.evaluate(transformed).contains(flag));
        transformed.getTraits().add(leaf("Attribute", SemanticType.ATTRIBUTE, flag));
        assertTrue(ExpressionStatus.evaluate(transformed).contains(flag));
      }
    }
  }

  @Test
  void runtimeProjectionUsesResolvedDeclarationsAndSurvivesPromotionWithoutMutatingThem() {
    var attribute = new ConceptImpl();
    attribute.setAbstract(true);
    attribute.getType().add(SemanticType.SUBJECTIVE);
    var canonical = new ConceptImpl();
    canonical.setName("Thing");
    canonical.setNamespace("test");
    canonical.setUrn("test:Thing");
    canonical.setReferenceName("test_thing");
    var syntax = leaf("Thing", SemanticType.SUBJECT);
    syntax.getTraits().add(leaf("Attribute", SemanticType.ATTRIBUTE));
    var projected = canonical.withExpressionStatus(syntax,
        name -> name.equals("test:Attribute") ? attribute : canonical);
    assertTrue(projected.isAbstract());
    assertTrue(projected.is(SemanticType.ABSTRACT));
    assertTrue(projected.is(SemanticType.SUBJECTIVE));
    var observable = ObservableImpl.promote(projected, null);
    assertTrue(new ObservableImpl(observable).isAbstract());
    assertTrue(observable.is(SemanticType.SUBJECTIVE));
    assertFalse(canonical.isAbstract());
    assertFalse(canonical.is(SemanticType.SUBJECTIVE));
  }

  private KimConceptImpl leaf(String name, SemanticType... flags) {
    var result = new KimConceptImpl();
    result.setName("test:" + name);
    result.setType(EnumSet.noneOf(SemanticType.class));
    result.getType().addAll(java.util.List.of(flags));
    return result;
  }
}
