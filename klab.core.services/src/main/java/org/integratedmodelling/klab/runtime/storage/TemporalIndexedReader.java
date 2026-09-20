package org.integratedmodelling.klab.runtime.storage;

import java.util.Map;
import org.integratedmodelling.klab.api.data.Storage;

/** Immutable sparse transaction snapshot; unchanged baseline reads stay primitive. */
final class TemporalIndexedReader implements IndexedStorageReader {
  private final IndexedStorageReader baseline;
  private final long[] locations;
  private final Object[] values;
  private final Storage.Type type;
  private final long size;
  private final int blockValues;
  private boolean closed;
  TemporalIndexedReader(IndexedStorageReader baseline, Map<Long, Object> changes, Storage.Type type, long size, int blockValues) {
    this.baseline = baseline; this.type = type; this.size = size; this.blockValues = blockValues;
    locations = changes.keySet().stream().mapToLong(Long::longValue).sorted().toArray();
    values = new Object[locations.length];
    for (int i = 0; i < locations.length; i++) values[i] = changes.get(locations[i]);
  }
  public Storage.Type type() { return type; }
  public long size() { return size; }
  public int blockValues() { return blockValues; }
  private Object change(long index) {
    if (closed) throw new IllegalStateException("Temporal snapshot closed");
    if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
    int found = java.util.Arrays.binarySearch(locations, index);
    return found < 0 ? null : values[found];
  }
  public boolean isValid(long index) {
    return switch (type) {
      case DOUBLE -> !Double.isNaN(readDouble(index));
      case FLOAT -> !Float.isNaN(readFloat(index));
      default -> { change(index); yield true; }
    };
  }
  public double readDouble(long index) { var value = change(index); return value == null ? baseline.readDouble(index) : (Double) value; }
  public float readFloat(long index) { var value = change(index); return value == null ? baseline.readFloat(index) : (Float) value; }
  public int readInt(long index) { var value = change(index); return value == null ? baseline.readInt(index) : (Integer) value; }
  public long readLong(long index) { var value = change(index); return value == null ? baseline.readLong(index) : (Long) value; }
  public boolean readBoolean(long index) { var value = change(index); return value == null ? baseline.readBoolean(index) : (Boolean) value; }
  public void close() { if (!closed) { closed = true; if (baseline != null) baseline.close(); } }
}
