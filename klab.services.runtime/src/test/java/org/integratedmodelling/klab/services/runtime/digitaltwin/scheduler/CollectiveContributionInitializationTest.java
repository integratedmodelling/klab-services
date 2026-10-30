package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;

class CollectiveContributionInitializationTest {
  @Test void distinctFragmentsExecuteNormallyOnceEachAndCancellationRollsBackTimestamps() throws Exception {
    ServiceConfiguration.injectInstantiators();
    var concept = new ConceptImpl(); concept.setUrn("each test:Tree");
    concept.getType().add(SemanticType.SUBJECT); concept.setCollective(true);
    var producer = new ObservationImpl(); producer.setId(42);
    producer.setObservable(ObservableImpl.promote(concept, null));
    producer.setGeometry(Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=10000}"));
    var scope = mock(ServiceContextScope.class);
    var twin = mock(DigitalTwinImpl.class);
    var graph = mock(KnowledgeGraphNeo4j.class);
    when(twin.getKnowledgeGraph()).thenReturn(graph); when(scope.getDigitalTwin()).thenReturn(twin);
    var field = DigitalTwinImpl.class.getDeclaredField("knowledgeGraph"); field.setAccessible(true); field.set(twin, graph);
    var transaction = twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION), scope,
        RuntimeAsset.PROVENANCE_ASSET, producer);
    when(scope.getCurrentTransaction()).thenReturn(transaction);
    var supports = new ArrayList<String>();
    var left = Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=5000}");
    var right = Geometry.create("T0(1){ttype=PHYSICAL,tstart=5000,tend=10000}");
    try (var scheduler = new SchedulerImpl(scope, twin)) {
      scheduler.registerExecutor(producer, (geometry, event, execution) -> {
        assertSame(scope, execution); supports.add(Geometry.forTransport(geometry).encode()); return true;
      });
      assertThrows(IllegalArgumentException.class, () -> scheduler.executeCollectiveContribution(producer, Geometry.UNIVERSAL, scope));
      assertTrue(scheduler.executeCollectiveContribution(producer, left, scope));
      assertTrue(scheduler.executeCollectiveContribution(producer, right, scope));
      assertTrue(scheduler.executeCollectiveContribution(producer, left, scope));
      assertEquals(List.of(Geometry.forTransport(left).encode(), Geometry.forTransport(right).encode()), supports);
      assertEquals(2, producer.getEventTimestamps().size(), "Normal scheduler wrapper records each executed fragment");
      when(scope.isInterrupted()).thenReturn(true);
      assertFalse(scheduler.executeCollectiveContribution(producer, right, scope), "Cancellation cannot reuse successful receipt");
      transaction.fail(new java.util.concurrent.CancellationException("cancelled"));
      assertTrue(producer.getEventTimestamps().isEmpty(), "Root rollback removes both own timestamp contributions");
    }
  }

  @Test void fragmentReceiptsAreSeparateFromOrdinaryInitializationAndReleaseOnRollback() {
    var runs = new InitializationRuns();
    var transaction = mock(org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.Transaction.class);
    var rolledBack = new ArrayList<Runnable>();
    doAnswer(call -> { rolledBack.add(call.getArgument(0)); return null; }).when(transaction).afterRollback(any());
    assertTrue(runs.executeContribution(transaction, 42, "left", () -> true));
    assertTrue(runs.executeContribution(transaction, 42, "right", () -> true));
    assertFalse(runs.execute(transaction, 42, () -> false), "Fragment receipt must not pretend full-observation INIT ran");
    assertTrue(runs.executeContribution(transaction, 42, "left", () -> { fail("duplicate fragment"); return false; }));
    rolledBack.forEach(Runnable::run);
    assertFalse(runs.executeContribution(transaction, 42, "left", () -> false));
  }
}
