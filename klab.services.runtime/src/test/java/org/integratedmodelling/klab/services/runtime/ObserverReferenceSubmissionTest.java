package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import java.util.List;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ObserverReferenceSubmissionTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  private org.integratedmodelling.klab.api.services.Reasoner semanticReceipt(SubmissionCancellationTest.Fixture fixture) {
    var reasoner = fixture.reasoner;
    var capabilities = mock(org.integratedmodelling.klab.api.services.Reasoner.Capabilities.class);
    when(capabilities.getKnowledgeRevision()).thenReturn(7L);
    when(reasoner.capabilities(any())).thenReturn(capabilities);
    when(reasoner.semanticDistance(any(), any(), isNull(), eq(7L))).thenReturn(0);
    var status = mock(org.integratedmodelling.klab.api.services.KlabService.ServiceStatus.class);
    when(status.isOperational()).thenReturn(true); when(reasoner.status()).thenReturn(status);
    fixture.scope.addService(reasoner); return reasoner;
  }

  private ObservationImpl process(long id) {
    var concept = new ConceptImpl(); concept.setUrn("test:Process");
    concept.setType(EnumSet.of(SemanticType.PROCESS));
    var observable = new ObservableImpl(); observable.setSemantics(concept); observable.setUrn(concept.getUrn());
    var observation = new ObservationImpl(); observation.setId(id); observation.setUrn("test:process:"+id);
    observation.setObservable(observable); observation.setGeometry(Geometry.create("1"));
    if (id > 0) observation.setParentId(org.integratedmodelling.klab.api.data.RuntimeAsset.CONTEXT_ASSET_ID);
    return observation;
  }

  @Test void unstagedCapturedRequestReusesFocusedPersistedReferenceWithoutCreatingATarget() {
    var fixture = new SubmissionCancellationTest.Fixture();
    semanticReceipt(fixture);
    var requested = process(-7); var existing = process(4416); existing.setParentId(4386);
    var focus = process(4386);
    ((ConceptImpl)focus.getObservable().asConcept()).setType(EnumSet.of(SemanticType.SUBJECT));
    ((ConceptImpl)focus.getObservable().asConcept()).setUrn("earth:Region");
    when(fixture.twin.getKnowledgeGraph().getAsset(eq(4386L), any(), eq(Observation.class))).thenReturn(focus);
    when(fixture.twin.getKnowledgeGraph().getAsset(eq(4416L), any(), eq(Observation.class))).thenReturn(existing);
    var reference = new ActuatorImpl(); reference.setId(4416); reference.setObservation(Observation.forTransport(existing));
    reference.setActuatorType(Actuator.Type.REFERENCE);
    var dataflow = new DataflowImpl(); dataflow.setResolvedCoverage(1); dataflow.setComputation(List.of(reference));
    var pending = fixture.runtime.submit(requested, fixture.scope.within(focus));
    fixture.resolution.complete(dataflow);
    var result = pending.join();
    assertSame(existing, result, () -> result.getNotifications().toString());
    assertEquals(-7, requested.getId()); assertEquals(-1, requested.getParentId());
    verify(fixture.rootTransaction, never()).setTarget(any());
    verify(fixture.scheduler, never()).submit(any(), any());
    verify(fixture.rootTransaction, never()).link(any(), eq(requested), any(), any(Object[].class));
  }

  @Test void referenceTerminalPreservesExistingIdentityMetadataAndSkipsCreationAndExecution() {
    var fixture = new SubmissionCancellationTest.Fixture();
    var requested = process(-2); var existing = process(42);
    existing.getMetadata().put("im:commit-id", 123L);
    var before = new java.util.HashMap<>(existing.getMetadata());
    when(fixture.twin.getKnowledgeGraph().getAsset(eq(42L), any(), eq(Observation.class))).thenReturn(existing);
    semanticReceipt(fixture);
    var reference = new ActuatorImpl(); reference.setId(42); reference.setObservation(Observation.forTransport(existing));
    reference.setActuatorType(Actuator.Type.REFERENCE); reference.setCoverage(existing.getGeometry());
    var dataflow = new DataflowImpl(); dataflow.setResolvedCoverage(1); dataflow.setComputation(List.of(reference));
    var pending = fixture.runtime.submit(requested, fixture.scope);
    fixture.resolution.complete(dataflow);
    assertSame(existing, pending.join());
    assertEquals(42, existing.getId()); assertEquals(before, existing.getMetadata());
    verify(fixture.scheduler, never()).submit(any(), any());
    verify(fixture.rootTransaction, never()).registerExecutors();
    verify(fixture.rootTransaction, never()).setTarget(any());
    verify(fixture.rootTransaction, never()).fail(any());
    verify(fixture.resolutionTransaction).commit(); verify(fixture.rootTransaction).commit();
    verify(fixture.rootTransaction, never()).link(any(), eq(requested), any(), any(Object[].class));
  }

  @Test void transactionLocalFocusIsResolvedThroughItsOwningTransaction() {
    var fixture = new SubmissionCancellationTest.Fixture(); semanticReceipt(fixture);
    var focus = process(-12);
    ((ConceptImpl)focus.getObservable().asConcept()).setType(EnumSet.of(SemanticType.SUBJECT));
    var existing = process(4416); existing.setParentId(-12);
    when(fixture.rootTransaction.assets()).thenReturn(List.of(focus));
    when(fixture.resolutionTransaction.assets()).thenReturn(List.of(focus));
    when(fixture.twin.getKnowledgeGraph().getAsset(eq(4416L), any(), eq(Observation.class))).thenReturn(existing);
    var reference = new ActuatorImpl(); reference.setId(4416); reference.setObservation(Observation.forTransport(existing));
    reference.setActuatorType(Actuator.Type.REFERENCE);
    var dataflow = new DataflowImpl(); dataflow.setResolvedCoverage(1); dataflow.setComputation(List.of(reference));
    var pending = fixture.runtime.submit(process(-7), fixture.scope.within(focus));
    fixture.resolution.complete(dataflow);
    assertSame(existing, pending.join());
    verify(fixture.twin.getKnowledgeGraph(), never()).getAsset(eq(-12L), any(), eq(Observation.class));
    verify(fixture.scheduler, never()).submit(any(), any());
  }

  @Test void inaccessibleReferenceFailsClosedWithoutCompilingTransportCopy() {
    var fixture = new SubmissionCancellationTest.Fixture(); var existing = process(42);
    var reference = new ActuatorImpl(); reference.setId(42); reference.setObservation(existing);
    reference.setActuatorType(Actuator.Type.REFERENCE);
    var dataflow = new DataflowImpl(); dataflow.setResolvedCoverage(1); dataflow.setComputation(List.of(reference));
    var pending = fixture.runtime.submit(process(-2), fixture.scope);
    fixture.resolution.complete(dataflow);
    assertTrue(pending.join().isEmpty());
    verify(fixture.scheduler, never()).submit(any(), any());
    verify(fixture.rootTransaction, never()).commit();
  }
  private org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.TransactionImpl receiptTransaction(
      org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.TransactionImpl original) {
    var transaction = spy(original);
    doReturn(0L).when(transaction).commit();
    doAnswer(call -> receiptTransaction((org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.TransactionImpl)
        call.callRealMethod())).when(transaction).getChild(any(), any(), any(Object[].class));
    return transaction;
  }

  @Test void realSharedTransactionGraphRetainsOnlyReceiptAndKeepsNestedParentOpen() {
    var fixture = new SubmissionCancellationTest.Fixture();
    semanticReceipt(fixture); // Derived scopes snapshot the available service catalog.
    var outer = mock(org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.class);
    var actualRoot = new java.util.concurrent.atomic.AtomicReference<org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.TransactionImpl>();
    when(fixture.twin.transaction(any(), any(), any(Object[].class))).thenAnswer(call -> {
      var transaction = receiptTransaction(outer.new TransactionImpl(call.getArgument(0), call.getArgument(1),
          (Object[]) call.getRawArguments()[2]));
      actualRoot.set(transaction); return transaction;
    });
    var parent = fixture.scope.executing(org.integratedmodelling.klab.api.provenance.Activity.of(
        org.integratedmodelling.klab.api.provenance.Activity.Type.RESOLUTION, "parent resolution"));
    var source = process(42); var requested = process(-2);
    source.getMetadata().put("old-commit", 123L);
    var metadata = new java.util.HashMap<>(source.getMetadata());
    when(fixture.twin.getKnowledgeGraph().getAsset(eq(42L), any(), eq(Observation.class))).thenReturn(source);
    var reference = new ActuatorImpl(); reference.setId(42); reference.setObservation(Observation.forTransport(source));
    reference.setActuatorType(Actuator.Type.REFERENCE); reference.setCoverage(source.getGeometry());
    var dataflow = new DataflowImpl(); dataflow.setResolvedCoverage(1); dataflow.setComputation(List.of(reference));
    var pending = fixture.runtime.submit(requested, parent); fixture.resolution.complete(dataflow);
    assertSame(source, pending.join());
    assertFalse(actualRoot.get().isFailed());
    assertTrue(actualRoot.get().assets().stream().noneMatch(Observation.class::isInstance));
    assertEquals(metadata, source.getMetadata()); assertEquals(42, source.getId());
    assertDoesNotThrow(() -> actualRoot.get().add(org.integratedmodelling.klab.api.provenance.Activity.of(
        org.integratedmodelling.klab.api.provenance.Activity.Type.RESOLUTION, "sibling work")));
    verify(fixture.scheduler, never()).submit(any(), any());
  }

  @Test void terminalRequiresCurrentFullWrapperCompatibilityReceipt() {
    for (boolean missingReceipt : List.of(false, true)) {
      var fixture = new SubmissionCancellationTest.Fixture(); var source = process(42);
      when(fixture.twin.getKnowledgeGraph().getAsset(eq(42L), any(), eq(Observation.class))).thenReturn(source);
      var reasoner = semanticReceipt(fixture);
      if (missingReceipt) when(reasoner.capabilities(any())).thenReturn(null);
      else when(reasoner.semanticDistance(any(), any(), isNull(), eq(7L))).thenReturn(-1);
      var reference = new ActuatorImpl(); reference.setId(42); reference.setObservation(source);
      reference.setActuatorType(Actuator.Type.REFERENCE);
      var dataflow = new DataflowImpl(); dataflow.setResolvedCoverage(1); dataflow.setComputation(List.of(reference));
      var pending = fixture.runtime.submit(process(-2), fixture.scope); fixture.resolution.complete(dataflow);
      assertTrue(pending.join().isEmpty());
      verify(fixture.scheduler, never()).submit(any(), any());
      verify(fixture.rootTransaction, never()).commit();
    }
  }
  @Test void claimedCompleteReferenceCannotHidePartialActualProcessSupport() {
    var fixture = new SubmissionCancellationTest.Fixture(); var source = process(42); var requested = process(-2);
    source.setGeometry(Geometry.create("T0(1){tstart=0,tend=5,ttype=PHYSICAL}"));
    requested.setGeometry(Geometry.create("T0(1){tstart=0,tend=10,ttype=PHYSICAL}"));
    when(fixture.twin.getKnowledgeGraph().getAsset(eq(42L), any(), eq(Observation.class))).thenReturn(source);
    semanticReceipt(fixture);
    var reference = new ActuatorImpl(); reference.setId(42); reference.setObservation(source);
    reference.setActuatorType(Actuator.Type.REFERENCE);
    var dataflow = new DataflowImpl(); dataflow.setResolvedCoverage(1); dataflow.setComputation(List.of(reference));
    var pending = fixture.runtime.submit(requested, fixture.scope); fixture.resolution.complete(dataflow);
    assertTrue(pending.join().isEmpty());
    verify(fixture.scheduler, never()).submit(any(), any());
    verify(fixture.rootTransaction, never()).commit();
  }
}
