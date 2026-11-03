package org.integratedmodelling.klab.services.runtime.digitaltwin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.digitaltwin.impl.CommitImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher;
import org.integratedmodelling.klab.services.runtime.RuntimeService;
import org.junit.jupiter.api.*;
class DefaultConfigurationBackendTest {
 @BeforeAll static void configure(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
 static final String FP=ConfigurationConsequenceRuntimeTest.FINGERPRINT;
 static final ConsequenceRuleResponse.Epoch EPOCH=new ConsequenceRuleResponse.Epoch("reasoner",1,FP,"session");
 static ConceptImpl concept(String urn,SemanticType... types){var c=new ConceptImpl();c.setUrn(urn);c.setType(EnumSet.copyOf(List.of(types)));c.setDescriptionType(c.is(SemanticType.CONFIGURATION)?Contextualization.DETECTION:Contextualization.ACKNOWLEDGEMENT);return c;}
 static ConsequenceRuleResponse.Entry recipe(String target) {
  var leaf=new KimConceptImpl();leaf.setName(target);leaf.setType(EnumSet.of(target.endsWith("Subject")?SemanticType.SUBJECT:SemanticType.RELATIONSHIP,SemanticType.OBSERVABLE,SemanticType.COUNTABLE));leaf.resetDefinition();
  return new ConsequenceRuleResponse.Entry("rule","test:Network",concept("test:Network",SemanticType.CONFIGURATION,SemanticType.OBSERVABLE),
    new KimConsequenceRule(KimConsequenceRule.Kind.EMERGES_FROM,leaf,KimConsequenceRule.Recipient.implicit(),null,null,new KimConceptStatement.DeclarationClause("emergesFromClause",target,0,target.length())),"test","1","a".repeat(64));
 }
 @Test void unrelatedRelationshipsCannotBridgeTwoAuthoredStructuringNetworks() {
  var original=ConfigurationComponentsTest.snapshot(new long[][]{{1,10,11},{2,11,20},{3,20,21}});
  var facts=original.facts().stream().map(f->new ConsequencePatternMatcher.Fact(f.id(),f.id()==2?"test:Other":"test:Wire",f.resolvedSupport(),false,0,100)).toList();
  var input=new ConsequencePatternMatcher.Snapshot(original.contextId(),0,EPOCH,facts,Map.of(),true,original.reads());
  var semantics=mock(ConsequencePatternMatcher.Semantics.class);
  when(semantics.matches(any(),any())).thenAnswer(call->call.getArgument(0,String.class).equals(call.getArgument(1,KimConcept.class).getUrn())?ConsequencePatternMatcher.Decision.YES:ConsequencePatternMatcher.Decision.NO);
  var parts=DefaultConfigurationBackend.partition(input,ConfigurationComponentsTest.kinds(Set.of(1L,2L,3L)),List.of(recipe("test:Wire")),semantics);
  assertEquals(List.of(List.of(1L),List.of(3L)),parts.stream().map(p->p.facts().stream().map(ConsequencePatternMatcher.Fact::id).toList()).toList());
 }
 @Test void bareSubjectsCannotManufactureRootConfigurationFromCoOccurrence() {
  var input=ConfigurationComponentsTest.snapshot(new long[][]{{1,10,11}});var semantics=mock(ConsequencePatternMatcher.Semantics.class);
  when(semantics.matches(any(),any())).thenReturn(ConsequencePatternMatcher.Decision.YES);
  var failure=assertThrows(RuntimeException.class,()->DefaultConfigurationBackend.partition(input,Map.of(),List.of(recipe("test:Subject")),semantics));
  assertTrue(failure.getMessage().contains("structuring relationship"));
 }
 static final class Fixture {
  final ConfigurationNativeInputsTest nativeInputs=new ConfigurationNativeInputsTest();final RuntimeService runtime=mock(RuntimeService.class);
  final Reasoner reasoner=mock(Reasoner.class);final GuardedConfigurationPublisher publisher=mock(GuardedConfigurationPublisher.class);
  final CommitImpl commit=new CommitImpl();final Consequence.Cause cause=new Consequence.Cause(1,"context",10,100,0,FP,null,"configuration",Consequence.Kind.CONFIGURATION_DETECTION);
  final DefaultConfigurationBackend backend;
  final Map<Long,List<RuntimeAsset>> nativeRows=new HashMap<>();
  Fixture()throws Exception {
   nativeInputs.setup();var scope=nativeInputs.scope;when(scope.getObserverId()).thenReturn(null);commit.setId(10);commit.getAddedObservations().add(1L);
   nativeInputs.assets.put(100L,new ConsequenceAssetSnapshot(100,"activity",List.of("Activity"),Map.of("outcome","SUCCESS"),null,null));
   when(scope.getResolutionCycleBinding()).thenReturn(new ResolutionCycleBinding(1,"cycle","context","runtime",true,new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",1,FP,"lease")));
   when(scope.getService(Reasoner.class)).thenReturn(reasoner);when(runtime.serviceId()).thenReturn("runtime");
   doAnswer(call->{Collection<Long> ids=call.getArgument(0);Collection<ConsequenceEdgeSelection> selections=call.getArgument(1);
    var selected=nativeInputs.edges.stream().filter(edge->selections.stream().anyMatch(selection->selection.relationship()==edge.relationship()&&Objects.equals(selection.direction()==GraphModel.Relationship.Direction.OUTGOING?edge.source().id():edge.target().id(),selection.assetId()))).toList();
    return new ConsequenceReadSet("context",ids.stream().sorted().map(nativeInputs.assets::get).toList(),List.copyOf(selections),selected);
   }).when(nativeInputs.transaction).captureConsequenceInputs(anyCollection(),anyCollection());
   when(nativeInputs.graph.query(eq(RuntimeAsset.class),eq(scope))).thenAnswer(call->new org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery<RuntimeAsset>(org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery.AssetType.ANY){
    @Override public List<RuntimeAsset> run(org.integratedmodelling.klab.api.scope.Scope execution) {
     assertSame(scope,execution);long id=((Number)getCriteria().getFirst().argument()).longValue();
     if(nativeRows.containsKey(id))return nativeRows.get(id);
     var snapshot=nativeInputs.assets.get(id);if(snapshot==null)return List.of();
     if(snapshot.labels().contains(GraphModel.Labels.OBSERVATION))return nativeInputs.observations.containsKey(id)?List.of(nativeInputs.observations.get(id)):List.of();
     Class<? extends RuntimeAsset> type=snapshot.labels().contains(GraphModel.Labels.DATA)?Storage.Shard.class:
         snapshot.labels().contains(GraphModel.Labels.ACTUATOR)?org.integratedmodelling.klab.api.services.runtime.Actuator.class:
         snapshot.labels().contains(GraphModel.Labels.COHORT)?Cohort.class:org.integratedmodelling.klab.api.provenance.Activity.class;
     var asset=mock(type);when(asset.getId()).thenReturn(id);return List.of(asset);
    }
   });
   backend=new DefaultConfigurationBackend(runtime,scopeArgument->scopeArgument);var field=DefaultConfigurationBackend.class.getDeclaredField("publisher");field.setAccessible(true);field.set(backend,publisher);
  }
 }
 @Test void changedFactEnumerationUsesRetainedEpochAndNeverFallsBackFromUnavailableSource()throws Exception {
  var f=new Fixture();when(f.reasoner.consequenceRules(any())).thenReturn(new ConsequenceRuleResponse(ConsequenceRuleResponse.Status.UNAVAILABLE,null,List.of(),null,"unavailable"));
  assertEquals(Consequence.Outcome.DEFERRED,f.backend.evaluate(f.cause,f.commit,f.nativeInputs.scope).outcome());
  var query=org.mockito.ArgumentCaptor.forClass(ConsequenceRuleQuery.class);verify(f.reasoner).consequenceRules(query.capture());
  assertEquals(List.of("test:Meaning1"),query.getValue().ingredientTriggers().stream().map(Concept::getUrn).toList());assertEquals(EPOCH,query.getValue().expectedEpoch());verifyNoInteractions(f.publisher);
 }
 static Fixture matchedFixture()throws Exception {
  var f=new Fixture();var recipe=recipe("test:Meaning1");var owner=recipe.owner();
  when(f.reasoner.resolveObservable("test:Meaning1")).thenReturn(f.nativeInputs.observations.get(1L).getObservable());
  var second=ConfigurationNativeInputsTest.observation(2,true);second.setObservable(f.nativeInputs.observations.get(1L).getObservable());
  f.nativeInputs.observations.put(2L,second);f.nativeInputs.assets.put(2L,new ConsequenceAssetSnapshot(2,"e2",List.of("Observation"),Map.of(),"1",null));
  for(long id:List.of(21L,22L)){f.nativeInputs.observations.put(id,ConfigurationNativeInputsTest.observation(id,false));f.nativeInputs.assets.put(id,new ConsequenceAssetSnapshot(id,"e"+id,List.of("Observation"),Map.of(),"1",null));}
  var edges=new ArrayList<>(f.nativeInputs.edges);edges.add(ConfigurationNativeInputsTest.edge(10,2,GraphModel.Relationship.HAS_MEMBER));edges.add(ConfigurationNativeInputsTest.edge(2,21,GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE));edges.add(ConfigurationNativeInputsTest.edge(2,22,GraphModel.Relationship.HAS_RELATIONSHIP_TARGET));f.nativeInputs.edges=edges;
  var cohort=mock(Cohort.class);when(cohort.getId()).thenReturn(90L);f.nativeInputs.assets.put(90L,new ConsequenceAssetSnapshot(90,"e90",List.of("Cohort"),Map.of(),"1",null));when(f.runtime.getConfigurationCohortFor(owner,f.nativeInputs.scope,true)).thenReturn(cohort);
  when(f.nativeInputs.scope.getObserverId()).thenReturn(null);
  var headers=List.of(new ConsequenceAssetSnapshot(10,"e10",List.of("Cohort"),Map.of(GraphModel.Fields.OBSERVABLE,"test:Meaning1"),null,null),new ConsequenceAssetSnapshot(90,"e90",List.of("Cohort"),Map.of(GraphModel.Fields.OBSERVABLE,owner.getUrn()),null,null));
  var catalogueEdges=headers.stream().map(h->new ConsequenceEdgeSnapshot("catalogue"+h.id(),new ConsequenceAssetRef("ctx","context",List.of("Context")),new ConsequenceAssetRef(h.elementId(),h.id(),h.labels()),GraphModel.Relationship.HAS_CHILD,Map.of())).toList();
  when(f.nativeInputs.transaction.captureContextCohortCatalogue()).thenReturn(new ContextCohortCatalogue("context","ctx",0,headers,catalogueEdges));
  when(f.reasoner.resolveConcept(owner.getUrn())).thenReturn(owner);
  when(f.reasoner.resolveObservable(owner.getUrn())).thenReturn(org.integratedmodelling.common.knowledge.ObservableImpl.promote(owner,null));

  var executionRequest=new SemanticDependencyRequest("reasoner",1,FP,List.of("test:ScientificModel","test:Meaning1"));
  var executionWitness=new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,"reasoner",1,FP,executionRequest.inputHash(),List.of(),null);
  var binding=new SemanticEvidenceBinding(SemanticEvidenceBinding.VERSION,true,executionRequest.obligations(),executionWitness);
  var metadata=org.integratedmodelling.klab.utilities.Utils.Json.asString(Map.of(SemanticEvidenceBinding.DATA_KEY,org.integratedmodelling.klab.utilities.Utils.Json.asString(binding)));
  var proofEdges=new ArrayList<>(f.nativeInputs.edges);
  for(long id:List.of(1L,2L)) {
   long activity=1000+id;f.nativeInputs.assets.put(activity,new ConsequenceAssetSnapshot(activity,"execution"+activity,List.of("Activity"),Map.of(GraphModel.Fields.OUTCOME,"SUCCESS",GraphModel.Fields.METADATA,metadata),null,null));
   var effect=GraphModel.Relationship.forContextualization(f.nativeInputs.observations.get(id).getObservable().getContextualization());
   proofEdges.add(new ConsequenceEdgeSnapshot("proof"+id,new ConsequenceAssetRef("execution"+activity,activity,List.of("Activity")),ConfigurationNativeInputsTest.ref(id),effect,Map.of()));
  }
  f.nativeInputs.edges=proofEdges;
  when(f.reasoner.semanticDependencies(any(),any())).thenAnswer(call->{var signature=call.getArgument(0,SemanticDependencyRequest.class);return new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,"reasoner",1,FP,signature.inputHash(),List.of(),null);});
  var request=new SemanticDependencyRequest("reasoner",1,FP,List.of(owner.getUrn(),"test:Meaning1"));var witness=new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,"reasoner",1,FP,request.inputHash(),List.of(),null);
  when(f.reasoner.consequenceRules(any())).thenReturn(new ConsequenceRuleResponse(ConsequenceRuleResponse.Status.COMPLETE,EPOCH,List.of(recipe),witness,null));
  when(f.reasoner.resolveConcept("test:Meaning1")).thenReturn(f.nativeInputs.observations.get(1L).getObservable().asConcept());when(f.reasoner.baseSubstantialType(any(),any())).thenAnswer(call->call.getArgument(0,Semantics.class).asConcept());when(f.reasoner.is(any(),any())).thenAnswer(call->call.getArgument(0,Concept.class).getUrn().equals(call.getArgument(1,Concept.class).getUrn()));when(f.reasoner.consequenceApplicable(eq(owner),isNull())).thenReturn(true);
  when(f.publisher.publishBatch(any(),any(),eq(f.cause),eq(f.nativeInputs.scope))).thenReturn(new ConfigurationConsequenceRuntime.Effect(Consequence.Outcome.APPLIED,List.of(301L,302L),"two components"));
  return f;
 }
 static void addUnresolvedUnrelatedMember(Fixture f) {
  var o=ConfigurationNativeInputsTest.observation(3,true);var meaning=concept("test:OtherWire",SemanticType.RELATIONSHIP,SemanticType.STRUCTURAL,SemanticType.UNIDIRECTIONAL,SemanticType.OBSERVABLE,SemanticType.COUNTABLE);var observable=new org.integratedmodelling.common.knowledge.ObservableImpl();observable.setSemantics(meaning);observable.setUrn(meaning.getUrn());observable.setDescriptionType(Contextualization.CONNECTION);o.setObservable(observable);o.setResolvedCoverage(0);
  f.nativeInputs.observations.put(3L,o);f.nativeInputs.assets.put(3L,new ConsequenceAssetSnapshot(3,"e3",List.of("Observation"),Map.of(GraphModel.Fields.OBSERVABLE,meaning.getUrn()),"1",null));
  var edges=new ArrayList<>(f.nativeInputs.edges);edges.add(ConfigurationNativeInputsTest.edge(10,3,GraphModel.Relationship.HAS_MEMBER));edges.add(ConfigurationNativeInputsTest.edge(3,11,GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE));edges.add(ConfigurationNativeInputsTest.edge(3,12,GraphModel.Relationship.HAS_RELATIONSHIP_TARGET));f.nativeInputs.edges=edges;when(f.reasoner.resolveConcept(meaning.getUrn())).thenReturn(meaning);
 }
 @Test void excludedUnresolvedMemberNeedsMeaningAuthorityAndNativeGuardsButNoInventedModel()throws Exception {
  var f=matchedFixture();addUnresolvedUnrelatedMember(f);var result=f.backend.evaluate(f.cause,f.commit,f.nativeInputs.scope);assertEquals(Consequence.Outcome.APPLIED,result.outcome(),result::explanation);
  @SuppressWarnings("unchecked") var births=(org.mockito.ArgumentCaptor<List<GuardedConfigurationPublisher.Birth>>)(Object)org.mockito.ArgumentCaptor.forClass(List.class);verify(f.publisher).publishBatch(any(),births.capture(),any(),any());
  for(var birth:births.getValue()){
   var receipt=(ConfigurationConsequenceRuntime.SourceReceipt)birth.authorities().getFirst();assertTrue(receipt.admissionRequest().obligations().contains("test:OtherWire"));
   assertTrue(birth.reads().stream().anyMatch(read->read.assetIds().contains(3L)&&read.selections().contains(new ConsequenceEdgeSelection(3,GraphModel.Relationship.CLASSIFIED,GraphModel.Relationship.Direction.INCOMING))));
   assertFalse(birth.participants().contains(3L));
  }
 }
 @Test void excludedMemberUnknownAttributionCannotBypassItsScientificClosure()throws Exception {
  var f=matchedFixture();addUnresolvedUnrelatedMember(f);
  var binding=new SemanticEvidenceBinding(SemanticEvidenceBinding.VERSION,false,List.of(),null);
  var metadata=org.integratedmodelling.klab.utilities.Utils.Json.asString(Map.of(SemanticEvidenceBinding.DATA_KEY,org.integratedmodelling.klab.utilities.Utils.Json.asString(binding)));
  f.nativeInputs.assets.put(2003L,new ConsequenceAssetSnapshot(2003,"attribution2003",List.of("Activity"),Map.of(GraphModel.Fields.OUTCOME,"SUCCESS",GraphModel.Fields.METADATA,metadata),null,null));
  var edges=new ArrayList<>(f.nativeInputs.edges);edges.add(new ConsequenceEdgeSnapshot("attribution3",new ConsequenceAssetRef("attribution2003",2003L,List.of("Activity")),ConfigurationNativeInputsTest.ref(3),GraphModel.Relationship.CLASSIFIED,Map.of()));f.nativeInputs.edges=edges;
  var outcome=f.backend.evaluate(f.cause,f.commit,f.nativeInputs.scope);assertEquals(Consequence.Outcome.DEFERRED,outcome.outcome());assertTrue(outcome.explanation().contains("attribution"),outcome::explanation);verifyNoInteractions(f.publisher);
 }
 @Test void wholeOwnerFamilyProducesOneBirthForEachDisconnectedComponent()throws Exception {
  var f=matchedFixture();
  assertEquals(Consequence.Outcome.APPLIED,f.backend.evaluate(f.cause,f.commit,f.nativeInputs.scope).outcome());
  @SuppressWarnings("unchecked") var births=(org.mockito.ArgumentCaptor<List<GuardedConfigurationPublisher.Birth>>)(Object)org.mockito.ArgumentCaptor.forClass(List.class);
  verify(f.publisher).publishBatch(eq(List.of()),births.capture(),eq(f.cause),eq(f.nativeInputs.scope));
  assertEquals(Set.of(Set.of(1L),Set.of(2L)),births.getValue().stream().map(b->Set.copyOf(b.participants())).collect(java.util.stream.Collectors.toSet()));
  assertTrue(births.getValue().stream().allMatch(b->b.placement().getId()==90&&b.authorities().size()==1));
  assertTrue(births.getValue().stream().allMatch(b->b.catalogues().size()==1));
  assertTrue(births.getValue().stream().allMatch(b->((ConfigurationConsequenceRuntime.SourceReceipt)b.authorities().getFirst()).match().placement().equals(ConsequencePatternMatcher.Placement.context("context"))));
  verify(f.reasoner).consequenceRules(argThat(q->q.ownerTriggers().size()==1&&q.ownerTriggers().getFirst().getUrn().equals("test:Network")));
 }

 @Test void unknownOriginalModelAccountingCannotBecomeAConfigurationBirth()throws Exception {
  var f=matchedFixture();var original=f.nativeInputs.assets.get(1001L);
  var unknown=new SemanticEvidenceBinding(SemanticEvidenceBinding.VERSION,false,List.of(),null);
  var metadata=org.integratedmodelling.klab.utilities.Utils.Json.asString(Map.of(SemanticEvidenceBinding.DATA_KEY,org.integratedmodelling.klab.utilities.Utils.Json.asString(unknown)));
  f.nativeInputs.assets.put(1001L,new ConsequenceAssetSnapshot(original.id(),original.elementId(),original.labels(),Map.of(GraphModel.Fields.OUTCOME,"SUCCESS",GraphModel.Fields.METADATA,metadata),null,null));
  assertEquals(Consequence.Outcome.DEFERRED,f.backend.evaluate(f.cause,f.commit,f.nativeInputs.scope).outcome());verifyNoInteractions(f.publisher);
 }
 @Test void mismatchedOriginalExecutionSignatureCannotBeReplacedByMeaningOnlyProof()throws Exception {
  var f=matchedFixture();var original=f.nativeInputs.assets.get(1001L);
  var mismatch=new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,"reasoner",1,FP,"b".repeat(64),List.of(),null);
  var binding=new SemanticEvidenceBinding(SemanticEvidenceBinding.VERSION,true,List.of("test:ScientificModel"),mismatch);
  var metadata=org.integratedmodelling.klab.utilities.Utils.Json.asString(Map.of(SemanticEvidenceBinding.DATA_KEY,org.integratedmodelling.klab.utilities.Utils.Json.asString(binding)));
  f.nativeInputs.assets.put(1001L,new ConsequenceAssetSnapshot(original.id(),original.elementId(),original.labels(),Map.of(GraphModel.Fields.OUTCOME,"SUCCESS",GraphModel.Fields.METADATA,metadata),null,null));
  assertEquals(Consequence.Outcome.DEFERRED,f.backend.evaluate(f.cause,f.commit,f.nativeInputs.scope).outcome());verifyNoInteractions(f.publisher);
 }

 @Test void providerFailureAfterPlanningInvalidatesExactActualInputAdmissionBeforePublication()throws Exception {
  var f=matchedFixture();assertEquals(Consequence.Outcome.APPLIED,f.backend.evaluate(f.cause,f.commit,f.nativeInputs.scope).outcome());
  @SuppressWarnings("unchecked") var births=(org.mockito.ArgumentCaptor<List<GuardedConfigurationPublisher.Birth>>)(Object)org.mockito.ArgumentCaptor.forClass(List.class);
  verify(f.publisher).publishBatch(any(),births.capture(),any(),any());var receipt=(ConfigurationConsequenceRuntime.SourceReceipt)births.getValue().getFirst().authorities().getFirst();
  assertTrue(receipt.admissionRequest().obligations().containsAll(List.of("test:ScientificModel","test:Meaning1","test:Network")));
  doReturn(SemanticDependencyWitness.unknown(receipt.admissionRequest(),"provider unavailable")).when(f.reasoner).semanticDependencies(any(),eq(f.nativeInputs.scope));
  var admit=DefaultConfigurationBackend.class.getDeclaredMethod("admit",List.class,org.integratedmodelling.klab.services.scopes.ServiceContextScope.class);admit.setAccessible(true);
  var rejected=assertThrows(java.lang.reflect.InvocationTargetException.class,()->admit.invoke(f.backend,births.getValue().getFirst().authorities(),f.nativeInputs.scope));
  assertTrue(rejected.getCause().getMessage().contains("admission unavailable"));
 }
 @Test void actualModelFreeFocusContributesMeaningWithoutInventedSourceModelRequirement()throws Exception {
  var f=matchedFixture();var focus=f.nativeInputs.observations.get(11L);when(f.nativeInputs.scope.getContextObservation()).thenReturn(focus);
  when(f.reasoner.resolveConcept(focus.getObservable().getUrn())).thenReturn(focus.getObservable().asConcept());when(f.reasoner.consequenceApplicable(any(),eq(focus.getObservable().asConcept()))).thenReturn(true);
  assertEquals(Consequence.Outcome.APPLIED,f.backend.evaluate(f.cause,f.commit,f.nativeInputs.scope).outcome());
  @SuppressWarnings("unchecked") var births=(org.mockito.ArgumentCaptor<List<GuardedConfigurationPublisher.Birth>>)(Object)org.mockito.ArgumentCaptor.forClass(List.class);
  verify(f.publisher).publishBatch(any(),births.capture(),any(),any());var receipt=(ConfigurationConsequenceRuntime.SourceReceipt)births.getValue().getFirst().authorities().getFirst();
  assertTrue(receipt.admissionRequest().obligations().contains(focus.getObservable().getUrn()));assertEquals(11L,births.getValue().getFirst().observation().getMetadata().get("klab.configuration.applicationContext"));
  assertFalse(f.nativeInputs.edges.stream().anyMatch(edge->Objects.equals(edge.target().id(),11L)&&edge.source().labels().contains("Activity")));
 }

 static Object retainedFamily(DefaultConfigurationBackend backend,ConsequenceRuleResponse first,ConsequenceRuleResponse second,org.integratedmodelling.klab.services.scopes.ServiceContextScope scope)throws Exception {
  var existing=new TreeMap<Long,ConfigurationCatalogSnapshot.Existing>();long id=90;
  for(var response:List.of(first,second)) {
   var observation=new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();observation.setId(id);
   var query=new ConsequenceRuleQuery(List.of(response.rules().getFirst().owner()),List.of(),Set.of(KimConsequenceRule.Kind.EMERGES_FROM),response.epoch());
   observation.getMetadata().put("klab.configuration.authorities",org.integratedmodelling.klab.utilities.Utils.Json.asString(List.of(Map.of("sourceResponseJson",org.integratedmodelling.klab.utilities.Utils.Json.asString(response),"queryJson",org.integratedmodelling.klab.utilities.Utils.Json.asString(query)))));
   existing.put(id,new ConfigurationCatalogSnapshot.Existing(observation,new ConfigurationConsequenceRuntime.Revision(id,1,1,List.of(id-89),"1")));id++;
  }
  var catalogue=new ConfigurationCatalogSnapshot.View(mock(Cohort.class),ConfigurationConsequenceRuntimeTest.read(90,91),existing);
  var method=DefaultConfigurationBackend.class.getDeclaredMethod("historicalFamily",ConfigurationCatalogSnapshot.View.class,org.integratedmodelling.klab.services.scopes.ServiceContextScope.class);method.setAccessible(true);
  return method.invoke(backend,catalogue,scope);
 }
 static ConsequenceRuleResponse family(ConsequenceRuleResponse.Entry recipe,ConsequenceRuleResponse.Epoch epoch,long expiry) {
  var proof=new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,"reasoner",1,FP,"b".repeat(64),List.of(new SemanticDependencyWitness.AuthorityUse("authority",SemanticDependencyWitness.Kind.CAPTURED,"c".repeat(64),expiry)),null);
  return new ConsequenceRuleResponse(ConsequenceRuleResponse.Status.COMPLETE,epoch,List.of(recipe),proof,null);
 }
 @Test void renewedFamilyWitnessLeaseDoesNotPreventDeterministicMergeGrouping()throws Exception {
  var recipe=recipe("test:Wire");var first=family(recipe,EPOCH,1000);var refreshed=family(recipe,EPOCH,2000);
  assertNotEquals(org.integratedmodelling.klab.utilities.Utils.Json.asString(first),org.integratedmodelling.klab.utilities.Utils.Json.asString(refreshed));
  assertEquals(DefaultConfigurationBackend.familyIdentity(first),DefaultConfigurationBackend.familyIdentity(refreshed));
  var fixture=new Fixture();assertNotNull(retainedFamily(fixture.backend,first,refreshed,fixture.nativeInputs.scope));
  var merged=ConfigurationComponents.assign(List.of(Set.of(1L,2L)),Map.of(90L,Set.of(1L),91L,Set.of(2L)),Set.of());
  assertEquals(90L,merged.components().get(0).observationId());assertEquals(Set.of(91L),merged.emptyIdentities());
 }
 @Test void familyIdentityRetainsSourceHashVersionConditionsAndOriginalGeneration()throws Exception {
  var recipe=recipe("test:Wire");var baseline=family(recipe,EPOCH,1000);
  var changedHash=new ConsequenceRuleResponse.Entry(recipe.ruleId(),recipe.declarationUrn(),recipe.owner(),recipe.rule(),recipe.sourceOntology(),recipe.sourceVersion(),"d".repeat(64));
  var changedVersion=new ConsequenceRuleResponse.Entry(recipe.ruleId(),recipe.declarationUrn(),recipe.owner(),recipe.rule(),recipe.sourceOntology(),"different-source-version",recipe.sourceHash());
  var generation=new ConsequenceRuleResponse.Epoch("reasoner",2,FP,"session");
  assertNotEquals(DefaultConfigurationBackend.familyIdentity(baseline),DefaultConfigurationBackend.familyIdentity(family(changedHash,EPOCH,2000)));
  assertNotEquals(DefaultConfigurationBackend.familyIdentity(baseline),DefaultConfigurationBackend.familyIdentity(family(changedVersion,EPOCH,2000)));
  assertNotEquals(DefaultConfigurationBackend.familyIdentity(baseline),DefaultConfigurationBackend.familyIdentity(family(recipe,generation,2000)));
  assertNotEquals(DefaultConfigurationBackend.familyIdentity(baseline),DefaultConfigurationBackend.familyIdentity(family(recipe("test:OtherWire"),EPOCH,2000)));
  var fixture=new Fixture();
  for(var changed:List.of(family(changedHash,EPOCH,2000),family(changedVersion,EPOCH,2000),family(recipe,generation,2000),family(recipe("test:OtherWire"),EPOCH,2000))) {
   var rejected=assertThrows(java.lang.reflect.InvocationTargetException.class,()->retainedFamily(fixture.backend,baseline,changed,fixture.nativeInputs.scope));assertTrue(rejected.getCause().getMessage().contains("Mixed historical source families"));
  }
 }
}
