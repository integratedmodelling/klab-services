package org.integratedmodelling.klab.runtime.storage;

import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.layout.LogLinearLayout;
import com.dynatrace.dynahist.layout.LogOptimalLayout;
import com.dynatrace.dynahist.layout.OpenTelemetryExponentialBucketsLayout;
import org.integratedmodelling.klab.api.Klab;
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

import java.util.UUID;

/**
 * Base buffer provides the histogram and the geometry indexing/merging.
 *
 * <p>FIXME this ends up in the knowledge graph but it's not serializable, so it will create
 * problems at client side. Must externalize the data buffers and all non-serializable information
 * to the StorageManager. This class should keep the serialized k.LAB histogram and the raw geometry
 * while the storage manager must manage the data buffer and true histogram and keep a reference
 * (also serializable) to its URN. The SM must also have low-priority threads to maintain and
 * restore the buffers as persistent storage when the persistence strategy requires it.
 */
public class ShardImpl implements Storage.Shard {

  private final Persistence persistence;
  private final Data.ShardingStrategy shardingStrategy;
  private final int shardIndex;
  private final long timestamp;
  private final Geometry geometry; // TODO must be a real Geometry, not a scale
  private long id; // for reference in the knowledge graph
  private final String urn; // for persistent reference in the storage manager
  private long transientId = Klab.getNextId();
  private long parentTransientId; // manage
  private long parentId = -1; // TODO manage
  private Histogram histogram;
  private Storage.Type nativeType;

  public ShardImpl() {
    geometry = null;
    shardingStrategy = null;
    shardIndex = -1;
    timestamp = -1;
    urn = null;
    persistence = null;
  }

  /**
   * @param geometry MUST be an actual Geometry, not a Scale.
   * @param observation
   * @param shardingStrategy
   * @param shardIndex
   * @param timestamp
   * @param persistence
   */
  protected ShardImpl(
      Geometry geometry,
      Observation observation,
      Data.ShardingStrategy shardingStrategy,
      int shardIndex,
      long timestamp,
      Persistence persistence,
      Storage.Type dataType) {
    this.geometry = geometry;
    this.shardingStrategy = shardingStrategy;
    this.shardIndex = shardIndex;
    this.timestamp = timestamp;
    this.urn = Utils.Names.fastName();
    this.persistence = persistence;
    this.nativeType = dataType;
  }

  public static ShardImpl trivial(Storage.Type dataType) {
    throw new KlabUnimplementedException("trivial shards are not yet supported");
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
  public int getChildrenCount() {
    return 0;
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
    return this.histogram;
  }

  @Override
  public long getParentTransientId() {
    return parentTransientId;
  }

  public void setParentTransientId(long parentTransientId) {
    this.parentTransientId = parentTransientId;
  }

  public String getUrn() {
    return urn;
  }

  @Override
  public Storage.Type getNativeType() {
    return nativeType;
  }

  public void setNativeType(Storage.Type nativeType) {
    this.nativeType = nativeType;
  }

  public long getTimestamp() {
    return timestamp;
  }

  @Override
  public long getParentId() {
    return parentId;
  }

  public void setParentId(long parentId) {
    this.parentId = parentId;
  }

  // TODO revise - this must be the serializable k.LAB histogram, which implies it cannot be merged
  //  or anything. Yet the KG does not have a proxy for the storage @geometry.
  public void setHistogram(Histogram histogram) {
    this.histogram = histogram;
  }
}
