package org.integratedmodelling.klab.runtime.storage;

import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.data.histogram.SPDTHistogram;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/**
 * Abstract storage class providing geometry and buffer indexing, histograms, merging and splitting.
 */
public abstract class AbstractStorage<B extends AbstractBuffer> implements Storage<B> {

  protected final Type type;
  protected final StateStorageImpl stateStorage;
  protected final Observation observation;
  protected final Geometry geometry;
  protected final ServiceContextScope contextScope;
  protected Persistence persistence;
  private List<AbstractBuffer> buffers = new ArrayList<>();
  protected Data.SpaceFillingCurve spaceFillingCurve;

  protected AbstractStorage(
      Type type,
      Observation observation,
      StateStorageImpl stateStorage,
      ServiceContextScope contextScope) {
    this.type = type;
    this.stateStorage = stateStorage;
    this.observation = observation;
    this.geometry = observation.getGeometry();
    this.contextScope = contextScope;
  }

  @Override
  public Data.SpaceFillingCurve spaceFillCurve() {
    return spaceFillingCurve;
  }
  /**
   * Retrieve the merged histogram. TODO we should cache if the owning state is finalized.
   *
   * @return
   */
  public SPDTHistogram<?> histogram() {
    //    if (buffers.size() == 1) {
    //      return buffers.getFirst().histogram;
    //    } else if (buffers.size() > 1) {
    //      SPDTHistogram ret = new SPDTHistogram<>(20);
    //      for (var buffer : buffers) {
    //        if (buffer.histogram != null) {
    //          ret.merge(buffer.histogram);
    //        }
    //      }
    //      // TODO cache if storage is finalized
    //      return ret;
    //    }
    return new SPDTHistogram<>(20);
  }

  protected void registerBuffer(AbstractBuffer buffer) {
    // TODO index geometries, validate
    buffers.add(buffer);
  }

  @Override
  public Persistence persistence() {
    return persistence;
  }

  @Override
  public List<Storage.Buffer> buffers() {
    // hope this gets optimized
    return buffers.stream().map(b -> (Storage.Buffer) b).toList();
  }

  @Override
  public Type getType() {
    return this.type;
  }

  @Override
  public Geometry getGeometry() {
    return this.geometry;
  }

  @Override
  public Histogram getHistogram() {
    return histogram().asHistogram();
  }

  @Override
  public long getId() {
    return 0;
  }

  public Data.SpaceFillingCurve getFillCurve() {
    return spaceFillingCurve;
  }
}
