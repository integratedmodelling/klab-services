package org.integratedmodelling.klab.services.runtime.digitaltwin;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph.*;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.services.reasoner.objects.ConsequenceRuleResponse;
import org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
/** Complete native bearer family: independent of actual scientific publication acceptance. */
class ConfigurationRelevanceCaptureTest {
 static final ConsequencePatternMatcher.Placement PLACE=new ConsequencePatternMatcher.Placement(ConsequencePatternMatcher.PlacementKind.DEPENDENT_BEARER,10);
 static ConfigurationNativeInputsTest fixture(double heightCoverage,double changeCoverage)throws Exception {
  var f=new ConfigurationNativeInputsTest();f.setup();f.assets.clear();f.observations.clear();
  for(long id:List.of(1L,2L,3L,10L)) {
   var o=ConfigurationNativeInputsTest.observation(id,false);
   var c=new ConceptImpl();c.setUrn(id==1?"imod:Height":id==2?"imod:Width":id==3?"change in imod:Height":"imod:Bearer");
   c.setType(id==3?EnumSet.of(SemanticType.PROCESS,SemanticType.OBSERVABLE):id==10?EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE):EnumSet.of(SemanticType.QUALITY,SemanticType.LENGTH,SemanticType.QUANTIFIABLE,SemanticType.OBSERVABLE));
   c.setDescriptionType(id==3?Contextualization.SIMULATION:Contextualization.ACKNOWLEDGEMENT);var observable=new ObservableImpl();observable.setSemantics(c);observable.setUrn(c.getUrn());observable.setDescriptionType(c.getDescriptionType());o.setObservable(observable);
   o.setResolvedCoverage(id==1?heightCoverage:id==3?changeCoverage:1);
   f.observations.put(id,o);f.assets.put(id,new ConsequenceAssetSnapshot(id,"e"+id,List.of(GraphModel.Labels.OBSERVATION),Map.of(GraphModel.Fields.OBSERVABLE,c.getUrn()),"1",null));
  }
  f.edges=List.of(edge(10,1),edge(10,2),edge(10,3));return f;
 }
 static ConsequenceEdgeSnapshot edge(long source,long target){return new ConsequenceEdgeSnapshot(source+":child:"+target,new ConsequenceAssetRef("e"+source,source,List.of(GraphModel.Labels.OBSERVATION)),new ConsequenceAssetRef("e"+target,target,List.of(GraphModel.Labels.OBSERVATION)),GraphModel.Relationship.HAS_CHILD,Map.of());}
 @BeforeAll static void configure(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
 static KimConceptImpl leaf(String urn){var c=new KimConceptImpl();c.setName(urn);c.setType(EnumSet.of(SemanticType.QUALITY,SemanticType.OBSERVABLE));c.resetDefinition();return c;}
 static ConsequenceRuleResponse.Entry recipe(String target){
  var expression=leaf(target);if(target.equals("imod:Height")){expression.setOperands(new ArrayList<>(List.of(leaf("imod:Width"))));expression.setExpressionType(KimConcept.Expression.INTERSECTION);expression.getType().add(SemanticType.INTERSECTION);expression.resetDefinition();}
  return new ConsequenceRuleResponse.Entry("pair","imod:Pair",DefaultConfigurationBackendTest.concept("imod:Pair",SemanticType.CONFIGURATION,SemanticType.OBSERVABLE),new KimConsequenceRule(KimConsequenceRule.Kind.EMERGES_FROM,expression,KimConsequenceRule.Recipient.implicit(),null,null,new KimConceptStatement.DeclarationClause("emergesFromClause",target,0,target.length())),"imod","1","a".repeat(64));
 }
 static ConsequencePatternMatcher.Semantics semantics(){return new ConsequencePatternMatcher.Semantics(){
  public ConsequenceRuleResponse.Epoch epoch(){return DefaultConfigurationBackendTest.EPOCH;}
  public ConsequencePatternMatcher.Decision matches(String actual,KimConcept expected){return actual.equals(expected.getUrn())?ConsequencePatternMatcher.Decision.YES:ConsequencePatternMatcher.Decision.NO;}
  public ConsequencePatternMatcher.Decision applicable(ConsequenceRuleResponse.Entry r,ConsequencePatternMatcher.Placement p,ConsequencePatternMatcher.Fact c){return ConsequencePatternMatcher.Decision.YES;}
 };}
 static ConsequencePatternMatcher.Support support()throws Exception{var method=DefaultConfigurationBackend.class.getDeclaredMethod("support");method.setAccessible(true);return (ConsequencePatternMatcher.Support)method.invoke(null);}
 static ConsequencePatternMatcher.Snapshot snapshot(ConfigurationNativeInputs.Capture c){return c.snapshot(new ConfigurationNativeInputs.ConsequenceRuleEpoch(DefaultConfigurationBackendTest.EPOCH),0);}
 @Test void completeQualitiesAndUnrelatedUnresolvedChangeMatchWithoutShrinkingNativeGuard()throws Exception {
  var f=fixture(1,0);var captured=ConfigurationNativeInputs.capture(PLACE,0,f.scope);var original=snapshot(captured);
  assertEquals(List.of(1L,2L,3L,10L),captured.reads().assetIds());assertEquals(3,captured.facts().size());
  assertNull(captured.facts().stream().filter(x->x.id()==3).findFirst().orElseThrow().resolvedSupport());
  var parts=DefaultConfigurationBackend.partition(original,captured.relationships(),List.of(recipe("imod:Height")),semantics());
  assertEquals(1,parts.size());assertSame(original.reads(),parts.getFirst().reads());assertEquals(List.of(1L,2L),parts.getFirst().facts().stream().map(ConsequencePatternMatcher.Fact::id).toList());
  assertTrue(parts.getFirst().reads().assetIds().contains(3L));assertTrue(parts.getFirst().reads().selections().contains(new ConsequenceEdgeSelection(10,GraphModel.Relationship.HAS_CHILD,GraphModel.Relationship.Direction.OUTGOING)));
  var matched=new ConsequencePatternMatcher().evaluate(recipe("imod:Height"),parts.getFirst(),semantics(),support());assertEquals(ConsequencePatternMatcher.Outcome.MATCH,matched.outcome(),matched.reason());assertEquals(List.of(1L,2L),matched.matches().getFirst().ingredientIds());
 }
 @Test void genuinelyRelevantMissingPartialOrUnknownSupportStillDefers()throws Exception {
  for(double coverage:new double[]{0,0.5}){
   var f=fixture(coverage,1);var captured=ConfigurationNativeInputs.capture(PLACE,0,f.scope);assertNull(captured.facts().getFirst().resolvedSupport());
   assertThrows(RuntimeException.class,()->DefaultConfigurationBackend.partition(snapshot(captured),captured.relationships(),List.of(recipe("imod:Height")),semantics()));
   assertEquals(ConsequencePatternMatcher.Outcome.DEFERRED,new ConsequencePatternMatcher().evaluate(recipe("imod:Height"),snapshot(captured),semantics(),support()).outcome());
  }
 }
 @Test void unresolvedProcessIsNotGloballyIgnoredWhenARecipeActuallyRequiresIt()throws Exception {
  var f=fixture(1,0);var c=ConfigurationNativeInputs.capture(PLACE,0,f.scope);
  assertThrows(RuntimeException.class,()->DefaultConfigurationBackend.partition(snapshot(c),c.relationships(),List.of(recipe("change in imod:Height")),semantics()));
 }
 @Test void unavailableRelevanceCannotBecomeDefinitiveExclusion()throws Exception {
  var f=fixture(1,0);var c=ConfigurationNativeInputs.capture(PLACE,0,f.scope);var semantic=mock(ConsequencePatternMatcher.Semantics.class);
  when(semantic.matches(any(),any())).thenReturn(ConsequencePatternMatcher.Decision.UNAVAILABLE);
  assertThrows(RuntimeException.class,()->DefaultConfigurationBackend.partition(snapshot(c),c.relationships(),List.of(recipe("imod:Height")),semantic));
 }
 @Test void malformedCoverageIsStillRejectedRatherThanTurnedIntoAnEmptyFootprint()throws Exception {
  for(double coverage:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,1.5}){var f=fixture(1,coverage);assertThrows(ConfigurationComponents.UnsupportedGrouping.class,()->ConfigurationNativeInputs.capture(PLACE,0,f.scope));}
 }
}
