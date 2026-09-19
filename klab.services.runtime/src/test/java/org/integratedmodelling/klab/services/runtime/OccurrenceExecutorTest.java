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
}
