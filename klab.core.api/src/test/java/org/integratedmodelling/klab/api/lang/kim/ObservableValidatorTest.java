package org.integratedmodelling.klab.api.lang.kim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.junit.jupiter.api.Test;

class ObservableValidatorTest {

  private final ObservableValidator validator = new ObservableValidator();

  @Test
  void rejectsDuringWithANonEventTarget() {
    var concept = concept(SemanticType.QUALITY);
    concept.setCooccurrent(concept(SemanticType.PROCESS));

    assertError(concept, "during");
  }

  @Test
  void rejectsPresenceOfANonCountableTarget() {
    var concept = concept(SemanticType.QUALITY);
    concept.setSemanticModifier(UnarySemanticOperator.PRESENCE);
    concept.setObservable(concept(SemanticType.QUALITY));

    assertError(concept, "presence of");
  }

  @Test
  void rejectsLogicalOperandsWithDifferentFundamentalTypes() {
    var concept = concept(SemanticType.INTERSECTION);
    concept.setExpressionType(KimConcept.Expression.INTERSECTION);
    concept.setOperands(List.of(concept(SemanticType.SUBJECT), concept(SemanticType.EVENT)));

    assertError(concept, "joined by 'and'");
  }

  @Test
  void rejectsBetweenOnANonRelationship() {
    var concept = concept(SemanticType.SUBJECT);
    concept.setRelationshipSource(concept(SemanticType.SUBJECT));
    concept.setRelationshipTarget(concept(SemanticType.SUBJECT));

    assertError(concept, "between");
  }

  @Test
  void rejectsEachOnANonCountableConcept() {
    var concept = concept(SemanticType.QUALITY);
    concept.setCollective(true);

    assertError(concept, "each");
  }

  @Test
  void acceptsTheValidFormsOfAllInitialRules() {
    var eventDuring = concept(SemanticType.QUALITY);
    eventDuring.setCooccurrent(concept(SemanticType.EVENT, SemanticType.COUNTABLE));

    var presence = concept(SemanticType.QUALITY);
    presence.setSemanticModifier(UnarySemanticOperator.PRESENCE);
    presence.setObservable(concept(SemanticType.SUBJECT, SemanticType.COUNTABLE));

    var union = concept(SemanticType.UNION);
    union.setExpressionType(KimConcept.Expression.UNION);
    union.setOperands(List.of(concept(SemanticType.EVENT), concept(SemanticType.EVENT)));

    var relationship = concept(SemanticType.RELATIONSHIP, SemanticType.COUNTABLE);
    relationship.setRelationshipSource(concept(SemanticType.SUBJECT, SemanticType.COUNTABLE));
    relationship.setRelationshipTarget(concept(SemanticType.SUBJECT, SemanticType.COUNTABLE));
    relationship.setCollective(true);

    assertTrue(validator.validateConcept(eventDuring).isEmpty());
    assertTrue(validator.validateConcept(presence).isEmpty());
    assertTrue(validator.validateConcept(union).isEmpty());
    assertTrue(validator.validateConcept(relationship).isEmpty());
  }

  @Test
  void exposesTheRulesAsImmutableReviewableData() {
    assertEquals(1, ObservableValidator.clauseRules().size());
    assertEquals(1, ObservableValidator.unaryOperatorRules().size());
    assertEquals(2, ObservableValidator.logicalOperatorRules().size());
    assertEquals(1, ObservableValidator.conceptAttributeRules().size());
    assertEquals(1, ObservableValidator.relationshipSpecializationRules().size());
    assertThrows(
        UnsupportedOperationException.class, () -> ObservableValidator.clauseRules().clear());
  }

  private void assertError(KimConcept concept, String messageFragment) {
    var notifications = validator.validateConcept(concept);
    assertEquals(1, notifications.size());
    assertTrue(notifications.getFirst().getMessage().contains(messageFragment));
  }

  private static KimConceptImpl concept(SemanticType... types) {
    var concept = new KimConceptImpl();
    concept.setType(EnumSet.copyOf(List.of(types)));
    if (types.length == 1 && SemanticType.FUNDAMENTAL_TYPES.contains(types[0])) {
      concept.setFundamentalType(types[0]);
    }
    return concept;
  }
}
