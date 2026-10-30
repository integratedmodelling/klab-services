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

  @Test void ofRejectsIndependentSubstantialHeadsButDoesNotRequireADependentOperand() {
    var tree=concept(SemanticType.SUBJECT,SemanticType.COUNTABLE);
    for(var type:List.of(SemanticType.SUBJECT,SemanticType.AGENT,SemanticType.EVENT)) {
      var independent=concept(type,SemanticType.COUNTABLE);independent.setInherent(tree);
      assertError(independent,"INHERENCY_HEAD_INDEPENDENT");
    }
    for(var type:List.of(SemanticType.QUALITY,SemanticType.PROCESS)) {
      var dependent=concept(type);dependent.setInherent(tree);
      assertTrue(validator.validateConcept(dependent).isEmpty());
    }
    tree.setCollective(true);
    var quality=concept(SemanticType.QUALITY);quality.setInherent(tree);
    assertTrue(validator.validateConcept(quality).isEmpty(),"of each changes the operand's collectivity, not the head rule");
  }

  @Test void transformedQualityHeadIsCheckedAfterItsUnaryOperator() {
    var derived=concept(SemanticType.SUBJECT,SemanticType.COUNTABLE);
    derived.setObservable(concept(SemanticType.SUBJECT,SemanticType.COUNTABLE));
    derived.setSemanticModifier(UnarySemanticOperator.PRESENCE);
    derived.setInherent(concept(SemanticType.SUBJECT,SemanticType.COUNTABLE));
    assertTrue(validator.validateConcept(derived).stream().noneMatch(n->
        n.getMessage().contains("INHERENCY_HEAD_INDEPENDENT")));
    var standalone=concept(SemanticType.QUALITY);standalone.setSemanticModifier(UnarySemanticOperator.COUNT);
    standalone.setObservable(concept(SemanticType.SUBJECT,SemanticType.COUNTABLE));
    assertTrue(validator.validateConcept(standalone).isEmpty(),"unary count of is not a binary inherency clause");
  }

  @Test void applicabilityOfPreservesPredicateContextualizationAndConfigurationTargets() {
    var target=concept(SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.ABSTRACT);
    var configuration=concept(SemanticType.CONFIGURATION);configuration.setInherent(target);
    assertTrue(validator.validateConcept(configuration).isEmpty());
    var predicate=concept(SemanticType.PREDICATE,SemanticType.ATTRIBUTE);predicate.setInherent(target);
    assertTrue(validator.validateConcept(predicate).isEmpty());
    assertEquals(org.integratedmodelling.klab.api.knowledge.Contextualization.CHARACTERIZATION,
        org.integratedmodelling.klab.api.knowledge.Contextualization.forSemantics(predicate));
    target.setCollective(true);
    assertEquals(org.integratedmodelling.klab.api.knowledge.Contextualization.CLASSIFICATION,
        org.integratedmodelling.klab.api.knowledge.Contextualization.forSemantics(predicate));
    predicate.setInherent(concept(SemanticType.QUALITY));
    assertTrue(validator.validateConcept(predicate).isEmpty());
    assertEquals(org.integratedmodelling.klab.api.knowledge.Contextualization.TRANSFORMATION,
        org.integratedmodelling.klab.api.knowledge.Contextualization.forSemantics(predicate));
  }

  @Test void booleanSubjectHeadsCannotHideAnInherencyClause() {
    var head=concept(SemanticType.UNION);
    head.setExpressionType(KimConcept.Expression.UNION);
    head.setOperands(List.of(concept(SemanticType.SUBJECT),concept(SemanticType.SUBJECT)));
    head.setInherent(concept(SemanticType.SUBJECT));
    assertError(head,"INHERENCY_HEAD_INDEPENDENT");
  }

  @Test void acceptsProcessInherencyWithoutMakingProcessesCountable() {
    var quality = concept(SemanticType.QUALITY);
    var process = concept(SemanticType.PROCESS);
    quality.setInherent(process);
    assertTrue(validator.validateConcept(quality).isEmpty());
    process.setCollective(true);
    assertError(process, "each");
    assertTrue(org.integratedmodelling.klab.api.lang.SemanticClause.OF.argument.contains(SemanticType.PROCESS));
  }

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
