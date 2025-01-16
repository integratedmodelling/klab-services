package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Offset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

public class KeyedStorage extends AbstractStorage<KeyedStorage.KeyedBuffer> {

  public KeyedStorage(
      Observation observation, StateStorageImpl scope, ServiceContextScope contextScope) {
    super(Type.KEYED, observation, scope, contextScope);
  }

  @Override
  public KeyedBuffer buffer(Geometry geometry, Data.FillCurve fillCurve) {
    return null;
  }

  public class KeyedBuffer extends AbstractStorage.AbstractBuffer {

    protected KeyedBuffer(Geometry geometry, Data.FillCurve fillCurve) {
      super(geometry, fillCurve);
    }

    @Override
    public <T extends Data.Filler> T filler(Class<T> fillerClass) {
      return null;
    }
  }
}
