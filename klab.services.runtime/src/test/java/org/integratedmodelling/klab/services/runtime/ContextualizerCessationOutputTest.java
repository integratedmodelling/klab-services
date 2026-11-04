package org.integratedmodelling.klab.services.runtime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j;

/** Real typed invocation + native ownership validation; this is not scientific commit authority. */
class ContextualizerCessationOutputTest {
 @BeforeAll static void setup(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
 static final ThreadLocal<java.util.function.Function<Data.Builder,Boolean>> action=new ThreadLocal<>();
 public static boolean report(Data.Builder builder){return action.get().apply(builder);}
 @AfterEach void clear(){action.remove();}
 static final class Fixture {
  final ServiceContextScope scope=mock(ServiceContextScope.class);
  final DigitalTwinImpl twin=mock(DigitalTwinImpl.class,CALLS_REAL_METHODS);
  final KnowledgeGraphNeo4j graph=mock(KnowledgeGraphNeo4j.class);
  final ObservationImpl producer=ConnectionExecutionTest.observation(30,SemanticType.PROCESS,false,false);
  final ObservationImpl target=ConnectionExecutionTest.observation(41,SemanticType.SUBJECT,false,false);
  final Activity activity=Activity.of(Activity.Type.SUBMISSION);
  final DigitalTwinImpl.TransactionImpl transaction;
  final Scheduler.Event event=Scheduler.Event.initialization();
  final ContextualizationScopeImpl result;
  final ContextualizerExecutor executor;
  Fixture() throws Exception {
   var field=DigitalTwinImpl.class.getDeclaredField("knowledgeGraph");field.setAccessible(true);field.set(twin,graph);
   producer.setGeometry(Geometry.UNIVERSAL);target.setGeometry(Geometry.UNIVERSAL);target.setUrn("a.41");target.setEventTimestamps(List.of(0L));
   when(scope.getId()).thenReturn("a");when(scope.getDigitalTwin()).thenReturn(twin);
   when(scope.getResolutionCycleBinding()).thenReturn(mock(org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding.class));
   transaction=twin.new TransactionImpl(activity,scope,RuntimeAsset.PROVENANCE_ASSET,producer);
   when(scope.getCurrentTransaction()).thenReturn(transaction);when(scope.getActivity()).thenReturn(activity);
   when(graph.getAsset(41L,scope,Observation.class)).thenReturn(target);when(graph.isObservationCurrent(target,scope)).thenReturn(true);
   var registry=mock(ComponentRegistry.class);var descriptor=new Extensions.FunctionDescriptor();descriptor.staticMethod=true;descriptor.serviceInfo=mock(ServiceInfo.class);
   var implementation=new ComponentRegistry.ServiceImplementation();implementation.method=ContextualizerCessationOutputTest.class.getMethod("report",Data.Builder.class);when(registry.implementation(descriptor)).thenReturn(implementation);
   executor=new ContextualizerExecutor(registry,new CompiledDataflow.CallDescriptors(null,descriptor,null,null),producer,Map.of(),new ServiceCallImpl(),scope,new ShardExecution(()->1));
   result=new ContextualizationScopeImpl(producer,event,producer.getGeometry(),scope);
  }
  boolean run(){return executor.run(event,Map.of(),scope,result);}
 }
 @Test void successfulTypedComponentForwardsExactHandleThroughNativeOutputBoundary() throws Exception {
  var f=new Fixture();action.set(builder->{builder.cease(f.target);return true;});assertTrue(f.run());assertSame(f.target,f.result.getCessations().getFirst());assertTrue(f.result.getOutcomes().isEmpty());verify(f.graph,never()).createTransaction(any());
 }
 @Test void failedOrErroredComponentCannotForwardPreviouslyReportedIntents() throws Exception {
  var f=new Fixture();action.set(builder->{builder.cease(f.target);return false;});assertFalse(f.run());assertTrue(f.result.getCessations().isEmpty());
  action.set(builder->{builder.cease(f.target);builder.notification(Notification.error("component failed"));return true;});assertFalse(f.run());assertTrue(f.result.getCessations().isEmpty());
 }
 @Test void serializedCopyRejectsTheWholeBatchBeforeAnyOutputIsPublished() throws Exception {
  var f=new Fixture();var copy=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper().convertValue(Observation.forTransport(f.target),Observation.class);assertNotSame(f.target,copy);
  action.set(builder->{builder.cease(f.target);builder.cease(copy);return true;});assertThrows(IllegalStateException.class,f::run);assertTrue(f.result.getCessations().isEmpty());
 }
 @Test void inaccessibleOrAlreadyCeasedTargetCannotBeReported() throws Exception {
  var f=new Fixture();action.set(builder->{builder.cease(f.target);return true;});when(f.graph.isObservationCurrent(f.target,f.scope)).thenReturn(false);assertThrows(IllegalStateException.class,f::run);assertTrue(f.result.getCessations().isEmpty());
  when(f.graph.isObservationCurrent(f.target,f.scope)).thenReturn(true);when(f.graph.getAsset(41L,f.scope,Observation.class)).thenReturn(null);assertThrows(IllegalStateException.class,f::run);assertTrue(f.result.getCessations().isEmpty());
 }
 @Test void unownedActivityAndMissingTransactionCannotGrantOutputAuthority() throws Exception {
  var f=new Fixture();action.set(builder->{builder.cease(f.target);return true;});when(f.scope.getActivity()).thenReturn(Activity.of(Activity.Type.SUBMISSION));assertThrows(IllegalStateException.class,f::run);
  when(f.scope.getActivity()).thenReturn(f.activity);when(f.scope.getCurrentTransaction()).thenReturn(null);assertThrows(IllegalStateException.class,f::run);assertTrue(f.result.getCessations().isEmpty());
 }
 @Test void mismatchedResultTargetRejectsOtherwiseValidIntent() throws Exception {
  var f=new Fixture();action.set(builder->{builder.cease(f.target);return true;});var wrong=new ContextualizationScopeImpl(f.target,f.event,f.target.getGeometry(),f.scope);assertThrows(IllegalStateException.class,()->f.executor.run(f.event,Map.of(),f.scope,wrong));assertTrue(wrong.getCessations().isEmpty());
 }
 @Test void localAdapterUsesTheSameGuardedResultBoundary() throws Exception {
  var f=new Fixture();var adapter=mock(org.integratedmodelling.klab.api.services.resources.adapters.Adapter.class);var resource=mock(Resource.class);when(resource.getUrn()).thenReturn("test:local:fixture:data");
  doAnswer(call->{call.getArgument(3,Data.Builder.class).cease(f.target);return true;}).when(adapter).encode(any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
  var local=new org.integratedmodelling.klab.data.LocalResourceContextualizer(adapter,resource,f.producer,Map.of());assertTrue(local.contextualize(null,f.event,f.scope,f.result));assertSame(f.target,f.result.getCessations().getFirst());
 }
 @Test void failedLocalAdapterCannotForwardAnIntent() throws Exception {
  var f=new Fixture();var adapter=mock(org.integratedmodelling.klab.api.services.resources.adapters.Adapter.class);var resource=mock(Resource.class);when(resource.getUrn()).thenReturn("test:local:fixture:data");
  doAnswer(call->{call.getArgument(3,Data.Builder.class).cease(f.target);return false;}).when(adapter).encode(any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
  var local=new org.integratedmodelling.klab.data.LocalResourceContextualizer(adapter,resource,f.producer,Map.of());assertFalse(local.contextualize(null,f.event,f.scope,f.result));assertTrue(f.result.getCessations().isEmpty());
 }
 @Test void siblingScopeAndSwappedTransactionCannotReplayTheBoundCarrier() throws Exception {
  var f=new Fixture();var binding=f.scope.getResolutionCycleBinding();var sibling=mock(ServiceContextScope.class);when(sibling.getId()).thenReturn("a");when(sibling.getDigitalTwin()).thenReturn(f.twin);when(sibling.getCurrentTransaction()).thenReturn(f.transaction);when(sibling.getActivity()).thenReturn(f.activity);when(sibling.getResolutionCycleBinding()).thenReturn(binding);when(f.graph.getAsset(41L,sibling,Observation.class)).thenReturn(f.target);when(f.graph.isObservationCurrent(f.target,sibling)).thenReturn(true);
  assertThrows(IllegalStateException.class,()->f.result.reportCessations(List.of(f.target),sibling));
  var another=f.twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION),f.scope,RuntimeAsset.PROVENANCE_ASSET,f.producer);when(f.scope.getCurrentTransaction()).thenReturn(another);
  assertThrows(IllegalStateException.class,()->f.result.reportCessations(List.of(f.target),f.scope));assertTrue(f.result.getCessations().isEmpty());
 }
 @Test void legacyUnboundCarrierPreservesOrdinaryOutputsButCannotReportCessation() throws Exception {
  var f=new Fixture();var legacy=new ContextualizationScopeImpl(f.producer,f.event);legacy.getOutcomes().add(f.target);legacy.reportCessations(List.of(),f.scope);assertEquals(1,legacy.getOutcomes().size());
  assertThrows(IllegalStateException.class,()->legacy.reportCessations(List.of(f.target),f.scope));assertTrue(legacy.getCessations().isEmpty());
 }
 @Test void configurationTargetsCannotBeReportedEvenAsAccessibleNativeHandles() throws Exception {
  var f=new Fixture();var config=ConnectionExecutionTest.observation(42,SemanticType.SUBJECT,false,false);((org.integratedmodelling.common.knowledge.ConceptImpl)config.getObservable().getSemantics()).setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.CONFIGURATION));when(f.graph.getAsset(42L,f.scope,Observation.class)).thenReturn(config);when(f.graph.isObservationCurrent(config,f.scope)).thenReturn(true);
  assertThrows(IllegalStateException.class,()->f.result.reportCessations(List.of(config),f.scope));assertTrue(f.result.getCessations().isEmpty());
 }
 @Test void foreignHandleAndFailedTransactionCannotForwardAnIntent() throws Exception {
  var f=new Fixture();var foreign=ConnectionExecutionTest.observation(61,SemanticType.SUBJECT,false,false);foreign.setUrn("b.61");when(f.graph.isObservationCurrent(foreign,f.scope)).thenReturn(true);
  assertThrows(IllegalStateException.class,()->f.result.reportCessations(List.of(foreign),f.scope));
  f.transaction.fail(new IllegalStateException("failed modeled computation"));assertThrows(IllegalStateException.class,()->f.result.reportCessations(List.of(f.target),f.scope));assertTrue(f.result.getCessations().isEmpty());
 }
 @Test void childErrorPreventsBothTypedAndLocalAdapterOutputForwarding() throws Exception {
  for(boolean local:List.of(false,true)) {
   var f=new Fixture();var child=ConnectionExecutionTest.observation(42,SemanticType.SUBJECT,false,false);child.setGeometry(Geometry.UNIVERSAL);var creation=mock(Observation.Builder.class,RETURNS_SELF);when(f.scope.observation(child.getObservable())).thenReturn(creation);when(creation.register()).thenReturn(child);
   java.util.function.Consumer<Data.Builder> reportChild=builder->builder.object("child",child.getObservable(),child.getGeometry(),null).cease(f.target).notification(Notification.error("child failed"));
   if(!local){action.set(builder->{reportChild.accept(builder);return true;});assertFalse(f.run());}
   else {var adapter=mock(org.integratedmodelling.klab.api.services.resources.adapters.Adapter.class);var resource=mock(Resource.class);when(resource.getUrn()).thenReturn("test:local:fixture:data");doAnswer(call->{reportChild.accept(call.getArgument(3,Data.Builder.class));return true;}).when(adapter).encode(any(),any(),any(),any(),any(),any(),any(),any(),any(),any());assertFalse(new org.integratedmodelling.klab.data.LocalResourceContextualizer(adapter,resource,f.producer,Map.of()).contextualize(null,f.event,f.scope,f.result));}
   assertTrue(f.result.getCessations().isEmpty());
  }
 }
}
