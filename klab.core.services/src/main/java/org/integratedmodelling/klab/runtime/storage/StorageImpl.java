package org.integratedmodelling.klab.runtime.storage;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Geometries;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.utilities.Utils;

public class StorageImpl implements Storage {

  private final Observation observation;
  private final ContextScope scope;
  private final Data.ShardingStrategy nativeShardingStrategy;
  private final StorageManagerImpl storageManager;

  /**
   * Used as a key for the geometry-aware shard cache. In all current implementations, the key will
   * be a single long for time, but this future-proofs the implementation for more dimensions in the
   * geometry.
   */
  private static class ComparableLongList extends ArrayList<Long>
      implements Comparable<ComparableLongList> {
    @Serial private static final long serialVersionUID = 1L;

    public ComparableLongList() {
      super();
    }

    public ComparableLongList(List<Long> key) {
      super(key);
    }

    @Override
    public int compareTo(ComparableLongList other) {
      int size = Math.min(this.size(), other.size());
      for (int i = 0; i < size; i++) {
        int comparison = Long.compare(this.get(i), other.get(i));
        if (comparison != 0) {
          return comparison;
        }
      }
      return Integer.compare(this.size(), other.size());
    }
  }

  /*
   * Buffer storage along slowest-varying dimensions. All dimensions except one (space) must have
   * linear indexing and come from the scheduler event that serves as an index.
   */
  private NavigableMap<ComparableLongList, List<Shard>> shards = new TreeMap<>();

  /**
   * Create the storage container for the observation according to the observation's own sharding
   * strategy, which determines the specific native type for numeric observations, and all options
   * related to storage and distribution. Shards are created upon first access and reinterpreted
   * according to the requesting sharding strategy.
   *
   * @param observation
   * @param contextScope
   */
  public StorageImpl(
      Observation observation,
      Data.ShardingStrategy shardingStrategy,
      ContextScope contextScope,
      StorageManagerImpl storageManager) {
    this.observation = observation;
    this.scope = contextScope;
    this.storageManager = storageManager;
    // TODO prepare shard descriptors from any existing shards in the knowledge graph! If there is a
    // different sharding strategy, adopt that as native and create mediators.
    this.nativeShardingStrategy = shardingStrategy;
  }

  // used only for testing, won't work for anything else
  private StorageImpl() {
    observation = null;
    scope = null;
    nativeShardingStrategy = Data.ShardingStrategy.trivial(Type.DOUBLE);
    storageManager = null;
  }

  @Override
  public Type getNativeType() {
    return nativeShardingStrategy.getDataType();
  }

  /**
   * Generate the best-case scenario for an overall geometry according to preferences set in the
   * adapter or contextualizer.
   *
   * @param original
   * @param desiredSplits
   * @param minSize
   * @param maxSize
   * @return
   */
  private List<Geometry> getGeometries(
      Geometry original, int desiredSplits, long minSize, long maxSize) {

    if (desiredSplits == 1) {
      return List.of(original);
    }
    var splits = desiredSplits;
    if (splits <= 0) {
      var dsplits = original.size() / minSize;
      while (original.size() / dsplits > maxSize) {
        dsplits *= 2;
      }
      splits = (int) dsplits;
    }

    return original.split(splits);
  }

  @Override
  public List<Shard> getNativeShards(Scheduler.Event event) {

    var time = event.getTime();
    if (time.size() != 1) {
      throw new KlabUnimplementedException(
          "Multiple time steps for a buffer request during contextualization");
    }

    var scale = GeometryRepository.INSTANCE.scale(observation.getGeometry()).at(time);
    long timeStart = time.is(Time.Type.INITIALIZATION) ? 0 : time.getStart().getMilliseconds();
    var key =
        scale.getExtents().stream()
            // TODO generalize: remove the moving dimension in the geometry - no scenarios so far
            //  under which it's not space.
            .filter(e -> e.getType() != Geometry.Dimension.Type.SPACE)
            .map(
                e ->
                    e.getType() == Geometry.Dimension.Type.TIME
                        ? timeStart
                        : /* TODO use the index for any further dimension, unused for now */ 0L)
            .toList();

    return shards.computeIfAbsent(new ComparableLongList(key), k -> createShards(scale, timeStart));
  }

  private List<Shard> createShards(Scale scale, long timeStart) {

    if (scale.size() == 1) {
      return List.of(ShardImpl.trivial(nativeShardingStrategy.getDataType()));
    }

    var shards = new ArrayList<Shard>();
    int index = 0; // TODO do we need this to change with the time slice?
    for (var geometry :
        getGeometries(
            scale,
            nativeShardingStrategy.getSuggestedSplits(),
            nativeShardingStrategy.getMinSplitSize(),
            nativeShardingStrategy.getMaxBufferSize())) {
      var shard =
          new ShardImpl(
              geometry,
              observation,
              nativeShardingStrategy,
              index++,
              timeStart,
              storageManager,
              scope.getConfiguration().getPersistence());

      shards.add(shard);
    }

    return shards;
  }

  @Override
  public <T extends Scanner> List<T> scan(
      Scheduler.Event locator,
      Data.ShardingStrategy shardingStrategy,
      Class<T> scannerClass,
      boolean readOnly) {
    if (this.nativeShardingStrategy.equals(shardingStrategy)) {
      return getNativeShards(locator).stream().map(shard -> (T) shard.getNativeScanner()).toList();
    }
    return remapScanners(
        getNativeShards(locator).stream().map(shard -> shard.getNativeScanner()).toList(),
        shardingStrategy,
        scannerClass);
  }

  /**
   * Remap a list of scanners to those representing the requested sharding strategy. This may entail
   * creating temporary geometries and merging or splitting scanners to reflect them. Data types may
   * need to be cast to remap to a different compatible type.
   *
   * @param nativeScanners
   * @param shardingStrategy
   * @param scannerClass
   * @param <T>
   * @return
   */
  private <T extends Scanner> List<T> remapScanners(
      List<Scanner> nativeScanners, Data.ShardingStrategy shardingStrategy, Class<T> scannerClass) {
    // TODO! two steps: 1) splits & curve; 2) data type casting
    return (List<T>) nativeScanners;
  }

  @Override
  public Data.ShardingStrategy getNativeShardingStrategy() {
    return nativeShardingStrategy;
  }

  public List<Shard> allShards() {
    var ret = new ArrayList<Shard>();
    shards.values().forEach(ret::addAll);
    return ret;
  }

  public com.dynatrace.dynahist.Histogram histogram() {

    var allBuffers = allShards();
    if (allBuffers.size() == 1) {
      return ((ShardImpl) allBuffers.getFirst()).histogram;
    } else if (allBuffers.size() > 1) {
      com.dynatrace.dynahist.Histogram ret = null;
      var first = ((ShardImpl) allBuffers.getFirst()).histogram;
      if (first != null) {
        ret = com.dynatrace.dynahist.Histogram.createDynamic(first.getLayout());
        for (var buffer : allBuffers) {
          if (((ShardImpl) buffer).histogram != null) {
            ret.addHistogram(((ShardImpl) buffer).histogram);
          }
        }
      }
    }
    return null;
  }

  private <T extends Scanner> T mapShardToScanner(
      Shard shard, Data.ShardingStrategy request, Class<T> scannerClass) {
    throw new KlabUnimplementedException("shard mapping unimplemented");
  }

  @Override
  public Histogram getHistogram() {
    return Utils.Data.adaptHistogram(histogram());
  }

  public static void main(String[] args) {

    var s = new StorageImpl();

    var original = Geometry.create(Geometries.CENTRAL_COLOMBIA);
    for (var g : s.getGeometries(original, -1, 65600, Long.MAX_VALUE)) {
      System.out.println(g);
    }
  }
}
