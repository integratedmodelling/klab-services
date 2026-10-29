package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;

class ContextualizerExecutionGeometryTest {
  private static final ThreadLocal<Geometry> received = new ThreadLocal<>();
  public static void instantiate(Geometry geometry) { received.set(geometry); }

  private Geometry invoke(Geometry invocation, Geometry selfShard) throws Exception {
    ServiceConfiguration.injectInstantiators();
    var observation = ConnectionExecutionTest.observation(-10, SemanticType.SUBJECT, true, false);
    ((org.integratedmodelling.common.knowledge.ObservableImpl) observation.getObservable())
        .setDescriptionType(org.integratedmodelling.klab.api.knowledge.Contextualization.INSTANTIATION);
    var declared = Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=10000}");
    observation.setGeometry(declared);
    var scope = mock(ServiceContextScope.class);
    var registry = mock(ComponentRegistry.class);
    var descriptor = new Extensions.FunctionDescriptor(); descriptor.staticMethod = true;
    descriptor.serviceInfo = mock(ServiceInfo.class);
    var implementation = new ComponentRegistry.ServiceImplementation();
    implementation.method = getClass().getMethod("instantiate", Geometry.class);
    when(registry.implementation(descriptor)).thenReturn(implementation);
    var executor = new ContextualizerExecutor(registry,
        new CompiledDataflow.CallDescriptors(null, descriptor, null, null), observation,
        Map.of(), new ServiceCallImpl(), scope, new ShardExecution(() -> 1));
    Map<String, Storage.Scanner> scanners = Map.of();
    if (selfShard != null) {
      var scanner = mock(Storage.Scanner.class, RETURNS_DEEP_STUBS);
      when(scanner.shard().getGeometry()).thenReturn(selfShard);
      scanners = Map.of(Dataflow.SELF_ID, scanner);
    }
    received.remove();
    assertTrue(executor.run(null, scanners, scope, new ContextualizationScopeImpl(observation, null, invocation)),
        () -> String.valueOf(executor.getCause()));
    assertSame(declared, observation.getGeometry(), "Invocation must not rewrite the producer identity");
    var result = received.get(); received.remove(); return result;
  }

  @Test void realTypedInstantiatorReceivesExactFragmentInsteadOfProducerExtent() throws Exception {
    var fragment = Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=5000}");
    assertSame(fragment, invoke(fragment, null));
  }

  @Test void legacyFullSupportInvocationStillUsesDeclaredProducerExtent() throws Exception {
    assertEquals(Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=10000}").encode(),
        invoke(null, null).encode());
  }

  @Test void selfScannerShardStillTakesPrecedenceOverInvocationSupport() throws Exception {
    var fragment = Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=5000}");
    var shard = Geometry.create("T0(1){ttype=PHYSICAL,tstart=2000,tend=3000}");
    assertSame(shard, invoke(fragment, shard));
  }
  private static final ThreadLocal<Integer> batchSize = new ThreadLocal<>();
  public static void produce(org.integratedmodelling.klab.api.data.Data.Builder builder, Geometry geometry) {
    for (int i = 0; i < batchSize.get(); i++) builder.object("member" + i,
        org.integratedmodelling.klab.api.knowledge.Observable.promote(
            ConnectionExecutionTest.observation(-1, SemanticType.SUBJECT, false, false).getObservable().getSemantics()),
        geometry, null);
  }

  @Test void twoBatchesAndAnEmptyBatchKeepCumulativeCountOwnedByTransactionalChildLinks() throws Exception {
    ServiceConfiguration.injectInstantiators();
    var producer = ConnectionExecutionTest.observation(-10, SemanticType.SUBJECT, true, false);
    ((org.integratedmodelling.common.knowledge.ObservableImpl) producer.getObservable())
        .setDescriptionType(org.integratedmodelling.klab.api.knowledge.Contextualization.INSTANTIATION);
    var geometry = Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=10000}");
    producer.setGeometry(geometry);
    var scope = mock(ServiceContextScope.class); var twin = mock(org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.class);
    var graph = mock(org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j.class);
    var field = org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.class.getDeclaredField("knowledgeGraph");
    field.setAccessible(true); field.set(twin, graph); when(twin.getKnowledgeGraph()).thenReturn(graph);
    when(scope.getDigitalTwin()).thenReturn(twin);
    var transaction = twin.new TransactionImpl(org.integratedmodelling.klab.api.provenance.Activity.of(
        org.integratedmodelling.klab.api.provenance.Activity.Type.SUBMISSION), scope,
        org.integratedmodelling.klab.api.data.RuntimeAsset.PROVENANCE_ASSET, producer);
    when(scope.getCurrentTransaction()).thenReturn(transaction);
    var ids = new java.util.concurrent.atomic.AtomicLong(-100);
    when(scope.observation(any(org.integratedmodelling.klab.api.knowledge.Observable.class))).thenAnswer(call ->
        new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationBuilderImpl((org.integratedmodelling.klab.api.knowledge.Observable) call.getArgument(0), scope) {
          public org.integratedmodelling.klab.api.knowledge.observation.Observation register() {
            var child = (org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl) build();
            child.setId(ids.decrementAndGet()); return child;
          }
          public java.util.concurrent.CompletableFuture<org.integratedmodelling.klab.api.knowledge.observation.Observation> submit() {
            return java.util.concurrent.CompletableFuture.completedFuture(register());
          }
        });
    var registry = mock(ComponentRegistry.class); var descriptor = new Extensions.FunctionDescriptor();
    descriptor.staticMethod = true; descriptor.serviceInfo = mock(ServiceInfo.class);
    var implementation = new ComponentRegistry.ServiceImplementation();
    implementation.method = getClass().getMethod("produce", org.integratedmodelling.klab.api.data.Data.Builder.class, Geometry.class);
    when(registry.implementation(descriptor)).thenReturn(implementation);
    var executor = new ContextualizerExecutor(registry, new CompiledDataflow.CallDescriptors(null, descriptor, null, null),
        producer, Map.of(), new ServiceCallImpl(), scope, new ShardExecution(() -> 1));
    int total = 0;
    try {
      for (int batch : new int[] {2, 1, 0}) {
        batchSize.set(batch); var context = new ContextualizationScopeImpl(producer, null, geometry);
        assertTrue(executor.run(null, Map.of(), scope, context), () -> String.valueOf(executor.getCause()));
        assertEquals(total, producer.getChildrenCount(), "Contextualizer must not preset or reset member counts");
        assertEquals(batch, context.getOutcomes().size());
        for (var child : context.getOutcomes()) transaction.link(producer, child,
            org.integratedmodelling.klab.api.digitaltwin.GraphModel.Relationship.HAS_CHILD);
        total += batch; assertEquals(total, producer.getChildrenCount());
      }
      assertEquals(3, producer.getChildrenCount());
      transaction.fail(new java.util.concurrent.CancellationException("rollback all batches"));
      assertEquals(0, producer.getChildrenCount());
    } finally { batchSize.remove(); }
  }
}
