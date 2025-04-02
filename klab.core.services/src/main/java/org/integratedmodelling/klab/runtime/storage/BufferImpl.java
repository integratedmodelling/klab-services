package org.integratedmodelling.klab.runtime.storage;

import com.dynatrace.dynahist.layout.Layout;
import org.integratedmodelling.klab.api.data.CursorImpl;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.utilities.Utils;

/** Base buffer provides the histogram and the geometry indexing/merging */
public abstract class BufferImpl extends CursorImpl implements Storage.Buffer {

  private final Data.SpaceFillingCurve spaceFillingCurve;
  private final Persistence persistence;
  private final Storage.Type dataType;
  private final long offset;
  private long id;
  private final StorageImpl storage;
  private long internalId;
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
      Observable observable,
      StorageImpl stateStorage,
      long size,
      Data.SpaceFillingCurve spaceFillingCurve,
      long offsets) {
    super(geometry, spaceFillingCurve);
    this.storage = stateStorage;
    this.dataType = stateStorage.getType();
    // TODO make this an immutable URN, the ID is managed by the knowledge graph as RuntimeAsset
    this.id = stateStorage.stateStorage.nextBufferId();
    this.persistence = Persistence.SERVICE_SHUTDOWN;
    this.offset = offsets;
    this.spaceFillingCurve = spaceFillingCurve;
    if (stateStorage.stateStorage.isRecordHistogram()) {
      this.histogram = com.dynatrace.dynahist.Histogram.createDynamic(histogramLayout(observable));
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

  public long getInternalId() {
    return internalId;
  }

  public void setInternalId(long internalId) {
    this.internalId = internalId;
  }

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
