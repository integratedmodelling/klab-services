package org.integratedmodelling.klab.runtime.storage;

import java.util.NoSuchElementException;
import org.integratedmodelling.klab.api.data.*;

/** Primitive decorators only for constrained coverage. Full rectangles retain the original cursor. */
final class CoveredScanner {
  private CoveredScanner() {}
  static Storage.Scanner unwrap(Storage.Scanner scanner) {
    return scanner instanceof Base base ? base.delegate : scanner;
  }
  static Storage.Scanner wrap(Storage.Scanner scanner, SpatialCoverage coverage) {
    if (coverage == null || coverage.unrestricted) return scanner;
    if (scanner instanceof Storage.DoubleScanner s) return new Doubles(s, coverage);
    if (scanner instanceof Storage.FloatScanner s) return new Floats(s, coverage);
    if (scanner instanceof Storage.IntScanner s) return new Ints(s, coverage);
    if (scanner instanceof Storage.LongScanner s) return new Longs(s, coverage);
    if (scanner instanceof Storage.BooleanScanner s) return new Booleans(s, coverage);
    if (scanner instanceof Storage.KeyScanner<?> s) return new Keys(s, coverage);
    throw new IllegalArgumentException("Unsupported covered scanner " + scanner.getClass());
  }
  private abstract static class Base implements Storage.Scanner {
    final Storage.Scanner delegate;
    final SpatialCoverage coverage;
    final SpatialCoverage.Cursor cursor;
    Base(Storage.Scanner delegate, SpatialCoverage coverage) {
      this.delegate = delegate; this.coverage = coverage; cursor = coverage.new Cursor();
    }
    void normalize() { long old = delegate.position(), next = cursor.next(old); if (old != next) delegate.seek(next); }
    void check() { normalize(); if (!delegate.hasNext()) throw new NoSuchElementException("Covered scan exhausted"); }
    public boolean hasNext() { normalize(); return delegate.hasNext(); }
    public long position() { normalize(); return delegate.position(); }
    public long size() { return delegate.size(); }
    public void seek(long offset) { delegate.seek(offset); normalize(); }
    public long nextLong() { check(); return delegate.nextLong(); }
    public boolean isValid() { check(); return delegate.isValid(); }
    public Storage.Shard shard() { return delegate.shard(); }
    public StorageScan.View view() { return delegate.view(); }
    public void spatialCoordinates(long[] coordinates) { check(); coverage.coordinates(delegate.position(), coordinates); }
    public StorageScan.Cell cell() { check(); return coverage.cell(delegate.position()); }
  }
  private static final class Doubles extends Base implements Storage.DoubleScanner {
    final Storage.DoubleScanner values;
    Doubles(Storage.DoubleScanner values, SpatialCoverage coverage) { super(values, coverage); this.values = values; }
    public double peek() { check(); return values.peek(); }
    public double get() { check(); return values.get(); }
    public void add(double value) { check(); values.add(value); }
  }
  private static final class Floats extends Base implements Storage.FloatScanner {
    final Storage.FloatScanner values;
    Floats(Storage.FloatScanner values, SpatialCoverage coverage) { super(values, coverage); this.values = values; }
    public float peek() { check(); return values.peek(); }
    public float get() { check(); return values.get(); }
    public void add(float value) { check(); values.add(value); }
  }
  private static final class Ints extends Base implements Storage.IntScanner {
    final Storage.IntScanner values;
    Ints(Storage.IntScanner values, SpatialCoverage coverage) { super(values, coverage); this.values = values; }
    public int peek() { check(); return values.peek(); }
    public int get() { check(); return values.get(); }
    public void add(int value) { check(); values.add(value); }
  }
  private static final class Longs extends Base implements Storage.LongScanner {
    final Storage.LongScanner values;
    Longs(Storage.LongScanner values, SpatialCoverage coverage) { super(values, coverage); this.values = values; }
    public long peek() { check(); return values.peek(); }
    public long get() { check(); return values.get(); }
    public void add(long value) { check(); values.add(value); }
  }
  private static final class Booleans extends Base implements Storage.BooleanScanner {
    final Storage.BooleanScanner values;
    Booleans(Storage.BooleanScanner values, SpatialCoverage coverage) { super(values, coverage); this.values = values; }
    public boolean peek() { check(); return values.peek(); }
    public boolean get() { check(); return values.get(); }
    public void add(boolean value) { check(); values.add(value); }
  }
  private static final class Keys extends Base implements Storage.KeyScanner<java.io.Serializable> {
    final Storage.KeyScanner<?> values;
    Keys(Storage.KeyScanner<?> values, SpatialCoverage coverage) { super(values, coverage); this.values = values; }
    public org.integratedmodelling.klab.api.data.mediation.classification.DataKey key() { return values.key(); }
    public java.io.Serializable peek() { check(); return values.peek(); }
    public java.io.Serializable get() { check(); return values.get(); }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void add(java.io.Serializable value) { check(); ((Storage.KeyScanner)values).add(value); }
  }
}
