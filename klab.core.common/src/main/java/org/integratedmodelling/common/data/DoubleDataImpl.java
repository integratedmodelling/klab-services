package org.integratedmodelling.common.data;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.common.data.Instance;

import java.util.PrimitiveIterator;

public class DoubleDataImpl extends BaseDataImpl implements PrimitiveIterator.OfDouble {

  public DoubleDataImpl(Observable observable, Geometry geometry, String name, Instance instance) {
    super(observable, geometry, name, instance);
  }

  @Override
  public boolean hasStates() {
    return true;
  }

  @Override
  public double nextDouble() {
    return 0;
  }

  @Override
  public boolean hasNext() {
    return false;
  }
}
