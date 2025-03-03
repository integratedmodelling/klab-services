package org.integratedmodelling.common.data;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.common.data.Instance;

import java.util.PrimitiveIterator;

public class DoubleDataImpl extends BaseDataImpl implements PrimitiveIterator.OfDouble {

  private /*final*/ OfLong iterator;

  public DoubleDataImpl(Instance instance) {
    super(instance);
//    this.iterator =
//        fillCurve()
//            .cursor(
//                GeometryRepository.INSTANCE.get(instance.getGeometry().toString(), Geometry.class));
  }

  public DoubleDataImpl(Observable observable, Geometry geometry, String name, Instance instance) {
    super(observable, geometry, name, instance);
//    this.iterator = fillCurve().cursor(geometry);
  }

  @Override
  public boolean hasStates() {
    return true;
  }

  @Override
  public double nextDouble() {
    return instance.getDoubleData().get((int) iterator.nextLong());
  }

  @Override
  public boolean hasNext() {
    return iterator.hasNext();
  }
}
