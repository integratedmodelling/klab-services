package org.integratedmodelling.klab.runtime.storage;

import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.layout.LogLinearLayout;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.data.CursorImpl;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.utilities.Utils;
import org.ojalgo.array.BufferArray;

/** Base buffer provides the histogram and the geometry indexing/merging */
public class ShardImpl extends CursorImpl implements Storage.Shard {

  private final Persistence persistence;
  private final Data.ShardingStrategy shardingStrategy;
  private final int shardIndex;
  private final long timestamp;
  private final Geometry geometry;
  private long id; // for reference in the knowledge graoh
  private final String urn; // for persistent reference in storage manager
  private final StorageManagerImpl storage;
  protected com.dynatrace.dynahist.Histogram histogram;
  private long transientId = Klab.getNextId();
  private final BufferArray data;

  /**
   * @param geometry
   * @param observation
   * @param stateStorage
   */
  protected ShardImpl(
      Geometry geometry,
      Observation observation,
      Data.ShardingStrategy shardingStrategy,
      int shardIndex,
      long timestamp,
      StorageManagerImpl stateStorage,
      Persistence persistence) {
    this.geometry = geometry;
    this.storage = stateStorage;
    this.shardingStrategy = shardingStrategy;
    this.shardIndex = shardIndex;
    this.timestamp = timestamp;
    this.urn = observation.getUrn() + "#" + stateStorage.nextBufferId();
    this.persistence = persistence;
    if (stateStorage.isRecordHistogram()) {
      this.histogram =
          com.dynatrace.dynahist.Histogram.createDynamic(
              histogramLayout(observation.getObservable()));
    }
    this.data =
        switch (shardingStrategy.getDataType()) {
          case DOUBLE -> stateStorage.getDoubleBuffer(geometry.size());
          case FLOAT -> stateStorage.getFloatBuffer(geometry.size());
          // TODO use size/int32.size for booleans and adapt the scanners
          case INTEGER, KEYED, BOOLEAN -> stateStorage.getIntBuffer(geometry.size());
          case LONG -> stateStorage.getLongBuffer(geometry.size());
        };
  }

  public static ShardImpl trivial(Storage.Type dataType) {
    throw new KlabUnimplementedException("trivial shards are not yet supported");
  }

  private Layout histogramLayout(Observable observable) {
    // TODO use sensible types and values for the observable
    return LogLinearLayout.create(Double.MIN_NORMAL, 0, Double.MIN_VALUE, Double.MAX_VALUE);
  }

  @Override
  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  @Override
  public long getTransientId() {
    return transientId;
  }

  @Override
  public Type classify() {
    return Type.DATA;
  }

  public void setTransientId(long transientId) {
    this.transientId = transientId;
  }

  public Persistence getPersistence() {
    return persistence;
  }

  @Override
  public Geometry getGeometry() {
    return geometry;
  }

  @Override
  public Data.ShardingStrategy getShardingStrategy() {
    return shardingStrategy;
  }

  @Override
  public int getShardIndex() {
    return shardIndex;
  }

  @Override
  public Histogram getHistogram() {
    return Utils.Data.adaptHistogram(this.histogram);
  }

  public String getUrn() {
    return urn;
  }

  public long getTimestamp() {
    return timestamp;
  }
}
