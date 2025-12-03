package org.integratedmodelling.klab.runtime.language;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;

import java.util.List;

public class ScannerAdapters {

  public static <T extends Storage.Scanner> T mergeScanners(
      List<Storage.Scanner> scanners, Class<? extends Storage.Scanner> requiredType) {
    if (scanners.size() == 1 && requiredType.isAssignableFrom(scanners.getFirst().getClass())) {
      return (T) scanners.getFirst();
    }
    throw new KlabUnimplementedException(
        "merging and remapping of scanners is not yet implemented");
  }

  // generic remapping scanner preserving geometry but remapping fill curve
  private abstract static class RemappingScanner implements Storage.Scanner {}

  // generic merging scanner preserving fill curve but remapping multiple shard scanners to an
  // overall geometry
  private abstract static class MergingScanner implements Storage.Scanner {}

  /**
   * Do the grueling work of adapting a scanner to a different type without having to use boxing.
   *
   * @param originalScanner
   * @param adaptedScannerClass
   * @return
   * @param <F>
   * @param <T>
   */
  public static <F extends Storage.Scanner, T extends Storage.Scanner> T adaptType(
      F originalScanner, Class<T> adaptedScannerClass) {
    return null;
  }

  private static class MockShard implements Storage.Shard {

    public MockShard(Geometry geometry, Data.ShardingStrategy shardingStrategy) {}

    @Override
    public Geometry getGeometry() {
      return null;
    }

    @Override
    public Data.ShardingStrategy getShardingStrategy() {
      return null;
    }

    @Override
    public int getShardIndex() {
      return 0;
    }

    @Override
    public int getShardCount() {
      return 0;
    }

    @Override
    public Histogram getHistogram() {
      return null;
    }

    @Override
    public Storage.Type getNativeType() {
      return null;
    }

      @Override
      public long getTimestamp() {
      return 0;
      }

      @Override
      public String getUrn() {
      return "";
      }

      @Override
    public long getId() {
      return 0;
    }

    @Override
    public long getParentId() {
      return 0;
    }

    @Override
    public long getTransientId() {
      return 0;
    }

    @Override
    public int getChildrenCount() {
      return 0;
    }

    @Override
    public long getParentTransientId() {
      return 0;
    }

    @Override
    public Type classify() {
      return Type.DATA;
    }
  }
}
