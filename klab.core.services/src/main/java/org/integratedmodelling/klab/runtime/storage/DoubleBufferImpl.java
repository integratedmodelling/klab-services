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

  @Override
  public double get() {
    return this.data.get(next ++);
  }

  @Override
  public double peek() {
    return this.data.get(next);
  }

  @Override
  public void add(double value) {
    if (histogram != null) {
      histogram.insert(value);
    }
    data.add(next++, value);
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
