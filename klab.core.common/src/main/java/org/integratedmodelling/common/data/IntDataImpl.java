package org.integratedmodelling.common.data;

import java.util.PrimitiveIterator;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.common.data.Instance;

public class IntDataImpl extends BaseDataImpl implements PrimitiveIterator.OfInt {

  private /*final*/ OfLong iterator;

  public IntDataImpl(Instance instance) {
    super(instance);
//    this.iterator =
//            fillCurve()
//                    .cursor(
//                            GeometryRepository.INSTANCE.get(instance.getGeometry().toString(), Geometry.class));
  }

  public IntDataImpl(Observable observable, Geometry geometry, String name, Instance instance) {
    super(observable, geometry, name, instance);
//    this.iterator = fillCurve().cursor(geometry);
  }

  @Override
  public boolean hasStates() {
    return true;
  }

  @Override
  public int nextInt() {
    return instance.getIntData().get((int) iterator.nextLong());
  }

  @Override
  public boolean hasNext() {
    return iterator.hasNext();
  }
}
