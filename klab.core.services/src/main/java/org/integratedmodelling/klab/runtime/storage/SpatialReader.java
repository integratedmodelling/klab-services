package org.integratedmodelling.klab.runtime.storage;

import java.util.List;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.data.StorageScan;

/** Target-driven spatial reader. Scratch and numeric source window are bounded per cursor. */
final class SpatialReader implements IndexedStorageReader {
  private final SpatialScan plan;
  private final ConformantScan.Box target;
  private final List<IndexedStorageReader> readers;
  private final Storage.Type type;
  private final int blockValues;
  private final long[] point = new long[2], sourcePoint = new long[2];
  private final double[] lower = new double[2], upper = new double[2];
  private final double[] window;
  private final double[] categoryWeights;
  private final int[] touched;
  private final String[] definitions;
  private int touchedCount, winningCode;
  public org.integratedmodelling.klab.api.data.mediation.classification.DataKey key() { return readers.getFirst().key(); }
  private int windowSource = -1, windowLength, selected = -1;
  private long windowStart, selectedOffset, last = -1;
  private double value;
  private boolean valid, closed;

  SpatialReader(
      SpatialScan plan, int partition, List<IndexedStorageReader> readers, int blockValues) {
    this.plan = plan;
    this.target = plan.target.targets[partition];
    this.readers = readers;
    this.type = readers.getFirst().type();
    this.blockValues = blockValues;
    window =
        new double[type == Storage.Type.KEYED || plan.sampling == StorageScan.Sampling.NEAREST ? 0 : Math.min(4096, blockValues)];
    int categories = plan.sampling == StorageScan.Sampling.MAJORITY ? key().size() - 1 : 0;
    categoryWeights = new double[categories + 1]; touched = new int[categories]; definitions = new String[categories + 1];
    for(int i=1;i<=categories;i++) definitions[i]=((org.integratedmodelling.klab.api.knowledge.Concept)key().lookup(i)).getUrn();
  }

  public Storage.Type type() {
    return type == Storage.Type.KEYED || plan.sampling == StorageScan.Sampling.NEAREST ? type : Storage.Type.DOUBLE;
  }

  public long size() {
    return target.size;
  }

  public int blockValues() {
    return blockValues;
  }

  private boolean select(long x, long y) {
    sourcePoint[0] = x;
    sourcePoint[1] = y;
    selected = plan.source.directory.find(sourcePoint);
    if (selected < 0) return false;
    selectedOffset = plan.source.sources[selected].encode(sourcePoint, plan.source.sourceCurve);
    return readers.get(selected).isValid(selectedOffset);
  }

  private double numeric() {
    if (windowSource != selected
        || selectedOffset < windowStart
        || selectedOffset - windowStart >= windowLength) {
      windowSource = selected;
      windowStart = selectedOffset / window.length * window.length;
      windowLength = (int) Math.min(window.length, readers.get(selected).size() - windowStart);
      for (int i = 0; i < windowLength; i++) {
        long offset = windowStart + i;
        window[i] =
            !readers.get(selected).isValid(offset)
                ? Double.NaN
                : type == Storage.Type.DOUBLE
                    ? readers.get(selected).readDouble(offset)
                    : readers.get(selected).readFloat(offset);
      }
    }
    return window[(int) (selectedOffset - windowStart)];
  }

  private void locate(long index) {
    if (closed) throw new IllegalStateException("Spatial reader closed");
    if (index < 0 || index >= size())
      throw new IndexOutOfBoundsException("Spatial offset " + index);
    if (index == last) return;
    target.decode(index, plan.target.targetCurve, point);
    valid = false;
    value = Double.NaN;
    if (plan.conservative()) aggregate();
    else {
      plan.world(point[0] + 0.5, point[1] + 0.5, lower);
      double x = plan.coordinate(lower[0], 0), y = plan.coordinate(lower[1], 1);
      if (plan.sampling == StorageScan.Sampling.NEAREST) {
        valid = select(SpatialScan.floor(x), SpatialScan.floor(y));
      } else interpolate(x - 0.5, y - 0.5);
    }
    last = index;
  }

  private void interpolate(double x, double y) {
    long ix = SpatialScan.floor(x), iy = SpatialScan.floor(y);
    double fx = x - ix, fy = y - iy, sum = 0;
    for (int dx = 0; dx < 2; dx++)
      for (int dy = 0; dy < 2; dy++) {
        double weight = (dx == 0 ? 1 - fx : fx) * (dy == 0 ? 1 - fy : fy);
        if (weight <= 0) continue;
        // Strict validity: do not fill holes or extrapolate across missing kernel support.
        if (!select(ix + dx, iy + dy)) return;
        sum += numeric() * weight;
      }
    value = sum;
    valid = !Double.isNaN(sum);
  }

  private void aggregate() {
    plan.world(point[0], point[1], lower);
    plan.world(point[0] + 1, point[1] + 1, upper);
    double x0 = plan.coordinate(lower[0], 0), y0 = plan.coordinate(lower[1], 1);
    double x1 = plan.coordinate(upper[0], 0), y1 = plan.coordinate(upper[1], 1);
    var bounds = plan.source.directory.bounds;
    long fromX = Math.max(bounds.start[0], SpatialScan.floor(x0));
    long fromY = Math.max(bounds.start[1], SpatialScan.floor(y0));
    long toX = Math.min(bounds.start[0] + bounds.shape[0], (long) Math.ceil(x1));
    long toY = Math.min(bounds.start[1] + bounds.shape[1], (long) Math.ceil(y1));
    double sum = 0, weightSum = 0;
    boolean majority = plan.sampling == StorageScan.Sampling.MAJORITY;
    for(int i=0;i<touchedCount;i++) categoryWeights[touched[i]]=0;
    touchedCount=0; winningCode=0;
    for (long x = fromX; x < toX; x++)
      for (long y = fromY; y < toY; y++) {
        double weight =
            plan.area(Math.max(x0, x), Math.min(x1, x + 1), Math.max(y0, y), Math.min(y1, y + 1));
        if (weight <= 0) continue;
        if (!select(x, y)) return;
        if (majority) {
          int code=readers.get(selected).readInt(selectedOffset);
          if(code<=0 || code>=categoryWeights.length)throw new IllegalStateException("Unknown category code " + code);
          if(categoryWeights[code]==0)touched[touchedCount++]=code;
          categoryWeights[code]+=weight;
        } else sum += numeric() * (plan.sampling == StorageScan.Sampling.CONSERVATIVE_TOTAL
            ? weight / plan.area(x,x+1,y,y+1) : weight);
        weightSum += weight;
      }
    if (weightSum > 0) {
      if (majority) {
        for(int i=0;i<touchedCount;i++) {
          int code=touched[i];
          if(winningCode==0 || categoryWeights[code]>categoryWeights[winningCode]
              || categoryWeights[code]==categoryWeights[winningCode] && definitions[code].compareTo(definitions[winningCode])<0) winningCode=code;
        }
        valid=true;return;
      }
      value = plan.sampling == StorageScan.Sampling.CONSERVATIVE_TOTAL ? sum : sum / weightSum;
      valid = !Double.isNaN(value);
    }
  }

  public synchronized boolean isValid(long index) {
    locate(index);
    return valid;
  }

  public synchronized double readDouble(long index) {
    locate(index);
    return !valid
        ? Double.NaN
        : plan.sampling == StorageScan.Sampling.NEAREST
            ? readers.get(selected).readDouble(selectedOffset)
            : value;
  }

  public synchronized float readFloat(long index) {
    locate(index);
    return !valid
        ? Float.NaN
        : plan.sampling == StorageScan.Sampling.NEAREST
            ? readers.get(selected).readFloat(selectedOffset)
            : (float) value;
  }

  private void requireValid(long index) {
    locate(index);
    if (!valid)
      throw new IllegalStateException(
          "Missing spatial value; check scanner.isValid() before reading integer or boolean values");
  }

  public synchronized int readInt(long index) {
    requireValid(index);
    return plan.sampling == StorageScan.Sampling.MAJORITY ? winningCode : readers.get(selected).readInt(selectedOffset);
  }

  public synchronized long readLong(long index) {
    requireValid(index);
    return readers.get(selected).readLong(selectedOffset);
  }

  public synchronized boolean readBoolean(long index) {
    requireValid(index);
    return readers.get(selected).readBoolean(selectedOffset);
  }

  public synchronized void close() {
    closed = true;
  }
}
