package org.integratedmodelling.klab.services.runtime.digitaltwin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher;
import org.junit.jupiter.api.*;
class ConfigurationObserverFilterTest {
 ConfigurationNativeInputsTest f;Reasoner reasoner;Concept good,bad;ObservableImpl expected;ConfigurationNativeInputs.Capture captured;
 @BeforeAll static void configure(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
 @BeforeEach void setup()throws Exception {
  f=new ConfigurationNativeInputsTest();f.setup();reasoner=mock(Reasoner.class);when(f.scope.getService(Reasoner.class)).thenReturn(reasoner);
  good=DefaultConfigurationBackendTest.concept("test:GoodAgent",SemanticType.AGENT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE);
  bad=DefaultConfigurationBackendTest.concept("test:BadAgent",SemanticType.AGENT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE);
  var agent=ConfigurationNativeInputsTest.observation(99,false);agent.getObservable().getSemantics().getType().remove(SemanticType.SUBJECT);agent.getObservable().getSemantics().getType().add(SemanticType.AGENT);agent.setUrn("context.99");
  f.assets.put(99L,new KnowledgeGraph.ConsequenceAssetSnapshot(99,"e99",List.of("Observation"),Map.of(),"1",null));f.observations.put(99L,agent);
  when(f.scope.getObserverId()).thenReturn(99L);when(f.scope.getObserver()).thenThrow(new AssertionError("Comparison must not refresh the live selected Agent"));
  var actual=f.observations.get(1L).getObservable();expected=new ObservableImpl();expected.setSemantics(actual.asConcept());expected.setUrn(actual.getUrn());expected.setDescriptionType(Contextualization.CONNECTION);
  ((ObservableImpl)actual).setObserverSemantics(good);
  var edges=new ArrayList<>(f.edges);
  for(long id:List.of(2L,3L)) {
   var member=ConfigurationNativeInputsTest.observation(id,true);var observable=new ObservableImpl();observable.setSemantics(actual.asConcept());observable.setUrn(actual.getUrn());observable.setDescriptionType(Contextualization.CONNECTION);if(id==2)observable.setObserverSemantics(bad);member.setObservable(observable);
   f.observations.put(id,member);f.assets.put(id,new KnowledgeGraph.ConsequenceAssetSnapshot(id,"e"+id,List.of("Observation"),Map.of(),"1",null));
   edges.add(ConfigurationNativeInputsTest.edge(10,id,GraphModel.Relationship.HAS_MEMBER));edges.add(ConfigurationNativeInputsTest.edge(id,11,GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE));edges.add(ConfigurationNativeInputsTest.edge(id,12,GraphModel.Relationship.HAS_RELATIONSHIP_TARGET));
  }
  f.edges=edges;captured=ConfigurationNativeInputs.capture(new ConsequencePatternMatcher.Placement(ConsequencePatternMatcher.PlacementKind.ROOT_COHORT,10),99,f.scope);
  when(reasoner.resolveObservable("test:Meaning1")).thenReturn(expected);when(reasoner.is(any(),any())).thenReturn(true);
  var caps=mock(Reasoner.Capabilities.class);when(caps.getServiceId()).thenReturn(ConfigurationComponentsTest.EPOCH.serviceId());when(caps.getKnowledgeRevision()).thenReturn(1L);when(caps.getSemanticContentFingerprint()).thenReturn(ConfigurationComponentsTest.EPOCH.semanticFingerprint());when(caps.getSemanticSessionId()).thenReturn(ConfigurationComponentsTest.EPOCH.semanticSessionId());when(reasoner.capabilities(any())).thenReturn(caps);
  when(reasoner.semanticDependencies(any(),any())).thenAnswer(call->{var signature=call.getArgument(0,SemanticDependencyRequest.class);return new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,signature.expectedServiceId(),signature.expectedKnowledgeRevision(),signature.expectedBaseFingerprint(),signature.inputHash(),List.of(),null);});
  when(reasoner.semanticDistance(any(),any(),isNull(),eq(1L))).thenAnswer(call->{var candidate=call.getArgument(0,Semantics.class);var request=call.getArgument(1,Semantics.class);
   if(candidate instanceof org.integratedmodelling.klab.api.knowledge.Observable observable&&bad.equals(observable.getObserverSemantics()))return -1;
   return candidate.asConcept().getUrn().equals(bad.getUrn())?-1:0;
  });
 }
 ConfigurationObserverFilter.Result filter(ConfigurationNativeInputs.Capture inputs) {
  var snapshot=inputs.snapshot(new ConfigurationNativeInputs.ConsequenceRuleEpoch(ConfigurationComponentsTest.EPOCH),99);
  return ConfigurationObserverFilter.filter(inputs,snapshot,List.of(DefaultConfigurationBackendTest.recipe("test:Meaning1")),f.scope);
 }
 @Test void normalCompatibilityKeepsSpecificAndAbsoluteButExcludesIncompatibleObservedMember() {
  var result=filter(captured);assertEquals(List.of(1L,3L),result.snapshot().facts().stream().map(ConsequencePatternMatcher.Fact::id).toList());assertSame(captured.reads(),result.snapshot().reads());
  assertTrue(result.snapshot().reads().assetIds().contains(2L));assertTrue(result.semanticTerms().containsAll(List.of(good.getUrn(),bad.getUrn(),"test:Meaning99")));
 }
 @Test void explicitExpectedObserverDoesNotFallBackToAnAbsoluteMemberAndRetainsRejectedTerms() {
  expected.setObserverSemantics(good);var result=filter(captured);
  assertEquals(List.of(1L),result.snapshot().facts().stream().map(ConsequencePatternMatcher.Fact::id).toList());assertTrue(result.semanticTerms().containsAll(List.of(good.getUrn(),bad.getUrn())));
 }
 @Test void missingCapturedAgentOrComparisonEpochIsUnavailableRatherThanSuccessfullyEmpty() {
  var observations=new HashMap<>(captured.observations());observations.remove(99L);
  var missing=new ConfigurationNativeInputs.Capture(captured.reads(),observations,captured.facts(),captured.contexts(),captured.relationships());
  assertThrows(ConfigurationObserverFilter.Unavailable.class,()->filter(missing));
  when(reasoner.capabilities(any())).thenReturn(null);assertThrows(ConfigurationObserverFilter.Unavailable.class,()->filter(captured));
 }
 @Test void revisionUnavailableDistanceCannotMasqueradeAsOrdinaryIncompatibility() {
  doReturn(-10000).when(reasoner).semanticDistance(any(),any(),isNull(),eq(1L));assertThrows(ConfigurationObserverFilter.Unavailable.class,()->filter(captured));
 }

 @Test void uncertifiedRejectedQualifierIsUnavailableEvenWhenNoCandidateWouldSurvive() {
  expected.setObserverSemantics(bad);doReturn(-1).when(reasoner).semanticDistance(any(),any(),isNull(),eq(1L));
  doAnswer(call->SemanticDependencyWitness.unknown(call.getArgument(0),"unknown qualifier authority")).when(reasoner).semanticDependencies(any(),any());
  assertThrows(ConfigurationObserverFilter.Unavailable.class,()->filter(captured));
 }

 @Test void abstractAuthoredParentUsesConcreteNeutralObserverRequestWithoutDroppingRepresentation() {
  var parent=DefaultConfigurationBackendTest.concept("test:AbstractParent",SemanticType.RELATIONSHIP,SemanticType.COUNTABLE,SemanticType.OBSERVABLE);parent.setAbstract(true);expected.setSemantics(parent);expected.setUrn(parent.getUrn());expected.setAbstract(true);
  doAnswer(call->{var request=call.getArgument(1,Semantics.class);assertFalse(request.asConcept().isAbstract(),"Pattern abstractness is not executable request eligibility");return 0;}).when(reasoner).semanticDistance(any(),any(),isNull(),eq(1L));
  assertEquals(3,filter(captured).snapshot().facts().size());
  var actual=(ObservableImpl)f.observations.get(1L).getObservable();actual.setUrn(actual.asConcept().getUrn()+" observed as "+good.getUrn()+" in m");actual.setUnit(mock(org.integratedmodelling.klab.api.data.mediation.Unit.class));
  var neutral=ConfigurationObserverFilter.observerRequest(actual,expected);assertEquals(actual.asConcept().getUrn()+" in m",neutral.getUrn());assertSame(actual.asConcept(),neutral.asConcept());assertSame(actual.getUnit(),neutral.getUnit());assertNull(neutral.getObserverSemantics());assertSame(good,actual.getObserverSemantics());
 }
}
