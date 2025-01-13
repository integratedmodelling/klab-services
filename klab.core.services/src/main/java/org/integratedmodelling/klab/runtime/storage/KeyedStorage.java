package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Offset;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;

/**
 * Base storage providing the general methods. Children enable either boxed I/O or faster native
 * operation (recommended). The runtime makes the choice.
 *
 * @author Ferd
 */
public class KeyedStorage implements Storage {

  private final IntStorage delegate;

  public KeyedStorage(Scale scale, StateStorageImpl scope) {
    this.delegate = new IntStorage(scale, scope);
  }

  /**
   * This should become the default way to set storage. Pass a geometry (equal to the native one or
   * partially covering it, but always in phase) and the desired fill curve, then call add(double)
   * on it.
   *
   * @param bufferGeometry
   * @param fillCurve
   * @return
   */
  public Data.KeyedFiller buffer(Geometry bufferGeometry, Data.FillCurve fillCurve) {
    return null;
  }

  @Override
  public Type getType() {
    return Type.KEYED;
  }

  @Override
  public Histogram getHistogram() {
    return delegate.getHistogram();
  }

  @Deprecated
  public void set(Object value, Offset locator) {}

  @Override
  public long getId() {
    return delegate.getId();
  }
}
