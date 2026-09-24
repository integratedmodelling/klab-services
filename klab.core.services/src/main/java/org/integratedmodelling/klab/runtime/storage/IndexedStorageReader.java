package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Storage;

/** Internal provider boundary: exact native primitives, bounded blocks, no buffers or file paths. */
interface IndexedStorageReader extends AutoCloseable {
  Storage.Type type();
  default org.integratedmodelling.klab.api.data.mediation.classification.DataKey key() { return null; }
  long size();
  int blockValues();
  boolean isValid(long index);
  double readDouble(long index);
  float readFloat(long index);
  int readInt(long index);
  long readLong(long index);
  boolean readBoolean(long index);
  default void readDoubles(long start, double[] target, int offset, int count) {
    block(start, target.length, offset, count);
    for (int i = 0; i < count; i++) target[offset + i] = readDouble(start + i);
  }
  default void readFloats(long start, float[] target, int offset, int count) {
    block(start, target.length, offset, count);
    for (int i = 0; i < count; i++) target[offset + i] = readFloat(start + i);
  }
  default void readInts(long start, int[] target, int offset, int count) {
    block(start, target.length, offset, count);
    for (int i = 0; i < count; i++) target[offset + i] = readInt(start + i);
  }
  default void readLongs(long start, long[] target, int offset, int count) {
    block(start, target.length, offset, count);
    for (int i = 0; i < count; i++) target[offset + i] = readLong(start + i);
  }
  default void readBooleans(long start, boolean[] target, int offset, int count) {
    block(start, target.length, offset, count);
    for (int i = 0; i < count; i++) target[offset + i] = readBoolean(start + i);
  }
  private void block(long start, int length, int offset, int count) {
    if (count < 0 || count > blockValues() || start < 0 || start > size() - count
        || offset < 0 || offset > length - count) throw new IndexOutOfBoundsException("Invalid read block");
  }
  @Override void close();
}
