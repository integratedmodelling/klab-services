package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Storage;

/** Source support gates validity before remapping/resampling; payload layout remains dense. */
final class CoverageReader implements IndexedStorageReader {
  private final IndexedStorageReader delegate;
  private final SpatialCoverage.Cursor spans;
  private long excludedStart = -1, excludedEnd = -1;
  CoverageReader(IndexedStorageReader delegate, SpatialCoverage coverage) {
    this.delegate = delegate; spans = coverage.new Cursor();
  }
  private boolean covered(long index) {
    if (index < 0 || index >= size()) throw new IndexOutOfBoundsException("Source offset " + index);
    if (index >= spans.acceptedStart && index < spans.acceptedEnd) return true;
    if (index >= excludedStart && index < excludedEnd) return false;
    long next = spans.next(index);
    if (next != index) { excludedStart = index; excludedEnd = next; }
    return next == index;
  }
  public Storage.Type type() { return delegate.type(); }
  public org.integratedmodelling.klab.api.data.mediation.classification.DataKey key() { return delegate.key(); }
  public long size() { return delegate.size(); }
  public int blockValues() { return delegate.blockValues(); }
  public boolean isValid(long index) { return covered(index) && delegate.isValid(index); }
  public double readDouble(long index) { return covered(index) ? delegate.readDouble(index) : Double.NaN; }
  public float readFloat(long index) { return covered(index) ? delegate.readFloat(index) : Float.NaN; }
  public int readInt(long index) { return covered(index) ? delegate.readInt(index) : Integer.MIN_VALUE; }
  public long readLong(long index) { return covered(index) ? delegate.readLong(index) : Long.MIN_VALUE; }
  public boolean readBoolean(long index) { return covered(index) && delegate.readBoolean(index); }
  public void close() { delegate.close(); }
}
