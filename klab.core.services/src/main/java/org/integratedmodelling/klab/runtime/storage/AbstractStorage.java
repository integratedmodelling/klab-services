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
public abstract class AbstractStorage<B extends AbstractStorage.AbstractBuffer>
    implements Storage<B> {

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
  public abstract class AbstractBuffer implements Buffer {

    private final Data.FillCurve fillCurve;
    private final Persistence persistence;
    private final long size;
    private final long[] offsets;
    private final long id;
    private long internalId;
    private SPDTHistogram<?> histogram;

    protected AbstractBuffer(long size, Data.FillCurve fillCurve, long[] offsets) {
      this.id = stateStorage.nextBufferId();
      this.persistence = Persistence.SERVICE_SHUTDOWN;
      this.size = size;
      this.offsets = offsets;
      this.fillCurve = fillCurve;
      if (stateStorage.isRecordHistogram()) {
        this.histogram = new SPDTHistogram<>(stateStorage.getHistogramBinSize());
      }
    }

    @Override
    public long getId() {
      return id;
    }

    @Override
    public Data.FillCurve fillCurve() {
      return fillCurve;
    }

    @Override
    public Storage.Type dataType() {
      return type;
    }

    @Override
    public long size() {
      return size;
    }

    @Override
    public long[] offsets() {
      return offsets;
    }

    public Persistence getPersistence() {
      return persistence;
    }

    public long getInternalId() {
      return internalId;
    }

    public void setInternalId(long internalId) {
      this.internalId = internalId;
    }

    public SPDTHistogram<?> getHistogram() {
      return histogram;
    }

    public void setHistogram(SPDTHistogram<?> histogram) {
      this.histogram = histogram;
    }

    @Override
    public Persistence persistence() {
      return persistence;
    }

    public Histogram histogram() {
      return this.histogram.asHistogram();
    }

    protected void finalizeStorage() {
      // TODO doing nothing at the moment. Should create images, statistics etc. within the storage
      //  manager based on the fill curve.
    }

    @Override
    public String toString() {
      return "Buffer{"
          + "type="
          + type
          + ", fillCurve="
          + fillCurve
          + ", size="
          + size
          + ", offsets="
          + offsets
          + ", id='"
          + id
          + '\''
          + ", histogram="
          + Utils.Json.asString(histogram.asHistogram())
          + '}';
    }
  }

  protected void registerBuffer(AbstractBuffer buffer) {
    // TODO index geometries, validate
    buffers.add(buffer);
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
}
