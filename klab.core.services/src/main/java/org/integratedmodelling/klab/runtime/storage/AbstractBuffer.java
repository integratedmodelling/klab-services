package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.CursorImpl;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.data.histogram.SPDTHistogram;
import org.integratedmodelling.klab.utilities.Utils;

/** Base buffer provides the histogram and the geometry indexing/merging */
public abstract class AbstractBuffer extends CursorImpl implements Storage.Buffer {

  private final Data.SpaceFillingCurve spaceFillingCurve;
  private final Persistence persistence;
  private final Storage.Type dataType;
  private final long size;
  private final long offset;
  private final long id;
  private final AbstractStorage<?> storage;
  private long internalId;
  private SPDTHistogram<?> histogram;

    /**
     *
     * @param geometry The <em>overall</em> geometry for the buffer
     * @param stateStorage
     * @param size
     * @param spaceFillingCurve
     * @param offsets extent-based offsets with the start offset in the
     */
  protected AbstractBuffer(
      Geometry geometry,
      AbstractStorage<?> stateStorage,
      long size,
      Data.SpaceFillingCurve spaceFillingCurve,
      long offsets) {
    super(geometry, spaceFillingCurve);
    this.storage = stateStorage;
    this.dataType = stateStorage.getType();
    this.id = stateStorage.stateStorage.nextBufferId();
    this.persistence = Persistence.SERVICE_SHUTDOWN;
    this.size = size;
    this.offset = offsets;
    this.spaceFillingCurve = spaceFillingCurve;
    if (stateStorage.stateStorage.isRecordHistogram()) {
      this.histogram = new SPDTHistogram<>(stateStorage.stateStorage.getHistogramBinSize());
    }
  }

  @Override
  public long getId() {
    return id;
  }

  //    @Override
  //    public Data.SpaceFillingCurve fillCurve() {
  //        return spaceFillingCurve;
  //    }
  //
  //    @Override
  //    public Storage.Type dataType() {
  //        return storage.type;
  //    }

  @Override
  public long size() {
    return size;
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

  public SPDTHistogram<?> getHistogram() {
    return histogram;
  }

  public void setHistogram(SPDTHistogram<?> histogram) {
    this.histogram = histogram;
  }

  public Histogram histogram() {
    return this.histogram.asHistogram();
  }

  //    protected void finalizeStorage() {
  //      // TODO doing nothing at the moment. Should create images, statistics etc. within the
  // storage
  //      //  manager based on the fill curve.
  //    }

  /**
   * Used to support the non-boxing parameters in contextualizers and data adapters
   *
   * @param bufferClass
   * @return
   */
  public static Class<? extends Storage<?>> getStorageClass(Class<?> bufferClass) {
      if (DoubleBuffer.class.isAssignableFrom(bufferClass)) {
        return DoubleStorage.class;
      }
      // TODO
      throw new KlabInternalErrorException("Wrong buffer class in AbstractBuffer::getStorageClass");
  }

  @Override
  public String toString() {
    return "Buffer{"
        + "fillCurve="
        + spaceFillingCurve
        + ", size="
        + size
        + ", offset="
        + offset
        + ", id='"
        + id
        + '\''
        + ", histogram="
        + Utils.Json.asString(histogram.asHistogram())
        + '}';
  }
}
