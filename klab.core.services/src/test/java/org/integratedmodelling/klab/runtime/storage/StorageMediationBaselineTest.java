package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.common.data.impl.ShardImpl;
import org.integratedmodelling.klab.runtime.language.ScannerAdapters;
import org.junit.jupiter.api.Test;
import org.ojalgo.array.BufferArray;

class StorageMediationBaselineTest {
  private StorageImpl storage() {
    var observation = new ObservationImpl();
    observation.setId(-1);
    return new StorageImpl(observation, Data.ShardingStrategy.trivial(Storage.Type.DOUBLE),
        mock(ContextScope.class), mock(StorageManagerImpl.class));
  }

  @Test
  void nativeAndAdaptedCursorsShareOnePositionAndReadonlyWritesCannotAdvanceIt() {
    var storage = storage();
    var shard = new ShardImpl();
    shard.setGeometry(Geometry.create("S1(3)"));
    var buffer = (BufferArray) BufferArray.R064.make(3);
    try {
      buffer.set(0, 1.25); buffer.set(1, 2.5); buffer.set(2, 3.75);
      var nativeScanner = storage.new LocalDoubleScanner(shard, buffer, null, true);
      var view = ScannerAdapters.adaptType(nativeScanner, Storage.FloatScanner.class);
      assertEquals(1.25f, view.peek());
      assertEquals(1.25f, view.peek());
      assertThrows(KlabIllegalStateException.class, () -> view.add(99));
      assertEquals(1.25f, view.get());
      assertEquals(1, view.nextLong(), "nextLong advances: it is not a location accessor");
      assertEquals(3.75f, view.get());
      assertFalse(view.hasNext());
      assertSame(shard, view.shard());
      // Current iterator does not enforce exhaustion; stage 1 must decide the contract.
      assertEquals(3, view.nextLong());
      assertEquals(2.5, buffer.doubleValue(1));
    } finally {
      buffer.close();
    }
  }

  @Test
  void mismatchedStrategyFailsBeforeAnyBufferOrHistogramAccess() {
    var manager = mock(StorageManagerImpl.class);
    var observation = new ObservationImpl(); observation.setId(-1);
    var nativeStrategy = Data.ShardingStrategy.trivial(Storage.Type.DOUBLE);
    var storage = new StorageImpl(observation, nativeStrategy, mock(ContextScope.class), manager);
    var request = new Data.ShardingStrategy(Data.FillCurve.D2_YX, 2, 0, 0, Storage.Type.DOUBLE);
    assertThrows(KlabUnimplementedException.class,
        () -> storage.scan(null, request, Storage.DoubleScanner.class, false));
    verifyNoInteractions(manager);
    assertTrue(nativeStrategy.equals(storage.getNativeShardingStrategy()));
  }

  @Test
  void explicitSplitCountCurrentlyBypassesSizeLimits() throws Exception {
    var split = StorageImpl.class.getDeclaredMethod("getGeometries", Geometry.class,
        int.class, long.class, long.class);
    split.setAccessible(true);
    var geometry = Geometry.create("S2(5,3){bbox=[0 5 0 3],proj=EPSG:4326}");
    var storage = storage();
    assertEquals(List.of(geometry), split.invoke(storage, geometry, 1, 0L, 1L));
    var normal = (List<?>) split.invoke(storage, geometry, 3, 0L, 0L);
    var constrained = (List<?>) split.invoke(storage, geometry, 3, 1000L, 1L);
    assertEquals(normal.stream().map(g -> ((Geometry) g).encode()).toList(),
        constrained.stream().map(g -> ((Geometry) g).encode()).toList());
  }
}
