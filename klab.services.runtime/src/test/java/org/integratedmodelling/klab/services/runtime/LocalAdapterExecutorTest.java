package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.junit.jupiter.api.Test;

class LocalAdapterExecutorTest {
  @Test void nonQualityAdapterUsesObservationGeometryWithEmptyScannerMap() {
    var adapter = mock(Adapter.class);
    var resource = mock(Resource.class);
    when(resource.getUrn()).thenReturn("test:local:resource:data");
    var observation = new ObservationImpl();
    observation.setGeometry(Geometry.UNIVERSAL);
    observation.setObservable(mock(Observable.class));
    var scope = mock(ContextScope.class);
    when(adapter.hasContextualizer()).thenReturn(true);
    when(adapter.contextualize(resource, Geometry.UNIVERSAL, scope)).thenReturn(resource);
    var executor = new LocalAdapterExecutor(
        new CompiledDataflow.CallDescriptors(null, null, resource, adapter), observation, Map.of(), scope, new ShardExecution(() -> 1));
    assertTrue(executor.run(null, Map.of(), scope, new ContextualizationScopeImpl(observation, null)));
    verify(adapter).contextualize(resource, Geometry.UNIVERSAL, scope);
    verify(adapter).encode(eq(resource), same(Geometry.UNIVERSAL), isNull(), any(), isNull(),
        same(observation), same(observation.getObservable()), any(), any(), same(scope));
  }
  @Test void adapterContextualizationAndEncodingReceiveFragmentWithoutChangingProducer() {
    var adapter = mock(Adapter.class); var resource = mock(Resource.class);
    when(resource.getUrn()).thenReturn("test:local:resource:data");
    var observation = new ObservationImpl();
    var full = Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=10000}");
    var fragment = Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=5000}");
    observation.setGeometry(full); observation.setObservable(mock(Observable.class));
    var scope = mock(ContextScope.class);
    when(adapter.hasContextualizer()).thenReturn(true);
    when(adapter.contextualize(resource, fragment, scope)).thenReturn(resource);
    var executor = new LocalAdapterExecutor(new CompiledDataflow.CallDescriptors(null, null, resource, adapter),
        observation, Map.of(), scope, new ShardExecution(() -> 1));
    assertTrue(executor.run(null, Map.of(), scope, new ContextualizationScopeImpl(observation, null, fragment)));
    verify(adapter).contextualize(resource, fragment, scope);
    verify(adapter).encode(eq(resource), same(fragment), isNull(), any(), isNull(),
        same(observation), same(observation.getObservable()), any(), any(), same(scope));
    assertSame(full, observation.getGeometry());
  }

  @Test void outputScannerGeometryStillWinsForAdapterContextualizationAndEncoding() {
    var adapter = mock(Adapter.class); var resource = mock(Resource.class);
    when(resource.getUrn()).thenReturn("test:local:resource:data");
    var observation = new ObservationImpl(); observation.setGeometry(Geometry.UNIVERSAL);
    observation.setObservable(mock(Observable.class)); var scope = mock(ContextScope.class);
    var fragment = Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=5000}");
    var shard = Geometry.create("T0(1){ttype=PHYSICAL,tstart=2000,tend=3000}");
    var scanner = mock(org.integratedmodelling.klab.api.data.Storage.Scanner.class, RETURNS_DEEP_STUBS);
    when(scanner.shard().getGeometry()).thenReturn(shard);
    when(adapter.hasContextualizer()).thenReturn(true);
    when(adapter.contextualize(resource, shard, scope)).thenReturn(resource);
    var executor = new LocalAdapterExecutor(new CompiledDataflow.CallDescriptors(null, null, resource, adapter),
        observation, Map.of(), scope, new ShardExecution(() -> 1));
    assertTrue(executor.run(null, Map.of(org.integratedmodelling.klab.api.services.runtime.Dataflow.SELF_ID, scanner),
        scope, new ContextualizationScopeImpl(observation, null, fragment)));
    verify(adapter).contextualize(resource, shard, scope);
    verify(adapter).encode(eq(resource), same(shard), isNull(), any(), same(scanner),
        same(observation), same(observation.getObservable()), any(), any(), same(scope));
  }
}
