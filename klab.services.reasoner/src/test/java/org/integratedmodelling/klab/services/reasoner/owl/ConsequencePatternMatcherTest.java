package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.services.reasoner.objects.ConsequenceRuleResponse;
import org.junit.jupiter.api.Test;

class ConsequencePatternMatcherTest {
  static final ConsequenceRuleResponse.Epoch EPOCH=new ConsequenceRuleResponse.Epoch("reasoner",1,"semantic-content-v2:"+"a".repeat(64),"session");
  static final ConsequencePatternMatcher.Placement BEARER=new ConsequencePatternMatcher.Placement(ConsequencePatternMatcher.PlacementKind.DEPENDENT_BEARER,100);
  static KimConceptImpl leaf(String name){var k=new KimConceptImpl();k.setName("audit:"+name);
    k.setType(EnumSet.of(SemanticType.OBSERVABLE,name.equals("Connection")?SemanticType.RELATIONSHIP:
        name.endsWith("Context")||name.equals("Region")?SemanticType.SUBJECT:SemanticType.QUALITY));if(k.getType().contains(SemanticType.SUBJECT)||k.getType().contains(SemanticType.RELATIONSHIP))k.getType().add(SemanticType.COUNTABLE);k.resetDefinition();return k;}
  static KimConceptImpl and(String left,String right){var k=leaf(left);k.setExpressionType(KimConcept.Expression.INTERSECTION);k.getOperands().add(leaf(right));k.resetDefinition();return k;}
  static ConsequenceRuleResponse.Entry recipe(KimConcept target,KimConcept within) {
    var owner=new ConceptImpl();owner.setUrn("audit:Pattern");
    var rule=new KimConsequenceRule(KimConsequenceRule.Kind.EMERGES_FROM,target,KimConsequenceRule.Recipient.implicit(),null,within,
        new KimConceptStatement.DeclarationClause("emergesFromClause","recipe",0,6));
    return new ConsequenceRuleResponse.Entry("rule","audit:Pattern",owner,rule,"audit","1","hash");
  }
  static ConsequencePatternMatcher.Fact quality(long id,String name,long bearer){return new ConsequencePatternMatcher.Fact(id,"audit:"+name,"1",true,bearer,0);}
  static ConsequencePatternMatcher.Fact bearer(long id){return new ConsequencePatternMatcher.Fact(id,"audit:Region","1",false,0,99);}
  static ConsequencePatternMatcher.Snapshot snapshot(List<ConsequencePatternMatcher.Fact> facts,Map<ConsequencePatternMatcher.Placement,ConsequencePatternMatcher.Fact> contexts,boolean complete) {
    var assets=new LinkedHashMap<Long,KnowledgeGraph.ConsequenceAssetSnapshot>();
    var all=new ArrayList<>(facts);all.addAll(contexts.values());
    for(var f:all)assets.put(f.id(),new KnowledgeGraph.ConsequenceAssetSnapshot(f.id(),"element-"+f.id(),List.of("Observation"),Map.of("observable",f.semantics()),f.resolvedSupport(),null));
    assets.put(7L,new KnowledgeGraph.ConsequenceAssetSnapshot(7,"observer",List.of("Observation"),Map.of("observable","audit:Agent"),"1",null));
    var read=new KnowledgeGraph.ConsequenceReadSet("context",List.copyOf(assets.values()),List.of(),List.of());
    return new ConsequencePatternMatcher.Snapshot("context",7,EPOCH,facts,contexts,complete,read);
  }
  static ConsequencePatternMatcher.Semantics semantics(AtomicReference<ConsequenceRuleResponse.Epoch> epoch,ConsequencePatternMatcher.Decision applies) {
    return new ConsequencePatternMatcher.Semantics(){
      public ConsequenceRuleResponse.Epoch epoch(){return epoch.get();}
      public ConsequencePatternMatcher.Decision matches(String actual,KimConcept expected){return actual.equals(expected.getUrn())?ConsequencePatternMatcher.Decision.YES:ConsequencePatternMatcher.Decision.NO;}
      public ConsequencePatternMatcher.Decision applicable(ConsequenceRuleResponse.Entry rule,ConsequencePatternMatcher.Placement p,ConsequencePatternMatcher.Fact context){return applies;}
    };
  }
  static ConsequencePatternMatcher.Semantics semantics(){return semantics(new AtomicReference<>(EPOCH),ConsequencePatternMatcher.Decision.YES);}
  @Test void commonBearerAndPreservesEveryFactAndNativeReadGuard() {
    var input=snapshot(List.of(quality(1,"Elevation",100),quality(2,"Slope",100),quality(3,"Elevation",100)),Map.of(BEARER,bearer(100)),true);
    var result=new ConsequencePatternMatcher().evaluate(recipe(and("Elevation","Slope"),leaf("Region")),input,semantics(),(expression,clauses,p)->{
      assertEquals(2,clauses.size());assertEquals(3,clauses.values().stream().mapToInt(List::size).sum());return "1";
    });
    assertEquals(ConsequencePatternMatcher.Outcome.MATCH,result.outcome());var match=result.matches().getFirst();
    assertEquals(List.of(1L,2L,3L),match.ingredientIds());assertEquals(BEARER,match.placement());
    assertEquals(100L,match.contextObservationId());assertSame(input.reads(),match.reads());
  }
  @Test void booleanOrPreservesSuccessfulAlternativeWithoutDemandingTheOther() {
    var expression=and("Elevation","Slope");expression.setExpressionType(KimConcept.Expression.UNION);expression.resetDefinition();
    var input=snapshot(List.of(quality(1,"Elevation",100)),Map.of(BEARER,bearer(100)),true);
    var result=new ConsequencePatternMatcher().evaluate(recipe(expression,null),input,semantics(),(e,c,p)->"1");
    assertEquals(ConsequencePatternMatcher.Outcome.MATCH,result.outcome());assertEquals(1,result.matches().getFirst().matchedClauses().size());
  }
  @Test void ingredientsSplitAcrossBearersDeferWithoutInventingPlacement() {
    var other=new ConsequencePatternMatcher.Placement(ConsequencePatternMatcher.PlacementKind.DEPENDENT_BEARER,200);
    var input=snapshot(List.of(quality(1,"Elevation",100),quality(2,"Slope",200)),Map.of(BEARER,bearer(100),other,bearer(200)),true);
    var result=new ConsequencePatternMatcher().evaluate(recipe(and("Elevation","Slope"),null),input,semantics(),(e,c,p)->"1");
    assertEquals(ConsequencePatternMatcher.Outcome.DEFERRED,result.outcome());assertTrue(result.matches().isEmpty());
  }
  @Test void relationshipFactsKeepAllIdsUnderTheirRootCohort() {
    var input=snapshot(List.of(new ConsequencePatternMatcher.Fact(1,"audit:Connection","1",false,0,300),new ConsequencePatternMatcher.Fact(2,"audit:Connection","1",false,0,300)),Map.of(),true);
    var result=new ConsequencePatternMatcher().evaluate(recipe(leaf("Connection"),null),input,semantics(),(e,c,p)->"1");
    assertEquals(ConsequencePatternMatcher.Outcome.MATCH,result.outcome());
    assertEquals(ConsequencePatternMatcher.PlacementKind.ROOT_COHORT,result.matches().getFirst().placement().kind());
    assertEquals(List.of(1L,2L),result.matches().getFirst().ingredientIds());
  }
  @Test void incompleteEmptyAndSuccessfullyEmptyAreDifferent() {
    var matcher=new ConsequencePatternMatcher();var rule=recipe(leaf("Elevation"),null);
    assertEquals(ConsequencePatternMatcher.Outcome.DEFERRED,matcher.evaluate(rule,snapshot(List.of(),Map.of(),false),semantics(),null).outcome());
    assertEquals(ConsequencePatternMatcher.Outcome.NO_MATCH,matcher.evaluate(rule,snapshot(List.of(),Map.of(),true),semantics(),null).outcome());
  }
  @Test void applicabilityAndWithinRemainIndependentOfIngredientPresence() {
    var input=snapshot(List.of(quality(1,"Elevation",100)),Map.of(BEARER,bearer(100)),true);var matcher=new ConsequencePatternMatcher();
    assertEquals(ConsequencePatternMatcher.Outcome.NO_MATCH,matcher.evaluate(recipe(leaf("Elevation"),leaf("OtherContext")),input,semantics(),(e,c,p)->"1").outcome());
    assertEquals(ConsequencePatternMatcher.Outcome.DEFERRED,matcher.evaluate(recipe(leaf("Elevation"),null),input,semantics(new AtomicReference<>(EPOCH),ConsequencePatternMatcher.Decision.UNAVAILABLE),(e,c,p)->"1").outcome());
  }
  @Test void semanticDriftAndUnknownSupportCannotPublishPartialMatches() {
    var input=snapshot(List.of(quality(1,"Elevation",100)),Map.of(BEARER,bearer(100)),true);var matcher=new ConsequencePatternMatcher();
    var epoch=new AtomicReference<>(EPOCH);
    var result=matcher.evaluate(recipe(leaf("Elevation"),null),input,semantics(epoch,ConsequencePatternMatcher.Decision.YES),(e,c,p)->{epoch.set(new ConsequenceRuleResponse.Epoch("reasoner",2,EPOCH.semanticFingerprint(),"session"));return "1";});
    assertEquals(ConsequencePatternMatcher.Outcome.DEFERRED,result.outcome());assertTrue(result.matches().isEmpty());
    assertEquals(ConsequencePatternMatcher.Outcome.DEFERRED,matcher.evaluate(recipe(leaf("Elevation"),null),input,semantics(),(e,c,p)->null).outcome());
    assertEquals(ConsequencePatternMatcher.Outcome.NO_MATCH,matcher.evaluate(recipe(leaf("Elevation"),null),input,semantics(),(e,c,p)->"X").outcome());
  }
  @Test void nativeCaptureMustCoverFactsAndContextAndNegationDefers() {
    var input=snapshot(List.of(quality(1,"Elevation",100)),Map.of(BEARER,bearer(100)),true);
    assertThrows(IllegalArgumentException.class,()->new ConsequencePatternMatcher.Snapshot("wrong",7,EPOCH,input.facts(),input.contexts(),true,input.reads()));
    assertThrows(IllegalArgumentException.class,()->new ConsequencePatternMatcher.Snapshot("context",7,EPOCH,List.of(quality(9,"Elevation",100)),input.contexts(),true,input.reads()));
    var negative=leaf("Elevation");negative.setNegated(true);negative.resetDefinition();
    assertEquals(ConsequencePatternMatcher.Outcome.DEFERRED,new ConsequencePatternMatcher().evaluate(recipe(negative,null),input,semantics(),(e,c,p)->"1").outcome());
  }
  @Test void constrainedObservableWrappersCannotLosePatternValueOrContextualization() {
    var input=snapshot(List.of(quality(1,"Elevation",100)),Map.of(BEARER,bearer(100)),true);
    for(var restriction:List.of("pattern","value","contextualization","parent")) {
      var wrapper=leaf("Elevation");wrapper.setName(null);wrapper.setObservable(leaf("Elevation"));wrapper.resetDefinition();
      switch(restriction) {
        case "pattern"->wrapper.setPattern(true);
        case "parent"->wrapper.setParent(leaf("Region"));
        case "value"->wrapper.getValueOperators().add(new org.integratedmodelling.klab.api.collections.impl.PairImpl<>(org.integratedmodelling.klab.api.lang.ValueOperator.GREATER,0));
        case "contextualization"->wrapper.setDescriptionType(org.integratedmodelling.klab.api.knowledge.Contextualization.INSTANTIATION);
      }
      var result=new ConsequencePatternMatcher().evaluate(recipe(wrapper,null),input,semantics(),(e,c,p)->"1");
      assertEquals(ConsequencePatternMatcher.Outcome.DEFERRED,result.outcome(),restriction);
    }
    var nativeWrapper=leaf("Elevation");nativeWrapper.setName(null);nativeWrapper.setObservable(leaf("Elevation"));nativeWrapper.setInherent(leaf("Region"));nativeWrapper.resetDefinition();
    assertSame(nativeWrapper,ConsequenceRuleIndex.positiveLeaves(nativeWrapper).getFirst());
    assertEquals("audit:Region",ConsequenceRuleIndex.positiveLeaves(nativeWrapper).getFirst().getInherent().getUrn());
  }
  @Test void semanticDriftDuringFinalNoMatchCallbackDefersInsteadOfCertifyingAbsence() {
    var epoch=new AtomicReference<>(EPOCH);var calls=new java.util.concurrent.atomic.AtomicInteger();
    var input=snapshot(List.of(quality(1,"Slope",100)),Map.of(BEARER,bearer(100)),true);
    var semantics=new ConsequencePatternMatcher.Semantics(){
      public ConsequenceRuleResponse.Epoch epoch(){return epoch.get();}
      public ConsequencePatternMatcher.Decision matches(String actual,KimConcept expected){
        if(calls.incrementAndGet()==2)epoch.set(new ConsequenceRuleResponse.Epoch("reasoner",2,EPOCH.semanticFingerprint(),"session"));
        return ConsequencePatternMatcher.Decision.NO;
      }
      public ConsequencePatternMatcher.Decision applicable(ConsequenceRuleResponse.Entry rule,ConsequencePatternMatcher.Placement p,ConsequencePatternMatcher.Fact context){return ConsequencePatternMatcher.Decision.YES;}
    };
    var result=new ConsequencePatternMatcher().evaluate(recipe(leaf("Elevation"),null),input,semantics,(e,c,p)->"1");
    assertEquals(2,calls.get());assertEquals(ConsequencePatternMatcher.Outcome.DEFERRED,result.outcome());assertTrue(result.matches().isEmpty());
  }
}
