package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.runtime.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBinding;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.services.runtime.digitaltwin.*;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.*;

/** Tests the Runtime preparation boundary; model selection/admission is the Resolver fixture seam. */
class ConfigurationExplanationPreparationTest {
  @BeforeAll static void configure(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
  final RuntimeService runtime=mock(RuntimeService.class,CALLS_REAL_METHODS);
  final ServiceContextScope scope=mock(ServiceContextScope.class);
  final DigitalTwinImpl twin=mock(DigitalTwinImpl.class);
  final DigitalTwinImpl.TransactionImpl transaction=mock(DigitalTwinImpl.TransactionImpl.class);
  final ResolutionCycles cycles=mock(ResolutionCycles.class);
  final Resolver resolver=mock(Resolver.class);
  final ObservationImpl configuration=new ObservationImpl();
  ConfigurationExplanationPreparationTest() {
    when(scope.getId()).thenReturn("context");when(scope.getDigitalTwin()).thenReturn(twin);
    when(scope.getCurrentTransaction()).thenReturn(transaction);when(twin.getResolutionCycles()).thenReturn(cycles);
    when(cycles.require(scope)).thenReturn(scope);when(scope.getService(Resolver.class)).thenReturn(resolver);
    when(scope.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(scope);
    when(scope.getConsequenceCause()).thenReturn(new Consequence.Cause(1,"context",10,2,0,null,null,"decision",Consequence.Kind.CONFIGURATION_DETECTION));
    when(scope.getResolutionCycleBinding()).thenReturn(new ResolutionCycleBinding(1,"cycle","context","runtime",true,
        new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",3,"recipe","lease")));
    var meaning=new ConceptImpl();meaning.setUrn("test:Network");meaning.setType(EnumSet.of(SemanticType.CONFIGURATION,SemanticType.OBSERVABLE));
    var observable=spy(new ObservableImpl());observable.setUrn(meaning.getUrn());observable.setSemantics(meaning);
    var builder=mock(org.integratedmodelling.klab.api.knowledge.Observable.Builder.class);
    doReturn(builder).when(observable).builder(scope);when(builder.optional(true)).thenReturn(builder);when(builder.buildObservable()).thenReturn(observable);
    configuration.setId(41);configuration.setObservable(observable);configuration.setGeometry(Geometry.create("1"));
    var placement=mock(Cohort.class);when(placement.getId()).thenReturn(211L);
    when(transaction.incoming(configuration)).thenReturn(List.of(new org.integratedmodelling.klab.api.data.impl.LinkImpl(
        placement,configuration,GraphModel.Relationship.HAS_MEMBER)));
  }
  @Test void nativeReferenceAndStaleSerializedCoverageNeverBypassOrdinaryResolver()throws Exception {
    var plan=new ActuatorImpl();plan.setId(41);plan.setActuatorType(Actuator.Type.REFERENCE);plan.setCoverage(configuration.getGeometry());
    var executor=mock(DigitalTwin.Executor.class);when(executor.getActuator()).thenReturn(plan);
    when(transaction.getExecutor(configuration)).thenReturn(executor);
    configuration.getMetadata().put(Scheduler.PLAN_METADATA_KEY,org.integratedmodelling.klab.utilities.Utils.Json.asString(plan));
    var failure=new IllegalStateException("ordinary model/source admission unavailable");
    when(resolver.resolve(any(),same(scope))).thenReturn(CompletableFuture.failedFuture(failure));
    var thrown=assertThrows(ExecutionException.class,()->runtime.prepareConfigurationExplanation(configuration,scope));
    assertSame(failure,thrown.getCause());verify(resolver).resolve(argThat(request->request.getId()==41),same(scope));
    verify(transaction,never()).registerExecutors();
  }
  @Test void ordinaryResolverNoModelIsTheOnlyNoModelReceipt()throws Exception {
    var flow=new DataflowImpl();flow.setEmpty(true);flow.setResolutionOutcome(Dataflow.ResolutionOutcome.NO_MODEL);
    when(resolver.resolve(any(),same(scope))).thenReturn(CompletableFuture.completedFuture(flow));
    var result=runtime.prepareConfigurationExplanation(configuration,scope);
    assertEquals(RuntimeService.ConfigurationPreparation.Status.NO_MODEL,result.status());
    result.require(runtime,configuration,scope);verify(resolver).resolve(any(),same(scope));
    verify(transaction).update(configuration);verify(transaction,never()).registerExecutors();
  }
  @Test void ordinaryResolverScientificPlanCompilesAndRegistersBeforeReady()throws Exception {
    var plan=new ActuatorImpl();plan.setId(41);plan.setActuatorType(Actuator.Type.OBSERVE);plan.setCoverage(configuration.getGeometry());
    var flow=new DataflowImpl();flow.setResolutionOutcome(Dataflow.ResolutionOutcome.RESOLVED);
    flow.getComputation().add(plan);flow.setCoverage(configuration.getGeometry());
    when(resolver.resolve(any(),same(scope))).thenReturn(CompletableFuture.completedFuture(flow));
    try(var compilation=mockConstruction(CompiledDataflow.class,(compiled,context)->{
      when(compiled.compile(plan)).thenReturn(true);when(compiled.store(transaction)).thenReturn(true);
    })) {
      var result=runtime.prepareConfigurationExplanation(configuration,scope);
      assertEquals(RuntimeService.ConfigurationPreparation.Status.READY,result.status());
      verify(resolver).resolve(any(),same(scope));assertEquals(1,compilation.constructed().size());
      verify(compilation.constructed().getFirst()).compile(plan);verify(transaction).registerExecutors();
    }
  }
  @Test void preparedReceiptRequiresExactCycleObserverAndFocus()throws Exception {
    var flow=new DataflowImpl();flow.setEmpty(true);flow.setResolutionOutcome(Dataflow.ResolutionOutcome.NO_MODEL);
    when(resolver.resolve(any(),same(scope))).thenReturn(CompletableFuture.completedFuture(flow));
    var original=scope.getResolutionCycleBinding();var result=runtime.prepareConfigurationExplanation(configuration,scope);
    result.require(runtime,configuration,scope);assertEquals("1",result.support().encode());
    var focus=mock(Observation.class);when(focus.getId()).thenReturn(21L);when(scope.getContextObservation()).thenReturn(focus);
    assertThrows(SecurityException.class,()->result.require(runtime,configuration,scope));when(scope.getContextObservation()).thenReturn(null);
    var agent=mock(Observation.class);var observable=mock(org.integratedmodelling.klab.api.knowledge.Observable.class);
    when(agent.getId()).thenReturn(17L);when(agent.getObservable()).thenReturn(observable);when(observable.is(SemanticType.AGENT)).thenReturn(true);
    when(scope.getObserverId()).thenReturn(17L);when(scope.getObserver()).thenReturn(agent);
    assertThrows(SecurityException.class,()->result.require(runtime,configuration,scope));when(scope.getObserverId()).thenReturn(null);
    when(scope.getResolutionCycleBinding()).thenReturn(new ResolutionCycleBinding(1,"cycle","context","runtime",true,
        new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",4,"recipe","other-lease")));
    assertThrows(SecurityException.class,()->result.require(runtime,configuration,scope));
    when(scope.getResolutionCycleBinding()).thenReturn(original);result.require(runtime,configuration,scope);
  }
  @Test void multipleExplanationRootsDeferBeforeReplacingAnyExecutor()throws Exception {
    var flow=new DataflowImpl();flow.setResolutionOutcome(Dataflow.ResolutionOutcome.RESOLVED);
    flow.setCoverage(configuration.getGeometry());
    for(int n=0;n<2;n++){var plan=new ActuatorImpl();plan.setId(41);plan.setActuatorType(Actuator.Type.OBSERVE);flow.getComputation().add(plan);}
    when(resolver.resolve(any(),same(scope))).thenReturn(CompletableFuture.completedFuture(flow));
    try(var compilation=mockConstruction(CompiledDataflow.class)) {
      var result=runtime.prepareConfigurationExplanation(configuration,scope);
      assertEquals(RuntimeService.ConfigurationPreparation.Status.DEFERRED,result.status());
      assertTrue(compilation.constructed().isEmpty());verify(transaction,never()).registerExecutors();
    }
  }

}
