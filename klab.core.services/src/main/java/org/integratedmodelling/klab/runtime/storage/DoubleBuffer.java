package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Data;
import org.ojalgo.array.BufferArray;

public class DoubleBuffer extends AbstractBuffer {

//  private final DoubleStorage doubleStorage;
  private final BufferArray data;

  protected DoubleBuffer(
          DoubleStorage doubleStorage, long size, Data.SpaceFillingCurve spaceFillingCurve, long[] offsets) {
    super(doubleStorage, size, spaceFillingCurve, offsets);
//    this.doubleStorage = doubleStorage;
    this.data = doubleStorage.stateStorage.getDoubleBuffer(doubleStorage.geometry.size());
  }

  public BufferArray data() {
    return data;
  }

  /**
   * Return the value at the current offset in the iterator and advance the iteration.
   *
   * @return
   */
  public double get() {
    return this.data.get(next ++);
  }

  /**
   * Return the value at the current offset in the iterator without advancing the iteration.
   *
   * @return
   */
  public double peek() {
    return this.data.get(next);
  }

  /**
   * Set the value at the current iterable offset and advance the iteration. Do not use after get()!
   *
   * @param value
   */
  public void add(double value) {
    data.add(next++, value);
  }

  /**
   * Random access value. The offset is according to the overall fill curve and buffer-specific
   * offsets.
   *
   * @param offset
   */
  public void get(long offset) {}

  /**
   * Random value set. May be inefficient. Offset as in {@link #get(long)}.
   *
   * @param value
   * @param offset
   */
  public void set(double value, long offset) {
    data.add(offset, value);
  }

}
