package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/**
 * Base storage providing the general methods. Children enable either boxed I/O or faster native
 * operation (recommended). The runtime makes the choice.
 *
 * @author Ferd
 */
public class BooleanStorage extends AbstractStorage<BooleanBuffer> {

    public BooleanStorage(
      Observation observation, StateStorageImpl scope, ServiceContextScope contextScope) {
    super(Type.BOOLEAN, observation, scope, contextScope);
  }

  @Override
  public BooleanBuffer buffer(long size, Data.FillCurve fillCurve, long[] offsets) {
    var ret = new BooleanBuffer(this, size, fillCurve, offsets);
    registerBuffer(ret);
    return ret;
  }
}
