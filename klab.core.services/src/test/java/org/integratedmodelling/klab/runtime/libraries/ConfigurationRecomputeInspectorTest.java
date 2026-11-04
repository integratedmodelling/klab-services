package org.integratedmodelling.klab.runtime.libraries;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.digitaltwin.impl.CommitImpl;
import org.integratedmodelling.klab.api.knowledge.SemanticEvidenceBinding;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.Test;

/** Structural diagnostic comparisons only. No test fabricates a successful scientific receipt
 * or replaces the public snapshot's existing ConfigurationInspector proof validation. */
class ConfigurationRecomputeInspectorTest {
  static final String AUTHORITIES="[{\"source\":\"original-source\"}]";
  static final String EXPLANATION="{\"outcome\":\"RESOLVED\",\"model\":\"first\"}";
  @Test void ordinaryModelDetectionIsNotManagedParticipantLineage() {
    var model=Activity.of(Activity.Type.DETECTION);
    assertNull(ConfigurationRecomputeInspector.managedDetectionRevision(model,10,Map.of()));
    assertThrows(IllegalStateException.class,()->ConfigurationRecomputeInspector.managedDetectionRevision(model,10,Map.of("configurationRevision",1)));
    model.getMetadata().put("klab.configuration.birth.10",Utils.Json.asString(Map.of("next",Map.of("observationId",10,"number",1))));
    assertEquals(1L,ConfigurationRecomputeInspector.managedDetectionRevision(model,10,Map.of("configurationRevision",1L)));
    assertThrows(IllegalStateException.class,()->ConfigurationRecomputeInspector.managedDetectionRevision(model,10,Map.of()));
    assertThrows(IllegalStateException.class,()->ConfigurationRecomputeInspector.managedDetectionRevision(model,10,Map.of("configurationRevision",1.5)));
    assertThrows(IllegalStateException.class,()->ConfigurationRecomputeInspector.managedDetectionRevision(model,11,Map.of("configurationRevision",1)));
  }
  @Test void managedRevisionRowsRequireOneMatchingNativeTarget() {
    var activity=Activity.of(Activity.Type.DETECTION);var row=Utils.Json.asString(Map.of("next",Map.of("observationId",10,"number",2)));
    activity.getMetadata().put("klab.configuration.revisions",List.of(row));
    assertEquals(2L,ConfigurationRecomputeInspector.managedDetectionRevision(activity,10,Map.of("configurationRevision",2)));
    activity.getMetadata().put("klab.configuration.revisions",List.of(row,row));
    assertThrows(IllegalStateException.class,()->ConfigurationRecomputeInspector.managedDetectionRevision(activity,10,Map.of("configurationRevision",2)));
    activity.getMetadata().put("klab.configuration.revisions",List.of("{}"));
    assertThrows(IllegalStateException.class,()->ConfigurationRecomputeInspector.managedDetectionRevision(activity,10,Map.of("configurationRevision",2)));
  }
  String current(long revision,long trigger) {
    return Utils.Json.asString(Map.of("observationId",10,"number",revision,"triggeringCommitId",trigger,
        "participants",List.of(20,21),"support","S2"));
  }
  ConfigurationRecomputeInspector.Snapshot snapshot(long revision,List<String> history) {
    return new ConfigurationRecomputeInspector.Snapshot("context",0,0,10,"test:Pair",revision,100+revision,
        current(revision,100+revision),"same-perspective","S2",AUTHORITIES,EXPLANATION,history,Set.of(20L,21L),List.of(),Map.of(),200L);
  }
  @Test void diagnosticSnapshotCopiesAllMutableCollections() {
    var history=new ArrayList<>(List.of("before"));var participants=new HashSet<>(Set.of(20L));
    var dependencies=new ArrayList<ConfigurationRecomputeInspector.Dependency>();
    var detections=new HashMap<Long,ConfigurationRecomputeInspector.Detection>();
    var snapshot=new ConfigurationRecomputeInspector.Snapshot("context",0,0,10,"test:Pair",1,101,
        current(1,101),"perspective","S2",AUTHORITIES,EXPLANATION,history,participants,dependencies,detections,null);
    history.add("changed");participants.add(21L);dependencies.add(new ConfigurationRecomputeInspector.Dependency(20,1,null,null));
    detections.put(1L,new ConfigurationRecomputeInspector.Detection(1,1,"altered"));
    assertEquals(List.of("before"),snapshot.history());assertEquals(Set.of(20L),snapshot.participants());
    assertTrue(snapshot.dependencies().isEmpty());assertTrue(snapshot.detections().isEmpty());
    assertThrows(UnsupportedOperationException.class,()->snapshot.history().clear());
  }
  @Test void immutableHistoryMustExtendExactlyAndLinkThePreviousAndCurrentRevision() {
    var before=snapshot(1,List.of("immutable-original"));
    var append=Utils.Json.asString(Map.of("previous",Utils.Json.parseObject(before.current(),Map.class),
        "next",Utils.Json.parseObject(current(2,102),Map.class)));
    assertTrue(ConfigurationRecomputeInspector.historyExtended(before,snapshot(2,List.of("immutable-original",append))));
    assertFalse(ConfigurationRecomputeInspector.historyExtended(before,snapshot(2,List.of("rewritten",append))));
    assertFalse(ConfigurationRecomputeInspector.historyExtended(before,snapshot(2,List.of("immutable-original","{}"))));
    assertFalse(ConfigurationRecomputeInspector.historyExtended(before,snapshot(2,List.of("immutable-original",append,append))));
  }
  @Test void managedInputsRequireExactSourcesRevisionAndNoDuplicates() {
    var links=List.of(new ConfigurationRecomputeInspector.Dependency(20,2,AUTHORITIES,EXPLANATION),
        new ConfigurationRecomputeInspector.Dependency(21,2,AUTHORITIES,EXPLANATION));
    assertTrue(ConfigurationRecomputeInspector.managed(links,Set.of(20L,21L),2,AUTHORITIES,EXPLANATION));
    assertFalse(ConfigurationRecomputeInspector.managed(links,Set.of(20L,21L,50L),2,AUTHORITIES,EXPLANATION));
    assertFalse(ConfigurationRecomputeInspector.managed(links,Set.of(20L,21L),3,AUTHORITIES,EXPLANATION));
    assertFalse(ConfigurationRecomputeInspector.managed(List.of(links.getFirst(),links.getFirst()),Set.of(20L),2,AUTHORITIES,EXPLANATION));
  }
  @Test void managedEdgesMustCarryTheirOwnRevisionsActualAuthorityAndModelReceipt() {
    var revisedAuthorities="[{\"source\":\"fresh-source\"}]";
    var revisedExplanation="{\"outcome\":\"RESOLVED\",\"model\":\"second\"}";
    var fresh=List.of(new ConfigurationRecomputeInspector.Dependency(20,2,revisedAuthorities,revisedExplanation));
    assertTrue(ConfigurationRecomputeInspector.managed(fresh,Set.of(20L),2,revisedAuthorities,revisedExplanation),
        "A legitimate fresh revision may have different source and model receipts");
    assertFalse(ConfigurationRecomputeInspector.managed(fresh,Set.of(20L),2,AUTHORITIES,revisedExplanation));
    assertFalse(ConfigurationRecomputeInspector.managed(fresh,Set.of(20L),2,revisedAuthorities,EXPLANATION));
    assertFalse(ConfigurationRecomputeInspector.managed(List.of(new ConfigurationRecomputeInspector.Dependency(20,2,null,revisedExplanation)),
        Set.of(20L),2,revisedAuthorities,revisedExplanation));
    assertFalse(ConfigurationRecomputeInspector.managed(List.of(new ConfigurationRecomputeInspector.Dependency(20,2,revisedAuthorities,null)),
        Set.of(20L),2,revisedAuthorities,revisedExplanation));
    assertFalse(ConfigurationRecomputeInspector.managed(fresh,Set.of(20L),2,"",revisedExplanation));
    assertFalse(ConfigurationRecomputeInspector.managed(fresh,Set.of(20L),2,"[]",revisedExplanation));
    assertFalse(ConfigurationRecomputeInspector.managed(fresh,Set.of(20L),2,revisedAuthorities,"not JSON"));
  }
  Activity transition(String context,long commitId,long end,List<Long> changed) {
    var commit=new CommitImpl();commit.setId(commitId);commit.getModifiedAssets().addAll(changed);
    var journal=new SchedulerJournal(1,"event",null,Scheduler.Event.Type.TEMPORAL_TRANSITION,100,end,"resize","plan","S2",commitId,changed,true);
    var receipt=new TransitionCommit(1,context,commit,List.of(journal),List.of());
    var activity=Activity.of(Activity.Type.SIMULATION);activity.setId(30);activity.setOutcome(Activity.Outcome.SUCCESS);
    activity.getMetadata().put(TransitionCommit.METADATA_KEY,Utils.Json.asString(receipt));return activity;
  }
  @Test void transitionComparisonRejectsDifferentCommitContextChangedFactsAndExtraTimeAdvance() {
    var source=transition("context",102,200,List.of(20L));
    assertTrue(ConfigurationRecomputeInspector.transitionMatches("context",102,200L,20,21,List.of(source)),
        "Only a structural comparison: no modeled-detection success is asserted here");
    assertFalse(ConfigurationRecomputeInspector.transitionMatches("context",103,200L,20,21,List.of(source)));
    assertFalse(ConfigurationRecomputeInspector.transitionMatches("other",102,200L,20,21,List.of(source)));
    assertFalse(ConfigurationRecomputeInspector.transitionMatches("context",102,201L,20,21,List.of(source)));
    assertFalse(ConfigurationRecomputeInspector.transitionMatches("context",102,null,20,21,List.of(source)));
    assertFalse(ConfigurationRecomputeInspector.transitionMatches("context",102,200L,20,21,List.of(source,source)));
    assertFalse(ConfigurationRecomputeInspector.transitionMatches("context",102,200L,20,21,
        List.of(transition("context",102,200,List.of(20L,21L)))));
  }
  @Test void namedChecksDistinguishIdentityValuesAndHistoryWithoutLeakingOperands() {
    var before=snapshot(1,List.of("immutable-original"));
    var same=ConfigurationRecomputeInspector.snapshotChecks(before,before,20,21);
    assertTrue(same.get("identity.configuration"));assertTrue(same.get("value.support"));
    assertFalse(same.get("history.oneRevision"));assertFalse(same.get("history.exactExtension"));
    assertFalse(same.get("execution.newDetection"));
    var changed=new ConfigurationRecomputeInspector.Snapshot(before.context(),before.observer(),before.focus(),99,before.definition(),
        before.revision(),before.trigger(),before.current(),before.perspective(),"S3",before.authorities(),before.explanation(),
        before.history(),before.participants(),before.dependencies(),before.detections(),before.currentInstant());
    var facts=ConfigurationRecomputeInspector.snapshotChecks(before,changed,20,21);
    assertFalse(facts.get("identity.configuration"));assertFalse(facts.get("value.support"));
    assertTrue(facts.get("identity.context"));assertTrue(facts.get("value.participantsPreserved"));
    assertFalse(facts.toString().contains("immutable-original"));assertFalse(facts.toString().contains(AUTHORITIES));
    assertThrows(UnsupportedOperationException.class,()->facts.put("forged",true));
  }
  @Test void malformedDiagnosticHistoryDoesNotHideKnownIdentityAndValueComparisons() {
    var before=snapshot(1,List.of("immutable-original"));
    var after=snapshot(2,List.of("immutable-original","not JSON SECRET-CANARY"));
    var facts=ConfigurationRecomputeInspector.snapshotChecks(before,after,20,21);
    assertTrue(facts.get("identity.configuration"));assertTrue(facts.get("value.support"));
    assertTrue(facts.get("history.oneRevision"));assertFalse(facts.get("history.parseAvailable"));
    assertNull(facts.get("history.exactExtension"));assertFalse(facts.toString().contains("SECRET-CANARY"));
  }
  @Test void optInEntryReportsEarlyRejectionDirectlyWithoutQueryingAndDefaultOffIsSilent() {
    var scope=mock(org.integratedmodelling.klab.api.scope.ContextScope.class);var original=System.err;
    var bytes=new java.io.ByteArrayOutputStream();var flag="klab.diagnostics.configurationRecompute";var previous=System.getProperty(flag);
    try {
      System.setErr(new java.io.PrintStream(bytes));System.clearProperty(flag);
      assertFalse(ConfigurationRecomputeInspector.recomputed(scope,null,null,null,null,null));assertEquals("",bytes.toString());
      System.setProperty(flag,"true");assertFalse(ConfigurationRecomputeInspector.recomputed(scope,null,null,null,null,null));
      assertTrue(bytes.toString().contains("entry-v2"));assertTrue(bytes.toString().contains("\"beforePresent\":false"));
      assertFalse(bytes.toString().contains("SECRET"));verifyNoInteractions(scope);
    }finally{System.setErr(original);if(previous==null)System.clearProperty(flag);else System.setProperty(flag,previous);}
  }
  @Test void diagnosticFormattingCannotReplaceOriginalSnapshotFailure() {
    var scope=mock(org.integratedmodelling.klab.api.scope.ContextScope.class);var observation=mock(Observation.class);when(observation.getId()).thenReturn(41L);
    var originalFailure=new IllegalStateException("SECRET-ORIGINAL") {
      @Override public StackTraceElement[] getStackTrace(){throw new IllegalArgumentException("SECRET-FORMATTING");}
    };
    when(scope.getDigitalTwin()).thenAnswer(invocation->{throw originalFailure;});
    var flag="klab.diagnostics.configurationRecompute";var previous=System.getProperty(flag);var original=System.err;
    var bytes=new java.io.ByteArrayOutputStream();
    try {
      System.setProperty(flag,"true");System.setErr(new java.io.PrintStream(bytes));
      assertSame(originalFailure,assertThrows(IllegalStateException.class,()->ConfigurationRecomputeInspector.recomputed(scope,
          snapshot(1,List.of("original")),observation,observation,observation,observation)));
      assertFalse(bytes.toString().contains("SECRET"));
    }finally{System.setErr(original);if(previous==null)System.clearProperty(flag);else System.setProperty(flag,previous);}
  }
  @Test void rejectedManagedStampReportsOnlyExactRevisionOperands() {
    var activity=Activity.of(Activity.Type.DETECTION);activity.setId(500);
    activity.getMetadata().put("klab.configuration.revisions",List.of(Utils.Json.asString(Map.of("next",Map.of("observationId",10,"number",2),"source","SECRET-RECEIPT"))));
    var flag="klab.diagnostics.configurationRecompute";var previous=System.getProperty(flag);var original=System.err;
    var bytes=new java.io.ByteArrayOutputStream();
    try {
      System.setErr(new java.io.PrintStream(bytes));System.setProperty(flag,"true");
      assertThrows(IllegalStateException.class,()->ConfigurationRecomputeInspector.managedDetectionRevision(activity,10,Map.of("configurationRevision",1)));
      assertTrue(bytes.toString().contains("managed-detection-stamp"));assertTrue(bytes.toString().contains("\"edgeRevision\":1"));
      assertTrue(bytes.toString().contains("\"matchingRevisions\":[2]"));assertFalse(bytes.toString().contains("SECRET"));
    }finally{System.setErr(original);if(previous==null)System.clearProperty(flag);else System.setProperty(flag,previous);}
  }
  @Test void snapshotCannotUpgradeMissingScientificDetectionEvidence() {
    var f=new ConfigurationInspectorTest.Fixture();
    f.detected.getMetadata().remove(SemanticEvidenceBinding.DATA_KEY);
    var scheduler=mock(Scheduler.class);when(scheduler.currentModeledInstant()).thenReturn(OptionalLong.empty());
    when(f.scope.getDigitalTwin().getScheduler()).thenReturn(scheduler);
    when(f.graph.query(Observation.class,f.scope)).thenReturn(new KnowledgeGraphQuery<Observation>(KnowledgeGraphQuery.AssetType.OBSERVATION){
      @Override public List<Observation> run(Scope scope){return List.of(f.cfg);}
    });
    assertThrows(IllegalStateException.class,()->ConfigurationRecomputeInspector.snapshot(f.scope,f.cfg));
    var bytes=new java.io.ByteArrayOutputStream();var original=System.err;
    var flag="klab.diagnostics.configurationRecompute";var previous=System.getProperty(flag);
    try {
      System.setErr(new java.io.PrintStream(bytes));System.setProperty(flag,"true");
      assertThrows(IllegalStateException.class,()->ConfigurationRecomputeInspector.recomputed(f.scope,snapshot(1,List.of("original")),f.cfg,f.cfg,f.cfg,f.cfg));
      assertTrue(bytes.toString().contains("snapshot-failure"));assertTrue(bytes.toString().contains("IllegalStateException"));
      assertFalse(bytes.toString().contains("Configuration has no currently verified modeled detection"));
    }finally{System.setErr(original);if(previous==null)System.clearProperty(flag);else System.setProperty(flag,previous);}
    verify(f.graph,never()).getConfigurationHistory(any(),any());
  }
}
