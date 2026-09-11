package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;

class MemberCharacterizerExecutorTest {
  public static class Characterizer {
    int calls; boolean success = true; ContextScope scope; Observation member; Observable observable;
    public boolean characterize(Observable observable, Observation member, ContextScope scope) {
      calls++; this.scope = scope; this.member = member; this.observable = observable; return success;
    }
    public Observation invalid(Observable observable, ContextScope scope) { return null; }
  }
  @Test void typedExecutionIsOnceOnlyAndLinksBeforeChildCommit() throws Exception {
    var f = new Fixture();
    assertTrue(f.plan.executeRoot(Geometry.UNIVERSAL, f.event, f.scope));
    assertTrue(f.plan.executeRoot(Geometry.UNIVERSAL, f.event, f.scope));
    assertEquals(1, f.characterizer.calls);
    assertSame(f.member, f.characterizer.member);
    assertSame(f.execution, f.characterizer.scope);
    assertSame(f.observable, f.characterizer.observable);
    var order = inOrder(f.tx, f.execution);
    order.verify(f.tx).link(any(Activity.class), eq(f.member), eq(GraphModel.Relationship.CHARACTERIZED));
    order.verify(f.execution).commit();
    verify(f.tx, never()).add(any(RuntimeAsset.class));
  }
  @Test void failedExecutionDoesNotProduceCharacterizedEffectOrCommit() throws Exception {
    var f = new Fixture(); f.characterizer.success = false;
    assertFalse(f.plan.executeRoot(Geometry.UNIVERSAL, f.event, f.scope));
    assertFalse(f.plan.executeRoot(Geometry.UNIVERSAL, f.event, f.scope));
    assertEquals(1, f.characterizer.calls);
    verify(f.execution).fail(any(Throwable.class));
    verify(f.execution, never()).commit();
    verify(f.tx, never()).link(any(), any(), eq(GraphModel.Relationship.CHARACTERIZED));
  }
  @Test void observationReturningFunctionsAreRejected() throws Exception {
    assertFalse(MemberCharacterizerExecutor.supports(Characterizer.class.getMethod("invalid", Observable.class, ContextScope.class)));
  }
  static class Fixture {
    final ServiceContextScope scope = mock(ServiceContextScope.class);
    final ServiceContextScope execution = mock(ServiceContextScope.class);
    final DigitalTwin.Transaction tx = mock(DigitalTwin.Transaction.class);
    final Scheduler.Event event = mock(Scheduler.Event.class);
    final ObservationImpl member = new ObservationImpl();
    final Observable observable = mock(Observable.class);
    final Characterizer characterizer = new Characterizer();
    final CompiledDataflow plan;
    Fixture() throws Exception {
      member.setId(42); member.setGeometry(Geometry.UNIVERSAL); member.setObservable(observable);
      when(observable.getSemantics()).thenReturn(mock(Concept.class));
      when(observable.getContextualization()).thenReturn(Contextualization.CHARACTERIZATION);
      when(observable.getUrn()).thenReturn("test:Concrete of test:Region");
      when(scope.getCurrentTransaction()).thenReturn(tx); when(tx.getId()).thenReturn("root");
      when(scope.getObservation(42)).thenReturn(member); when(scope.within(member)).thenReturn(scope);
      when(scope.executing(any(Activity.class), any(Object[].class))).thenReturn(execution);
      when(execution.getCurrentTransaction()).thenReturn(tx);
      var runtime = mock(RuntimeService.class); var registry = mock(ComponentRegistry.class);
      when(runtime.getComponentRegistry()).thenReturn(registry);
      when(scope.getDigitalTwin()).thenReturn(mock(DigitalTwin.class));
      var node = new ActuatorImpl(); node.setActuatorType(Actuator.Type.UPDATE);
      node.setEffect(Actuator.Effect.SEMANTIC_UPDATE); node.setContextualization(Contextualization.CHARACTERIZATION);
      node.setOperationObservable(observable);
      var binding = new ActuatorImpl.TargetBindingImpl(); binding.setKind(Actuator.TargetBinding.Kind.OBSERVATION);
      binding.setTarget(member); node.getTargetBindings().add(binding);
      var call = mock(ServiceCall.class); when(call.getUrn()).thenReturn("test.characterize"); node.getComputation().add(call);
      var descriptor = new Extensions.FunctionDescriptor();
      var implementation = new ComponentRegistry.ServiceImplementation();
      implementation.method = Characterizer.class.getMethod("characterize", Observable.class, Observation.class, ContextScope.class);
      implementation.mainClassInstance = characterizer;
      when(registry.getFunctionDescriptor(call)).thenReturn(List.of(descriptor));
      when(registry.implementation(descriptor)).thenReturn(implementation);
      plan = new CompiledDataflow(runtime, member, scope);
      assertTrue(plan.compile(node));
    }
  }
}
