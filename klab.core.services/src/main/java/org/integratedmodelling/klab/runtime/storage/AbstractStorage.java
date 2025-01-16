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
import org.integratedmodelling.klab.utilities.Utils;

/**
 * Abstract storage class providing geometry and buffer indexing, histograms, merging and splitting.
 */
abstract class AbstractStorage<B extends AbstractStorage.AbstractBuffer> implements Storage<B> {

  protected final Type type;
  protected final StateStorageImpl stateStorage;
  protected final Observation observation;
  protected final Geometry geometry;
  protected final ServiceContextScope contextScope;
  List<AbstractBuffer> buffers = new ArrayList<>();

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

  /**
   * Retrieve the merged histogram. TODO we should cache if the owning state is finalized.
   *
   * @return
   */
  public SPDTHistogram<?> histogram() {
    if (buffers.size() == 1) {
      return buffers.getFirst().histogram;
    } else if (buffers.size() > 1) {
      SPDTHistogram ret = new SPDTHistogram<>(20);
      for (var buffer : buffers) {
        if (buffer.histogram != null) {
          ret.merge(buffer.histogram);
        }
      }
      // TODO cache if storage is finalized
      return ret;
    }
    return new SPDTHistogram<>(20);
  }

  /** Base buffer provides the histogram and the geometry indexing/merging */
  protected abstract class AbstractBuffer implements Buffer {

    final Data.FillCurve fillCurve;
    final Geometry geometry;
    final Persistence persistence;
    final String id;
    SPDTHistogram<?> histogram;

    protected AbstractBuffer(Geometry geometry, Data.FillCurve fillCurve) {
      this.id = Utils.Names.shortUUID();
      this.persistence = Persistence.SERVICE_SHUTDOWN;
      this.geometry = geometry;
      this.fillCurve = fillCurve;
      if (stateStorage.isRecordHistogram()) {
        this.histogram = new SPDTHistogram<>(stateStorage.getHistogramBinSize());
      }
    }

    @Override
    public Data.FillCurve fillCurve() {
      return fillCurve;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public Persistence persistence() {
      return persistence;
    }

    protected void finalizeStorage() {
      contextScope.registerDataBuffer(observation, this);
    }

    @Override
    public String toString() {
      return "AbstractBuffer{"
          + "geometry="
          + geometry
          + ", fillCurve="
          + fillCurve
          + ", id='"
          + id
          + '\''
          + ", histogram="
          + Utils.Json.printAsJson(histogram.asHistogram())
          + '}';
    }
  }

  protected void registerBuffer(AbstractBuffer buffer) {
    // TODO index geometries, validate
    buffers.add(buffer);
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
}
