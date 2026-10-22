package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import java.util.List;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ObserverCompiledDataflowTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  private ConceptImpl concept(String urn, SemanticType type) {
    var result = new ConceptImpl(); result.setUrn(urn); result.setType(EnumSet.of(type)); return result;
  }
  private ObservationImpl observation(long id, ObservableImpl observable) {
    var result = new ObservationImpl(); result.setId(id); result.setObservable(observable);
    result.setGeometry(Geometry.create("1")); return result;
  }
  private class Fixture {
    final ObservableImpl requested = new ObservableImpl();
    final ConceptImpl observer = concept("test:Observer", SemanticType.AGENT);
    final ObservationImpl root;
    final ActuatorImpl plan = new ActuatorImpl();
    final Reasoner reasoner = mock(Reasoner.class);
    final ServiceContextScope scope = mock(ServiceContextScope.class);
    final RuntimeService runtime = mock(RuntimeService.class);
    final org.integratedmodelling.klab.api.knowledge.Model source = mock(org.integratedmodelling.klab.api.knowledge.Model.class);
    Fixture() {
      requested.setSemantics(concept("test:Process", SemanticType.PROCESS)); requested.setUrn("test:Process");
      root = observation(-2, requested);
      var agent = new ObservableImpl(); agent.setSemantics(observer); agent.setUrn(observer.getUrn());
      when(scope.getObserver()).thenReturn(observation(20, agent));
      when(scope.getDigitalTwin()).thenReturn(mock(DigitalTwin.class));
      when(scope.getService(Reasoner.class)).thenReturn(reasoner);
      when(reasoner.semanticDistance(any(), any(), isNull(), eq(7L))).thenReturn(0);
      var resources = mock(org.integratedmodelling.klab.api.services.ResourcesService.class);
      when(scope.getService(org.integratedmodelling.klab.api.services.ResourcesService.class)).thenReturn(resources);
      when(resources.declareObservable(nullable(String.class))).thenAnswer(call -> new org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl());
      var namespace = new org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl(); namespace.setUrn("test");
      var declaration = new org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl(); declaration.setUrn("test:model");
      namespace.getStatements().add(declaration);
      when(resources.retrieve("test", org.integratedmodelling.klab.api.lang.kim.KimNamespace.class, scope)).thenReturn(namespace);
      when(reasoner.validateDocument(any(), any())).thenAnswer(call -> {
        var response = org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.forRequest(call.getArgument(0));
        response.setStatus(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.COMPLETE);
        response.setKnowledgeRevision(7); return response;
      });
      when(source.getUrn()).thenReturn("test:model"); when(source.getNamespace()).thenReturn("test");
      when(source.getCoverage()).thenReturn(Geometry.create("1"));
      when(runtime.observerSourceModel("test:model", scope)).thenReturn(source);
      plan.setObservation(Observation.forTransport(root)); plan.setId(-2); plan.setName("process");
      plan.setOperationObservable(requested); plan.setActuatorType(Actuator.Type.OBSERVE);
    }
    void refine() {
      var result = ObservableImpl.withObserverSemantics(requested, observer);
      ((ObservationImpl) plan.getObservation()).setObservable(result);
      when(source.getObservables()).thenReturn(List.of(result));
      plan.getData().put(Actuator.PRODUCING_MODELS_KEY, List.of("test:model"));
      plan.getData().put("im:observer-contributions", List.of(org.integratedmodelling.klab.utilities.Utils.Json.asString(
          new CompiledDataflow.ObserverContribution("test:model", observer.getUrn(), "1", 7))));
    }
    CompiledDataflow compiler() { return new CompiledDataflow(runtime, root, scope); }
  }

  @Test void actualCompileAdoptsVerifiedSpecificResultAndRetainsImplicitOperation() {
    var f = new Fixture(); f.refine();
    assertTrue(f.compiler().compile(f.plan));
    assertSame(f.root, f.plan.getObservation());
    assertEquals(f.observer.getUrn(), f.root.getObservable().getObserverSemantics().getUrn());
    assertNull(f.requested.getObserverSemantics()); assertSame(f.requested, f.plan.getOperationObservable());
    assertTrue(f.root.getMetadata().containsKey("im:observer-contributions"));
  }
  @Test void changedRevisionOrMissingProducingEvidenceCannotRefineRootMeaning() {
    var f = new Fixture(); f.refine();
    when(f.reasoner.semanticDistance(any(), any(), isNull(), eq(7L))).thenReturn(-1);
    assertThrows(IllegalArgumentException.class, () -> f.compiler().compile(f.plan));
    assertNull(f.root.getObservable().getObserverSemantics());
    var noReceipt = new Fixture(); noReceipt.refine(); noReceipt.plan.getData().remove(Actuator.PRODUCING_MODELS_KEY);
    assertThrows(IllegalArgumentException.class, () -> noReceipt.compiler().compile(noReceipt.plan));
  }
  @Test void mixedModelReferenceCoverageDoesNotPretendToFillOutputValues() {
    var f = new Fixture();
    var entries = List.of(
        org.integratedmodelling.klab.utilities.Utils.Json.asString(new CompiledDataflow.ObserverContribution("test:model", f.observer.getUrn(), "part-a", 7)),
        org.integratedmodelling.klab.utilities.Utils.Json.asString(new CompiledDataflow.ObserverContribution(null, null, "part-b", -1)));
    f.plan.getData().put("im:observer-contributions", entries);
    assertThrows(UnsupportedOperationException.class, () -> f.compiler().compile(f.plan));
    assertNull(f.root.getObservable().getObserverSemantics());
    assertFalse(f.root.getMetadata().containsKey("im:observer-contributions"));
  }
  @Test void positiveReferenceCannotBeReboundToProvisionalTarget() {
    var f = new Fixture(); f.plan.setActuatorType(Actuator.Type.REFERENCE);
    f.plan.setId(42); ((ObservationImpl) f.plan.getObservation()).setId(42);
    assertThrows(IllegalArgumentException.class, () -> f.compiler().compile(f.plan));
    assertEquals(-2, f.root.getId());
  }
  @Test void mediatedRefinementRetainsUnitTailRangeAndOriginalOperationDuringRebinding() {
    var f = new Fixture();
    f.requested.setSemantics(concept("test:Quality", SemanticType.QUALITY));
    f.requested.setUrn("test:Quality in m");
    when(f.scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(f.runtime);
    when(f.runtime.getDefaultShardingStrategy(any(), any())).thenReturn(org.integratedmodelling.klab.api.data.Data.ShardingStrategy.neutral());
    var unit = new org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl("m");
    f.requested.setUnit(unit);
    var range = org.integratedmodelling.klab.api.data.mediation.NumericRange.create(0, 10);
    f.requested.setRange(range);
    f.refine();
    assertTrue(f.compiler().compile(f.plan));
    assertEquals("test:Quality observed as test:Observer in m", f.root.getObservable().getUrn());
    assertSame(range, f.root.getObservable().getRange());
    assertNotNull(f.root.getObservable().getUnit()); assertSame(unit, f.root.getObservable().getUnit());
    assertNull(f.requested.getObserverSemantics());
    f.plan.setObservation(Observation.forTransport(f.root));
    assertSame(f.requested, f.plan.getOperationObservable());
  }
  @Test void nestedUnboundRefinementRequiresCurrentEvidenceBeforeAnyAllocation() {
    var parent = new Fixture(); var child = new Fixture(); child.refine();
    ((ObservationImpl) child.plan.getObservation()).setId(-3); child.plan.setId(-3);
    parent.plan.getChildren().add(child.plan);
    when(parent.reasoner.semanticDistance(any(), any(), isNull(), eq(7L))).thenReturn(-1);
    assertThrows(IllegalArgumentException.class, () -> parent.compiler().compile(parent.plan));
    assertNull(parent.root.getObservable().getObserverSemantics());
    assertEquals(-3, child.plan.getId());
  }
  @Test void selfAssertedModelOrObserverAndMalformedPartialSupportCannotAuthorizeRefinement() {
    var absent = new Fixture(); absent.refine();
    when(absent.runtime.observerSourceModel("test:model", absent.scope)).thenReturn(null);
    assertThrows(IllegalArgumentException.class, () -> absent.compiler().compile(absent.plan));
    var wrong = new Fixture(); wrong.refine();
    when(wrong.source.getObservables()).thenReturn(List.of(ObservableImpl.withObserverSemantics(
        wrong.requested, concept("test:UnrelatedObserver", SemanticType.AGENT))));
    assertThrows(IllegalArgumentException.class, () -> wrong.compiler().compile(wrong.plan));
    var partial = new Fixture(); partial.refine();
    when(partial.source.getCoverage()).thenReturn(Geometry.create("T0(1){tstart=0,tend=5,ttype=PHYSICAL}"));
    ((ObservationImpl) partial.plan.getObservation()).setGeometry(Geometry.create("T0(1){tstart=0,tend=10,ttype=PHYSICAL}"));
    partial.root.setGeometry(partial.plan.getObservation().getGeometry());
    assertThrows(IllegalArgumentException.class, () -> partial.compiler().compile(partial.plan));
  }
  @Test void nullDefaultObserverStillPreservesVerifiedActualSpecificOutput() {
    var f = new Fixture(); f.refine(); when(f.scope.getObserver()).thenReturn(null);
    assertTrue(f.compiler().compile(f.plan));
    assertEquals(f.observer.getUrn(), f.root.getObservable().getObserverSemantics().getUrn());
    assertNull(f.plan.getOperationObservable().getObserverSemantics());
  }
  @Test void portableReplayRetainsImplicitOperationSeparateFromSpecificResult() {
    var f = new Fixture(); f.refine();
    ((ObservationImpl) f.plan.getObservation()).setId(42); f.plan.setId(42);
    var snapshot = CompiledDataflow.portableOccurrencePlan(f.plan);
    assertNotSame(f.plan.getObservation(), snapshot.getObservation());
    assertSame(f.requested, snapshot.getOperationObservable());
    var replay = org.integratedmodelling.klab.utilities.Utils.Json.parseObject(
        org.integratedmodelling.klab.utilities.Utils.Json.asString(snapshot), Actuator.class);
    assertEquals(42, replay.getObservation().getId());
    assertEquals(f.observer.getUrn(), replay.getObservation().getObservable().getObserverSemantics().getUrn());
    assertEquals(f.requested.getUrn(), replay.getOperationObservable().getUrn());
    assertNull(replay.getOperationObservable().getObserverSemantics());
    ((ActuatorImpl) replay).setObservation(Observation.forTransport(replay.getObservation()));
    assertNull(replay.getOperationObservable().getObserverSemantics());
  }
}
