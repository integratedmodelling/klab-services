package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.junit.jupiter.api.Test;

class ConsequenceRuleIndexTest {
  static final class Fixture {
    final KimOntologyImpl ontology=new KimOntologyImpl();
    final WorldviewImpl source=new WorldviewImpl();
    final Map<String,Concept> concepts=new HashMap<>();
    final AtomicInteger resolutions=new AtomicInteger();
    Fixture() {
      ontology.setUrn("audit");ontology.setSourceCode("confers Role\nimplies Quality\nemerges Quality and Other\n");
      source.setUrn("audit");source.setWorldviewId("audit");source.setOntologies(List.of(ontology));
      declare("Role",SemanticType.ROLE,SemanticType.PREDICATE);
      declare("ChildRole",SemanticType.ROLE,SemanticType.PREDICATE);
      declare("Quality",SemanticType.QUALITY,SemanticType.OBSERVABLE);
      declare("Other",SemanticType.QUALITY,SemanticType.OBSERVABLE);
      declare("Pattern",SemanticType.CONFIGURATION,SemanticType.OBSERVABLE);
      declare("Process",SemanticType.PROCESS,SemanticType.OBSERVABLE);
    }
    Concept declare(String name,SemanticType... flags) {
      var c=new ConceptImpl();c.setUrn("audit:"+name);c.setType(EnumSet.copyOf(List.of(flags)));concepts.put(c.getUrn(),c);return c;
    }
    KimConceptImpl leaf(String name) {var k=new KimConceptImpl();k.setName("audit:"+name);k.setType(EnumSet.copyOf(concepts.get(k.getName()).getType()));k.resetDefinition();return k;}
    KimConceptStatementImpl owner(String name) {
      var s=new KimConceptStatementImpl();s.setUrn(name);s.setNamespace("audit");s.setType(EnumSet.copyOf(concepts.get("audit:"+name).getType()));
      ontology.getStatements().add(s);return s;
    }
    KimConsequenceRule rule(KimConsequenceRule.Kind kind,KimConcept target,String span) {
      return new KimConsequenceRule(kind,target,KimConsequenceRule.Recipient.implicit(),null,null,
          new KimConceptStatement.DeclarationClause(kind.name(),span,ontology.getSourceCode().indexOf(span),span.length()));
    }
    ConsequenceRuleIndex capture() {
      return ConsequenceRuleIndex.capture(source,concepts::get,k->{resolutions.incrementAndGet();return concepts.get(k.getUrn());});
    }
    Collection<String> keys(Concept c) {return c.getUrn().equals("audit:ChildRole")?List.of(c.getUrn(),"audit:Role"):List.of(c.getUrn());}
  }
  @Test void ownerAndIngredientTriggersUseSeparateIndexesAndNeverResolveAtQueryTime() {
    var f=new Fixture();f.owner("Role").getConsequenceRules().add(f.rule(KimConsequenceRule.Kind.IMPLIES,f.leaf("Quality"),"implies Quality"));
    f.owner("Pattern").getConsequenceRules().add(f.rule(KimConsequenceRule.Kind.EMERGES_FROM,f.leaf("Quality"),"emerges Quality and Other"));
    var index=f.capture();int captured=f.resolutions.get();
    var owners=index.select(new ConsequenceRuleQuery(List.of(f.concepts.get("audit:ChildRole")),List.of(),null,null),f::keys);
    assertEquals(List.of(KimConsequenceRule.Kind.IMPLIES),owners.rules().stream().map(e->e.rule().kind()).toList());
    var ingredients=index.select(new ConsequenceRuleQuery(List.of(),List.of(f.concepts.get("audit:Quality")),null,null),f::keys);
    assertEquals(List.of(KimConsequenceRule.Kind.EMERGES_FROM),ingredients.rules().stream().map(e->e.rule().kind()).toList());
    assertTrue(index.select(new ConsequenceRuleQuery(List.of(),List.of(),null,null),f::keys).rules().isEmpty());
    assertEquals(captured,f.resolutions.get(),"Query cannot parse/declare semantics or scan source again");
  }
  @Test void sourceEntriesAreDetachedAndGeneratedFactsDoNotBecomeAuthoredRules() {
    var f=new Fixture();var statement=f.owner("Role");
    statement.getConsequenceRules().add(f.rule(KimConsequenceRule.Kind.IMPLIES,f.leaf("Quality"),"implies Quality"));
    var index=f.capture();var query=new ConsequenceRuleQuery(List.of(f.concepts.get("audit:Role")),List.of(),null,null);
    var first=index.select(query,f::keys).rules().get(0);
    ((KimConceptImpl)first.rule().target()).setName("audit:Corrupted");
    statement.getConsequenceRules().clear();f.declare("Generated",SemanticType.ROLE,SemanticType.PREDICATE);
    var next=index.select(query,f::keys).rules().get(0);
    assertEquals("audit:Quality",next.rule().target().getUrn());
    assertFalse(index.belongsTo(new WorldviewImpl()));assertTrue(index.belongsTo(f.source));
    assertTrue(index.select(new ConsequenceRuleQuery(List.of(f.concepts.get("audit:Generated")),List.of(),null,null),f::keys).rules().isEmpty());
  }
  @Test void legacyUnnormalizedKindsFailClosedWithoutDisablingOtherKinds() {
    var f=new Fixture();var process=f.owner("Process");
    process.getDeclarationClauses().add(new KimConceptStatement.DeclarationClause("confersClause","confers Role",0,12));
    f.owner("Role").getConsequenceRules().add(f.rule(KimConsequenceRule.Kind.IMPLIES,f.leaf("Quality"),"implies Quality"));
    var index=f.capture();
    assertThrows(IllegalStateException.class,()->index.select(new ConsequenceRuleQuery(List.of(f.concepts.get("audit:Process")),List.of(),Set.of(KimConsequenceRule.Kind.CONFERS),null),f::keys));
    assertEquals(1,index.select(new ConsequenceRuleQuery(List.of(f.concepts.get("audit:Role")),List.of(),Set.of(KimConsequenceRule.Kind.IMPLIES),null),f::keys).rules().size());
  }
  @Test void mismatchedOriginalSourceSpanIsUnavailableRatherThanShiftedOrGuessed() {
    var f=new Fixture();var owner=f.owner("Role");
    owner.getConsequenceRules().add(new KimConsequenceRule(KimConsequenceRule.Kind.IMPLIES,f.leaf("Quality"),
        KimConsequenceRule.Recipient.implicit(),null,null,new KimConceptStatement.DeclarationClause("impliesClause","wrong text",0,10)));
    var index=f.capture();
    assertThrows(IllegalStateException.class,()->index.select(new ConsequenceRuleQuery(List.of(f.concepts.get("audit:Role")),List.of(),null,null),f::keys));
  }
  @Test void aliasesRetainDistinctAuthoredRuleIdentityEvenWhenTheirOwnerMeaningIsShared() {
    var f=new Fixture();var original=f.owner("Role");
    original.getConsequenceRules().add(f.rule(KimConsequenceRule.Kind.IMPLIES,f.leaf("Quality"),"implies Quality"));
    f.declare("Alias",SemanticType.ROLE,SemanticType.PREDICATE);var alias=f.owner("Alias");
    alias.setAlias(true);alias.setDeclaredParent(f.leaf("Role"));f.concepts.put("audit:Alias",f.concepts.get("audit:Role"));
    alias.getConsequenceRules().add(f.rule(KimConsequenceRule.Kind.IMPLIES,f.leaf("Other"),"implies Quality"));
    var result=f.capture().select(new ConsequenceRuleQuery(List.of(f.concepts.get("audit:Role")),List.of(),null,null),f::keys).rules();
    assertEquals(2,result.size());assertEquals(2,result.stream().map(ConsequenceRuleResponse.Entry::ruleId).distinct().count());
  }
  @Test void nestedBooleanSourceKeepsItsTreeAndIndexesEveryPositiveIngredient() {
    var f=new Fixture();var left=f.leaf("Quality");left.setExpressionType(KimConcept.Expression.INTERSECTION);
    var right=f.leaf("Other");right.setExpressionType(KimConcept.Expression.UNION);right.getOperands().add(f.leaf("Quality"));
    left.getOperands().add(right);
    assertEquals(List.of("audit:Quality","audit:Other","audit:Quality"),ConsequenceRuleIndex.positiveLeaves(left).stream().map(KimConcept::getUrn).toList());
    f.declare("Boolean",SemanticType.QUALITY,SemanticType.OBSERVABLE);
    f.concepts.put(left.getUrn(),f.concepts.get("audit:Boolean"));
    f.owner("Pattern").getConsequenceRules().add(f.rule(KimConsequenceRule.Kind.EMERGES_FROM,left,"emerges Quality and Other"));
    var index=f.capture();var selected=index.select(new ConsequenceRuleQuery(List.of(),List.of(f.concepts.get("audit:Other")),null,null),f::keys).rules();
    assertEquals(1,selected.size());var syntax=selected.get(0).rule().target();
    assertEquals(KimConcept.Expression.INTERSECTION,syntax.getExpressionType());
    assertEquals(KimConcept.Expression.UNION,syntax.getOperands().getFirst().getExpressionType());
  }
  @Test void canonicalSemanticKeysIncludeInferredParentsWithoutMatchingAnotherNamespaceByName() throws Exception {
    try(var t=new MonotoneRefinementTest.Fixture()) {
      assertTrue(ConsequenceRuleIndex.semanticKeys(t.owl,t.concept("Concrete")).contains(t.concept("Category").getUrn()));
      var impostor=new ConceptImpl();impostor.setUrn("other:Concrete");impostor.setType(t.concept("Concrete").getType());
      assertThrows(IllegalStateException.class,()->ConsequenceRuleIndex.semanticKeys(t.owl,impostor));
    }
  }
  @Test void aRuleOfTheSameKindCannotCoverAnotherAuthoredClause() {
    var f=new Fixture();f.ontology.setSourceCode("implies Quality\nimplies Other\n");
    var owner=f.owner("Role");
    owner.getDeclarationClauses().add(new KimConceptStatement.DeclarationClause("impliesClause","implies Quality",0,15));
    owner.getDeclarationClauses().add(new KimConceptStatement.DeclarationClause("impliesClause","implies Other",16,13));
    owner.getConsequenceRules().add(f.rule(KimConsequenceRule.Kind.IMPLIES,f.leaf("Quality"),"Quality"));
    var query=new ConsequenceRuleQuery(List.of(f.concepts.get("audit:Role")),List.of(),Set.of(KimConsequenceRule.Kind.IMPLIES),null);
    assertThrows(IllegalStateException.class,()->f.capture().select(query,f::keys));
    owner.getConsequenceRules().add(f.rule(KimConsequenceRule.Kind.IMPLIES,f.leaf("Other"),"Other"));
    assertEquals(2,f.capture().select(query,f::keys).rules().size());
  }
  @Test void everyItemInOneClauseAndEveryLegacyTargetNeedsCorrespondence() {
    var f=new Fixture();String source="implies Quality, /* retained separator */ Other";f.ontology.setSourceCode(source);
    var owner=f.owner("Role");owner.getDeclarationClauses().add(new KimConceptStatement.DeclarationClause("impliesClause",source,0,source.length()));
    owner.getConsequenceRules().add(f.rule(KimConsequenceRule.Kind.IMPLIES,f.leaf("Quality"),"Quality"));
    var query=new ConsequenceRuleQuery(List.of(f.concepts.get("audit:Role")),List.of(),Set.of(KimConsequenceRule.Kind.IMPLIES),null);
    assertThrows(IllegalStateException.class,()->f.capture().select(query,f::keys));
    owner.getConsequenceRules().add(f.rule(KimConsequenceRule.Kind.IMPLIES,f.leaf("Other"),"Other"));
    assertEquals(2,f.capture().select(query,f::keys).rules().size());
    owner.getDeclarationClauses().clear();owner.getImpliedObservables().addAll(List.of(f.leaf("Quality"),f.leaf("Other")));
    owner.getConsequenceRules().removeLast();
    assertThrows(IllegalStateException.class,()->f.capture().select(query,f::keys));
  }
  @Test void emergenceOwnerQueryEnumeratesEveryIngredientFamilyAndInheritedOwner() {
    var f=new Fixture();f.ontology.setSourceCode("emerges Quality, Other\n");
    var owner=f.owner("Pattern");owner.getConsequenceRules().add(f.rule(KimConsequenceRule.Kind.EMERGES_FROM,f.leaf("Quality"),"Quality"));
    owner.getConsequenceRules().add(f.rule(KimConsequenceRule.Kind.EMERGES_FROM,f.leaf("Other"),"Other"));
    var child=f.declare("ChildPattern",SemanticType.CONFIGURATION,SemanticType.OBSERVABLE);var index=f.capture();
    var query=new ConsequenceRuleQuery(List.of(child),List.of(),Set.of(KimConsequenceRule.Kind.EMERGES_FROM),null);
    var selected=index.select(query,trigger->List.of(trigger.getUrn(),"audit:Pattern"));
    assertEquals(Set.of("audit:Quality","audit:Other"),selected.rules().stream().map(entry->entry.rule().target().getUrn()).collect(java.util.stream.Collectors.toSet()));
    assertTrue(index.select(new ConsequenceRuleQuery(List.of(),List.of(),Set.of(KimConsequenceRule.Kind.EMERGES_FROM),null),f::keys).rules().isEmpty());
  }
  @Test void unavailableEmergenceCannotBecomeSuccessfulEmptyThroughOwnerOnlyLookup() {
    var f=new Fixture();var owner=f.owner("Pattern");owner.getDeclarationClauses().add(new KimConceptStatement.DeclarationClause("emergesFromClause","emerges Quality and Other",f.ontology.getSourceCode().indexOf("emerges"),"emerges Quality and Other".length()));
    var index=f.capture();assertThrows(IllegalStateException.class,()->index.select(new ConsequenceRuleQuery(List.of(f.concepts.get("audit:Pattern")),List.of(),Set.of(KimConsequenceRule.Kind.EMERGES_FROM),null),f::keys));
  }


  @Test void observedWithinContextChangesAreIncrementalEmergenceTriggers() {
    var f=new Fixture();f.ontology.setSourceCode("emerges Quality within Region\n");
    var region=f.declare("Region",SemanticType.SUBJECT,SemanticType.OBSERVABLE,SemanticType.COUNTABLE);var child=f.declare("ChildRegion",SemanticType.SUBJECT,SemanticType.OBSERVABLE,SemanticType.COUNTABLE);
    var plain=f.rule(KimConsequenceRule.Kind.EMERGES_FROM,f.leaf("Quality"),"emerges Quality within Region");
    f.owner("Pattern").getConsequenceRules().add(new KimConsequenceRule(plain.kind(),plain.target(),plain.recipient(),null,f.leaf("Region"),plain.source()));
    var selected=f.capture().select(new ConsequenceRuleQuery(List.of(),List.of(child),Set.of(KimConsequenceRule.Kind.EMERGES_FROM),null),trigger->List.of(trigger.getUrn(),region.getUrn()));
    assertEquals(1,selected.rules().size());assertEquals("audit:Pattern",selected.rules().getFirst().owner().getUrn());assertTrue(selected.obligations().contains("audit:Region"));
  }
}
