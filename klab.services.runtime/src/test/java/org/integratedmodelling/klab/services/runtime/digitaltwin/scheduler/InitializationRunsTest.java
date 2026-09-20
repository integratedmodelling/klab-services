package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.common.data.impl.ShardImpl;
import org.integratedmodelling.klab.runtime.storage.StorageImpl;
import org.junit.jupiter.api.Test;
import org.ojalgo.array.BufferArray;

class InitializationRunsTest {
  @Test void parallelDiamondPathsShareOneCompleteBufferAndHistogram() throws Exception {
    var gate=new InitializationRuns();
    var root=mock(DigitalTwin.Transaction.class);
    var child=mock(DigitalTwin.Transaction.class);when(child.getParent()).thenReturn(root);
    var callbacks=new ArrayList<Runnable>();
    doAnswer(call -> {callbacks.add(call.getArgument(0));return null;}).when(root).afterRollback(any());
    int cells=4096;
    var shard=mock(ShardImpl.class);var geometry=mock(Geometry.class);
    when(shard.getGeometry()).thenReturn(geometry);when(geometry.size()).thenReturn((long)cells);
    var histogram=com.dynatrace.dynahist.Histogram.createDynamic(
        com.dynatrace.dynahist.layout.OpenTelemetryExponentialBucketsLayout.create(1));
    var generations=new AtomicInteger();
    try(var buffer=(BufferArray)BufferArray.R064.make(cells);
        var pool=Executors.newVirtualThreadPerTaskExecutor()) {
      var ready=new CountDownLatch(16);var start=new CountDownLatch(1);
      List<Future<Boolean>> results=new ArrayList<>();
      for(int branch=0;branch<16;branch++) results.add(pool.submit(() -> {
        ready.countDown();assertTrue(start.await(5,TimeUnit.SECONDS));
        return gate.execute(child,42,() -> {
          int generation=generations.incrementAndGet();
          var scanner=scanner(shard,buffer,histogram);
          for(int cell=0;cell<cells;cell++) {
            scanner.add(generation*10000+cell);
            if(cell%64==0) Thread.yield();
          }
          return true;
        });
      }));
      assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();
      for(var result:results) assertTrue(result.get(10,TimeUnit.SECONDS));
      for(int cell=0;cell<cells;cell++) assertEquals(10000+cell,buffer.doubleValue(cell));
      assertEquals(cells,histogram.getTotalCount());
      long binCount=0;for(var bin:histogram.nonEmptyBinsAscending()) binCount+=bin.getBinCount();
      assertEquals(cells,binCount);
      assertTrue(gate.execute(root,42,() -> {fail("Repeated graph snapshot reran INIT");return false;}));
      callbacks.forEach(Runnable::run);
      assertFalse(gate.execute(root,42,() -> false),"Rollback must release the prior receipt");
    }
  }

  @Test void failureIsSharedAndCommitReleasesReceipts() {
    var gate=new InitializationRuns();var root=mock(DigitalTwin.Transaction.class);
    var committed=new ArrayList<Runnable>();
    doAnswer(call -> {committed.add(call.getArgument(0));return null;}).when(root).afterCommit(any());
    assertFalse(gate.execute(root,1,() -> false));
    assertFalse(gate.execute(root,1,() -> {fail("Failed initialization reran in the same transaction");return true;}));
    assertThrows(IllegalStateException.class,() -> gate.execute(root,2,() -> {throw new IllegalStateException("failed");}));
    assertThrows(CompletionException.class,() -> gate.execute(root,2,() -> true));
    committed.forEach(Runnable::run);
    assertTrue(gate.execute(root,1,() -> true));
  }

  private org.integratedmodelling.klab.api.data.Storage.DoubleScanner scanner(
      ShardImpl shard,BufferArray buffer,com.dynatrace.dynahist.Histogram histogram) {
    try {
      var constructor=Class.forName(StorageImpl.class.getName()+"$LocalDoubleScanner")
          .getDeclaredConstructor(StorageImpl.class,ShardImpl.class,BufferArray.class,com.dynatrace.dynahist.Histogram.class,boolean.class);
      constructor.setAccessible(true);
      return (org.integratedmodelling.klab.api.data.Storage.DoubleScanner)
          constructor.newInstance(mock(StorageImpl.class),shard,buffer,histogram,false);
    } catch(ReflectiveOperationException failure) { throw new AssertionError(failure); }
  }
}
