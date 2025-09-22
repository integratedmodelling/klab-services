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

/** Base buffer provides the histogram and the geometry indexing/merging */
public class ShardImpl /*extends CursorImpl*/ implements Storage.Shard {

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
  private long parentTransientId;
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
    return OpenTelemetryExponentialBucketsLayout.create(10);
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
    return Utils.Data.adaptHistogram(this.histogram);
  }

  @Override
  public Storage.Scanner getNativeScanner() {
    return switch (shardingStrategy.getDataType()) {
      case DOUBLE -> new LocalDoubleScanner();
      case FLOAT -> new LocalFloatScanner();
      case INTEGER, KEYED, BOOLEAN -> new LocalIntScanner(); // TODO needs to implement KEYED
      case LONG -> new LocalLongScanner();
    };
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

  public long getTimestamp() {
    return timestamp;
  }

  class BaseScanner implements Storage.Scanner {

    protected long size = geometry.size();
    long index = 0L;

    @Override
    public Storage.Shard shard() {
      return ShardImpl.this;
    }

    @Override
    public long size() {
      return size;
    }

    @Override
    public long nextLong() {
      return index++;
    }

    @Override
    public boolean hasNext() {
      return index < size;
    }
  }

  /* TODO handle the histogram */
  class LocalDoubleScanner extends BaseScanner implements Storage.DoubleScanner {

    @Override
    public double get() {
      return data.get(index++);
    }

    @Override
    public double peek() {
      return data.get(index);
    }

    @Override
    public void add(double value) {
      if (histogram != null) {
        histogram.addValue(value);
      }
      data.set(index++, value);
    }
  }

  class LocalFloatScanner extends BaseScanner implements Storage.FloatScanner {

    @Override
    public float get() {
      return data.get(index++).floatValue();
    }

    @Override
    public float peek() {
      return data.get(index).floatValue();
    }

    @Override
    public void add(float value) {
      if (histogram != null) {
        histogram.addValue(value);
      }
      data.set(index++, value);
    }
  }

  class LocalIntScanner extends BaseScanner implements Storage.IntScanner {

    @Override
    public int get() {
      return data.get(index++).intValue();
    }

    @Override
    public int peek() {
      return data.get(index).intValue();
    }

    @Override
    public void add(int value) {
      if (histogram != null) {
        histogram.addValue(value);
      }
      data.set(index++, value);
    }
  }

  class LocalLongScanner extends BaseScanner implements Storage.LongScanner {

    @Override
    public long get() {
      return data.get(index++).longValue();
    }

    @Override
    public long peek() {
      return data.get(index).longValue();
    }

    @Override
    public void add(long value) {
      if (histogram != null) {
        histogram.addValue(value);
      }
      data.set(index++, value);
    }
  }
}
