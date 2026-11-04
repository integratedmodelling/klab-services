package org.integratedmodelling.klab.runtime.language;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.junit.jupiter.api.Test;

class ConsequenceDeclarationValidationTest {
  static KimConceptStatementImpl declaration(String name,SemanticType type) {
    var statement=new KimConceptStatementImpl();statement.setUrn(name);statement.setNamespace("rules");
    statement.setType(EnumSet.of(type));
    if(type==SemanticType.RELATIONSHIP){statement.setAbstract(true);statement.getType().add(SemanticType.ABSTRACT);}
    return statement;
  }
  static KimConceptImpl target(String name,SemanticType... type) {
    var concept=new KimConceptImpl();concept.setName("rules:"+name);concept.setUrn("rules:"+name);
    concept.setType(type.length==0?EnumSet.noneOf(SemanticType.class):EnumSet.copyOf(List.of(type)));return concept;
  }
  static KimConsequenceRule rule(KimConsequenceRule.Kind kind,KimConcept target,
      KimConsequenceRule.Recipient recipient,KimConcept secondary,KimConcept within) {
    return new KimConsequenceRule(kind,target,recipient,secondary,within,
        new KimConceptStatement.DeclarationClause("testClause","authored consequence",0,20));
  }
  static KimConsequenceRule rule(KimConsequenceRule.Kind kind,KimConcept target) {
    return rule(kind,target,KimConsequenceRule.Recipient.implicit(),null,null);
  }
  static List<String> errors(KimConceptStatement... statements) {
    var ontology=new KimOntologyImpl();ontology.setUrn("rules");ontology.setStatements(new ArrayList<>(List.of(statements)));
    return new WorldviewDeclarationRegistry(List.of(ontology)).errors().stream().map(n->n.getMessage()).toList();
  }
  static void valid(KimConceptStatement... statements) {assertEquals(List.of(),errors(statements));}
  static void invalid(String code,KimConceptStatement... statements) {
    var errors=errors(statements);assertTrue(errors.stream().anyMatch(message->message.contains(code)),errors.toString());
  }
  @Test void scopedConferralsValidateTargetsEvenWhenLegacyFlatListsAreEmpty() {
    for(var source:List.of(SemanticType.PROCESS,SemanticType.RELATIONSHIP)) {
      var owner=declaration("Source",source);
      owner.getConsequenceRules().add(rule(KimConsequenceRule.Kind.CONFERS,target("Role",SemanticType.ROLE),
          KimConsequenceRule.Recipient.implicit(),null,target("Specialized",SemanticType.SUBJECT)));
      assertTrue(owner.getTraitsConferred().isEmpty());valid(owner);
      owner.getConsequenceRules().clear();owner.getConsequenceRules().add(rule(KimConsequenceRule.Kind.CONFERS,target("Attribute",SemanticType.ATTRIBUTE)));
      invalid("CLAUSE_TYPE_INVALID",owner);
    }
    var invalidOwner=declaration("Owner",SemanticType.QUALITY);
    invalidOwner.getConsequenceRules().add(rule(KimConsequenceRule.Kind.CONFERS,target("Role",SemanticType.ROLE)));
    invalid("CLAUSE_SOURCE_INVALID",invalidOwner);
  }
  @Test void conferralRecipientsUseCanonicalSubstantialContractAndEndpointSelectorsRequireRelationships() {
    for(var recipientType:List.of(SemanticType.SUBJECT,SemanticType.AGENT,SemanticType.EVENT,SemanticType.RELATIONSHIP,SemanticType.PROCESS,SemanticType.QUALITY)) {
      var owner=declaration("Source",SemanticType.PROCESS);
      owner.getConsequenceRules().add(rule(KimConsequenceRule.Kind.CONFERS,target("Role",SemanticType.ROLE),
          new KimConsequenceRule.Recipient(KimConsequenceRule.RecipientKind.EXPRESSION,target("Recipient",recipientType)),null,null));
      if(SemanticType.isEnumerableSubstantial(Set.of(recipientType)))valid(owner);else invalid("CLAUSE_TYPE_INVALID",owner);
    }
    for(var recipient:List.of(KimConsequenceRule.RecipientKind.SOURCE,KimConsequenceRule.RecipientKind.TARGET)) {
      var process=declaration("Process",SemanticType.PROCESS);var relationship=declaration("Relationship",SemanticType.RELATIONSHIP);
      var rule=rule(KimConsequenceRule.Kind.CONFERS,target("Role",SemanticType.ROLE),new KimConsequenceRule.Recipient(recipient,null),null,null);
      process.getConsequenceRules().add(rule);relationship.getConsequenceRules().add(rule);
      invalid("RECIPIENT_INVALID",process);valid(relationship);
    }
  }
  @Test void roleImplicationsAdmitDependentsRelationshipsAndPredicatesButNotSubstantialsOrConfigurations() {
    for(var targetType:List.of(SemanticType.QUALITY,SemanticType.PROCESS,SemanticType.RELATIONSHIP,
        SemanticType.ROLE,SemanticType.ATTRIBUTE,SemanticType.IDENTITY,SemanticType.REALM,
        SemanticType.SUBJECT,SemanticType.AGENT,SemanticType.EVENT,SemanticType.CONFIGURATION)) {
      var role=declaration("Role",SemanticType.ROLE);
      role.getConsequenceRules().add(rule(KimConsequenceRule.Kind.IMPLIES,target("Target",targetType)));
      if(SemanticType.isDependent(Set.of(targetType))||targetType==SemanticType.RELATIONSHIP||targetType.isPredicate())valid(role);
      else invalid("CLAUSE_TYPE_INVALID",role);
    }
    var invalidSource=declaration("Source",SemanticType.QUALITY);
    invalidSource.getConsequenceRules().add(rule(KimConsequenceRule.Kind.IMPLIES,target("Target",SemanticType.PROCESS)));
    invalid("CLAUSE_SOURCE_INVALID",invalidSource);
  }
  @Test void relationshipImplicationsCannotDuplicateRoleConferral() {
    for(var type:List.of(SemanticType.ROLE,SemanticType.QUALITY,SemanticType.PROCESS,SemanticType.RELATIONSHIP,SemanticType.ATTRIBUTE)) {
      var owner=declaration("Source",SemanticType.RELATIONSHIP);
      owner.getConsequenceRules().add(rule(KimConsequenceRule.Kind.IMPLIES,target("Target",type),
          KimConsequenceRule.Recipient.implicit(),target("Recipient",SemanticType.SUBJECT),null));
      if(type==SemanticType.ROLE)invalid("CLAUSE_TYPE_INVALID",owner);else valid(owner);
    }
  }
  @Test void conferralAndRoleImplicationWithinSpecializeTheRoleBearingSubstantialNotTheDeclarationOwner() {
    for(var withinType:List.of(SemanticType.SUBJECT,SemanticType.AGENT,SemanticType.EVENT,
        SemanticType.RELATIONSHIP,SemanticType.QUALITY,SemanticType.PROCESS,SemanticType.ROLE)) {
      for(var source:List.of(SemanticType.PROCESS,SemanticType.RELATIONSHIP,SemanticType.ROLE)) {
        var owner=declaration("Source",source);
        var kind=source==SemanticType.ROLE?KimConsequenceRule.Kind.IMPLIES:KimConsequenceRule.Kind.CONFERS;
        owner.getConsequenceRules().add(rule(kind,target("Target",SemanticType.ROLE),
            KimConsequenceRule.Recipient.implicit(),null,target("Specialized",withinType)));
        if(SemanticType.isEnumerableSubstantial(Set.of(withinType)))valid(owner);else invalid("CLAUSE_TYPE_INVALID",owner);
      }
    }
    var configuration=declaration("Configuration",SemanticType.CONFIGURATION);
    configuration.getConsequenceRules().add(rule(KimConsequenceRule.Kind.EMERGES_FROM,target("Quality",SemanticType.QUALITY),
        KimConsequenceRule.Recipient.implicit(),null,target("Specialized",SemanticType.PROCESS)));
    valid(configuration); // Emergence's context specialization remains its separate contract.
  }
  @Test void configurationImplicationTargetsOnlyRolesAndExplicitForMustBeSubstantial() {
    var owner=declaration("Configuration",SemanticType.CONFIGURATION);
    for(var type:List.of(SemanticType.ROLE,SemanticType.ATTRIBUTE,SemanticType.QUALITY)) {
      owner.getConsequenceRules().clear();owner.getConsequenceRules().add(rule(KimConsequenceRule.Kind.IMPLIES,target("Target",type),
          KimConsequenceRule.Recipient.implicit(),target("Recipient",SemanticType.SUBJECT),null));
      if(type==SemanticType.ROLE)valid(owner);else invalid("CLAUSE_TYPE_INVALID",owner);
    }
    owner.getConsequenceRules().clear();owner.getConsequenceRules().add(rule(KimConsequenceRule.Kind.IMPLIES,target("Role",SemanticType.ROLE),
        KimConsequenceRule.Recipient.implicit(),target("Dependent",SemanticType.PROCESS),null));
    invalid("CLAUSE_TYPE_INVALID",owner);
  }
  @Test void onlyDefinitivelySubstantialConfigurationsRejectMissingForWithoutRequiringRuntimePlacement() {
    for(var ingredient:List.of(SemanticType.QUALITY,SemanticType.PROCESS,SemanticType.SUBJECT,SemanticType.RELATIONSHIP)) {
      var owner=declaration("Configuration",SemanticType.CONFIGURATION);
      owner.getConsequenceRules().add(rule(KimConsequenceRule.Kind.EMERGES_FROM,target("Ingredient",ingredient)));
      owner.getConsequenceRules().add(rule(KimConsequenceRule.Kind.IMPLIES,target("Role",SemanticType.ROLE)));
      if(SemanticType.isDependent(Set.of(ingredient)))valid(owner);else invalid("RECIPIENT_MISSING",owner);
    }
    var unknown=declaration("Configuration",SemanticType.CONFIGURATION);
    unknown.getConsequenceRules().add(rule(KimConsequenceRule.Kind.IMPLIES,target("Role",SemanticType.ROLE)));
    valid(unknown); // Actual bearer existence remains mandatory at Runtime.
  }
  @Test void normalizedConfigurationRoleImplicationIsNotRejectedByOldFlatObservableValidation() {
    var owner=declaration("Configuration",SemanticType.CONFIGURATION);var role=target("Role",SemanticType.ROLE);
    owner.getImpliedObservables().add(role);owner.getConsequenceRules().add(rule(KimConsequenceRule.Kind.IMPLIES,role));
    valid(owner);
  }
  static KimConceptImpl wrapper(KimConcept child) {
    var wrapper=new KimConceptImpl();wrapper.setObservable(child);wrapper.setType(EnumSet.of(SemanticType.SUBJECT));
    wrapper.setUrn("wrapped ingredients");return wrapper;
  }
  @Test void transparentWrappersCannotHideMixedDependentBooleanIngredients() {
    for(var operator:List.of(KimConcept.Expression.INTERSECTION,KimConcept.Expression.UNION)) {
      for(var otherType:List.of(SemanticType.SUBJECT,SemanticType.QUALITY)) {
        var compound=target("Subject",SemanticType.SUBJECT);compound.setExpressionType(operator);
        compound.setOperands(List.of(target("Other",otherType)));
        var owner=declaration("Configuration",SemanticType.CONFIGURATION);
        owner.getConsequenceRules().add(rule(KimConsequenceRule.Kind.EMERGES_FROM,wrapper(wrapper(compound))));
        owner.getConsequenceRules().add(rule(KimConsequenceRule.Kind.IMPLIES,target("Role",SemanticType.ROLE)));
        if(otherType==SemanticType.SUBJECT)invalid("RECIPIENT_MISSING",owner);else valid(owner);
      }
    }
  }
  @Test void conditionedIngredientWrappersAreNotDiscardedToAssertSubstantialDefaultAbsence() {
    for(boolean values:List.of(true,false)) {
      var ingredient=wrapper(target("Subject",SemanticType.SUBJECT));
      if(values)ingredient.getValueOperators().add(org.integratedmodelling.klab.api.collections.Pair.of(
          org.integratedmodelling.klab.api.lang.ValueOperator.WHERE,"condition"));
      else ingredient.setCooccurrent(target("Event",SemanticType.EVENT));
      var owner=declaration("Configuration",SemanticType.CONFIGURATION);
      owner.getConsequenceRules().add(rule(KimConsequenceRule.Kind.EMERGES_FROM,ingredient));
      owner.getConsequenceRules().add(rule(KimConsequenceRule.Kind.IMPLIES,target("Role",SemanticType.ROLE)));
      valid(owner);
    }
  }
  @Test void normalizedRulesOfOneKindDoNotHideInvalidLegacyOperandsOfAnotherKind() {
    var owner=declaration("Source",SemanticType.PROCESS);
    owner.getConsequenceRules().add(rule(KimConsequenceRule.Kind.CONFERS,target("Role",SemanticType.ROLE)));
    owner.getEmergenceTriggers().add(target("InvalidIngredient",SemanticType.ROLE));
    invalid("CLAUSE_TYPE_INVALID",owner);
  }
  @Test void sourceOwnedTargetTypesOverrideTransportedFlagsAndUnknownMeaningsFailClosed() {
    var owner=declaration("Source",SemanticType.PROCESS);var role=declaration("Role",SemanticType.ROLE);
    owner.getConsequenceRules().add(rule(KimConsequenceRule.Kind.CONFERS,target("Role",SemanticType.ATTRIBUTE)));
    valid(owner,role);
    owner.getConsequenceRules().clear();owner.getConsequenceRules().add(rule(KimConsequenceRule.Kind.CONFERS,target("Quality",SemanticType.ROLE)));
    invalid("CLAUSE_TYPE_INVALID",owner,declaration("Quality",SemanticType.QUALITY));
    owner.getConsequenceRules().clear();owner.getConsequenceRules().add(rule(KimConsequenceRule.Kind.CONFERS,target("Unknown")));
    invalid("CLAUSE_TYPE_INVALID",owner);
  }
  @Test void onlyConfigurationsMayDeclareEmergenceAndIngredientsMustBeObservable() {
    var config=declaration("Configuration",SemanticType.CONFIGURATION);
    config.getConsequenceRules().add(rule(KimConsequenceRule.Kind.EMERGES_FROM,target("Ingredient",SemanticType.QUALITY)));
    valid(config);
    for(var type:List.of(SemanticType.PROCESS,SemanticType.SUBJECT,SemanticType.AGENT)) {
      var source=declaration("Source",type);source.getConsequenceRules().addAll(config.getConsequenceRules());
      invalid("CLAUSE_SOURCE_INVALID",source);
    }
    config.getConsequenceRules().clear();config.getConsequenceRules().add(rule(KimConsequenceRule.Kind.EMERGES_FROM,target("Role",SemanticType.ROLE)));
    invalid("CLAUSE_TYPE_INVALID",config);
  }
  @Test void legacyFlatCooccurrenceCannotAuthorizeDirectSubstantialEmergenceEither() {
    for(var type:List.of(SemanticType.SUBJECT,SemanticType.AGENT)) {
      var source=declaration("Assemblage",type);source.getEmergenceTriggers().add(target("Ingredient",SemanticType.SUBJECT));
      invalid("CLAUSE_SOURCE_INVALID",source);
    }
  }
}
