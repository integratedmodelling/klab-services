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
        new CompiledDataflow.CallDescriptors(null, null, resource, adapter), observation, Map.of(), scope);
    assertTrue(executor.run(null, Map.of(), scope, new ContextualizationScopeImpl(observation, null)));
    verify(adapter).contextualize(resource, Geometry.UNIVERSAL, scope);
    verify(adapter).encode(eq(resource), same(Geometry.UNIVERSAL), isNull(), any(), isNull(),
        same(observation), same(observation.getObservable()), any(), any(), same(scope));
  }
}
