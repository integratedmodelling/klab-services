package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.lang.*;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.extension.*;
import org.integratedmodelling.klab.api.services.runtime.impl.ExpressionCodeImpl;
import org.integratedmodelling.klab.api.services.runtime.objects.*;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.runtime.computation.ScalarComputationGroovy;
import org.integratedmodelling.klab.runtime.storage.StorageReads;
import org.integratedmodelling.klab.services.resolver.MemberQualityParentPlanningFixture;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.*;

/** One compiled parent invocation. Catalog/semantic responses, discovery, ordinary submit, graph
 * persistence, scheduler and buffers are controlled. Actual Resolver/DataflowCompiler, admission
 * receipt guards, native collection continuation, Groovy/storage and typed invocation are exercised.
 * The maximum is an explicit TEST MODEL, not automatic Region redistribution or E/S sampling.
 */
class MemberQualityParentPipelineTest {
 static final List<String> sequence=new ArrayList<>();
 public static boolean maximum(@KlabFunction.Input(name="gradients") MemberQualityCollection collection,
     @KlabFunction.Output(name="self") Storage.DoubleScanner self,ContextScope scope) {
   sequence.add("mapper");assertEquals(2,collection.getEntries().size());
   double maximum=Double.NEGATIVE_INFINITY;
   for(var entry:collection.getEntries()) {
     assertEquals(entry.getMember().getId(),entry.getQuality().getParentId());
     assertEquals("test:Stream",entry.getMember().getObservable().getUrn());
     try(var reads=StorageReads.open(entry.getQuality(),scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)) {
       var scanner=reads.scanners().getFirst();assertEquals(1,scanner.size());assertTrue(scanner.isValid());maximum=Math.max(maximum,scanner.get());
     }
   }
   assertEquals(.4,maximum,1e-12);while(self.hasNext())self.add(maximum);return true;
 }
 @BeforeAll static void configure(){CollectiveCompletionQueryTest.configure();}
 @Test @Timeout(60) void oneCompiledParentPreparesActualNativeStorageThenInvokesExplicitMaximum() throws Exception {
  var graph=new CollectiveCompletionQueryTest();graph.setup();sequence.clear();
  try(var parentStorage=new StorageConsumerExecutionTest.Fixture();var first=new StorageConsumerExecutionTest.Fixture();var second=new StorageConsumerExecutionTest.Fixture()) {
   var scope=graph.scope;var runtime=graph.runtime;
   var region=MemberQualityScalarPipelineTest.observation(21,"test:Region",SemanticType.SUBJECT,"T0(1){ttype=PHYSICAL,tstart=1000,tend=10000}");
   var one=MemberQualityScalarPipelineTest.observation(31,"test:Stream",SemanticType.SUBJECT,"T0(1){ttype=PHYSICAL,tstart=1000,tend=4000}");
   var two=MemberQualityScalarPipelineTest.observation(32,"test:Stream",SemanticType.SUBJECT,"T0(1){ttype=PHYSICAL,tstart=4000,tend=8000}");
   var query=MemberQualityScalarPipelineTest.observation(40,"each test:Stream",SemanticType.SUBJECT,region.getGeometry().encode());
   ((ConceptImpl)query.getObservable().getSemantics()).setCollective(true);
   var quality=MemberQualityScalarPipelineTest.observable("test:StreamGradient of test:Stream",SemanticType.QUALITY);
   quality.setStatedName("gradients");quality.setDescriptionType(Contextualization.QUANTIFICATION);((ConceptImpl)quality.asConcept()).setDescriptionType(Contextualization.QUANTIFICATION);((ConceptImpl)quality.asConcept()).getType().add(SemanticType.RATIO);
   var output=parentStorage.quality(-100,1,Data.FillCurve.D1_LINEAR,Storage.Type.DOUBLE,region.getGeometry().encode());
   var summary=MemberQualityScalarPipelineTest.observable("test:MaximumGradient of test:Region",SemanticType.QUALITY);summary.setDescriptionType(Contextualization.QUANTIFICATION);((ConceptImpl)summary.asConcept()).setDescriptionType(Contextualization.QUANTIFICATION);((ConceptImpl)summary.asConcept()).getType().add(SemanticType.RATIO);output.setObservable(summary);output.setParentId(region.getId());
   var visible=new HashMap<Long,Observation>();for(var value:List.of(region,one,two,output))visible.put(value.getId(),value);
   when(scope.getContextObservation()).thenReturn(region);when(scope.getObservation(anyLong())).thenAnswer(call->visible.get(call.getArgument(0,Long.class)));
   when(graph.twin.getStorageManager()).thenReturn(parentStorage.manager);
   var reasoner=mock(Reasoner.class);var caps=mock(Reasoner.Capabilities.class);when(scope.getService(Reasoner.class)).thenReturn(reasoner);when(reasoner.capabilities(any())).thenReturn(caps);when(caps.getServiceId()).thenReturn("reasoner");when(caps.getKnowledgeRevision()).thenReturn(3L);
   when(reasoner.resolveConcept("test:Stream")).thenReturn(one.getObservable().asConcept());when(reasoner.resolveObservable(query.getObservable().getUrn())).thenReturn(query.getObservable());when(reasoner.resolveObservable(quality.getUrn())).thenReturn(quality);when(reasoner.inherent(quality)).thenReturn(one.getObservable().asConcept());when(reasoner.is(any(),any())).thenReturn(true);
   var resources=mock(ResourcesService.class);when(scope.getService(ResourcesService.class)).thenReturn(resources);
   doReturn(runtime).when(scope).getService(RuntimeService.class);doReturn(runtime).when(scope).getService(org.integratedmodelling.klab.api.services.RuntimeService.class);doReturn("fixture-runtime").when(runtime).serviceId();
   var settings=mock(org.integratedmodelling.klab.api.configuration.Settings.class);doReturn(settings).when(runtime).settings();when(settings.get(any(),eq(Boolean.class))).thenReturn(false);
   var registry=mock(ComponentRegistry.class);doReturn(registry).when(runtime).getComponentRegistry();
   var info=new ServiceInfoImpl();info.setName("test.explicit.maximum");var input=new ServiceInfoImpl.ArgumentImpl();input.setName("gradients");info.getInputs().add(input);var out=new ServiceInfoImpl.ArgumentImpl();out.setName("self");info.getOutputs().add(out);
   var descriptor=new Extensions.FunctionDescriptor();descriptor.staticMethod=true;descriptor.serviceInfo=info;
   var implementation=new ComponentRegistry.ServiceImplementation();implementation.method=getClass().getMethod("maximum",MemberQualityCollection.class,Storage.DoubleScanner.class,ContextScope.class);when(registry.implementation(descriptor)).thenReturn(implementation);
   when(registry.getFunctionDescriptor(any(org.integratedmodelling.klab.api.lang.ServiceCall.class))).thenReturn(List.of(descriptor));when(registry.getFunctionDescriptor(any(org.integratedmodelling.klab.api.lang.ServiceCall.class),any())).thenReturn(List.of(descriptor));
   doReturn(0).when(runtime).matchImplementation(any(),any(),any(),any(),any());doReturn(output.getContextualizationData().getNativeShardingStrategy()).when(runtime).getDefaultShardingStrategy(any(),any());doReturn(parentStorage.execution).when(runtime).shardExecution();
   // Without an installed explicit mapper this cross-bearer dependency must not silently sample
   // native values or synthesize a Region field. The source/port is otherwise unchanged.
   when(registry.getFunctionDescriptor(any(org.integratedmodelling.klab.api.lang.ServiceCall.class))).thenReturn(List.of());
   assertThrows(AssertionError.class,()->MemberQualityParentPlanningFixture.plan(output,query.getObservable(),quality,scope,reasoner));
   assertTrue(sequence.isEmpty());
   when(registry.getFunctionDescriptor(any(org.integratedmodelling.klab.api.lang.ServiceCall.class))).thenReturn(List.of(descriptor));
   var planned=MemberQualityParentPlanningFixture.plan(output,query.getObservable(),quality,scope,reasoner);
   var fixtures=List.of(first,second);var members=List.of(one,two);var values=List.of(.2,.4);
   when(runtime.getMembers(query,scope)).thenReturn(List.of(one,two));
   for(int i=0;i<2;i++) {
    var f=fixtures.get(i);var member=members.get(i);var number=values.get(i);when(scope.within(member)).thenReturn(f.scope);when(f.scope.getContextObservation()).thenReturn(member);
    doAnswer(call->{Observation submitted=call.getArgument(0);sequence.add("native:"+member.getId());
     assertEquals(quality.getUrn(),submitted.getObservable().getUrn());assertEquals(Geometry.forTransport(member.getGeometry()).encode(),Geometry.forTransport(submitted.getGeometry()).encode());
     var result=f.quality(member.getId()+100,1,Data.FillCurve.D1_LINEAR,Storage.Type.DOUBLE,member.getGeometry().encode());result.setObservable(quality);result.setParentId(member.getId());result.setResolvedCoverage(1);result.setEventTimestamps(List.of(1000L));
     var actuator=new ActuatorImpl();actuator.setObservation(result);actuator.setOperationObservable(quality);actuator.setActuatorType(Actuator.Type.OBSERVE);
     var code=new ExpressionCodeImpl();code.setCode(Double.toString(number));var builder=ScalarComputationGroovy.builder(result,f.scope,actuator,Map.of());assertTrue(builder.add(ServiceCallImpl.create(org.integratedmodelling.klab.api.services.RuntimeService.CoreFunctor.EXPRESSION_RESOLVER.getServiceCallName(),"expression",code)));
     var executor=new ScalarOperationExecutor(builder,result,Map.of(),f.scope,f.execution);assertTrue(executor.validate());assertTrue(executor.execute(Scheduler.Event.initialization(),f.scope,null));
     visible.put(result.getId(),result);var nativeStore=f.manager.getStorage(result);when(parentStorage.manager.getStorage(argThat(o->o!=null&&o.getId()==result.getId()))).thenReturn(nativeStore);
     return CompletableFuture.completedFuture(result);
    }).when(runtime).submit(any(),eq(f.scope));
   }
   doAnswer(call->{sequence.add("prepare-collection");return MemberQualityResolution.resolve(runtime,call.getArgument(0),call.getArgument(1),(m,v)->query);}).when(runtime).resolveMemberQualities(any(),any());
   when(scope.getActivity()).thenReturn(graph.root.getActivity());var failure=new AtomicReference<Throwable>();
   doAnswer(call->{var activity=call.getArgument(0,Activity.class);var executing=mock(ServiceContextScope.class,org.mockito.AdditionalAnswers.delegatesTo(scope));var tx=graph.root.getChild(activity,executing,(Object[])call.getRawArguments()[1]);when(executing.getCurrentTransaction()).thenReturn(tx);when(executing.getActivity()).thenReturn(activity);when(executing.commit()).thenAnswer(ignored->tx.commit());doAnswer(error->{failure.set(error.getArgument(0));tx.fail(error.getArgument(0));return null;}).when(executing).fail(any(Throwable.class));return executing;}).when(scope).executing(any(Activity.class),any(Object[].class));
   var plan=org.integratedmodelling.klab.utilities.Utils.Json.parseObject(org.integratedmodelling.klab.utilities.Utils.Json.asString(planned.actuator()),Actuator.class);
   var compiled=new CompiledDataflow(runtime,output,scope);assertTrue(compiled.compile(plan));assertTrue(sequence.isEmpty(),"Preallocation validation must not execute native submissions");
   assertTrue(compiled.executeRoot(output.getGeometry(),Scheduler.Event.initialization(),scope),()->String.valueOf(failure.get())+sequence);
   assertEquals(List.of("prepare-collection","native:31","native:32","mapper"),sequence);
   try(var read=StorageReads.open(output,parentStorage.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){var scanner=read.scanners().getFirst();assertEquals(1,scanner.size());assertTrue(scanner.isValid());assertEquals(.4,scanner.get(),1e-12);}
   assertEquals(region.getId(),output.getParentId());assertEquals(3,visible.values().stream().filter(o->o.getObservable().is(SemanticType.QUALITY)).count(),"Two native outputs plus only explicitly mapped parent output");
  }
 }
}
