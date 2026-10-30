package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.util.List;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;

class ObservationViewExportTest {
  @Test
  void noMatchCompletesAsAnEmptyQueryWithoutMutatingTheSharedEmptySentinel() {
    var runtime = mock(RuntimeService.class, CALLS_REAL_METHODS);
    var scope = mock(ServiceContextScope.class);
    var semantics = new org.integratedmodelling.common.knowledge.ConceptImpl();
    semantics.setType(java.util.EnumSet.of(SemanticType.QUALITY));
    var observable = new org.integratedmodelling.common.knowledge.ObservableImpl();
    observable.setSemantics(semantics);
    var query = new ObservationImpl(); query.setId(0);
    query.setObservable(observable); query.setGeometry(Geometry.create("1"));
    var result = runtime.submit(query, scope).join();
    assertTrue(result.isEmpty());
    assertEquals(Observation.QUERY_ID, result.getId());
    assertEquals(Observation.UNASSIGNED_ID, Observation.empty().getId());
    verify(scope, never()).getDigitalTwin();
  }

  static class CapturingRuntime extends RuntimeService {
    Observation exported;
    CapturingRuntime() { super(null, null); }
    @Override protected InputStream exportResolvedAsset(RuntimeAsset asset, String urn,
        KlabAsset.KnowledgeClass type, String media, Parameters<String> parameters, Scope scope) {
      exported = (Observation) asset;
      return InputStream.nullInputStream();
    }
  }

  @Test
  void qualityExportRevalidatesSourceAndRetainsTheConsumerContractWithoutResolution() {
    var runtime = mock(CapturingRuntime.class, CALLS_REAL_METHODS);
    var scope = mock(ServiceContextScope.class, RETURNS_DEEP_STUBS);
    var nativeObservable = mock(Observable.class);
    var requested = mock(Observable.class);
    when(requested.is(SemanticType.QUALITY)).thenReturn(true);
    var source = new ObservationImpl(); source.setId(42);
    source.setObservable(nativeObservable); source.setGeometry(Geometry.create("1"));
    var view = new ObservationImpl(); view.setId(0); view.setObservable(requested);
    view.setGeometry(Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}"));
    view.getMetadata().put(Metadata.IM_QUERY_SOURCE_IDS, List.of(999L));
    view.mergeAnnotations(List.of(Annotation.of("colormap", "palette", "terrain")), 2);
    when(scope.getObservation(view)).thenReturn(source);
    var storage = mock(Storage.class);
    when(scope.getDigitalTwin().getStorageManager().getStorage(source)).thenReturn(storage);
    when(storage.getNativeShardingStrategy())
        .thenReturn(Data.ShardingStrategy.trivial(Storage.Type.DOUBLE));

    assertThrows(IllegalArgumentException.class,
        () -> runtime.exportObservation(view, "image/png", Parameters.create(), scope));
    assertNull(runtime.exported);
    view.getMetadata().put(Metadata.IM_QUERY_SOURCE_IDS, List.of(42L));
    assertNotNull(runtime.exportObservation(view, "image/png", Parameters.create(), scope));
    var exported = runtime.exported;
    assertEquals(0, exported.getId());
    assertSame(requested, exported.getObservable());
    assertSame(view.getGeometry(), exported.getGeometry());
    assertEquals(List.of(42L), exported.getMetadata().get(Metadata.IM_QUERY_SOURCE_IDS));
    assertEquals("terrain", exported.getAnnotations().iterator().next().get("palette"));
    assertEquals(42, source.getId());
    assertSame(nativeObservable, source.getObservable());
    when(scope.getCurrentTransaction()).thenReturn(null);
    assertEquals(Observation.QUERY_ID, runtime.submit(view, scope).join().getId());
    // Even with an active observer, an explicit query must not record perception in a transaction.
    verify(scope.getDigitalTwin(), never()).getKnowledgeGraph();
    verify(scope, never()).getService(org.integratedmodelling.klab.api.services.Resolver.class);
    verify(scope.getDigitalTwin().getStorageManager(), never()).createStorage(any());
  }
}
