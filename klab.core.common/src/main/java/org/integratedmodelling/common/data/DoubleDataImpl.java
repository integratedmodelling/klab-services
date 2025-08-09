package org.integratedmodelling.common.data;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.common.data.Instance;

import java.util.PrimitiveIterator;

public class DoubleDataImpl extends BaseDataImpl implements PrimitiveIterator.OfDouble {

  //  private OfLong iterator;
  private long offset;
  private long index = 0;
  private final long size;

  public DoubleDataImpl(Instance instance, long size, long offset) {
    super(instance);
    this.offset = offset;
    this.size = size;
  }

  //  public DoubleDataImpl(Observable observable, Geometry geometry, String name, Instance
  // instance) {
  //    super(observable, geometry, name, instance);
  //    //    this.iterator = fillCurve().cursor(geometry);
  //  }

  @Override
  public boolean hasStates() {
    return true;
  }

  @Override
  public double nextDouble() {
    index++;
    return instance.getDoubleData().get((int) offset++);
  }

  @Override
  public boolean hasNext() {
    return index < size;
  }
}
