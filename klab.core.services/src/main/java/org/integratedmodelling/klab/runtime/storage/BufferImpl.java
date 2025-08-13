package org.integratedmodelling.klab.runtime.storage;

import com.dynatrace.dynahist.layout.Layout;
import org.integratedmodelling.klab.api.Klab;
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

  private final Data.FillCurve fillCurve;
  private final Persistence persistence;
  private final Storage.Type dataType;
  protected final long offset;
  private long id; // for reference in the knowledge graoh
  private final String urn; // for persistent reference in storage manager
  private final StorageImplObsolete storage;
  private final long timestamp;
  protected com.dynatrace.dynahist.Histogram histogram;
  private long transientId = Klab.getNextId();

  /**
   * @param geometry The <em>overall</em> geometry for the buffer
   * @param stateStorage
   * @param size
   * @param fillCurve
   * @param offset extent-based offsets with the start offset in the storage
   */
  protected BufferImpl(
      Geometry geometry,
      Observation observation,
      StorageImplObsolete stateStorage,
      long size,
      Data.FillCurve fillCurve,
      long offset,
      long timestamp) {
//    super(geometry, spaceFillingCurve);
    this.storage = stateStorage;
    this.timestamp = timestamp;
    // NAAAH
    this.dataType = stateStorage.getNativeType();
    this.urn = observation.getUrn() + "#" + stateStorage.stateStorage.nextBufferId();
    this.persistence = Persistence.SERVICE_SHUTDOWN;
    this.offset = offset;
    this.fillCurve = fillCurve;
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

//  @Override
  public long size() {
    return multiplicity;
  }

//  @Override
  public long offset() {
    return offset;
  }

  public Storage.Type getDataType() {
    return dataType;
  }

  @Override
  public long getTransientId() {
    return transientId;
  }

  public void setTransientId(long transientId) {
    this.transientId = transientId;
  }

  public Data.FillCurve getFillingCurve() {
    return fillCurve;
  }

  public Persistence getPersistence() {
    return persistence;
  }

//  @Override
  public String getUrn() {
    return urn;
  }

//  @Override
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
        + fillCurve
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
