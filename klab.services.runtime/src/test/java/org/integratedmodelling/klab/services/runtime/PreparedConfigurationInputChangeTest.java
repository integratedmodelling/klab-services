package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.services.runtime.digitaltwin.ResolutionCycles;
import org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.SchedulerImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Real private preparation, actual compiler, native scientific child, Scheduler and ledger.
 * Semantic/source resolution and persistence remain the existing controlled fixture boundaries. */
class PreparedConfigurationInputChangeTest {
 @Test void preparedInputChangeRunsExactNoOpCompilerAndCompletesItsPrivateLedger() throws Exception { run(false,false); }
 @Test void preparedInitializationRunsExactNoOpCompilerAndStagedPrerequisites() throws Exception { run(true,false); }
 @Test void failedStagedPrerequisitePreventsPreparedRootAndScientificCompletion() throws Exception { run(false,true); }
 @Test void actualResolverRootRetainsNativeMeaningRatherThanOptionalRequestSyntax() throws Exception { run(false,false,true); }
 void run(boolean initial,boolean dependencyFails) throws Exception {
  run(initial,dependencyFails,false);
 }
 void run(boolean initial,boolean dependencyFails,boolean resolverRoot) throws Exception {
  var f=new ScientificRootCompletionTest().new Fixture(true);
  f.nativeGraph.existing.add(f.target.getId());
  var scope=f.nativeGraph.scope;var transaction=scope.getCurrentTransaction();
  var cycles=mock(ResolutionCycles.class);var admission=mock(ResolutionCycles.Admission.class);
  when(f.nativeGraph.twin.getResolutionCycles()).thenReturn(cycles);when(cycles.require(scope)).thenReturn(scope);
  when(cycles.admit(scope,true)).thenReturn(CompletableFuture.completedFuture(admission));when(admission.scope()).thenReturn(scope);
  transaction.link(f.nativeGraph.cohort,f.target,GraphModel.Relationship.HAS_MEMBER);
  when(f.nativeGraph.graph.getScheduledObservations(scope)).thenReturn(List.of());
  when(f.nativeGraph.graph.dataflow()).thenReturn(RuntimeAsset.DATAFLOW_ASSET);
  var resolver=mock(Resolver.class);when(scope.getService(Resolver.class)).thenReturn(resolver);
  when(scope.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(scope);
  var observable=spy(f.target.getObservable());f.target.setObservable(observable);
  var builder=mock(Observable.Builder.class);doReturn(builder).when(observable).builder(scope);
  when(builder.optional(true)).thenReturn(builder);
  var optional=new org.integratedmodelling.common.knowledge.ObservableImpl((org.integratedmodelling.common.knowledge.ObservableImpl)observable);
  optional.setOptional(true);optional.setUrn(optional.getUrn()+" optional");when(builder.buildObservable()).thenReturn(resolverRoot?optional:observable);
  if(!initial)f.target.getMetadata().put("klab.configuration.scientificInitialization","configuration-scientific-initialization-v1");
  var flow=new DataflowImpl();flow.setResolutionOutcome(Dataflow.ResolutionOutcome.RESOLVED);
  flow.setCoverage(f.target.getGeometry());flow.getComputation().add(f.plan);
  when(resolver.resolve(any(),same(scope))).thenAnswer(call->{
   if(resolverRoot)f.plan.setObservation(Observation.forTransport(call.getArgument(0,Observation.class)));
   return CompletableFuture.completedFuture(flow);
  });
  var scheduler=new SchedulerImpl(scope,f.nativeGraph.twin);when(f.nativeGraph.twin.getScheduler()).thenReturn(scheduler);
  var staleCalls=new AtomicInteger();scheduler.registerExecutor(f.target,(geometry,event,execution)->{staleCalls.incrementAndGet();return true;});
  try {
   var prepared=f.runtime.prepareConfigurationExplanation(f.target,scope);
   assertEquals(RuntimeService.ConfigurationPreparation.Status.READY,prepared.status());assertEquals(initial,prepared.initialCharacterization());
   // A controlled prerequisite executor tests actual transaction-edge overlay and ordering; the
   // configuration root itself retains its real legitimate no-computation scientific compiler.
   var prerequisite=new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();
   prerequisite.setId(-77);prerequisite.setObservable(f.target.getObservable());prerequisite.setGeometry(f.target.getGeometry());
   transaction.add(prerequisite);transaction.link(prerequisite,f.target,GraphModel.Relationship.AFFECTS,
       org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.EDGE_ROLE,org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.PREREQUISITE);
   var prerequisiteCalls=new AtomicInteger();var operation=mock(DigitalTwin.Executor.class);
   when(operation.executionScope(any())).thenReturn(scope);when(operation.run(any(),any(),any())).thenAnswer(call->{
    assertEquals(0,f.actualPublications.get(),"Newly staged prerequisite must run before exact prepared root");
    prerequisiteCalls.incrementAndGet();return !dependencyFails;
   });transaction.resolveWith(prerequisite,operation);
   when(f.nativeGraph.graph.getLinks(any(),eq(GraphModel.Relationship.Direction.INCOMING),same(scope),eq(GraphModel.Relationship.AFFECTS)))
       .thenAnswer(call->transaction.incoming(call.getArgument(0)).stream().filter(edge->edge.type()==GraphModel.Relationship.AFFECTS).toList());
   assertNull(transaction.getExecutor(f.target),"Legitimate no-op scientific root has no per-actuator executor");
   assertEquals(0,f.actualPublications.get());
   assertThrows(SecurityException.class,()->prepared.executePreparedRoot(f.target,scope,prepared.support(),Scheduler.Event.initialization()),
       "A private preparation alone cannot replace the recorded Scheduler invocation");
   var ledger=RuntimeService.ConfigurationPreparation.class.getDeclaredMethod("requireScientificCompletion",Observation.class,ServiceContextScope.class);
   ledger.setAccessible(true);assertThrows(java.lang.reflect.InvocationTargetException.class,()->ledger.invoke(prepared,f.target,scope));
   assertEquals(dependencyFails?Scheduler.ConfigurationRecomputeOutcome.FAILED:Scheduler.ConfigurationRecomputeOutcome.RESOLVED,
       scheduler.recomputeConfiguration(f.target,prepared.support(),101,2,scope,prepared));
   assertEquals(1,prerequisiteCalls.get());
   assertEquals(0,staleCalls.get(),"Committed cached compiler cannot fulfill a fresh preparation");
   if(dependencyFails) {
    assertEquals(0,f.actualPublications.get());assertThrows(java.lang.reflect.InvocationTargetException.class,()->ledger.invoke(prepared,f.target,scope));
   } else {
    assertEquals(1,f.actualPublications.get(),"Actual newly compiled scientific child must run");
    assertDoesNotThrow(()->ledger.invoke(prepared,f.target,scope));
    if(initial)assertEquals("Configuration prepared scientific root was already invoked",
        assertThrows(SecurityException.class,()->prepared.executePreparedRoot(f.target,scope,prepared.support(),Scheduler.Event.initialization())).getMessage());
   }
   assertTrue(scheduler.currentModeledInstant().isEmpty());
  } finally {scheduler.close();}
 }
}
