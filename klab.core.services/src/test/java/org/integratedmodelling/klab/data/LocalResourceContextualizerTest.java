package org.integratedmodelling.klab.data;

import static org.mockito.Mockito.*;

import java.util.Map;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.junit.jupiter.api.Test;

class LocalResourceContextualizerTest {
  @Test void adapterReceivesOriginalOutputScannerWithoutRescanningDependencies() {
    var adapter = mock(Adapter.class);
    var resource = mock(Resource.class);
    when(resource.getUrn()).thenReturn("test:local:resource:data");
    var observation = mock(Observation.class);
    var observable = mock(Observable.class);
    when(observation.getObservable()).thenReturn(observable);
    when(observable.getUrn()).thenReturn("test:Quality");
    var scope = mock(ContextScope.class);
    var scanner = mock(Storage.Scanner.class, RETURNS_DEEP_STUBS);
    when(scanner.shard().getGeometry()).thenReturn(Geometry.UNIVERSAL);
    var contextualizer = new LocalResourceContextualizer(adapter, resource, observation,
        Map.of(Dataflow.SELF_ID, observation, "input", mock(Observation.class)));
    var builder = contextualizer.getData(scanner, null, scope);
    verify(adapter).encode(eq(resource), same(Geometry.UNIVERSAL), isNull(), same(builder),
        same(scanner), same(observation), same(observable), any(), any(), same(scope));
    verifyNoInteractions(scope);
  }
}
