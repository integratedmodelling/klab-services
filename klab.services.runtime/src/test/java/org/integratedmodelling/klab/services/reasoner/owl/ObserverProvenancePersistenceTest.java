package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import org.integratedmodelling.common.runtime.ObserverProvenance;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.services.runtime.neo4j.*;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.*;

/** Embedded commit/cold graph proof of actual-Agent provenance, separately from wrapper class identity. */
class ObserverProvenancePersistenceTest {
  private static final class Fixture implements AutoCloseable {
    final ObserverDurableSelectionTest helpers=new ObserverDurableSelectionTest();
    final ObserverDurableSelectionTest.Vocabulary vocabulary=new ObserverDurableSelectionTest.Vocabulary();
    final NativeFamilyRecovery database=new NativeFamilyRecovery();
    final ServiceContextScope scope=helpers.scope(vocabulary);
    final KnowledgeGraphNeo4j graph=database.initializeRoot(scope);
    final ObservationImpl first=helpers.observation(vocabulary.meaning("Observer",false));
    final ObservationImpl second=helpers.observation(first.getObservable());
    Fixture() throws Exception {
      helpers.attach(scope,graph);
      try(var tx=graph.createTransaction(scope)) {
        for(var observer:List.of(first,second)) {tx.store(observer);tx.link(RuntimeAsset.CONTEXT_ASSET,observer,GraphModel.Relationship.HAS_CHILD);}
      }
    }
    ObservationImpl quality(){return helpers.observation(vocabulary.meaning("Input",false));}
    Activity activity(Activity.Type type){return Activity.of(type);}
    void cold(){helpers.attach(scope,database.coldGraph());}
    public void close(){database.close();vocabulary.close();}
  }

  @Test @Timeout(90) void nearestRecordedActivitySurvivesColdGraphAndOverridesOnlyAutonomousScope()throws Exception {
    try(var f=new Fixture()) {
      var observation=f.quality();var submission=f.activity(Activity.Type.SUBMISSION);var resolution=f.activity(Activity.Type.RESOLUTION);
      ObserverProvenance.recordSelection(submission,f.first);
      try(var tx=f.graph.createTransaction(f.scope)) {
        tx.store(observation);tx.store(submission);tx.store(resolution);
        tx.link(RuntimeAsset.CONTEXT_ASSET,observation,GraphModel.Relationship.HAS_CHILD);
        tx.link(RuntimeAsset.PROVENANCE_ASSET,submission,GraphModel.Relationship.HAS_ACTIVITY);
        tx.link(submission,resolution,GraphModel.Relationship.TRIGGERED);
        tx.link(resolution,observation,GraphModel.Relationship.CREATED);
        tx.link(submission,f.first,GraphModel.Relationship.HAS_OBSERVER);
      }
      f.cold();var root=f.scope.withObserver(f.second);
      assertEquals(f.first.getId(),root.getObserverOf(observation).getId());
      var autonomous=root.of(observation);
      assertEquals(f.first.getId(),autonomous.getObserver().getId());
      assertEquals(f.second.getId(),root.getObserver().getId());
      var submitted=root.withResolutionConstraints(ResolutionConstraint.of(ResolutionConstraint.Type.Observer,f.second.getId()));
      assertEquals(f.second.getId(),submitted.getObserver().getId());
      assertNull(observation.getObservable().getObserverSemantics());
      assertEquals(f.first.getId(),dispatchSelected(root,observation));
    }
  }

  @Test @Timeout(90) void recordedNullIsDistinctFromLegacyUnknownAndCannotBorrowCurrentDefault()throws Exception {
    try(var f=new Fixture()) {
      var absolute=f.quality();var legacy=f.quality();var parent=f.quality();var unrecorded=f.quality();
      var known=f.activity(Activity.Type.SUBMISSION);var unknown=f.activity(Activity.Type.SUBMISSION);var parentSubmission=f.activity(Activity.Type.SUBMISSION);
      ObserverProvenance.recordSelection(known,null);
      ObserverProvenance.recordSelection(parentSubmission,f.first);
      try(var tx=f.graph.createTransaction(f.scope)) {
        for(var o:List.of(absolute,legacy,parent,unrecorded)){tx.store(o);tx.link(RuntimeAsset.CONTEXT_ASSET,o,GraphModel.Relationship.HAS_CHILD);}
        for(var a:List.of(known,unknown,parentSubmission)){tx.store(a);tx.link(RuntimeAsset.PROVENANCE_ASSET,a,GraphModel.Relationship.HAS_ACTIVITY);}
        tx.link(known,absolute,GraphModel.Relationship.CREATED);tx.link(unknown,legacy,GraphModel.Relationship.CREATED);
        tx.link(parentSubmission,parent,GraphModel.Relationship.CREATED);tx.link(parentSubmission,f.first,GraphModel.Relationship.HAS_OBSERVER);
        tx.link(parent,legacy,GraphModel.Relationship.HAS_CHILD);tx.link(parent,unrecorded,GraphModel.Relationship.HAS_CHILD);
      }
      f.cold();var root=f.scope.withObserver(f.first);
      assertTrue(ObserverProvenance.recover(root,absolute).known());assertNull(root.of(absolute).getObserver());
      assertEquals(0,dispatchSelected(root,absolute));
      assertFalse(ObserverProvenance.recover(root,legacy).known());
      assertTrue(assertThrows(IllegalStateException.class,()->root.of(legacy)).getMessage().contains("resubmit"));
      assertEquals(f.first.getId(),root.getObserverOf(parent).getId());
      assertThrows(IllegalStateException.class,()->root.of(unrecorded),"Known parent cannot identify unrecorded child selection");
      assertEquals(f.first.getId(),root.getObserver().getId());
    }
  }

  @Test @Timeout(90) void inaccessibleOrConflictingActualAgentsFailInsteadOfChoosingFirst()throws Exception {
    try(var f=new Fixture()) {
      var target=f.quality();var ambiguous=f.quality();var branched=f.quality();
      var first=f.activity(Activity.Type.SUBMISSION);var other=f.activity(Activity.Type.SUBMISSION);var child=f.activity(Activity.Type.RESOLUTION);
      ObserverProvenance.recordSelection(first,f.first);ObserverProvenance.recordSelection(other,f.second);
      try(var tx=f.graph.createTransaction(f.scope)) {
        for(var o:List.of(target,ambiguous,branched)){tx.store(o);tx.link(RuntimeAsset.CONTEXT_ASSET,o,GraphModel.Relationship.HAS_CHILD);}
        for(var a:List.of(first,other,child)){tx.store(a);tx.link(RuntimeAsset.PROVENANCE_ASSET,a,GraphModel.Relationship.HAS_ACTIVITY);}
        tx.link(first,target,GraphModel.Relationship.CREATED);tx.link(first,ambiguous,GraphModel.Relationship.CREATED);
        tx.link(other,ambiguous,GraphModel.Relationship.CREATED);
        tx.link(first,f.first,GraphModel.Relationship.HAS_OBSERVER);tx.link(other,f.second,GraphModel.Relationship.HAS_OBSERVER);
        tx.link(first,branched,GraphModel.Relationship.CREATED);tx.link(other,child,GraphModel.Relationship.TRIGGERED);
        tx.link(child,branched,GraphModel.Relationship.CREATED);
      }
      f.cold();assertThrows(IllegalStateException.class,()->f.scope.of(ambiguous));
      assertThrows(IllegalStateException.class,()->f.scope.of(branched),"A shallow record cannot hide a conflicting deeper creation branch");
      // Keep the Activity visible but move its expected observer outside this context.
      f.database.execute("MATCH (:Context {id:$context})-[r:HAS_CHILD]->(o:Observation {id:$observer}) DELETE r WITH o CREATE (:Context {id:'foreign'})-[:HAS_CHILD]->(o)",
          Map.of("context",f.scope.getId(),"observer",f.first.getId()));
      assertThrows(IllegalStateException.class,()->f.scope.of(target));
    }
  }

  @Test @Timeout(90) void cyclicUnrecordedActivityChainTerminatesWithoutInventingAnObserver()throws Exception {
    try(var f=new Fixture()) {
      var observation=f.quality();var first=f.activity(Activity.Type.RESOLUTION);var second=f.activity(Activity.Type.RESOLUTION);
      try(var tx=f.graph.createTransaction(f.scope)) {
        tx.store(observation);tx.store(first);tx.store(second);
        tx.link(RuntimeAsset.CONTEXT_ASSET,observation,GraphModel.Relationship.HAS_CHILD);
        tx.link(RuntimeAsset.PROVENANCE_ASSET,first,GraphModel.Relationship.HAS_ACTIVITY);
        tx.link(first,observation,GraphModel.Relationship.CREATED);
        tx.link(first,second,GraphModel.Relationship.TRIGGERED);tx.link(second,first,GraphModel.Relationship.TRIGGERED);
      }
      f.cold();assertFalse(ObserverProvenance.recover(f.scope,observation).known());
      assertThrows(IllegalStateException.class,()->f.scope.of(observation));
    }
  }

  @Test @Timeout(90) void fractionalNonfiniteAndStringSelectionMetadataAreRejected()throws Exception {
    try(var f=new Fixture()) {
      var observations=new ArrayList<Observation>();
      try(var tx=f.graph.createTransaction(f.scope)) {
        for(var corrupt:List.of((double)f.first.getId(),1.5,Double.NaN,Double.POSITIVE_INFINITY,"0",-1L)) {
          var observation=f.quality();var submission=f.activity(Activity.Type.SUBMISSION);
          submission.getMetadata().put(org.integratedmodelling.klab.api.data.Metadata.IM_OBSERVER_SELECTION_ID,corrupt);
          tx.store(observation);tx.store(submission);tx.link(RuntimeAsset.CONTEXT_ASSET,observation,GraphModel.Relationship.HAS_CHILD);
          tx.link(RuntimeAsset.PROVENANCE_ASSET,submission,GraphModel.Relationship.HAS_ACTIVITY);
          tx.link(submission,observation,GraphModel.Relationship.CREATED);tx.link(submission,f.first,GraphModel.Relationship.HAS_OBSERVER);
          observations.add(observation);
        }
      }
      f.cold();for(var observation:observations)assertThrows(IllegalStateException.class,()->f.scope.of(observation));
    }
  }

  /** Exercise autonomous dispatch's actual scope selection, stopping at its transaction boundary. */
  private long dispatchSelected(ServiceContextScope root,Observation observation)throws Exception {
    var graph=root.getDigitalTwin().getKnowledgeGraph();
    var twin=mock(org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.class);
    when(twin.getKnowledgeGraph()).thenReturn(graph);root.setDigitalTwin(twin);
    var seen=new java.util.concurrent.atomic.AtomicLong(-99);
    when(twin.transaction(any(Activity.class),any(org.integratedmodelling.klab.api.scope.ContextScope.class),any(Object[].class))).thenAnswer(call->{
      var selected=call.getArgument(1,org.integratedmodelling.klab.api.scope.ContextScope.class).getObserver();
      seen.set(selected==null?0:selected.getId());
      var recorded=call.getArgument(0,Activity.class).getMetadata().get(org.integratedmodelling.klab.api.data.Metadata.IM_OBSERVER_SELECTION_ID);
      assertEquals(seen.get(),((Number)recorded).longValue());
      throw new IllegalStateException("Fixture stops after autonomous observer selection, before transaction execution");
    });
    try(var scheduler=new org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.SchedulerImpl(root,twin)) {
      var dispatch=scheduler.getClass().getDeclaredMethod("dispatch",Observation.class,
          org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.OccurrenceRegistration.class,
          org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.TransitionEvent.class,boolean.class);
      dispatch.setAccessible(true);
      assertFalse((Boolean)dispatch.invoke(scheduler,observation,null,
          new org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.TransitionEvent("observer-fixture",0,1,null),false));
    }
    return seen.get();
  }
}
