package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.InvocationTargetException;
import java.util.EnumSet;
import java.util.List;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.junit.jupiter.api.Test;

class ReferenceParentValidationTest {
  private static ObservationImpl observation(long id, String urn, SemanticType type) {
    var concept = new ConceptImpl(); concept.setUrn(urn); concept.setType(EnumSet.of(type));
    var observable = new ObservableImpl(); observable.setSemantics(concept); observable.setUrn(urn);
    var observation = new ObservationImpl(); observation.setId(id); observation.setObservable(observable);
    observation.setGeometry(Geometry.create("1")); return observation;
  }
  private static final class Fixture {
    final RuntimeService runtime = mock(RuntimeService.class);
    final ContextScope scope = mock(ContextScope.class);
    final KnowledgeGraph graph = mock(KnowledgeGraph.class);
    final Reasoner reasoner = mock(Reasoner.class);
    final ObservationImpl requested = observation(-7, "change in geography:Elevation", SemanticType.PROCESS);
    final ObservationImpl existing = observation(4416, "change in geography:Elevation", SemanticType.PROCESS);
    final DataflowImpl dataflow = new DataflowImpl();
    Fixture() {
      var twin = mock(DigitalTwin.class); when(scope.getDigitalTwin()).thenReturn(twin);
      when(twin.getKnowledgeGraph()).thenReturn(graph);
      when(graph.getAsset(4416, scope, Observation.class)).thenReturn(existing);
      when(scope.getService(Reasoner.class)).thenReturn(reasoner);
      var receipt = mock(Reasoner.Capabilities.class); when(receipt.getKnowledgeRevision()).thenReturn(7L);
      when(reasoner.capabilities(scope)).thenReturn(receipt);
      when(reasoner.semanticDistance(any(), any(), isNull(), eq(7L))).thenReturn(0);
      existing.setParentId(4386);
      var root = new ActuatorImpl(); root.setId(4416); root.setObservation(Observation.forTransport(existing));
      root.setActuatorType(Actuator.Type.REFERENCE); dataflow.setResolvedCoverage(1); dataflow.setComputation(List.of(root));
      focus(4386); existing.setParentId(4386);
    }
    ObservationImpl focus(long id) {
      var focus = observation(id, "earth:Region", SemanticType.SUBJECT);
      when(scope.getContextObservation()).thenReturn(focus); when(scope.getObservation(id)).thenReturn(focus);
      return focus;
    }
    Observation result() throws Throwable {
      var method = RuntimeService.class.getDeclaredMethod("referenceResult", Dataflow.class, Observation.class, ContextScope.class);
      method.setAccessible(true);
      try { return (Observation)method.invoke(runtime, dataflow, requested, scope); }
      catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
  }

  @Test void capturedUnsetParentUsesAccessibleFocusWithoutMutatingTransientRequest() throws Throwable {
    var fixture = new Fixture();
    assertSame(fixture.existing, fixture.result());
    assertEquals(-7, fixture.requested.getId()); assertEquals(-1, fixture.requested.getParentId());
    verify(fixture.scope).getObservation(4386);
  }
  @Test void explicitParentMustMatchBothExistingObservationAndEffectiveFocus() throws Throwable {
    var fixture = new Fixture(); fixture.requested.setParentId(4386);
    assertSame(fixture.existing, fixture.result());
    fixture.requested.setParentId(9999);
    assertThrows(IllegalStateException.class, fixture::result);
    fixture.existing.setParentId(9999);
    assertThrows(IllegalStateException.class, fixture::result);
  }
  @Test void absentFocusMeansRootAndNeverInfersFocusFromAnExistingReference() throws Throwable {
    var fixture = new Fixture(); when(fixture.scope.getContextObservation()).thenReturn(null);
    assertThrows(IllegalStateException.class, fixture::result);
    fixture.existing.setParentId(RuntimeAsset.CONTEXT_ASSET_ID);
    assertSame(fixture.existing, fixture.result());
    fixture.requested.setParentId(4386);
    assertThrows(IllegalStateException.class, fixture::result);
  }
  @Test void missingEmptyOrWrongIdentityFocusCannotAuthorizeAReference() {
    var fixture = new Fixture();
    when(fixture.scope.getObservation(4386)).thenReturn(null);
    assertThrows(IllegalStateException.class, fixture::result);
    when(fixture.scope.getObservation(4386)).thenReturn(Observation.empty());
    assertThrows(IllegalStateException.class, fixture::result);
    when(fixture.scope.getObservation(4386)).thenReturn(observation(9999, "earth:Region", SemanticType.SUBJECT));
    assertThrows(IllegalStateException.class, fixture::result);
  }
  @Test void unassignedAndQueryFocusAreRejectedEvenIfAnObservationIsReturned() {
    for (long id : new long[] {Observation.UNASSIGNED_ID, Observation.QUERY_ID}) {
      var fixture = new Fixture(); fixture.focus(id); fixture.existing.setParentId(id);
      assertThrows(IllegalStateException.class, fixture::result);
    }
  }
  @Test void accessibleTransactionLocalFocusAndExplicitParentRemainValid() throws Throwable {
    var fixture = new Fixture(); fixture.focus(-12); fixture.existing.setParentId(-12);
    assertSame(fixture.existing, fixture.result());
    fixture.requested.setParentId(-12);
    assertSame(fixture.existing, fixture.result());
    verify(fixture.scope, times(2)).getObservation(-12);
  }
  @Test void observerPerspectiveStillRequiresACompatibleReceipt() {
    var fixture = new Fixture();
    ((ObservableImpl)fixture.requested.getObservable()).setObserverSemantics(
        observation(1, "test:RequestedAgent", SemanticType.AGENT).getObservable().asConcept());
    assertThrows(IllegalStateException.class, fixture::result);
  }
  @Test void exactConceptUrnAndCurrentSemanticReceiptGuardsRemainInForce() {
    var fixture = new Fixture();
    fixture.requested.setObservable(observation(-7, "earth:Erosion", SemanticType.PROCESS).getObservable());
    assertThrows(IllegalStateException.class, fixture::result);
    verify(fixture.reasoner, never()).semanticDistance(any(), any(), any(), anyLong());
    fixture.requested.setObservable(fixture.existing.getObservable());
    when(fixture.reasoner.capabilities(fixture.scope)).thenReturn(null);
    assertThrows(IllegalStateException.class, fixture::result);
  }
}
