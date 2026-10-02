package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.common.data.impl.ShardImpl;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ojalgo.array.BufferArray;
import org.ojalgo.structure.Access1D;

/** Real storage, maintenance executor and files; graph descriptors/scopes use existing fixture conventions. */
class BulkShardStorageTest {
  @TempDir Path directory;
  private static final Scheduler.Event INIT = Scheduler.Event.initialization();

  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  /** Keep scratch buffers in memory, isolating durable file I/O from mmap cleanup on Windows. */
  private static final class MemoryManager extends StorageManagerImpl {
    int allocations;
    Access1D<?> substitute;
    final ServiceContextScope testScope;
    MemoryManager(ServiceContextScope scope, Path scratch, Path durable) {
      super(scope, scratch.toFile(), durable.toFile());
      testScope = scope;
    }
    @Override public synchronized BufferArray getDoubleBuffer(long size) {
      allocations++;
      return (BufferArray) BufferArray.R064.make(size);
    }
    @Override public boolean saveBufferArray(BufferArray array, java.io.File file, Storage.Type type) {
      if (substitute == null) return super.saveBufferArray(array, file, type);
      // Inject a failing primitive source into the real file writer, without instrumenting
      // BufferArray's final accessors (which would distort other tests' allocation measurements).
      try {
        writeBufferArray(substitute, file, type);
        return true;
      } catch (Exception failure) {
        testScope.error("Error saving buffer array: " + failure.getMessage());
        return false;
      }
    }
  }

  private ServiceContextScope scope() {
    var scope = mock(ServiceContextScope.class, RETURNS_DEEP_STUBS);
    when(scope.getId()).thenReturn("bulk-io-context");
    when(scope.getConfiguration()).thenReturn(DigitalTwin.Configuration.builder()
        .id("bulk-io-context").persistence(Persistence.EXPLICIT_ACTION).build());
    doReturn(mock(RuntimeService.class, RETURNS_DEEP_STUBS)).when(scope).getService(RuntimeService.class);
    return scope;
  }

  @Test void persistedObservationReopensThroughPlannedAndNativeReads() throws Exception {
    var scope = scope();
    var concept = new ConceptImpl(); concept.setUrn("test:Elevation"); concept.setName("Elevation");
    concept.getType().add(SemanticType.QUALITY);
    var observation = new ObservationImpl(); observation.setId(101); observation.setUrn("test:bulk-elevation");
    observation.setObservable(ObservableImpl.promote(concept, null));
    observation.setGeometry(Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=10000}S2(128,257){proj=EPSG:3857,"
        + "shape=EPSG:3857 POLYGON ((0 0&comma;0 257&comma;128 257&comma;128 0&comma;0 0))}"));
    var layout = new Data.ShardingStrategy(Data.FillCurve.D2_YX, 4, 0, 0, Storage.Type.DOUBLE);
    var cd = new ObservationImpl.ContextualizationDataImpl(); cd.setNativeShardingStrategy(layout);
    observation.setContextualizationData(cd);
    var descriptors = new ArrayList<Storage.Shard>();
    when(scope.getDigitalTwin().getKnowledgeGraph().query(Storage.Shard.class, scope)
        .source(observation).along(GraphModel.Relationship.HAS_DATA).run(scope))
        .thenAnswer(call -> new ArrayList<>(descriptors));
    var dataDirectory = directory.resolve("durable");
    var writer = new MemoryManager(scope, directory.resolve("scratch-write"), dataDirectory);
    try {
      var storage = (StorageImpl) writer.createStorage(observation);
      for (var scanner : storage.scan(INIT, layout, Storage.DoubleScanner.class, false)) {
        for (long i = 0; i < scanner.size(); i++) scanner.add(value(scanner.shard().getShardIndex(), i));
        storage.finalizeRun(scanner);
      }
      writer.flushPendingPersistence();
      descriptors.addAll(storage.allShards());
      assertTrue(descriptors.size() > 1, "The requested split count is a hint, not an exact partition count");
      assertTrue(descriptors.stream().anyMatch(shard -> shard.getGeometry().size() * Double.BYTES > ShardBufferIO.BLOCK_BYTES));
      for (var shard : descriptors) {
        assertEquals(24 + shard.getGeometry().size() * Double.BYTES, Files.size(writer.getStorageFile(shard).toPath()));
      }
    } finally {
      writer.close();
    }

    // Graph order is not storage traversal order; descriptors survive, original buffers do not.
    Collections.reverse(descriptors);
    var reader = new MemoryManager(scope, directory.resolve("scratch-read"), dataDirectory);
    try {
      var restored = reader.getStorage(observation);
      assertEquals(0, reader.allocations, "Descriptor reconstruction remains lazy");
      long cells = 0;
      try (var session = restored.open(restored.plan(StorageScan.Request.nativeRead(INIT, layout, Storage.DoubleScanner.class)))) {
        for (var scanner : session.scanners()) cells += verifyValues(scanner);
      }
      assertEquals(128L * 257, cells);
      assertEquals(descriptors.size(), reader.allocations, "Opening planned readers restores through loadBufferArray and the bulk decoder");
      cells = 0;
      for (var scanner : restored.scan(INIT, layout, Storage.DoubleScanner.class, true)) cells += verifyValues(scanner);
      assertEquals(128L * 257, cells);
      assertEquals(descriptors.size(), reader.allocations, "Native scans reuse the restored buffers");
      System.out.println("BULK SHARD I/O: " + descriptors.size() + " shards / " + cells
          + " cells persisted, closed, reopened and verified through planned and native reads (including missing values).");
    } finally {
      reader.close();
    }
  }

  @Test void failedAsyncReplacementPreservesExistingFileAndCleansTemporaryOutput() throws Exception {
    var scope = scope();
    var manager = new MemoryManager(scope, directory.resolve("scratch"), directory.resolve("durable"));
    int size = ShardBufferIO.BLOCK_BYTES / Double.BYTES + 17;
    try (var source = ShardBufferIOTest.array(Storage.Type.DOUBLE, size)) {
      ShardBufferIOTest.fill(source, Storage.Type.DOUBLE);
      var shard = shard(size);
      var original = new StorageImpl.BaseScanner(shard, source, null, true);
      try {
        manager.persistShard(original);
        manager.flushPendingPersistence();
        var destination = manager.getStorageFile(shard).toPath();
        byte[] before = Files.readAllBytes(destination);
        manager.substitute = failingSource(size);
        manager.persistShard(original);
        assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIOException.class, manager::flushPendingPersistence);
        assertArrayEquals(before, Files.readAllBytes(destination));
        assertOnlyDestination(destination);
        verify(scope).error(contains("injected failure after a complete block"));
        System.out.println("BULK SHARD I/O: failed replacement preserved the existing file and removed its partial temporary file.");
      } finally {
        // Complete a successful retry so the existing maintenance-future lifecycle can shut down.
        manager.substitute = null;
        manager.persistShard(original);
        manager.flushPendingPersistence();
        manager.close();
      }
    }
  }

  @Test void failedTemporalPreparationPreservesExistingFileAndRemovesPendingFile() throws Exception {
    var manager = new MemoryManager(scope(), directory.resolve("scratch"), directory.resolve("durable"));
    int size = ShardBufferIO.BLOCK_BYTES / Double.BYTES + 17;
    try (var source = ShardBufferIOTest.array(Storage.Type.DOUBLE, size)) {
      ShardBufferIOTest.fill(source, Storage.Type.DOUBLE);
      var shard = shard(size);
      manager.persistTemporalShard(shard, source);
      var destination = manager.getStorageFile(shard).toPath();
      byte[] before = Files.readAllBytes(destination);
      var failure = assertThrows(IllegalStateException.class, () -> manager.persistTemporalShard(shard, failingSource(size)));
      assertEquals("injected failure after a complete block", failure.getMessage());
      assertArrayEquals(before, Files.readAllBytes(destination));
      assertOnlyDestination(destination);
    } finally {
      manager.close();
    }
  }

  @Test void publicBooleanIoMethodsReportFileFailures() throws Exception {
    var scope = scope();
    var manager = new MemoryManager(scope, directory.resolve("scratch"), directory.resolve("durable"));
    try (var values = ShardBufferIOTest.array(Storage.Type.DOUBLE, 4)) {
      var missingParent = directory.resolve("absent-parent").resolve("shard.dat");
      assertFalse(manager.saveBufferArray(values, missingParent.toFile(), Storage.Type.DOUBLE));
      verify(scope).error(contains("Error saving buffer array"));
      assertFalse(Files.exists(missingParent));
      assertFalse(manager.loadBufferArray(values, missingParent.toFile(), Storage.Type.DOUBLE));
      verify(scope).error(contains("Error loading buffer array"));
    } finally {
      manager.close();
    }
  }

  private static Access1D<?> failingSource(int size) {
    var source = mock(Access1D.class);
    when(source.count()).thenReturn((long) size);
    when(source.doubleValue(anyLong())).thenAnswer(call -> {
      if (call.getArgument(0, Long.class) >= ShardBufferIO.BLOCK_BYTES / Double.BYTES)
        throw new IllegalStateException("injected failure after a complete block");
      return -99.0;
    });
    return source;
  }

  private static ShardImpl shard(int size) {
    var shard = new ShardImpl(); shard.setUrn("durable-shard"); shard.setNativeType(Storage.Type.DOUBLE);
    shard.setGeometry(Geometry.create("S2(" + size + ",1)"));
    return shard;
  }

  private static void assertOnlyDestination(Path destination) throws Exception {
    try (var paths = Files.list(destination.getParent())) {
      assertEquals(List.of(destination), paths.toList());
    }
  }

  private static long verifyValues(Storage.DoubleScanner scanner) {
    for (long i = 0; i < scanner.size(); i++) assertEquals(value(scanner.shard().getShardIndex(), i), scanner.get());
    assertFalse(scanner.hasNext());
    return scanner.size();
  }

  private static double value(int shard, long position) {
    long index = shard * 100000L + position;
    return index % 97 == 0 ? Double.NaN : index * .125;
  }
}
