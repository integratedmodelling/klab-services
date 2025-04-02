package org.integratedmodelling.klab.runtime.storage;

import com.dynatrace.dynahist.layout.Layout;
import org.integratedmodelling.klab.api.data.CursorImpl;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.utilities.Utils;

/** Base buffer provides the histogram and the geometry indexing/merging */
public abstract class BufferImpl extends CursorImpl implements Storage.Buffer {

  private final Data.SpaceFillingCurve spaceFillingCurve;
  private final Persistence persistence;
  private final Storage.Type dataType;
  private final long offset;
  private long id; // for reference in the knowledge graoh
  private final String urn; // for persistent reference in storage manager
  private final StorageImpl storage;
  private final long timestamp;
  protected com.dynatrace.dynahist.Histogram histogram;

  /**
   * @param geometry The <em>overall</em> geometry for the buffer
   * @param stateStorage
   * @param size
   * @param spaceFillingCurve
   * @param offsets extent-based offsets with the start offset in the
   */
  protected BufferImpl(
      Geometry geometry,
      Observation observation,
      StorageImpl stateStorage,
      long size,
      Data.SpaceFillingCurve spaceFillingCurve,
      long offsets,
      long timestamp) {
    super(geometry, spaceFillingCurve);
    this.storage = stateStorage;
    this.timestamp = timestamp;
    this.dataType = stateStorage.getType();
    this.urn = observation.getUrn() + "#" + stateStorage.stateStorage.nextBufferId();
    this.persistence = Persistence.SERVICE_SHUTDOWN;
    this.offset = offsets;
    this.spaceFillingCurve = spaceFillingCurve;
    if (stateStorage.stateStorage.isRecordHistogram()) {
      this.histogram =
          com.dynatrace.dynahist.Histogram.createDynamic(
              histogramLayout(observation.getObservable()));
    }
  }

  protected abstract Layout histogramLayout(Observable observable);

  @Override
  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  @Override
  public long size() {
    return multiplicity;
  }

  @Override
  public long offset() {
    return offset;
  }

  public Storage.Type getDataType() {
    return dataType;
  }

  public Data.SpaceFillingCurve getFillingCurve() {
    return spaceFillingCurve;
  }

  public Persistence getPersistence() {
    return persistence;
  }

  @Override
  public String getUrn() {
    return urn;
  }

  @Override
  public long getTimestamp() {
    return timestamp;
  }

  //  public long getInternalId() {
  //    return internalId;
  //  }

  //  public void setInternalId(long internalId) {
  //    this.internalId = internalId;
  //  }

  public Histogram histogram() {
    return Utils.Data.adaptHistogram(this.histogram);
  }

  @Override
  public String toString() {
    return "Buffer{"
        + "fillCurve="
        + spaceFillingCurve
        + ", size="
        + multiplicity
        + ", offset="
        + offset
        + ", id='"
        + id
        + '\''
        + ", histogram="
        + Utils.Json.asString(histogram())
        + '}';
  }
}
