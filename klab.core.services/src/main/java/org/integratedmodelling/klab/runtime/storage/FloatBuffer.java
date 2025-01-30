package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.ojalgo.array.BufferArray;

import java.util.PrimitiveIterator;

public class FloatBuffer extends AbstractStorage.AbstractBuffer {

  private final FloatStorage floatStorage;
  private final BufferArray data;

  protected FloatBuffer(
      FloatStorage floatStorage, long size, Data.FillCurve fillCurve, long[] offsets) {
    super(floatStorage, size, fillCurve, offsets);
    this.floatStorage = floatStorage;
    this.data = floatStorage.stateStorage.getFloatBuffer(floatStorage.geometry.size());
  }

  @Override
  public <T extends Data.Filler> T filler(Class<T> fillerClass) {

    final PrimitiveIterator.OfLong iterator = fillCurve().cursor(floatStorage.geometry);

    if (fillerClass == Data.DoubleFiller.class) {
      return (T)
          new Data.DoubleFiller() {

            @Override
            public void add(double value) {
              data.add(iterator.nextLong(), value);
              if (getHistogram() != null) {
                getHistogram().insert((double) value);
              }
              //                if (!iterator.hasNext()) {
              //                  finalizeStorage();
              //                }
            }
          };
    } else if (fillerClass == Data.IntFiller.class) {
      return (T)
          new Data.IntFiller() {

            @Override
            public void add(int value) {
              data.add(iterator.nextLong(), (double) value);
              if (getHistogram() != null) {
                getHistogram().insert((double) value);
              }
              //                if (!iterator.hasNext()) {
              //                  finalizeStorage();
              //                }
            }
          };
    } else if (fillerClass == Data.LongFiller.class) {
      return (T)
          new Data.LongFiller() {

            @Override
            public void add(long value) {
              data.add(iterator.nextLong(), (double) value);
              if (getHistogram() != null) {
                getHistogram().insert((double) value);
              }
              //                if (!iterator.hasNext()) {
              //                  finalizeStorage();
              //                }
            }
          };
    } else if (fillerClass == Data.FloatFiller.class) {
      return (T)
          new Data.FloatFiller() {

            @Override
            public void add(float value) {
              data.add(iterator.nextLong(), value);
              if (getHistogram() != null) {
                getHistogram().insert((double) value);
              }
              //                if (!iterator.hasNext()) {
              //                  finalizeStorage();
              //                }
            }
          };
    }

    throw new KlabIllegalStateException("Unexpected filler type requested for buffer");
  }
}
