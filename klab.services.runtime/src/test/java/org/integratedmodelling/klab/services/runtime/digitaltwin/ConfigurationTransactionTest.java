package org.integratedmodelling.klab.services.runtime.digitaltwin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.junit.jupiter.api.*;
class ConfigurationTransactionTest {
 ClassificationTransactionTest f;ObservationImpl configuration,participant;KnowledgeGraph.ConsequenceReadSet reads;
 ConfigurationConsequenceRuntime.Update update;Runnable admission;
 @BeforeAll static void configure(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
 @BeforeEach void setup()throws Exception {
  f=new ClassificationTransactionTest();f.setup();
  var software=mock(org.integratedmodelling.klab.api.provenance.Agent.class);when(software.getName()).thenReturn("k.LAB");when(software.getId()).thenReturn(3L);when(f.kg.klab()).thenReturn(software);
  when(f.scope.getConsequenceCause()).thenReturn(new Consequence.Cause(1,"context",10,2,0,null,null,"decision",Consequence.Kind.CONFIGURATION_DETECTION));
  var trigger=Activity.of(Activity.Type.SUBMISSION);trigger.setId(2);when(f.kg.getAsset(2L,f.scope,Activity.class)).thenReturn(trigger);
  f.root=f.twin.new TransactionImpl(Activity.of(Activity.Type.DETECTION),f.scope,RuntimeAsset.PROVENANCE_ASSET);
  configuration=(ObservationImpl)f.pending(41,false).member();participant=(ObservationImpl)f.pending(43,false).member();
  var meaning=new ConceptImpl();meaning.setUrn("test:Network");meaning.setType(EnumSet.of(SemanticType.CONFIGURATION,SemanticType.OBSERVABLE));
  var observable=new ObservableImpl();observable.setUrn(meaning.getUrn());observable.setSemantics(meaning);observable.setArtifactType(Artifact.Type.OBJECT);
  configuration.setObservable(observable);configuration.setGeometry(Geometry.create("1"));
  configuration.getMetadata().put(ConfigurationPerspective.METADATA,org.integratedmodelling.klab.utilities.Utils.Json.asString(new ConfigurationPerspective("context",0,0)));
  var participantMeaning=new ConceptImpl();participantMeaning.setUrn("test:Participant");participantMeaning.setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE));
  var participantObservable=new ObservableImpl();participantObservable.setUrn(participantMeaning.getUrn());participantObservable.setSemantics(participantMeaning);participantObservable.setArtifactType(Artifact.Type.OBJECT);
  participant.setObservable(participantObservable);participant.setGeometry(Geometry.create("1"));
  var old=new KnowledgeGraph.ConsequenceAssetSnapshot(41,"n41",List.of("Observation"),Map.of("configurationRevision",1,"observable","test:Network"),"1",null);
  var member=new KnowledgeGraph.ConsequenceAssetSnapshot(43,"n43",List.of("Observation"),Map.of(),"1",null);
  reads=new KnowledgeGraph.ConsequenceReadSet("context",List.of(old,member),List.of(new KnowledgeGraph.ConsequenceEdgeSelection(41,GraphModel.Relationship.EMERGED_FROM,GraphModel.Relationship.Direction.OUTGOING)),List.of(new KnowledgeGraph.ConsequenceEdgeSnapshot("old-membership",new KnowledgeGraph.ConsequenceAssetRef("n41",41L,List.of("Observation")),
      new KnowledgeGraph.ConsequenceAssetRef("n42",42L,List.of("Observation")),GraphModel.Relationship.EMERGED_FROM,Map.of())));
  when(f.kg.materializeConsequenceObservation(old,f.scope)).thenReturn(configuration);when(f.kg.materializeConsequenceObservation(member,f.scope)).thenReturn(participant);
  var request=new SemanticDependencyRequest("reasoner",1,"semantic-content-v2:"+"a".repeat(64),List.of("test:Network"));
  var witness=new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,"reasoner",1,request.expectedBaseFingerprint(),request.inputHash(),List.of(),null);
  var proof=new ConfigurationConsequenceRuntime.AffectsReceipt(41,"prior-model",request,witness,reads);
  update=new ConfigurationConsequenceRuntime.Update(new ConfigurationConsequenceRuntime.Revision(41,1,1,List.of(42L),"1"),
      new ConfigurationConsequenceRuntime.Revision(41,2,10,List.of(43L),"1"),List.of(reads),true,List.of(proof));
  admission=mock(Runnable.class);
 }
 Observation stage(){f.root.guardConsequenceBaseline(reads,f.scope);return f.root.stageConfiguration(update,f.scope,admission);}
 void temporalBaseline(String revisionSupport) {
  var raw="T0(1){ttype=GRID,tstart=1388534400000,tend=1420070400000,tscope=1.0,tunit=YEAR}";
  // The native materializer reconstructs with Geometry.create, whose shape parser
  // derives time dimensionality from the single supplied shape dimension.
  configuration.setGeometry(Geometry.create(raw));
  assertNotEquals(raw,configuration.getGeometry().encode());
  var old=reads.assets().getFirst();
  var captured=new KnowledgeGraph.ConsequenceAssetSnapshot(old.id(),old.elementId(),old.labels(),old.nativeProperties(),raw,old.perceivedGeometry());
  reads=new KnowledgeGraph.ConsequenceReadSet(reads.contextId(),List.of(captured,reads.assets().get(1)),reads.selections(),reads.edges());
  when(f.kg.materializeConsequenceObservation(captured,f.scope)).thenReturn(configuration);
  update=new ConfigurationConsequenceRuntime.Update(new ConfigurationConsequenceRuntime.Revision(41,1,1,List.of(42L),revisionSupport==null?configuration.getGeometry().encode():revisionSupport),update.next(),List.of(reads),true,update.authorities());
 }
 @Test void mergedStructuralSelectionsUseNativeCanonicalOrderWithoutLosingGuards() throws Exception {
  var high=new KnowledgeGraph.ConsequenceEdgeSelection(43,GraphModel.Relationship.CLASSIFIED,GraphModel.Relationship.Direction.INCOMING);
  var low=new KnowledgeGraph.ConsequenceEdgeSelection(41,GraphModel.Relationship.HAS_CHILD,GraphModel.Relationship.Direction.OUTGOING);
  var first=new KnowledgeGraph.ConsequenceReadSet("context",reads.assets(),List.of(high),reads.edges());
  var second=new KnowledgeGraph.ConsequenceReadSet("context",reads.assets(),List.of(low,high),reads.edges());
  var method=f.root.getClass().getDeclaredMethod("mergeConfigurationReads",List.class);method.setAccessible(true);
  var merged=(KnowledgeGraph.ConsequenceReadSet)method.invoke(f.root,List.of(first,second));
  assertEquals(List.of(low,high),merged.selections());
  assertEquals(List.of(high),first.selections());assertEquals(List.of(low,high),second.selections());
  assertEquals(reads.assets(),merged.assets());assertEquals(reads.edges(),merged.edges());
 }
 @Test void capturedRawGeometryAndMaterializedRevisionUseTheirOwnRepresentations() throws Exception {
  temporalBaseline(null);var previous=update.previous();var raw=reads.assets().getFirst().geometry();
  stage();assertTrue(f.root.commit()>0);
  assertEquals(previous,update.previous());assertEquals(raw,reads.assets().getFirst().geometry());
  verify(f.storage).validateConsequenceInputs(reads);
  verify(f.storage).updateConfigurations(anyList());
 }
 @Test void genuinelyDifferentHistoricalSupportStillPoisonsTransaction() {
  temporalBaseline("T1(1){ttype=GRID,tstart=1388534400000,tend=1451606400000,tscope=1.0,tunit=YEAR}");
  var failure=assertThrows(IllegalStateException.class,this::stage);
  assertEquals("Configuration prior history differs from captured current participants/support",failure.getMessage());
  assertTrue(f.root.isFailed());verify(f.storage,never()).updateConfigurations(anyList());
 }
 @Test void nativeConcurrentChangeStillRejectsAfterMaterializedSupportComparison() throws Exception {
  temporalBaseline(null);stage();
  doThrow(new IllegalStateException("captured native geometry changed")).when(f.storage).validateConsequenceInputs(reads);
  assertEquals(-1,f.root.commit());verify(f.storage,never()).updateConfigurations(anyList());
  assertFalse(configuration.getMetadata().containsKey("klab.configuration.current"));
 }
 @Test void legitimateNextSupportChangeDoesNotRewriteOrCompareAgainstPreviousSupport() {
  var proposed="T0(1){ttype=PHYSICAL,tstart=10,tend=20}";
  update=new ConfigurationConsequenceRuntime.Update(update.previous(),new ConfigurationConsequenceRuntime.Revision(41,2,10,List.of(43L),proposed),update.reads(),true,update.authorities());
  var replacement=stage();assertEquals("1",configuration.getGeometry().encode());assertEquals("1",update.previous().support());assertEquals(Geometry.create(proposed).encode(),replacement.getGeometry().encode());
  f.root.fail(new IllegalStateException("test rollback"));verify(f.storage,never()).updateConfigurations(anyList());
 }
 void diagnosticMismatch(boolean participants) {
  var property="klab.diagnostics.configurationRevision";var prior=System.getProperty(property);
  try {
   var previous=new ConfigurationConsequenceRuntime.Revision(41,1,1,participants?List.of(99L):List.of(42L),participants?"1":"different-prior-support");
   update=new ConfigurationConsequenceRuntime.Update(previous,update.next(),update.reads(),true,update.authorities());
   System.setProperty(property,"true");var failure=assertThrows(IllegalStateException.class,this::stage);assertEquals("Configuration prior history differs from captured current participants/support",failure.getMessage());
   String encoded;
   try {
    var formatter=DigitalTwinImpl.TransactionImpl.class.getDeclaredMethod("configurationPriorStateDetails",ConfigurationConsequenceRuntime.Update.class,org.integratedmodelling.klab.api.scope.ContextScope.class,KnowledgeGraph.ConsequenceAssetSnapshot.class,KnowledgeGraph.ConsequenceReadSet.class,Observation.class,Set.class);
    formatter.setAccessible(true);encoded=(String)formatter.invoke(f.root,update,f.scope,reads.assets().getFirst(),reads,configuration,new TreeSet<>(List.of(42L)));
   }catch(ReflectiveOperationException failureToFormat){throw new AssertionError(failureToFormat);}
   var facts=org.integratedmodelling.klab.utilities.Utils.Json.parseObject(encoded,Map.class);
   assertEquals(!participants,facts.get("participantsEqual"));assertEquals(participants,facts.get("supportEqual"));
   assertNotNull(facts.get("previous"));assertNotNull(facts.get("proposedNext"));assertEquals("1",facts.get("capturedNativeGeometry"));
   assertFalse(configuration.getMetadata().containsKey("klab.configuration.current"));verify(f.storage,never()).updateConfigurations(anyList());assertTrue(f.root.isFailed());
  }finally{if(prior==null)System.clearProperty(property);else System.setProperty(property,prior);}
 }
 @Test void diagnosticSeparatesParticipantMismatchWithoutWeakeningGuard(){diagnosticMismatch(true);}
 @Test void diagnosticSeparatesSupportMismatchWithoutWeakeningGuard(){diagnosticMismatch(false);}
 @Test void stagingIsDetachedAndBothLocalDirectionsExposeOnlyNewParticipants() {
  var replacement=stage();assertFalse(configuration.getMetadata().containsKey("klab.configuration.current"));
  assertTrue(replacement.getMetadata().containsKey("klab.configuration.current"));assertEquals(Set.of(41L),f.root.configurationParticipantReplacements());
  assertEquals(List.of(43L),f.root.outgoing(configuration).stream().filter(l->l.type()==GraphModel.Relationship.EMERGED_FROM).map(l->l.target().getId()).toList());
  assertEquals(41,f.root.incoming(participant).stream().filter(l->l.type()==GraphModel.Relationship.EMERGED_FROM).findFirst().orElseThrow().source().getId());
 }
 @Test void nativePublicationFollowsAllInputGuardsAndAdmission()throws Exception {
  stage();assertTrue(f.root.commit()>0);verify(admission).run();
  var order=inOrder(f.storage);order.verify(f.storage).lockConsequenceInputs(new TreeSet<>(List.of(41L,43L)));order.verify(f.storage).validateConsequenceInputs(reads);
  order.verify(f.storage).updateConfigurations(argThat(rows->rows.size()==1&&rows.getFirst().participants().equals(List.of(43L))&&rows.getFirst().revision()==2));
  assertTrue(configuration.getMetadata().containsKey("klab.configuration.current"));
 }
 @Test void rollbackNeverPublishesTentativeMetadataOrNativeRevision() {
  stage();f.root.fail(new IllegalStateException("abort"));assertFalse(configuration.getMetadata().containsKey("klab.configuration.current"));verify(f.storage,never()).updateConfigurations(anyList());
 }
 @Test void admissionFailureLeavesCommittedConfigurationUnchanged() {
  stage();doThrow(new IllegalStateException("provider drift")).when(admission).run();assertEquals(-1,f.root.commit());
  assertFalse(configuration.getMetadata().containsKey("klab.configuration.current"));verify(f.storage,never()).updateConfigurations(anyList());
 }
 @Test void missingCapturedParticipantPoisonsTheWholeAttemptEvenIfCallerCatchesIt() {
  f.root.guardConsequenceBaseline(new KnowledgeGraph.ConsequenceReadSet("context",List.of(reads.assets().getFirst()),reads.selections(),List.of()),f.scope);
  assertThrows(IllegalStateException.class,()->f.root.stageConfiguration(update,f.scope,admission));assertTrue(f.root.isFailed());assertEquals(-1,f.root.commit());
  assertFalse(configuration.getMetadata().containsKey("klab.configuration.current"));
 }
 @Test void birthUsesDedicatedCatalogueAndNormalTransientIdentityBeforeFinalNativeAllocation() {
  var cohort=new CohortImpl();cohort.setId(10);cohort.setObservable(configuration.getObservable());
  var placementSnapshot=new KnowledgeGraph.ConsequenceAssetSnapshot(10,"catalogue",List.of("Cohort"),Map.of(),"1",null);
  var birthRead=new KnowledgeGraph.ConsequenceReadSet("context",List.of(placementSnapshot,reads.assets().get(1)),
      List.of(new KnowledgeGraph.ConsequenceEdgeSelection(10,GraphModel.Relationship.HAS_MEMBER,GraphModel.Relationship.Direction.OUTGOING)),List.of());
  f.root.guardConsequenceBaseline(birthRead,f.scope);
  var epoch=new ConsequenceRuleResponse.Epoch("reasoner",1,"semantic-content-v2:"+"a".repeat(64),"session");
  var match=new org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher.Match("rule","context",0,epoch,
      new org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher.Placement(org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher.PlacementKind.ROOT_COHORT,100),
      null,List.of(43L),Map.of("0",List.of(43L)),"1",birthRead);
  var receipt=new ConfigurationConsequenceRuntime.SourceReceipt(epoch,update.authorities().getFirst().witness(),"{}","{}",match);
  var newborn=new ObservationImpl();newborn.getMetadata().put(ConfigurationPerspective.METADATA,org.integratedmodelling.klab.utilities.Utils.Json.asString(new ConfigurationPerspective("context",0,0)));newborn.setObservable(configuration.getObservable());newborn.setGeometry(Geometry.create("1"));when(f.scope.getNextObservationId()).thenReturn(-2L);
  var birth=new GuardedConfigurationPublisher.Birth(newborn,cohort,List.of(43L),List.of(birthRead),List.of(receipt),"component-birth");
  assertSame(newborn,f.root.stageConfigurationBirth(birth,f.scope,admission));assertEquals(-2,newborn.getId());
  assertTrue(f.root.outgoing(cohort).stream().anyMatch(l->l.type()==GraphModel.Relationship.HAS_MEMBER&&l.target()==newborn));
  assertTrue(f.root.outgoing(newborn).stream().anyMatch(l->l.type()==GraphModel.Relationship.EMERGED_FROM&&l.target().getId()==43));
  f.root.fail(new IllegalStateException("abort"));assertEquals(-1,f.root.commit());verify(f.storage,never()).store(any());
 }

 @Test void birthCommitAllocatesNativeIdentityAndInitialRevisionHistoryAtomically() {
  var cohort=new CohortImpl();cohort.setId(10);cohort.setObservable(configuration.getObservable());
  var placement=new KnowledgeGraph.ConsequenceAssetSnapshot(10,"catalogue",List.of("Cohort"),Map.of(),"1",null);
  var read=new KnowledgeGraph.ConsequenceReadSet("context",List.of(placement,reads.assets().get(1)),List.of(
      new KnowledgeGraph.ConsequenceEdgeSelection(10,GraphModel.Relationship.HAS_MEMBER,GraphModel.Relationship.Direction.OUTGOING)),List.of());
  f.root.guardConsequenceBaseline(read,f.scope);var epoch=new ConsequenceRuleResponse.Epoch("reasoner",1,"semantic-content-v2:"+"a".repeat(64),"session");
  var match=new org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher.Match("rule","context",0,epoch,
      new org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher.Placement(org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher.PlacementKind.ROOT_COHORT,100),null,List.of(43L),Map.of("0",List.of(43L)),"1",read);
  var receipt=new ConfigurationConsequenceRuntime.SourceReceipt(epoch,update.authorities().getFirst().witness(),"{}","{}",match);
  var newborn=new ObservationImpl();newborn.getMetadata().put(ConfigurationPerspective.METADATA,org.integratedmodelling.klab.utilities.Utils.Json.asString(new ConfigurationPerspective("context",0,0)));newborn.setObservable(configuration.getObservable());newborn.setGeometry(Geometry.create("1"));when(f.scope.getNextObservationId()).thenReturn(-2L);
  var birth=new GuardedConfigurationPublisher.Birth(newborn,cohort,List.of(43L),List.of(read),List.of(receipt),"component-birth");f.root.stageConfigurationBirth(birth,f.scope,admission);
  when(f.storage.captureConsequenceInputs(any(),any())).thenAnswer(call->new KnowledgeGraph.ConsequenceReadSet("context",List.of(
      new KnowledgeGraph.ConsequenceAssetSnapshot(newborn.getId(),"allocated",List.of("Observation"),Map.of("observable","test:Network"),"1",null),reads.assets().get(1)),
      List.copyOf(call.getArgument(1)),List.of()));
  assertTrue(f.root.commit()>0);assertTrue(newborn.getId()>0);assertTrue(newborn.getMetadata().get("klab.configuration.current").toString().contains(Long.toString(newborn.getId())));
  verify(f.storage).updateConfigurations(argThat(rows->rows.size()==1&&rows.getFirst().replacement()==newborn&&rows.getFirst().revision()==1&&rows.getFirst().historyJson().contains("component-birth")));
  verify(f.storage,never()).link(eq(newborn),eq(participant),eq(GraphModel.Relationship.EMERGED_FROM),any());
 }

}

