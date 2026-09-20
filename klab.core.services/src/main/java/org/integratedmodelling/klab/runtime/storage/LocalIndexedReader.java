package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Storage;
import org.ojalgo.array.BufferArray;

/** Storage owns the buffer; the session owns this handle. Reader-local locking coordinates cancellation without serializing independent shards. */
final class LocalIndexedReader implements IndexedStorageReader {
  private final Object monitor = new Object();
  private final BufferArray buffer;
  private final Storage.Type type;
  private final int blockValues;
  private boolean closed;
  LocalIndexedReader(BufferArray buffer, Storage.Type type, int blockValues) {
    this.buffer = buffer; this.type = type; this.blockValues = blockValues;
  }
  public Storage.Type type() { return type; }
  public long size() { synchronized (monitor) { checkOpen(); return buffer.count(); } }
  public int blockValues() { return blockValues; }
  private void checkOpen() { if (closed) throw new IllegalStateException("Source reader closed"); }
  private void check(long index, Storage.Type expected) {
    checkOpen();
    if (type != expected) throw new IllegalArgumentException("Expected " + expected + ", got " + type);
    if (index < 0 || index >= buffer.count()) throw new IndexOutOfBoundsException("Source offset: " + index);
  }
  public boolean isValid(long index) {
    synchronized (monitor) {
      check(index, type);
      if (type == Storage.Type.DOUBLE) return !Double.isNaN(buffer.doubleValue(index));
      if (type == Storage.Type.FLOAT) return !Float.isNaN(buffer.floatValue(index));
      if (type == Storage.Type.KEYED) throw new UnsupportedOperationException("No durable dictionary");
      return true;
    }
  }
  public double readDouble(long index) { synchronized (monitor) { check(index, Storage.Type.DOUBLE); return buffer.doubleValue(index); } }
  public float readFloat(long index) { synchronized (monitor) { check(index, Storage.Type.FLOAT); return buffer.floatValue(index); } }
  public int readInt(long index) { synchronized (monitor) { check(index, Storage.Type.INTEGER); return buffer.intValue(index); } }
  public long readLong(long index) { synchronized (monitor) { check(index, Storage.Type.LONG); return buffer.longValue(index); } }
  public boolean readBoolean(long index) { synchronized (monitor) { check(index, Storage.Type.BOOLEAN); return buffer.byteValue(index) != 0; } }
  private void checkBlock(long start, int length, int offset, int count, Storage.Type expected) {
    checkOpen();
    if (type != expected) throw new IllegalArgumentException("Wrong primitive block type");
    if (count < 0 || count > blockValues || start < 0 || start > buffer.count() - count
        || offset < 0 || offset > length - count) throw new IndexOutOfBoundsException("Invalid read block");
  }
  @Override public void readDoubles(long start, double[] target, int offset, int count) {
    synchronized (monitor) {
      checkBlock(start, target.length, offset, count, Storage.Type.DOUBLE);
      for (int i = 0; i < count; i++) target[offset + i] = buffer.doubleValue(start + i);
    }
  }
  @Override public void readFloats(long start, float[] target, int offset, int count) {
    synchronized (monitor) {
      checkBlock(start, target.length, offset, count, Storage.Type.FLOAT);
      for (int i = 0; i < count; i++) target[offset + i] = buffer.floatValue(start + i);
    }
  }
  @Override public void readInts(long start, int[] target, int offset, int count) {
    synchronized (monitor) {
      checkBlock(start, target.length, offset, count, Storage.Type.INTEGER);
      for (int i = 0; i < count; i++) target[offset + i] = buffer.intValue(start + i);
    }
  }
  @Override public void readLongs(long start, long[] target, int offset, int count) {
    synchronized (monitor) {
      checkBlock(start, target.length, offset, count, Storage.Type.LONG);
      for (int i = 0; i < count; i++) target[offset + i] = buffer.longValue(start + i);
    }
  }
  @Override public void readBooleans(long start, boolean[] target, int offset, int count) {
    synchronized (monitor) {
      checkBlock(start, target.length, offset, count, Storage.Type.BOOLEAN);
      for (int i = 0; i < count; i++) target[offset + i] = buffer.byteValue(start + i) != 0;
    }
  }
  public void close() { synchronized (monitor) { closed = true; } }
}
