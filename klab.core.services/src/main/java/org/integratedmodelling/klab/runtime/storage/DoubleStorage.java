package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

import java.util.Collection;
import java.util.List;

/**
 * Base storage providing the general methods. Children enable either boxed I/O or faster native
 * operation (recommended). The runtime makes the choice.
 *
 * @author Ferd
 */
public class DoubleStorage extends AbstractStorage<DoubleBuffer> {

  public DoubleStorage(
      Observation observation, StateStorageImpl scope, ServiceContextScope contextScope) {
    super(Type.DOUBLE, observation, scope, contextScope);
  }

  @Override
  public <T extends Buffer> List<T> buffers(
          Geometry geometry, Class<T> bufferClass, Collection<Annotation> annotations) {
    // TODO honor the split instructions in the observation or use defaults
    return List.of();
  }

  //  @Override
  //  public DoubleBuffer buffer(long size, Data.FillCurve fillCurve, long[] offsets) {
  //    var ret = new DoubleBuffer(this, size, fillCurve, offsets);
  //    registerBuffer(ret);
  //    return ret;
  //  }
}
