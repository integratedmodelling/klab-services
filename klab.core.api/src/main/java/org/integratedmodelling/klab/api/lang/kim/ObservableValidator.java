package org.integratedmodelling.klab.api.lang.kim;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.SemanticClause;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * Default, environment-independent validation for observables and concepts.
 *
 * <p>The rule declarations are deliberately kept as immutable data at the top of the class so the
 * semantic contract can be reviewed and changed without following the validation control flow.
 * Rules that require a reasoner or a worldview belong in a specialized validator.
 */
public class ObservableValidator {

  public record ClauseRule(
      SemanticClause clause, Set<SemanticType> requiredArgumentType, String message) {}

  public record UnaryOperatorRule(
      UnarySemanticOperator operator, Set<SemanticType> requiredOperandType, String message) {}

  public record LogicalOperatorRule(
      KimConcept.Expression expression, String operator, String message) {}

  public enum ConceptAttribute {
    EACH,
    ABSTRACT,
    ANY,
    SUBJECTIVE,
    SEALED
  }

  public record ConceptAttributeRule(
      ConceptAttribute attribute, Set<SemanticType> requiredConceptType, String message) {}

  public enum RelationshipSpecialization {
    BETWEEN
  }

  public record RelationshipSpecializationRule(
      RelationshipSpecialization specialization,
      Set<SemanticType> requiredConceptType,
      String message) {}

  private static final List<ClauseRule> CLAUSE_RULES =
      List.of(
          new ClauseRule(
              SemanticClause.DURING,
              EnumSet.of(SemanticType.EVENT),
              "The 'during' clause must target an event"));

  private static final List<UnaryOperatorRule> UNARY_OPERATOR_RULES =
      List.of(
          new UnaryOperatorRule(
              UnarySemanticOperator.PRESENCE,
              EnumSet.of(SemanticType.COUNTABLE),
              "The 'presence of' operator must target a countable concept"),
          new UnaryOperatorRule(
              UnarySemanticOperator.PROBABILITY,
              EnumSet.of(SemanticType.EVENT),
              "The 'probability of' operator must target an event"),
          new UnaryOperatorRule(
              UnarySemanticOperator.PROPORTION,
              EnumSet.of(SemanticType.EVENT),
              "The 'proportion of' operator must target an event"),
          new UnaryOperatorRule(
              UnarySemanticOperator.TYPE,
              // TODO check - may need to become stricter (IDENTITY only)?
              EnumSet.of(SemanticType.PREDICATE),
              "The 'type of' operator must target a predicate"));

  private static final List<LogicalOperatorRule> LOGICAL_OPERATOR_RULES =
      List.of(
          new LogicalOperatorRule(
              KimConcept.Expression.INTERSECTION,
              "and",
              "Concepts joined by 'and' must have the same semantic type"),
          new LogicalOperatorRule(
              KimConcept.Expression.UNION,
              "or",
              "Concepts joined by 'or' must have the same semantic type"));

  private static final List<ConceptAttributeRule> CONCEPT_ATTRIBUTE_RULES =
      List.of(
          new ConceptAttributeRule(
              ConceptAttribute.EACH,
              EnumSet.of(SemanticType.COUNTABLE),
              "The 'each' qualifier can only apply to a countable concept"));

  private static final List<RelationshipSpecializationRule> RELATIONSHIP_SPECIALIZATION_RULES =
      List.of(
          new RelationshipSpecializationRule(
              RelationshipSpecialization.BETWEEN,
              EnumSet.of(SemanticType.RELATIONSHIP),
              "The 'between' clause can only specialize a relationship"));

  public static List<ClauseRule> clauseRules() {
    return CLAUSE_RULES;
  }

  public static List<UnaryOperatorRule> unaryOperatorRules() {
    return UNARY_OPERATOR_RULES;
  }

  public static List<LogicalOperatorRule> logicalOperatorRules() {
    return LOGICAL_OPERATOR_RULES;
  }

  public static List<ConceptAttributeRule> conceptAttributeRules() {
    return CONCEPT_ATTRIBUTE_RULES;
  }

  public static List<RelationshipSpecializationRule> relationshipSpecializationRules() {
    return RELATIONSHIP_SPECIALIZATION_RULES;
  }

  /** Validate attributes that belong to the observable rather than its semantics. */
  public List<Notification> validateObservable(KimObservable observable) {
    if (observable != null && knownNonAgentObserver(observable.getObserverSemantics(),
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())))
      return List.of(Notification.error(
          "OBSERVER_AGENT_REQUIRED: 'observed as' must name Agent-class semantics",
          observable.getObserverSemantics()));
    return List.of();
  }

  /** Reject proven wrong kinds; unknown source references remain the resolver's responsibility. */
  private static boolean knownNonAgentObserver(KimConcept concept, Set<KimConcept> visited) {
    if (concept == null || !visited.add(concept)) return false;
    var effective = effectiveHeadTypes(concept,
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    if (effective.contains(SemanticType.NOTHING)) return false;
    if (!java.util.Collections.disjoint(effective, SemanticType.FUNDAMENTAL_TYPES)
        && !effective.contains(SemanticType.AGENT)) return true;
    // Every alternative must be agentic; overlap with one Agent branch is not enough.
    if (concept.getSemanticModifier() == null) {
      if (knownNonAgentObserver(concept.getObservable(), visited)) return true;
      for (var operand : safe(concept.getOperands()))
        if (knownNonAgentObserver(operand, visited)) return true;
    }
    return false;
  }

  /** Validate the rules local to one concept. The caller visits nested concepts. */
  public List<Notification> validateConcept(KimConcept concept) {
    if (concept == null) {
      return List.of();
    }

    if (concept.is(SemanticType.NOTHING)) {
      return List.of(
          Notification.error("Concept is unknown or semantically inconsistent", concept));
    }

    var notifications = new ArrayList<Notification>();
    validateInherencyHead(concept, notifications);
    validateClauses(concept, notifications);
    validateUnaryOperator(concept, notifications);
    validateLogicalExpression(concept, notifications);
    validateConceptAttributes(concept, notifications);
    validateRelationshipSpecialization(concept, notifications);
    return List.copyOf(notifications);
  }

  private static boolean isAny(KimConcept type, Set<SemanticType> types) {
    return types.stream().anyMatch(t -> type.is(t));
  }

  private void validateInherencyHead(KimConcept concept, List<Notification> notifications) {
    if (concept.getInherent() == null) return; // Not the token 'of' in unary operators or other grammar.
    var effective = effectiveHeadTypes(concept,
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    if (!SemanticType.isDependent(effective) && SemanticType.isSubstantial(effective))
      notifications.add(Notification.error(
          "INHERENCY_HEAD_INDEPENDENT: 'of' cannot qualify an independent subject/agent/event; "
              + "use a dependent quality or process head: " + concept.getUrn(), concept));
      // Predicate 'of' is applicability with shared OWL contextualization encoding.
      // Configuration 'of' is emergence. Neither category is silently promoted to
      // native quality/process dependence here.
  }

  protected static Set<SemanticType> effectiveHeadTypes(KimConcept concept, Set<KimConcept> visited) {
    if (concept == null || !visited.add(concept)) return Set.of();
    var own = concept.getType() == null ? Set.<SemanticType>of() : concept.getType();
    if (concept.getSemanticModifier() != null) {
      var operand = concept.getObservable() == null ? own
          : effectiveHeadTypes(concept.getObservable(), visited);
      var transformed = concept.getSemanticModifier().apply(operand);
      return transformed == null ? operand : transformed;
    }
    if (!java.util.Collections.disjoint(own, SemanticType.BASE_OBSERVABLE_TYPES)) return own;
    var result = EnumSet.noneOf(SemanticType.class);
    if (concept.getObservable() != null) result.addAll(effectiveHeadTypes(concept.getObservable(), visited));
    for (var operand : safe(concept.getOperands())) result.addAll(effectiveHeadTypes(operand, visited));
    return result.isEmpty() ? own : result;
  }

  private void validateClauses(KimConcept concept, List<Notification> notifications) {
    for (var rule : CLAUSE_RULES) {
      var argument = concept.semanticClause(rule.clause());
      if (hasKnownType(argument) && !isAny(argument, rule.requiredArgumentType())) {
        notifications.add(Notification.error(rule.message(), concept));
      }
    }
  }

  private void validateUnaryOperator(KimConcept concept, List<Notification> notifications) {
    var operation = concept.semanticOperation();
    if (operation == null) {
      return;
    }
    for (var rule : UNARY_OPERATOR_RULES) {
      if (operation.getFirst() == rule.operator()
          && hasKnownType(operation.getSecond())
          && !isAny(operation.getSecond(), rule.requiredOperandType())) {
        notifications.add(Notification.error(rule.message(), concept));
      }
    }
  }

  private void validateLogicalExpression(KimConcept concept, List<Notification> notifications) {
    for (var rule : LOGICAL_OPERATOR_RULES) {
      if (concept.getExpressionType() != rule.expression()) {
        continue;
      }
      SemanticType commonType = null;
      for (var operand : safe(concept.getOperands())) {
        var operandType = fundamentalType(operand);
        if (operandType == null) {
          continue;
        }
        if (commonType == null) {
          commonType = operandType;
        } else if (commonType != operandType) {
          notifications.add(Notification.error(rule.message(), concept));
          break;
        }
      }
    }
  }

  private void validateRelationshipSpecialization(
      KimConcept concept, List<Notification> notifications) {
    for (var rule : RELATIONSHIP_SPECIALIZATION_RULES) {
      boolean applies =
          switch (rule.specialization()) {
            case BETWEEN ->
                concept.getRelationshipSource() != null || concept.getRelationshipTarget() != null;
          };
      if (applies && hasKnownType(concept) && !isAny(concept, rule.requiredConceptType())) {
        notifications.add(Notification.error(rule.message(), concept));
      }
    }
  }

  private void validateConceptAttributes(KimConcept concept, List<Notification> notifications) {
    for (var rule : CONCEPT_ATTRIBUTE_RULES) {
      boolean applies =
          switch (rule.attribute()) {
            case EACH -> concept.isCollective();
            default -> false; // TODO check abstract or abstracted status
          };
      if (applies && hasKnownType(concept) && !isAny(concept, rule.requiredConceptType())) {
        notifications.add(Notification.error(rule.message(), concept));
      }
    }
  }

  private static SemanticType fundamentalType(KimConcept concept) {
    if (!hasKnownType(concept)) {
      return null;
    }
    if (concept.getFundamentalType() != null
        && concept.getFundamentalType() != SemanticType.NOTHING) {
      return concept.getFundamentalType();
    }
    Set<SemanticType> types = EnumSet.copyOf(concept.getType());
    types.retainAll(SemanticType.FUNDAMENTAL_TYPES);
    return types.size() == 1 ? types.iterator().next() : null;
  }

  private static boolean hasKnownType(KimConcept concept) {
    return concept != null && concept.getType() != null && !concept.getType().isEmpty();
  }

  private static <T> List<T> safe(List<T> values) {
    return values == null ? List.of() : values;
  }
}
