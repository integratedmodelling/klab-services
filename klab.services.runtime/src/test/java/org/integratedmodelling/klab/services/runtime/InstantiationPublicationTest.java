package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.*;

/** Native transaction/compiled executors and root publication; storage is atomic controlled I/O. */
class InstantiationPublicationTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  @Test void acknowledgedChildrenCommitTogetherWithMembershipProvenanceAndIndependentPlansOrRollbackTogether() throws Exception {
    for (boolean abort : List.of(false, true)) {
      var f = new CollectiveCompletionQueryTest(); f.setup();
      doReturn("runtime").when(f.runtime).serviceId();
      doReturn(new ShardExecution(() -> 1)).when(f.runtime).shardExecution();
      doReturn(mock(org.integratedmodelling.klab.components.ComponentRegistry.class)).when(f.runtime).getComponentRegistry();
      when(f.graph.dataflow()).thenReturn(RuntimeAsset.DATAFLOW_ASSET);
      when(f.graph.scope()).thenReturn(RuntimeAsset.CONTEXT_ASSET);
      when(f.scope.getService(RuntimeService.class)).thenReturn(f.runtime);
      when(f.scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(f.runtime);
      when(f.scope.getActivity()).thenReturn(f.root.getActivity());
      when(f.scope.executing(any(Activity.class),any(Observation.class))).thenAnswer(call -> {
        var execution=mock(ServiceContextScope.class);
        var activity=call.getArgument(0,Activity.class);
        var tx=f.root.getChild(activity,execution,call.getArgument(1,Observation.class));
        var scopeId=f.scope.getId(); var user=f.scope.getUser();
        when(execution.getId()).thenReturn(scopeId); when(execution.getActivity()).thenReturn(activity);
        when(execution.getUser()).thenReturn(user); when(execution.getDigitalTwin()).thenReturn(f.twin);
        when(execution.getCurrentTransaction()).thenReturn(tx);
        when(execution.getService(RuntimeService.class)).thenReturn(f.runtime);
        when(execution.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(f.runtime);
        when(execution.commit()).thenAnswer(ignored -> tx.commit());
        doAnswer(failed -> { tx.fail(failed.getArgument(0)); return null; }).when(execution).fail(any(Throwable.class));
        return execution;
      });
      var ids=new AtomicLong(2000);
      doAnswer(call -> {
        RuntimeAsset asset=call.getArgument(0); long id=ids.incrementAndGet();
        if(asset instanceof ObservationImpl observation) observation.setId(id);
        if(asset instanceof org.integratedmodelling.common.knowledge.CohortImpl cohort) cohort.setId(id);
        if(asset instanceof ActivityImpl activity) activity.setId(id);
        if(asset instanceof ActuatorImpl actuator) actuator.setId(id);
        f.staged.put(asset.getId(),asset);return null;
      }).when(f.storage).store(any());
      var a=InstantiationPolicyTest.observation(-30,"road:a",false,f.producer.getGeometry());
      var b=InstantiationPolicyTest.observation(-31,"road:b",false,f.producer.getGeometry());
      var outputs=InstantiationPolicyTest.batch((ObservationImpl)f.producer,List.of(a,b),"none");
      var planners=InstantiationPlanning.prepare(outputs,f.scope);
      for(var child:List.of(a,b)) {
        var plan=planners.get(child).plan(child,f.scope).join();
        var compiled=new CompiledDataflow(f.runtime,child,f.scope,plan);
        assertTrue(compiled.compile(plan.getComputation().getFirst()));
        assertTrue(compiled.store(f.root));
        f.root.link(f.producer,child,GraphModel.Relationship.HAS_CHILD);
        f.root.link(f.cohort,child,GraphModel.Relationship.HAS_MEMBER);
        f.root.link(f.root.getActivity(),child,GraphModel.Relationship.CREATED);
        assertNotNull(f.root.getExecutor(child),"Acknowledgment retains its own ordinary lifecycle executor");
        assertTrue(f.root.getExecutor(child).run(child.getGeometry(),
            org.integratedmodelling.klab.api.digitaltwin.Scheduler.Event.initialization(),f.scope));
      }
      assertTrue(f.durable.isEmpty(),"A child cannot publish before the root");
      if(abort) {
        f.root.fail(new IllegalStateException("Required later child failed"));
        assertEquals(-1,f.root.commit()); assertTrue(f.durable.isEmpty());
        assertFalse(a.getMetadata().containsKey(InstantiationPlanning.MODE));
        verify(f.storage,never()).store(any());
      } else {
        assertEquals(900,f.root.commit());
        for(var child:List.of(a,b)) {
          var saved=(Observation)f.durable.get(child.getId()); assertNotNull(saved);
          assertEquals("none",saved.getMetadata().get(InstantiationPlanning.MODE));
          assertTrue(saved.getMetadata().containsKey(org.integratedmodelling.klab.api.digitaltwin.Scheduler.PLAN_METADATA_KEY));
          assertTrue(f.durableLinks.stream().anyMatch(link -> link.get(0)==f.cohort
              && ((RuntimeAsset)link.get(1)).getId()==child.getId() && link.get(2)==GraphModel.Relationship.HAS_MEMBER));
          assertTrue(f.durableLinks.stream().anyMatch(link -> link.get(0)==f.producer
              && ((RuntimeAsset)link.get(1)).getId()==child.getId() && link.get(2)==GraphModel.Relationship.HAS_CHILD));
        }
        assertNotEquals(a.getId(),b.getId());
      }
    }
  }
}
