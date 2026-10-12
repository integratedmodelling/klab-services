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
  void subjectiveClauseFillersPropagateWhileAbstractFillersDoNot() {
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
      assertEquals(flag == SemanticType.SUBJECTIVE, ExpressionStatus.evaluate(bearer).contains(flag));
      // A head's own declared status is retained even when it has clauses.
      bearer.getType().add(flag);
      assertTrue(ExpressionStatus.evaluate(bearer).contains(flag));
    }
  }

  @Test
  void unaryBoundaryStopsOnlyAbstractOperandStatus() {
    for (var flag : EnumSet.of(SemanticType.ABSTRACT, SemanticType.SUBJECTIVE)) {
      for (var operator : UnarySemanticOperator.values()) {
        var operand = leaf("Thing", SemanticType.SUBJECT, flag);
        var transformed = new KimConceptImpl();
        transformed.setObservable(operand);
        transformed.setSemanticModifier(operator);
        transformed.setComparisonConcept(operand);
        assertEquals(flag == SemanticType.SUBJECTIVE, ExpressionStatus.evaluate(transformed).contains(flag));
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

  @Test
  void everySemanticBoundaryPropagatesOnlySubjectivity() {
    java.util.List<java.util.function.BiConsumer<KimConceptImpl, KimConceptImpl>> boundaries = java.util.List.of(
        KimConceptImpl::setObservable, KimConceptImpl::setInherent, KimConceptImpl::setGoal,
        KimConceptImpl::setCausant, KimConceptImpl::setCaused, KimConceptImpl::setCompresent,
        KimConceptImpl::setCooccurrent, KimConceptImpl::setAdjacent,
        KimConceptImpl::setRelationshipSource, KimConceptImpl::setRelationshipTarget,
        KimConceptImpl::setComparisonConcept, KimConceptImpl::setParent,
        (p, c) -> p.getTraits().add(c), (p, c) -> p.getRoles().add(c),
        (p, c) -> p.getOperands().add(c),
        (p, c) -> p.getValueOperators().add(org.integratedmodelling.klab.api.collections.Pair.of(
            org.integratedmodelling.klab.api.lang.ValueOperator.values()[0], java.util.Map.of("value", java.util.List.of(c)))));
    for (var boundary : boundaries) {
      var outer = leaf("Outer", SemanticType.SUBJECT);
      var middle = leaf("Middle", SemanticType.SUBJECT);
      var inner = leaf("Inner", SemanticType.SUBJECT);
      boundary.accept(outer, middle);
      boundary.accept(middle, inner);
      assertFalse(ExpressionStatus.evaluate(outer).contains(SemanticType.SUBJECTIVE));
      inner.getType().add(SemanticType.SUBJECTIVE);
      var before = EnumSet.copyOf(outer.getType());
      assertTrue(ExpressionStatus.evaluate(outer).contains(SemanticType.SUBJECTIVE));
      assertEquals(before, outer.getType());
      assertFalse(ExpressionStatus.evaluate(outer).contains(SemanticType.ABSTRACT));
    }
  }

  @Test
  void observableDescriptionAndDefaultValuesAreTransitive() {
    for (boolean defaultValue : new boolean[] {false, true}) {
      var observable = new org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl();
      observable.setSemantics(leaf("Thing", SemanticType.SUBJECT));
      var value = leaf("Value", SemanticType.ATTRIBUTE);
      if (defaultValue) observable.setDefaultValue(java.util.List.of(value));
      else observable.setValue(java.util.Map.of("description", value));
      assertFalse(ExpressionStatus.isSubjective(observable));
      value.getType().add(SemanticType.SUBJECTIVE);
      assertTrue(ExpressionStatus.isSubjective(observable));
    }
  }

  @Test
  void resolvedDescriptionValuesProjectWithoutMutatingCanonicalSelectors() {
    var canonical = new ConceptImpl();
    canonical.getType().add(SemanticType.ANY);
    var description = new org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl();
    description.setSemantics(leaf("Thing", SemanticType.SUBJECT));
    description.setValue(java.util.Map.of("nested", leaf("Value", SemanticType.ATTRIBUTE)));
    var value = new ConceptImpl();
    assertFalse(canonical.withObservableStatus(description, name -> value).is(SemanticType.SUBJECTIVE));
    value.getType().add(SemanticType.SUBJECTIVE);
    var projected = canonical.withObservableStatus(description, name -> name.equals("test:Value") ? value : canonical);
    assertTrue(projected.is(SemanticType.SUBJECTIVE));
    assertTrue(projected.is(SemanticType.ANY));
    assertFalse(canonical.is(SemanticType.SUBJECTIVE));
    assertTrue(ObservableImpl.promote(projected, null).is(SemanticType.SUBJECTIVE));
  }

  private KimConceptImpl leaf(String name, SemanticType... flags) {
    var result = new KimConceptImpl();
    result.setName("test:" + name);
    result.setType(EnumSet.noneOf(SemanticType.class));
    result.getType().addAll(java.util.List.of(flags));
    return result;
  }
}
