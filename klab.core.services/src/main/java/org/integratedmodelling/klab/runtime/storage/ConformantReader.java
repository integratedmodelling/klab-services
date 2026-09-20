package org.integratedmodelling.klab.runtime.storage;

import java.util.List;
import org.integratedmodelling.klab.api.data.Data.FillCurve;
import org.integratedmodelling.klab.api.data.Storage;

/** Session-local mapping cursor: fixed scratch coordinates, cached lookup, primitive payload only. */
final class ConformantReader implements IndexedStorageReader {
  private final ConformantScan plan;
  private final ConformantScan.Box target;
  private final List<IndexedStorageReader> sources;
  private final long[] point;
  private final int blockValues;
  private final Storage.Type type;
  private long lastIndex = -1, offset;
  private int source = -1;
  private boolean closed;

  ConformantReader(ConformantScan plan, int partition, List<IndexedStorageReader> sources, int blockValues) {
    this.plan = plan; this.target = plan.targets[partition]; this.sources = sources;
    this.blockValues = blockValues; this.type = sources.get(0).type(); point = new long[target.shape.length];
  }
  public Storage.Type type() { return type; }
  public long size() { return target.size; }
  public int blockValues() { return blockValues; }
  private void locate(long index) {
    if (closed) throw new IllegalStateException("Mapped reader closed");
    if (index < 0 || index >= target.size) throw new IndexOutOfBoundsException("View offset " + index);
    if (index == lastIndex) return;
    target.decode(index, plan.targetCurve, point);
    if (source < 0 || !plan.sources[source].contains(point)) source = plan.directory.find(point);
    if (source < 0) throw new IllegalStateException("Validated source coverage lost a cell");
    offset = plan.sources[source].encode(point, plan.sourceCurve); lastIndex = index;
  }
  public synchronized boolean isValid(long index) { locate(index); return sources.get(source).isValid(offset); }
  public synchronized double readDouble(long index) { locate(index); return sources.get(source).readDouble(offset); }
  public synchronized float readFloat(long index) { locate(index); return sources.get(source).readFloat(offset); }
  public synchronized int readInt(long index) { locate(index); return sources.get(source).readInt(offset); }
  public synchronized long readLong(long index) { locate(index); return sources.get(source).readLong(offset); }
  public synchronized boolean readBoolean(long index) { locate(index); return sources.get(source).readBoolean(offset); }
  private void block(long start, int length, int offset, int count) {
    if (closed) throw new IllegalStateException("Mapped reader closed");
    if (count < 0 || count > blockValues || start < 0 || start > size() - count
        || offset < 0 || offset > length - count) throw new IndexOutOfBoundsException("Invalid mapped block");
  }
  /** A contiguous native run ends at either the source or consumer's fastest-axis boundary. */
  private int span(int remaining) {
    int sourceAxis = plan.sourceCurve == FillCurve.D2_YX ? 0 : point.length - 1;
    int targetAxis = plan.targetCurve == FillCurve.D2_YX ? 0 : point.length - 1;
    boolean sourceReverse = plan.sourceCurve == FillCurve.D2_XInvY;
    boolean targetReverse = plan.targetCurve == FillCurve.D2_XInvY;
    if (sourceAxis != targetAxis || sourceReverse != targetReverse) return 1;
    var nativeBox = plan.sources[source];
    long sourceRun = sourceReverse ? point[sourceAxis] - nativeBox.start[sourceAxis] + 1
        : nativeBox.start[sourceAxis] + nativeBox.shape[sourceAxis] - point[sourceAxis];
    long targetRun = targetReverse ? point[targetAxis] - target.start[targetAxis] + 1
        : target.start[targetAxis] + target.shape[targetAxis] - point[targetAxis];
    return (int) Math.min(remaining, Math.min(sourceRun, targetRun));
  }
  @Override public synchronized void readDoubles(long start, double[] values, int into, int count) {
    if (type() != Storage.Type.DOUBLE) throw new IllegalArgumentException("Wrong primitive block type");
    block(start, values.length, into, count);
    for (int done = 0; done < count;) {
      locate(start + done); int run = span(count - done);
      sources.get(source).readDoubles(offset, values, into + done, run); done += run;
    }
  }
  @Override public synchronized void readFloats(long start, float[] values, int into, int count) {
    if (type() != Storage.Type.FLOAT) throw new IllegalArgumentException("Wrong primitive block type");
    block(start, values.length, into, count);
    for (int done = 0; done < count;) {
      locate(start + done); int run = span(count - done);
      sources.get(source).readFloats(offset, values, into + done, run); done += run;
    }
  }
  @Override public synchronized void readInts(long start, int[] values, int into, int count) {
    if (type() != Storage.Type.INTEGER) throw new IllegalArgumentException("Wrong primitive block type");
    block(start, values.length, into, count);
    for (int done = 0; done < count;) {
      locate(start + done); int run = span(count - done);
      sources.get(source).readInts(offset, values, into + done, run); done += run;
    }
  }
  @Override public synchronized void readLongs(long start, long[] values, int into, int count) {
    if (type() != Storage.Type.LONG) throw new IllegalArgumentException("Wrong primitive block type");
    block(start, values.length, into, count);
    for (int done = 0; done < count;) {
      locate(start + done); int run = span(count - done);
      sources.get(source).readLongs(offset, values, into + done, run); done += run;
    }
  }
  @Override public synchronized void readBooleans(long start, boolean[] values, int into, int count) {
    if (type() != Storage.Type.BOOLEAN) throw new IllegalArgumentException("Wrong primitive block type");
    block(start, values.length, into, count);
    for (int done = 0; done < count;) {
      locate(start + done); int run = span(count - done);
      sources.get(source).readBooleans(offset, values, into + done, run); done += run;
    }
  }
  public synchronized void close() { closed = true; }
}
