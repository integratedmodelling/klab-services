package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageFinalizationTest {
  @Test
  void repeatedSameIdFinalizationPreservesAllocatedCellData(@TempDir Path directory) {
    ServiceConfiguration.injectInstantiators();
    var scope = mock(ServiceContextScope.class, RETURNS_DEEP_STUBS);
    when(scope.getId()).thenReturn("test.context");
    when(scope.getConfiguration()).thenReturn(DigitalTwin.Configuration.builder()
        .id("test.context").persistence(Persistence.EXPLICIT_ACTION).build());
    var manager = spy(new StorageManagerImpl(scope, directory.resolve("scratch").toFile(),
        directory.resolve("persistent").toFile()));
    // Keep real storage/registry/cell data without Windows mapped-file deletion timing.
    doAnswer(call -> (org.ojalgo.array.BufferArray) org.ojalgo.array.BufferArray.R064
        .make(call.getArgument(0, Long.class))).when(manager).getDoubleBuffer(anyLong());
    try {
      var concept = new ConceptImpl();
      concept.setUrn("test:quality");
      concept.setName("quality");
      concept.getType().add(SemanticType.QUALITY);
      var observation = new ObservationImpl();
      observation.setId(42);
      observation.setUrn("test:grid");
      observation.setObservable(ObservableImpl.promote(concept, null));
      observation.setGeometry(Geometry.create(ConformantScanTest.grid(2, 2, 0, 2, 0, 2)));
      var strategy = new Data.ShardingStrategy(Data.FillCurve.D2_XY, 1, 0, 0, Storage.Type.DOUBLE);
      var data = new ObservationImpl.ContextualizationDataImpl();
      data.setNativeShardingStrategy(strategy);
      observation.setContextualizationData(data);
      var storage = manager.createStorage(observation);
      for (var scanner : storage.scan(Scheduler.Event.initialization(), strategy, Storage.DoubleScanner.class, false)) {
        while (scanner.hasNext()) scanner.add(scanner.position() + 0.25);
        storage.finalizeRun(scanner);
      }
      assertTrue(manager.hasAllocatedStorage(42));
      assertTrue(manager.finalizeStorage(42, 42));
      assertTrue(manager.finalizeStorage(42, 42));
      assertSame(storage, manager.getStorage(observation));
      long count = 0;
      for (var scanner : storage.scan(Scheduler.Event.initialization(), strategy, Storage.DoubleScanner.class, true)) {
        while (scanner.hasNext()) {
          assertEquals(count + 0.25, scanner.get());
          count++;
        }
      }
      assertEquals(4, count);
      assertFalse(manager.finalizeStorage(99, 99));
    } finally {
      manager.close();
    }
  }
}
