package org.integratedmodelling.common.data;

import java.util.PrimitiveIterator;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.common.data.Instance;

public class LongDataImpl extends BaseDataImpl implements PrimitiveIterator.OfLong {

  private /*final*/ OfLong iterator;

  public LongDataImpl(Instance instance) {
    super(instance);
//    this.iterator =
//            fillCurve()
//                    .cursor(
//                            GeometryRepository.INSTANCE.get(instance.getGeometry().toString(), Geometry.class));
  }

  public LongDataImpl(Observable observable, Geometry geometry, String name, Instance instance) {
    super(observable, geometry, name, instance);
//    this.iterator = fillCurve().cursor(geometry);
  }

  @Override
  public boolean hasStates() {
    return true;
  }

  @Override
  public long nextLong() {
    return instance.getLongData().get((int) iterator.nextLong());
  }

  @Override
  public boolean hasNext() {
    return iterator.hasNext();
  }
}
