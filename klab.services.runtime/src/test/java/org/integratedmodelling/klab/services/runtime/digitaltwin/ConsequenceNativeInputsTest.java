package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;

class ConsequenceNativeInputsTest {
  static class Fixture {
    final ServiceContextScope scope=mock(ServiceContextScope.class);final KnowledgeGraph graph=mock(KnowledgeGraph.class);
    final KnowledgeGraph.Transaction transaction=mock(KnowledgeGraph.Transaction.class);
    final Map<Long,Observation> assets=new HashMap<>();
    final List<KnowledgeGraph.ConsequenceReadSet> captured=new ArrayList<>();
    Fixture() {
      var twin=mock(org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.class);when(scope.getDigitalTwin()).thenReturn(twin);when(twin.getKnowledgeGraph()).thenReturn(graph);
      when(scope.getId()).thenReturn("context");when(scope.getObserverId()).thenReturn(null);when(graph.createTransaction(scope)).thenReturn(transaction);
      assets.put(70L,ConsequenceRecipientsTest.observation(70,SemanticType.PROCESS));assets.put(41L,ConsequenceRecipientsTest.observation(41,SemanticType.SUBJECT));
      when(transaction.captureConsequenceInputs(anyCollection(),anyCollection())).thenAnswer(call->{
        var ids=call.getArgument(0,Collection.class);var snapshots=new ArrayList<KnowledgeGraph.ConsequenceAssetSnapshot>();
        for(var id:ids)snapshots.add(snapshot(((Number)id).longValue()));
        var edge=new KnowledgeGraph.ConsequenceEdgeSnapshot("bearer-edge",ref(41),ref(70),GraphModel.Relationship.HAS_CHILD,Map.of());
        var result=new KnowledgeGraph.ConsequenceReadSet("context",snapshots,List.copyOf(call.getArgument(1,Collection.class)),List.of(edge));captured.add(result);return result;
      });
      when(graph.materializeConsequenceObservation(any(),eq(scope))).thenAnswer(call->assets.get(call.getArgument(0,KnowledgeGraph.ConsequenceAssetSnapshot.class).id()));
    }
    KnowledgeGraph.ConsequenceAssetSnapshot snapshot(long id){return new KnowledgeGraph.ConsequenceAssetSnapshot(id,"native-"+id,List.of(GraphModel.Labels.OBSERVATION),Map.of("id",id),"S2",null);}
    KnowledgeGraph.ConsequenceAssetRef ref(long id){return new KnowledgeGraph.ConsequenceAssetRef("native-"+id,id,List.of(GraphModel.Labels.OBSERVATION));}
  }
  @Test void includesActualObserverFocusSourceAndStructuralBearerWithoutCacheRefresh() {
    var f=new Fixture();var observer=ConsequenceRecipientsTest.observation(88,SemanticType.AGENT);var focus=ConsequenceRecipientsTest.observation(42,SemanticType.SUBJECT);
    f.assets.put(88L,observer);f.assets.put(42L,focus);when(f.scope.getObserverId()).thenReturn(88L);when(f.scope.getContextObservation()).thenReturn(focus);
    var captured=ConsequenceNativeInputs.capture(70,List.of(),f.scope);
    assertEquals(Set.of(41L,42L,70L,88L),captured.observations().keySet());assertEquals(88L,captured.observerId());assertEquals(42,captured.focusId());
    verify(f.scope,never()).getObservation(anyLong());
  }
  @Test void nonAgentPerspectiveOrMissingMaterializationCannotAuthorizeAnySource() {
    var f=new Fixture();when(f.scope.getObserverId()).thenReturn(41L);
    assertThrows(IllegalStateException.class,()->ConsequenceNativeInputs.capture(70,List.of(),f.scope));
    when(f.scope.getObserverId()).thenReturn(null);f.assets.remove(70L);
    assertThrows(IllegalStateException.class,()->ConsequenceNativeInputs.capture(70,List.of(),f.scope));
  }
  @Test void sourceNeighborhoodMutationBetweenCapturePassesFailsClosed() {
    var f=new Fixture();var original=f.transaction;
    doAnswer(call->{
      var source=f.snapshot(70);var selections=List.copyOf(call.getArgument(1,Collection.class));
      if(f.captured.isEmpty()) {
        var read=new KnowledgeGraph.ConsequenceReadSet("context",List.of(source),selections,List.of(new KnowledgeGraph.ConsequenceEdgeSnapshot("original-edge",f.ref(41),f.ref(70),GraphModel.Relationship.HAS_CHILD,Map.of())));
        f.captured.add(read);return read;
      }
      return new KnowledgeGraph.ConsequenceReadSet("context",List.of(source,f.snapshot(41)),selections,List.of(new KnowledgeGraph.ConsequenceEdgeSnapshot("replacement-edge",f.ref(41),f.ref(70),GraphModel.Relationship.HAS_CHILD,Map.of())));
    }).when(original).captureConsequenceInputs(anyCollection(),anyCollection());
    assertThrows(IllegalStateException.class,()->ConsequenceNativeInputs.capture(70,List.of(),f.scope));
    verify(f.graph,never()).materializeConsequenceObservation(any(),any());
  }
  @Test void closesProvisionalPassesBeforeLockingFinalSortedUnionAndRevalidatingThem() throws Exception {
    var f=new Fixture();var attempts=new ArrayList<KnowledgeGraph.Transaction>();var closed=new ArrayList<Boolean>();
    doAnswer(call->{
      assertTrue(closed.stream().allMatch(Boolean::booleanValue));
      int pass=attempts.size();var tx=mock(KnowledgeGraph.Transaction.class);attempts.add(tx);closed.add(false);
      doAnswer(done->{closed.set(pass,true);return null;}).when(tx).close();
      when(tx.captureConsequenceInputs(anyCollection(),anyCollection())).thenAnswer(read->{
        var ids=new ArrayList<Long>(read.getArgument(0,Collection.class));var selections=List.copyOf(read.getArgument(1,Collection.class));
        if(pass==3)assertEquals(List.of(3L,41L,70L),ids,"Final acquisition must know the lower-key Activity before locking");
        var snapshots=ids.stream().map(id->id==3?new KnowledgeGraph.ConsequenceAssetSnapshot(3,"native-3",List.of(GraphModel.Labels.ACTIVITY),Map.of(),null,null):f.snapshot(id)).toList();
        var edges=new ArrayList<KnowledgeGraph.ConsequenceEdgeSnapshot>();edges.add(new KnowledgeGraph.ConsequenceEdgeSnapshot("bearer",f.ref(41),f.ref(70),GraphModel.Relationship.HAS_CHILD,Map.of()));
        if(pass>=2)edges.add(new KnowledgeGraph.ConsequenceEdgeSnapshot("execution",new KnowledgeGraph.ConsequenceAssetRef("native-3",3L,List.of(GraphModel.Labels.ACTIVITY)),f.ref(70),GraphModel.Relationship.SIMULATED,Map.of()));
        return new KnowledgeGraph.ConsequenceReadSet("context",snapshots,selections,edges);
      });return tx;
    }).when(f.graph).createTransaction(f.scope);
    var snapshot=ConsequenceNativeInputs.capture(70,List.of(),f.scope);
    assertEquals(4,attempts.size());assertTrue(closed.stream().allMatch(Boolean::booleanValue));
    assertEquals(Set.of(3L,41L,70L),new HashSet<>(snapshot.reads().assetIds()));
    verify(attempts.getLast(),times(3)).validateConsequenceInputs(any());
  }

}

