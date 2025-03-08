package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.ojalgo.array.BufferArray;

public class DoubleBufferImpl extends BufferImpl implements Storage.DoubleBuffer {

  private final BufferArray data;

  protected DoubleBufferImpl(Geometry geometry,
                             StorageImpl doubleStorage, long size, Data.SpaceFillingCurve spaceFillingCurve, long offsets) {
    super(geometry, doubleStorage, size, spaceFillingCurve, offsets);
    this.data = doubleStorage.stateStorage.getDoubleBuffer(doubleStorage.geometry.size());
  }

  public BufferArray data() {
    return data;
  }

  public DoubleScanner scan() {
    return new DoubleScanner() {

      long next = 0L;

      @Override
      public double get() {
        return data.get(next++);
      }

      @Override
      public double peek() {
        return data.get(next);
      }

      @Override
      public void add(double value) {
        if (histogram != null) {
          histogram.insert(value);
        }
        data.set(next++, value);
      }

      @Override
      public long nextLong() {
        return next++;
      }

      @Override
      public boolean hasNext() {
        return next < multiplicity;
      }
    };
  }

  @Override
  public double get(long offset) { return 0; }

  @Override
  public void set(double value, long offset) {
    data.add(offset, value);
  }

  @Override
  public void fill(double value) {
    data.fillAll(value);
  }

}
