package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.ScalarComputation;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

class OccurrenceExecutorTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  @Test void fullCollapsedResolverCoverageIsNotPartialOccurrenceCoverage() {
    var process=observation("erosion",SemanticType.PROCESS,100);
    var geometry=Geometry.create("T1(365){ttype=GRID,tstart=1388534400000,tend=1420070400000,tscope=1.0,tunit=DAY}S2(10,10){proj=EPSG:4326,shape=EPSG:4326 POLYGON ((0 0&comma;0 1&comma;1 1&comma;1 0&comma;0 0))}");
    process.setGeometry(geometry);
    var plan=new ActuatorImpl();plan.setObservation(process);plan.setName("erosion");
    plan.setActuatorType(Actuator.Type.RESOLVE);plan.setExecutionRole(Actuator.ExecutionRole.PROCESS);
    plan.getComputation().add(new ServiceCallImpl("test.process"));
    plan.getOccurrenceSchedules().put(0,new OccurrenceSchedule(1,"","",1,
        Time.Resolution.Type.MONTH,true,OccurrenceSchedule.Source.MODEL));
    var scale=org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(geometry);
    plan.setCoverage(Geometry.forTransport(org.integratedmodelling.klab.api.services.resolver.Coverage.create(scale,1).as(Geometry.class)));
    assertDoesNotThrow(() -> CompiledDataflow.validateSupportedPlan(plan));
    plan.setCoverage(Geometry.create("T0(1){ttype=PHYSICAL,tstart=1388534400000,tend=1404172800000}S2(10,10){proj=EPSG:4326,shape=EPSG:4326 POLYGON ((0 0&comma;0 1&comma;1 1&comma;1 0&comma;0 0))}"));
    assertThrows(UnsupportedOperationException.class,() -> CompiledDataflow.validateSupportedPlan(plan));
    plan.setCoverage(Geometry.create("T1(365){ttype=GRID,tstart=1388534400000,tend=1420070400000,tscope=1.0,tunit=DAY}S2(5,10){proj=EPSG:4326,shape=EPSG:4326 POLYGON ((0 0&comma;0 1&comma;0.5 1&comma;0.5 0&comma;0 0))}"));
    assertThrows(UnsupportedOperationException.class,() -> CompiledDataflow.validateSupportedPlan(plan));
  }
  private static final java.util.List<String> transitions = new java.util.concurrent.CopyOnWriteArrayList<>();
  public static void simulate(Geometry geometry, Scheduler.Event event) {
    var time = org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(geometry).getTime();
    assertEquals(event.getTime().getStart().getMilliseconds(), time.getStart().getMilliseconds());
    assertEquals(event.getTime().getEnd().getMilliseconds(), time.getEnd().getMilliseconds());
    transitions.add(event.toKey());
  }

  @Test void portableJavaProcessRunsWithTransitionGeometryAfterEveryCacheRestoration() throws Exception {
    transitions.clear();
    var runtime = mock(RuntimeService.class);
    var registry = mock(org.integratedmodelling.klab.components.ComponentRegistry.class);
    when(runtime.getComponentRegistry()).thenReturn(registry);
    var descriptor = new org.integratedmodelling.klab.api.services.runtime.extension.Extensions.FunctionDescriptor();
    descriptor.staticMethod = true; descriptor.serviceInfo = mock(org.integratedmodelling.klab.api.lang.ServiceInfo.class);
    var implementation = new org.integratedmodelling.klab.components.ComponentRegistry.ServiceImplementation();
    implementation.method = getClass().getMethod("simulate", Geometry.class, Scheduler.Event.class);
    when(registry.getFunctionDescriptor(any())).thenReturn(java.util.List.of(descriptor));
    when(registry.implementation(descriptor)).thenReturn(implementation);
    var process = observation("process",SemanticType.PROCESS,100);
    process.setObservable(((ObservableImpl)process.getObservable()).as(org.integratedmodelling.klab.api.knowledge.Contextualization.SIMULATION));
    var plan = new ActuatorImpl(); plan.setName("process"); plan.setObservation(process);
    plan.setActuatorType(Actuator.Type.RESOLVE); plan.setExecutionRole(Actuator.ExecutionRole.PROCESS);
    plan.getComputation().add(new ServiceCallImpl("test.simulate"));
    plan.getOccurrenceSchedules().put(0,new OccurrenceSchedule(1,"","",1,Time.Resolution.Type.MONTH,true,OccurrenceSchedule.Source.MODEL));
    var encoded = Utils.Json.asString(CompiledDataflow.portableOccurrencePlan(plan));
    for (int n=0;n<2;n++) {
      var scope = mock(ServiceContextScope.class); var childScope = mock(ServiceContextScope.class);
      when(scope.getObservation(100)).thenReturn(process);
      when(scope.getDigitalTwin()).thenReturn(mock(DigitalTwin.class));
      when(scope.executing(any(),eq(process))).thenReturn(childScope);
      when(childScope.getService(RuntimeService.class)).thenReturn(runtime);
      when(childScope.commit()).thenReturn(0L);
      var restored = new CompiledDataflow(runtime,process,scope).restoreOccurrenceExecutor(Utils.Json.parseObject(encoded,Actuator.class));
      var event = new org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.TransitionEvent("tick:"+n,
          1388534400000L+n*86400000L,1388534400000L+(n+1)*86400000L,null);
      assertTrue(restored.run(TemporalGeometry.localize(process.getGeometry(),event),event,scope));
    }
    assertEquals(java.util.List.of("tick:0","tick:1"),transitions);
    // Dispatch must not write quality buffers without the transaction-owned temporal write set.
    var quality = observation("quality",SemanticType.QUALITY,101);
    plan.setObservation(quality); plan.setExecutionRole(Actuator.ExecutionRole.INITIALIZATION);
    plan.getOccurrenceSchedules().clear();
    var scope = mock(ServiceContextScope.class); var twin = mock(DigitalTwin.class);
    when(scope.getDigitalTwin()).thenReturn(twin); when(scope.getObservation(101)).thenReturn(quality);
    var restored = new CompiledDataflow(runtime,quality,scope).restoreOccurrenceExecutor(plan);
    assertThrows(UnsupportedOperationException.class, () -> restored.run(quality.getGeometry(),
        new org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.TransitionEvent("quality",1,2,null),scope));
    verify(twin,never()).getStorageManager();
  }

  private ObservationImpl observation(String name, SemanticType type, long id) {
    var concept = new ConceptImpl();
    concept.setUrn("test:" + name); concept.setName(name); concept.setNamespace("test");
    concept.getType().add(type);
    var result = new ObservationImpl();
    result.setId(id); result.setUrn("context." + name);
    result.setObservable(ObservableImpl.promote(concept, null));
    result.setGeometry(Geometry.create("T0(1){tstart=1388534400000,tend=1420070400000,ttype=PHYSICAL}"));
    return result;
  }

  @Test void restoredTemporalExecutorsInitializeInputsWithoutExecutingComputation() {
    for (var role : new Actuator.ExecutionRole[] {
        Actuator.ExecutionRole.PROCESS, Actuator.ExecutionRole.EVENT_INSTANTIATOR}) {
      var scope = mock(ServiceContextScope.class);
      var runtime = mock(RuntimeService.class);
      var twin = mock(DigitalTwin.class);
      var scheduler = mock(Scheduler.class);
      when(scope.getDigitalTwin()).thenReturn(twin);
      when(twin.getScheduler()).thenReturn(scheduler);
      var process = observation("occurrence", role == Actuator.ExecutionRole.PROCESS
          ? SemanticType.PROCESS : SemanticType.EVENT, 100);
      var input = observation("elevation", SemanticType.QUALITY, 101);
      when(scope.getObservation(100)).thenReturn(process);
      when(scope.getObservation(101)).thenReturn(input);
      var plan = new ActuatorImpl();
      plan.setObservation(process); plan.setName("occurrence");
      plan.setActuatorType(Actuator.Type.RESOLVE); plan.setExecutionRole(role);
      plan.getComputation().add(new ServiceCallImpl("klab.core.expression.resolver"));
      plan.getOccurrenceSchedules().put(0, new OccurrenceSchedule(1, "", "", 1,
          Time.Resolution.Type.MONTH, true, OccurrenceSchedule.Source.MODEL));
      var dependency = new ActuatorImpl();
      dependency.setObservation(input); dependency.setName("elevation");
      dependency.setActuatorType(Actuator.Type.REFERENCE);
      plan.getChildren().add(dependency);
      var snapshot = Utils.Json.parseObject(
          Utils.Json.asString(CompiledDataflow.portableOccurrencePlan(plan)), Actuator.class);
      assertNotSame(input, snapshot.getChildren().getFirst().getObservation());
      var scalar = mock(ScalarComputation.class);
      var builder = mock(ScalarComputation.Builder.class);
      when(builder.build()).thenReturn(scalar);
      when(runtime.getComputationBuilder(eq(process), eq(scope), any(), anyMap())).thenAnswer(call -> {
        assertSame(input, ((java.util.Map<?, ?>) call.getArgument(3)).get("elevation"));
        return builder;
      });
      var executor = new CompiledDataflow(runtime, process, scope).restoreOccurrenceExecutor(snapshot);
      verifyNoInteractions(scheduler, scalar);
      when(scheduler.executeDependency(eq(input), eq(input.getGeometry()), any(), eq(scope)))
          .thenReturn(true, false);
      assertTrue(executor.run(process.getGeometry(), Scheduler.Event.initialization(), scope));
      assertFalse(executor.run(process.getGeometry(), Scheduler.Event.initialization(), scope));
      assertThrows(UnsupportedOperationException.class,
          () -> executor.run(process.getGeometry(), Scheduler.event(1, 2), scope));
      verifyNoInteractions(scalar);
      verify(twin, never()).getStorageManager();
      when(scope.getObservation(101)).thenReturn(null);
      assertThrows(RuntimeException.class,
          () -> new CompiledDataflow(runtime, process, scope).restoreOccurrenceExecutor(snapshot));
    }
  }

  @Test void createdQualityRemainsADeclarationThroughRestoreAndInit() {
    var scope = mock(ServiceContextScope.class);
    var runtime = mock(RuntimeService.class);
    var twin = mock(DigitalTwin.class);
    when(scope.getDigitalTwin()).thenReturn(twin);
    var process = observation("rainfall", SemanticType.PROCESS, 100);
    var bearer = observation("region", SemanticType.SUBJECT, 200);
    when(scope.getObservation(100)).thenReturn(process);
    when(scope.getContextObservation()).thenReturn(bearer);
    when(scope.within(bearer)).thenReturn(scope);
    var plan = new ActuatorImpl(); plan.setObservation(process); plan.setName("rainfall");
    plan.setExecutionRole(Actuator.ExecutionRole.PROCESS); plan.setActuatorType(Actuator.Type.RESOLVE);
    plan.getOccurrenceSchedules().put(0, new OccurrenceSchedule(1, "", "", 1,
        Time.Resolution.Type.MONTH, true, OccurrenceSchedule.Source.MODEL));
    plan.getComputation().add(new ServiceCallImpl("klab.core.expression.resolver", "_targetId", "precipitation",
        "expression", org.integratedmodelling.klab.api.lang.ExpressionCode.of("10", "groovy")));
    var quality = observation("precipitation", SemanticType.QUALITY, -1).getObservable();
    var bindings = new ProcessPlan(1, 200, "test:rainfallModel", java.util.List.of(
        new ProcessPlan.Binding("precipitation", quality, ProcessPlan.Effect.CREATED, true, true)), java.util.List.of());
    plan.getData().put(ProcessPlan.DATA_KEY, Utils.Json.asString(bindings));
    var snapshot = Utils.Json.parseObject(Utils.Json.asString(CompiledDataflow.portableOccurrencePlan(plan)), Actuator.class);
    var executor = new CompiledDataflow(runtime, process, scope).restoreOccurrenceExecutor(snapshot);
    assertTrue(executor.run(process.getGeometry(), Scheduler.Event.initialization(), scope));
    assertTrue(snapshot.getChildren().isEmpty());
    verify(runtime, never()).getComputationBuilder(any(), any(), any(), anyMap());
    verify(twin, never()).getStorageManager();
    verify(twin, never()).getScheduler();
    var invalidInput = new ActuatorImpl(); invalidInput.setName("precipitation");
    invalidInput.setObservation(observation("precipitation", SemanticType.QUALITY, 300));
    snapshot.getChildren().add(invalidInput);
    assertThrows(IllegalArgumentException.class, () -> CompiledDataflow.validateSupportedPlan(snapshot));
  }
}
