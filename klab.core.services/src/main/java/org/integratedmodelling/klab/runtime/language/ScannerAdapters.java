package org.integratedmodelling.klab.runtime.language;

import java.util.List;
import java.util.Objects;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;

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
    Objects.requireNonNull(originalScanner, "originalScanner");
    Objects.requireNonNull(adaptedScannerClass, "adaptedScannerClass");

    if (adaptedScannerClass.isInstance(originalScanner)) {
      return adaptedScannerClass.cast(originalScanner);
    }

    Storage.Scanner adapted = null;
    if (originalScanner instanceof Storage.FloatScanner floatScanner
        && adaptedScannerClass == Storage.DoubleScanner.class) {
      adapted = new DoubleScannerAdapter(floatScanner);
    } else if (originalScanner instanceof Storage.DoubleScanner doubleScanner
        && adaptedScannerClass == Storage.FloatScanner.class) {
      adapted = new FloatScannerAdapter(doubleScanner);
    }

    if (adapted == null) {
      var shard = originalScanner.shard();
      throw new KlabIllegalArgumentException(
          "Cannot adapt storage scanner "
              + originalScanner.getClass().getSimpleName()
              + " (native type "
              + (shard == null ? "unknown" : shard.getNativeType())
              + ") to "
              + adaptedScannerClass.getSimpleName());
    }
    return adaptedScannerClass.cast(adapted);
  }

  private abstract static class TypeAdapter implements Storage.Scanner {

    protected final Storage.Scanner delegate;

    private TypeAdapter(Storage.Scanner delegate) {
      this.delegate = delegate;
    }

    @Override
    public Storage.Shard shard() {
      return delegate.shard();
    }

    @Override
    public long size() {
      return delegate.size();
    }

    @Override
    public boolean hasNext() {
      return delegate.hasNext();
    }

    @Override
    public long nextLong() {
      return delegate.nextLong();
    }
  }

  /** Presents float-backed storage to contextualizers that use the traditional double API. */
  private static final class DoubleScannerAdapter extends TypeAdapter
      implements Storage.DoubleScanner {

    private final Storage.FloatScanner delegate;

    private DoubleScannerAdapter(Storage.FloatScanner delegate) {
      super(delegate);
      this.delegate = delegate;
    }

    @Override
    public double get() {
      return delegate.get();
    }

    @Override
    public double peek() {
      return delegate.peek();
    }

    @Override
    public void add(double value) {
      delegate.add((float) value);
    }
  }

  /** Presents double-backed storage to contextualizers that explicitly request short floats. */
  private static final class FloatScannerAdapter extends TypeAdapter
      implements Storage.FloatScanner {

    private final Storage.DoubleScanner delegate;

    private FloatScannerAdapter(Storage.DoubleScanner delegate) {
      super(delegate);
      this.delegate = delegate;
    }

    @Override
    public float get() {
      return (float) delegate.get();
    }

    @Override
    public float peek() {
      return (float) delegate.peek();
    }

    @Override
    public void add(float value) {
      delegate.add(value);
    }
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
