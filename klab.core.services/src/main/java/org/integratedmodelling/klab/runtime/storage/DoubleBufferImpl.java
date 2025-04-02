package org.integratedmodelling.klab.runtime.storage;

import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.layout.OpenTelemetryExponentialBucketsLayout;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.ojalgo.array.BufferArray;

public class DoubleBufferImpl extends BufferImpl implements Storage.DoubleBuffer {

  private final BufferArray data;

  protected DoubleBufferImpl(
      Geometry geometry,
      Observation observation,
      StorageImpl doubleStorage,
      long size,
      Data.SpaceFillingCurve spaceFillingCurve,
      long offsets,
      long timestamp) {
    super(geometry, observation, doubleStorage, size, spaceFillingCurve, offsets, timestamp);
    this.data = doubleStorage.stateStorage.getDoubleBuffer(doubleStorage.geometry.size());
  }

  public BufferArray data() {
    return data;
  }

  @Override
  protected Layout histogramLayout(Observable observable) {
    // TODO if the observable has numeric boundaries, use those for a predefined layout
    return OpenTelemetryExponentialBucketsLayout.create(10);
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
          histogram.addValue(value);
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
  public double get(long offset) {
    return 0;
  }

  @Override
  public void set(double value, long offset) {
    data.add(offset, value);
  }

  @Override
  public void fill(double value) {
    data.fillAll(value);
  }
}
