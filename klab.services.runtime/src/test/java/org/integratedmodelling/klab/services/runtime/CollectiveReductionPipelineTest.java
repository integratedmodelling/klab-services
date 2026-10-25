package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.*;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.resolver.CollectiveReductionPipelineFixture;
import org.junit.jupiter.api.*;

/** One invocation of the production planned dataflow; synthetic instantiator and graph/storage I/O. */
class CollectiveReductionPipelineTest {
 public static boolean discover(Data.Builder builder,Observable observable,Geometry geometry,org.integratedmodelling.klab.api.scope.ContextScope scope) {
  int count=(Integer)scope.getData().get("fixtureMemberCount");
  if(count<0)return false;
  for(int i=0;i<count;i++)builder.object("member"+i,Observable.promote(observable.asConcept().singular()),geometry,Urn.of("fixture:member"+i));
  return true;
 }
 @BeforeAll static void configure(){CollectiveReductionExecutionTest.configure();}
 @Test void plannedChildInstantiationCompletesBeforeWholeSupportReduction() throws Exception { runPipeline(false,CollectiveReductionBinding.Mode.COUNT,2); }
 @Test void initialScaleRetainsNonDistributionThroughActualPlanTransport() throws Exception { runPipeline(true,CollectiveReductionBinding.Mode.COUNT,2); }
 @Test void successfulEmptyDiscoveryWritesZeroAndFalseInOnePipeline() throws Exception {
  for(var mode:CollectiveReductionBinding.Mode.values())runPipeline(false,mode,0);
 }
 @Test void nonEmptyPresenceWritesTrueInOnePipeline() throws Exception {runPipeline(false,CollectiveReductionBinding.Mode.PRESENCE,2);}
 @Test void failedDiscoveryDoesNotCompileOrWriteAnAbsenceValue() throws Exception {runPipeline(false,CollectiveReductionBinding.Mode.COUNT,-1);}
 @Test void distributedPortableAndScaleRequestsNeverPlanDiscovery() throws Exception {
  for(var geometry:List.of(Geometry.create("T0(1){tstart=0,tend=10,ttype=PHYSICAL}"),
      org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(Geometry.create("T1(1){tstart=0,tend=10,ttype=GRID}")))) {
   var x=new CollectiveReductionExecutionTest();x.setup();try(var storage=new StorageConsumerExecutionTest.Fixture()) {
    var output=x.output(CollectiveReductionBinding.Mode.COUNT,storage);output.setId(-100);output.setGeometry(geometry);
    doReturn(x.f.runtime).when(x.f.scope).getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
    assertNull(CollectiveReductionBinding.capture(output,x.f.scope,x.reasoner));
    assertThrows(AssertionError.class,()->CollectiveReductionPipelineFixture.plan(output,x.f.producer,x.f.scope,x.reasoner));
    verify(x.f.runtime,never()).submit(any(),any());
   }
  }
 }
 void runPipeline(boolean initialScale,CollectiveReductionBinding.Mode mode,int count) throws Exception {
  var x=new CollectiveReductionExecutionTest();x.setup();var f=x.f;
  try(var storage=new StorageConsumerExecutionTest.Fixture()) {
   var output=x.output(mode,storage);output.setId(-100);output.setParentId(x.context.getId());
   if(initialScale)output.setGeometry(org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(output.getGeometry()));
   doReturn("fixture-runtime").when(f.runtime).serviceId();
   var settings=mock(org.integratedmodelling.klab.api.configuration.Settings.class);
   doReturn(settings).when(f.runtime).settings();when(settings.get(any(),eq(Boolean.class))).thenReturn(false);
   when(f.scope.getActivity()).thenReturn(f.root.getActivity());
   doReturn(f.runtime).when(f.scope).getService(RuntimeService.class);
   doReturn(f.runtime).when(f.scope).getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
   when(x.reasoner.capabilities(any())).thenReturn(x.caps);
   var registry=mock(ComponentRegistry.class);doReturn(registry).when(f.runtime).getComponentRegistry();
   var descriptor=new Extensions.FunctionDescriptor();descriptor.staticMethod=true;descriptor.serviceInfo=mock(ServiceInfo.class);
   when(descriptor.serviceInfo.listInputs()).thenReturn(List.of());
   var implementation=new ComponentRegistry.ServiceImplementation();implementation.method=getClass().getMethod("discover",Data.Builder.class,Observable.class,Geometry.class,org.integratedmodelling.klab.api.scope.ContextScope.class);
   when(registry.implementation(descriptor)).thenReturn(implementation);
   when(registry.getFunctionDescriptor(any(org.integratedmodelling.klab.api.lang.ServiceCall.class),eq(f.scope))).thenReturn(List.of(descriptor));
   when(registry.getFunctionDescriptor(any(org.integratedmodelling.klab.api.lang.ServiceCall.class))).thenReturn(List.of(descriptor));
   var planned=CollectiveReductionPipelineFixture.plan(output,f.producer,f.scope,x.reasoner);
   f.scope.getData().put("fixtureMemberCount",count);
   var resources=f.scope.getService(ResourcesService.class);
   when(resources.retrieve(eq("test"),eq(org.integratedmodelling.klab.api.lang.kim.KimNamespace.class),any())).thenReturn(planned.namespace());
   when(x.reasoner.validateDocument(any(),any())).thenAnswer(call->{var r=org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.forRequest(call.getArgument(0));r.setStatus(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.COMPLETE);r.setKnowledgeRevision(3);return r;});
   var plan=org.integratedmodelling.klab.utilities.Utils.Json.parseObject(org.integratedmodelling.klab.utilities.Utils.Json.asString(planned.actuator()),Actuator.class);
   doReturn(0).when(f.runtime).matchImplementation(any(),any(),any(),any(),any());
   doReturn(output.getContextualizationData().getNativeShardingStrategy()).when(f.runtime).getDefaultShardingStrategy(any(),any());
   doReturn(storage.execution).when(f.runtime).shardExecution();
   when(storage.manager.getBooleanBuffer(anyLong())).thenAnswer(call->(org.ojalgo.array.BufferArray)org.ojalgo.array.BufferArray.Z008.make(call.getArgument(0,Long.class)));
   doReturn(f.cohort).when(f.runtime).getCohortFor(any(),any(),eq(false));
   var failure=new AtomicReference<Throwable>();var sequence=new ArrayList<String>();var ids=new AtomicLong(-200);
   doAnswer(call->{
    var activity=call.getArgument(0,Activity.class);var executing=mock(ServiceContextScope.class,org.mockito.AdditionalAnswers.delegatesTo(f.scope));
    var tx=f.root.getChild(activity,executing,(Object[])call.getRawArguments()[1]);
    when(executing.getCurrentTransaction()).thenReturn(tx);when(executing.getActivity()).thenReturn(activity);
    when(executing.commit()).thenAnswer(ignored->{sequence.add("complete:"+activity.getType());return tx.commit();});
    doAnswer(err->{failure.set(err.getArgument(0));tx.fail(err.getArgument(0));return null;}).when(executing).fail(any(Throwable.class));
    return executing;
   }).when(f.scope).executing(any(Activity.class),any(Object[].class));
   when(f.scope.observation(any(Observable.class))).thenAnswer(call->new ObservationBuilderImpl(call.getArgument(0,Observable.class),f.scope){public Observation register(){return build();}public CompletableFuture<Observation> submit(){return CompletableFuture.completedFuture(build());}});
   doAnswer(call->{var child=call.getArgument(0,Observation.class);((ObservationImpl)child).setId(ids.decrementAndGet());
    sequence.add("member");f.root.link(f.cohort,child,GraphModel.Relationship.HAS_MEMBER);
    var creator=Activity.of(Activity.Type.INSTANTIATION);org.integratedmodelling.common.runtime.ObserverProvenance.recordSelection(creator,null);f.root.link(creator,child,GraphModel.Relationship.CREATED);
    return CompletableFuture.completedFuture(child);
   }).when(f.runtime).submit(any(Observation.class),any());
   doAnswer(call->org.integratedmodelling.klab.runtime.computation.ScalarComputationGroovy.builder(call.getArgument(0),call.getArgument(1),call.getArgument(2),call.getArgument(3)))
       .when(f.runtime).getComputationBuilder(any(),any(),any(),anyMap());
   var compiled=new CompiledDataflow(f.runtime,output,f.scope);assertTrue(compiled.compile(plan));
   var scheduler=mock(Scheduler.class);when(f.twin.getScheduler()).thenReturn(scheduler);
   var child=plan.getChildren().getFirst();var childExecutor=compiled.new ExecutorImpl(child);assertTrue(childExecutor.isOperational());
   when(scheduler.executeDependency(any(),any(),any(),any())).thenAnswer(call->{sequence.add("discover");return childExecutor.run(call.getArgument(1),call.getArgument(2),call.getArgument(3));});
   var parent=compiled.new ExecutorImpl(plan);assertTrue(parent.isOperational());
   var succeeded=compiled.executeRoot(output.getGeometry(),Scheduler.Event.initialization(),f.scope);
   if(count<0) {
    assertFalse(succeeded);verify(f.runtime,never()).getComputationBuilder(any(),any(),any(),anyMap());
    assertTrue(f.runtime.queryCollective(f.query,f.scope).isEmpty());return;
   }
   assertTrue(succeeded,()->String.valueOf(failure.get())+sequence);
   assertEquals(mode==CollectiveReductionBinding.Mode.COUNT?count:count>0?1:0,x.read(output,storage));
   assertEquals(count,f.runtime.getMembers(f.runtime.queryCollective(f.query,f.scope),f.scope).size());
   assertTrue(sequence.indexOf("complete:INSTANTIATION")>sequence.lastIndexOf("member"),sequence.toString());
   verify(scheduler,times(1)).executeDependency(any(),any(),any(),any());
  }
 }
}
