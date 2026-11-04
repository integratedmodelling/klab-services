package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.digitaltwin.impl.CommitImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;

class ConsequenceTriggerTest {
  @org.junit.jupiter.api.BeforeAll static void configure() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
  }
  static KnowledgeGraph.ConsequenceAssetSnapshot row(long id, String label, String type) {
    return new KnowledgeGraph.ConsequenceAssetSnapshot(id,"node-"+id,List.of(label),
        type==null?Map.of():Map.of(GraphModel.Fields.SEMANTICTYPE,type),null,null);
  }

  @Test void nativeNewObserverSkipsQualityWorkWithoutSemanticQueries() throws Exception {
    try(var f=new ConsequenceEngineTest.Fixture()) {
      f.scope.setDigitalTwin(f.twin);
      when(f.twin.getKnowledgeGraph()).thenReturn(f.graph);
      when(f.graph.inspectAssetHeaders(any(),any())).thenReturn(List.of(
          row(42,"Observation","AGENT"),row(100,"Activity",null),
          row(501,"Actuator",null)));
      var commit=ConsequenceEngineTest.commit(1);commit.getAddedObservations().add(42L);
      commit.getModifiedAssets().add(501L);
      var trigger=ConsequenceTrigger.capture(commit,100,f.scope);
      assertTrue(trigger.known());assertFalse(trigger.relevant(Consequence.Kind.UNCERTAINTY));
      assertFalse(trigger.relevant(Consequence.Kind.CHANGE_PROCESS));
      assertTrue(trigger.relevant(Consequence.Kind.CONFIGURATION_DETECTION));
      assertTrue(trigger.relevant(Consequence.Kind.ROLE_CONFERRAL));
      var assessed=new java.util.concurrent.atomic.AtomicInteger();
      for(var kind:Consequence.Kind.values())f.engine.register(kind,(cause,changes,scope)->{
        assessed.incrementAndGet();return new ConsequenceEngine.Result(Consequence.Outcome.NO_ACTION,"Checked",List.of());
      });
      f.engine.committed(commit,f.scope,100,0);
      var messages=f.messages(3);
      assertEquals(3,assessed.get());
      assertTrue(messages.stream().noneMatch(m->m.getPayload(Consequence.Decision.class)
          .cause().kind()==Consequence.Kind.UNCERTAINTY));
      verify(f.graph,never()).query(any(),any());
      verify(f.storage,never()).captureConsequenceInputs(any(),any());
      verify(f.graph,never()).createTransaction(any());
    }
  }

  @Test void qualitiesOccurrencesExistingBearersAndUnknownEvidenceRemainEligible() throws Exception {
    for(String type:List.of("QUALITY","PROCESS","EVENT","AGENT","unknown"))
      try(var f=new ConsequenceEngineTest.Fixture()) {
        f.scope.setDigitalTwin(f.twin);
        when(f.twin.getKnowledgeGraph()).thenReturn(f.graph);
        var commit=ConsequenceEngineTest.commit(1);commit.getModifiedAssets().add(42L);
        when(f.graph.inspectAssetHeaders(any(),any())).thenReturn(List.of(
            row(42,"Observation",type),row(100,"Activity",null)));
        var trigger=ConsequenceTrigger.capture(commit,100,f.scope);
        assertTrue(trigger.relevant(Consequence.Kind.CHANGE_PROCESS));
        assertEquals(type.equals("QUALITY")||type.equals("unknown"),
            trigger.relevant(Consequence.Kind.UNCERTAINTY));
      }
  }

  @Test void journalOnlyQualityChangesCannotBeSkipped() throws Exception {
    try(var f=new ConsequenceEngineTest.Fixture()) {
      f.scope.setDigitalTwin(f.twin);
      when(f.twin.getKnowledgeGraph()).thenReturn(f.graph);
      var commit=new CommitImpl();commit.setId(1);
      var journal=new SchedulerJournal(1,"step",null,Scheduler.Event.Type.TEMPORAL_TRANSITION,
          0,1,"registration","revision","support",1,List.of(42L),false);
      var receipt=new TransitionCommit(1,"context",commit,List.of(journal),List.of());
      var activity=new KnowledgeGraph.ConsequenceAssetSnapshot(100,"activity",List.of("Activity"),
          Map.of(TransitionCommit.METADATA_KEY,org.integratedmodelling.common.utils.Utils.Json.asString(receipt)),null,null);
      when(f.graph.inspectAssetHeaders(any(),any())).thenAnswer(call->{
        Collection<Long> ids=call.getArgument(0);
        var assets=new ArrayList<KnowledgeGraph.ConsequenceAssetSnapshot>();assets.add(activity);
        if(ids.contains(42L))assets.add(row(42,"Observation","QUALITY"));
        return List.copyOf(assets);
      });
      var trigger=ConsequenceTrigger.capture(commit,100,f.scope);
      assertTrue(trigger.known());assertTrue(trigger.relevant(Consequence.Kind.UNCERTAINTY));
      assertTrue(trigger.relevant(Consequence.Kind.CHANGE_PROCESS));
    }
  }

  @Test void storageUpdateWithoutAnIdentifiedQualityRemainsUnknownWithoutGuardedCapture() throws Exception {
    try(var f=new ConsequenceEngineTest.Fixture()) {
      f.scope.setDigitalTwin(f.twin);when(f.twin.getKnowledgeGraph()).thenReturn(f.graph);
      var commit=ConsequenceEngineTest.commit(1);commit.getAddedObservations().add(42L);
      commit.getModifiedAssets().add(502L);
      when(f.graph.inspectAssetHeaders(any(),any())).thenReturn(List.of(
          row(42,"Observation","AGENT"),row(100,"Activity",null),row(502,"Data",null)));
      var trigger=ConsequenceTrigger.capture(commit,100,f.scope);
      assertFalse(trigger.known());assertTrue(trigger.relevant(Consequence.Kind.UNCERTAINTY));
      assertTrue(trigger.relevant(Consequence.Kind.CHANGE_PROCESS));
      verify(f.storage,never()).captureConsequenceInputs(any(),any());
    }
  }
}
