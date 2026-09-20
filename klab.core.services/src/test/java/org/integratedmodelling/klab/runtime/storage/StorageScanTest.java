package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.ojalgo.array.BufferArray;

class StorageScanTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  private static class Fixture implements AutoCloseable {
    final StorageManagerImpl manager = mock(StorageManagerImpl.class);
    final ObservationImpl observation = new ObservationImpl();
    final Data.ShardingStrategy strategy;
    StorageImpl storage;
    Fixture(int splits, Storage.Type type) {
      var concept = new ConceptImpl(); concept.setUrn("test:Elevation"); concept.setName("Elevation");
      concept.getType().add(SemanticType.QUALITY);
      observation.setObservable(ObservableImpl.promote(concept, null));
      observation.setId(-1); observation.setUrn("test:observation");
      observation.setGeometry(Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=10000}S2(3,1){proj=EPSG:4326,shape=EPSG:4326 POLYGON ((0 0&comma;0 1&comma;3 1&comma;3 0&comma;0 0))}"));
      strategy = new Data.ShardingStrategy(Data.FillCurve.D2_XY, splits, 0, 0, type);
      var scope = mock(ServiceContextScope.class, RETURNS_DEEP_STUBS);
      when(scope.getConfiguration().getPersistence()).thenReturn(Persistence.EXPLICIT_ACTION);
      when(manager.getDoubleBuffer(anyLong())).thenAnswer(c -> (BufferArray) BufferArray.R064.make(c.getArgument(0, Long.class)));
      when(manager.getFloatBuffer(anyLong())).thenAnswer(c -> (BufferArray) BufferArray.R032.make(c.getArgument(0, Long.class)));
      when(manager.getIntBuffer(anyLong())).thenAnswer(c -> (BufferArray) BufferArray.Z032.make(c.getArgument(0, Long.class)));
      when(manager.getLongBuffer(anyLong())).thenAnswer(c -> (BufferArray) BufferArray.Z064.make(c.getArgument(0, Long.class)));
      when(manager.getBooleanBuffer(anyLong())).thenAnswer(c -> (BufferArray) BufferArray.Z008.make(c.getArgument(0, Long.class)));
      storage = new StorageImpl(observation, strategy, scope, manager);
      var outputs = storage.scan(Scheduler.Event.initialization(), strategy, Storage.Scanner.class, false);
      for (var output : outputs) {
        while (output.hasNext()) {
          switch (type) {
            case DOUBLE -> ((Storage.DoubleScanner) output).add(1.25);
            case FLOAT -> ((Storage.FloatScanner) output).add(1.25f);
            case INTEGER -> ((Storage.IntScanner) output).add(Integer.MAX_VALUE);
            case LONG -> ((Storage.LongScanner) output).add(Long.MAX_VALUE);
            case BOOLEAN -> ((Storage.BooleanScanner) output).add(false);
            default -> throw new AssertionError(type);
          }
        }
        storage.finalizeRun(output);
      }
      clearInvocations(manager);
    }
    <T extends Storage.Scanner> StorageScan.Request<T> request(Class<T> type) {
      return StorageScan.Request.nativeRead(Scheduler.Event.initialization(), strategy, type);
    }
    public void close() { storage.close(null); }
  }

  @Test void metadataPlanningIsLazyImmutableAndSerializable() {
    try (var f = new Fixture(1, Storage.Type.DOUBLE)) {
      var request = f.request(Storage.DoubleScanner.class);
      var plan = f.storage.plan(request);
      verifyNoInteractions(f.manager);
      var copy = Utils.Json.parseObject(Utils.Json.asString(plan.description()), StorageScan.Description.class);
      assertEquals(plan.description(), copy);
      assertEquals(plan.description().fingerprint(), copy.fingerprint());
      f.strategy.setSuggestedSplits(20);
      assertEquals(1, request.layout().splits());
      var physical = f.storage.getNativeShards(Scheduler.Event.initialization()).getFirst();
      physical.getShardingStrategy().setCurve(Data.FillCurve.D2_YX);
      assertEquals(Data.FillCurve.D2_XY, physical.getShardingStrategy().getCurve());
      var mutable = f.storage.getNativeShardingStrategy(); mutable.setCurve(Data.FillCurve.D2_YX);
      assertEquals(Data.FillCurve.D2_XY, f.storage.getNativeShardingStrategy().getCurve());
      assertThrows(UnsupportedOperationException.class, () -> plan.description().partitions().clear());
      assertThrows(IllegalArgumentException.class, () -> f.storage.finalizeRun(mock(Storage.DoubleScanner.class)));
    }
  }

  @Test void sessionsHaveIndependentStrictCursorsAndSeparateViewMetadata() {
    try (var f = new Fixture(1, Storage.Type.FLOAT)) {
      var plan = f.storage.plan(f.request(Storage.DoubleScanner.class));
      var first = f.storage.open(plan); var second = f.storage.open(plan);
      var a = first.scanners().getFirst(); var b = second.scanners().getFirst();
      assertEquals(Storage.Type.FLOAT, a.shard().getNativeType());
      assertEquals(Storage.Type.DOUBLE, a.view().valueType());
      assertEquals(StorageScan.HistogramPolicy.UNAVAILABLE, a.view().histogram());
      assertTrue(a.isValid()); assertEquals(0, a.location().offset());
      assertEquals(1.25, a.peek()); assertEquals(0, a.position());
      assertEquals(1.25, a.get()); assertEquals(0, b.position());
      assertEquals(1, a.nextLong()); assertEquals(1.25, a.get());
      assertFalse(a.hasNext()); assertEquals(a.size(), a.position());
      assertThrows(NoSuchElementException.class, a::get);
      assertThrows(NoSuchElementException.class, a::peek);
      assertThrows(NoSuchElementException.class, a::nextLong);
      assertThrows(NoSuchElementException.class, a::isValid);
      assertThrows(NoSuchElementException.class, a::location);
      assertThrows(IllegalStateException.class, () -> b.add(0));
      first.cancel(); first.close();
      assertThrows(IllegalStateException.class, a::get);
      assertThrows(IllegalStateException.class, first::scanners);
      assertEquals(1.25, b.get());
      second.close();
    }
  }

  @Test void metadataBudgetsAlignmentAndProviderOwnershipAreCheckedBeforeOpening() {
    try (var f = new Fixture(2, Storage.Type.DOUBLE);
         var other = new Fixture(1, Storage.Type.DOUBLE)) {
      var request = f.request(Storage.DoubleScanner.class);
      var plan = f.storage.plan(request);
      assertThrows(IllegalArgumentException.class, () -> other.storage.open(plan));
      assertThrows(IllegalArgumentException.class, () -> f.storage.plan(new StorageScan.Request<>(
          request.slice(), request.layout(), null, List.of(), null, request.scannerClass(),
          request.access(), request.precision(), request.coverage(), request.sampling(),
          new StorageScan.Budget(1, 16))));
      var partitions = new ArrayList<>(plan.description().partitions());
      Collections.reverse(partitions);
      assertDoesNotThrow(() -> f.storage.plan(new StorageScan.Request<>(
          request.slice(), request.layout(), null, partitions, null, request.scannerClass(),
          request.access(), request.precision(), request.coverage(), request.sampling(), request.budget())));
      verifyNoInteractions(f.manager, other.manager);
    }
  }

  @Test void leaseProtectsExistingWritersAndStalePlansCannotReadNewValues() {
    try (var f = new Fixture(1, Storage.Type.DOUBLE)) {
      var plan = f.storage.plan(f.request(Storage.DoubleScanner.class));
      var shard = f.storage.getNativeShards(Scheduler.Event.initialization()).getFirst();
      var writer = (Storage.DoubleScanner) f.storage.getNativeScanner(shard);
      try (var session = f.storage.open(plan)) {
        assertThrows(IllegalStateException.class, () -> writer.add(7));
        assertThrows(IllegalStateException.class, () -> f.storage.scan(Scheduler.Event.initialization(), f.strategy, Storage.DoubleScanner.class, false));
        assertThrows(IllegalStateException.class, () -> f.storage.close(null));
        assertEquals(1.25, session.scanners().getFirst().get());
      }
      writer.add(7);
      assertThrows(IllegalStateException.class, () -> f.storage.open(plan));
      assertThrows(IllegalStateException.class, () -> f.storage.plan(f.request(Storage.DoubleScanner.class)));
      f.storage.finalizeRun(writer);
      assertNotEquals(plan.description().fingerprint(),
          f.storage.plan(f.request(Storage.DoubleScanner.class)).description().fingerprint());
      try (var session = f.storage.open(f.storage.plan(f.request(Storage.DoubleScanner.class)))) {
        assertEquals(7, session.scanners().getFirst().get());
      }
    }
  }

  @Test void unsupportedRequestsFailBeforeBufferAccessOrHistogramReset() {
    try (var f = new Fixture(1, Storage.Type.DOUBLE)) {
      var original = f.request(Storage.DoubleScanner.class);
      var changed = f.strategy.copy(); changed.setCurve(Data.FillCurve.D2_HILBERT);
      assertThrows(UnsupportedOperationException.class, () -> f.storage.plan(
          StorageScan.Request.nativeRead(Scheduler.Event.initialization(), changed, Storage.DoubleScanner.class)));
      assertThrows(IllegalArgumentException.class, () -> f.storage.plan(f.request(Storage.FloatScanner.class)));
      assertThrows(IllegalArgumentException.class, () -> f.storage.plan(f.request(Storage.LongScanner.class)));
      assertThrows(UnsupportedOperationException.class, () -> f.storage.plan(new StorageScan.Request<>(
          original.slice(), original.layout(), null, List.of(), null, original.scannerClass(), StorageScan.Access.WRITE,
          original.precision(), original.coverage(), original.sampling(), original.budget())));
      assertThrows(UnsupportedOperationException.class, () -> f.storage.plan(new StorageScan.Request<>(
          original.slice(), original.layout(), "S2(9,9)", List.of(), null, original.scannerClass(), original.access(),
          original.precision(), original.coverage(), original.sampling(), original.budget())));
      verifyNoInteractions(f.manager);
    }
  }

  @Test void floatNarrowingIsExplicitAndValidityPreservesNanWithoutAFalseOrZeroSentinel() {
    try (var f = new Fixture(1, Storage.Type.DOUBLE)) {
      var writer = (Storage.DoubleScanner) f.storage.getNativeScanner(f.storage.getNativeShards(Scheduler.Event.initialization()).getFirst());
      writer.add(Double.NaN); writer.add(Double.MAX_VALUE); f.storage.finalizeRun(writer);
      var original = f.request(Storage.FloatScanner.class);
      var request = new StorageScan.Request<>(original.slice(), original.layout(), null, List.of(), null,
          original.scannerClass(), original.access(), StorageScan.Precision.ALLOW_FLOAT_NARROWING,
          original.coverage(), original.sampling(), original.budget());
      try (var session = f.storage.open(f.storage.plan(request))) {
        var cursor = session.scanners().getFirst();
        assertFalse(cursor.isValid()); assertTrue(Float.isNaN(cursor.get()));
        assertTrue(cursor.isValid()); assertEquals(Float.POSITIVE_INFINITY, cursor.get());
      }
    }
    try (var f = new Fixture(1, Storage.Type.BOOLEAN);
         var session = f.storage.open(f.storage.plan(f.request(Storage.BooleanScanner.class)))) {
      assertTrue(session.scanners().getFirst().isValid()); assertFalse(session.scanners().getFirst().get());
    }
  }

  @Test void indexedBlocksRespectBoundsBudgetsAndNativeIntegerPrecision() {
    try (var f = new Fixture(1, Storage.Type.LONG);
         var session = f.storage.open(f.storage.plan(f.request(Storage.LongScanner.class)))) {
      var cursor = session.scanners().getFirst();
      assertEquals(Long.MAX_VALUE, cursor.get());
      try (var reader = f.storage.openReader(cursor.shard(), 2)) {
        var values = new long[3]; reader.readLongs(0, values, 1, 2);
        assertArrayEquals(new long[] {0, Long.MAX_VALUE, Long.MAX_VALUE}, values);
        assertThrows(IndexOutOfBoundsException.class, () -> reader.readLongs(0, values, 0, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.readLong(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> reader.readDouble(0));
      }
    }
  }

  private static volatile double allocationSink;

  @Test void primitiveFloatAdaptationAndValidityDoNotAllocatePerValue() {
    var management = java.lang.management.ManagementFactory.getThreadMXBean();
    org.junit.jupiter.api.Assumptions.assumeTrue(
        management instanceof com.sun.management.ThreadMXBean);
    var allocations = (com.sun.management.ThreadMXBean) management;
    org.junit.jupiter.api.Assumptions.assumeTrue(allocations.isThreadAllocatedMemorySupported());
    allocations.setThreadAllocatedMemoryEnabled(true);
    try (var f = new Fixture(1, Storage.Type.FLOAT);
         var session = f.storage.open(f.storage.plan(f.request(Storage.DoubleScanner.class)))) {
      var cursor = session.scanners().getFirst();
      double total = 0;
      for (int i = 0; i < 200000; i++) if (cursor.isValid()) total += cursor.peek();
      long thread = Thread.currentThread().threadId();
      long before = allocations.getThreadAllocatedBytes(thread);
      for (int i = 0; i < 1000000; i++) if (cursor.isValid()) total += cursor.peek();
      long bytes = allocations.getThreadAllocatedBytes(thread) - before;
      allocationSink = total;
      assertEquals(1500000, allocationSink);
      // Allows fixed JVM bookkeeping while detecting even one boxed primitive per value.
      assertTrue(bytes < 65536, "Per-value allocation regression: " + bytes + " bytes");
      System.out.println("Primitive scan allocation: " + bytes + " bytes / 1000000 reads");
    }
  }

  @Test void partialSessionOpenClosesEarlierReadersAndReleasesLease() {
    try (var f = new Fixture(2, Storage.Type.DOUBLE)) {
      f.storage = spy(f.storage);
      var opened = new ArrayList<IndexedStorageReader>();
      var count = new AtomicInteger();
      doAnswer(call -> {
        if (count.incrementAndGet() == 2) throw new IllegalStateException("simulated source failure");
        var reader = (IndexedStorageReader) call.callRealMethod(); opened.add(reader); return reader;
      }).when(f.storage).openReader(any(), anyInt());
      var plan = f.storage.plan(f.request(Storage.DoubleScanner.class));
      assertTrue(plan.description().sources().size() > 1);
      assertThrows(IllegalStateException.class, () -> f.storage.open(plan));
      assertThrows(IllegalStateException.class, () -> opened.getFirst().readDouble(0));
      var writer = (Storage.DoubleScanner) f.storage.getNativeScanner(f.storage.getNativeShards(Scheduler.Event.initialization()).getFirst());
      assertDoesNotThrow(() -> writer.add(4));
    }
  }
}
