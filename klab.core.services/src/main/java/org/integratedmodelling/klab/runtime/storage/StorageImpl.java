package org.integratedmodelling.klab.runtime.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Geometries;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.scope.ContextScope;

// TODO this will become the next Storage implementation
public class StorageImpl implements Storage {

  private final Observation observation;
  private final ContextScope scope;
  private final Data.ShardingStrategy nativeShardingStrategy;
  private final StorageManagerImpl storageManager;
  private Data.ShardingStrategy shardingStrategy;

  /*
   * Buffer storage along slowest-varying dimensions. All dimensions except one (space) must have
   * linear indexing and come from the scheduler event that serves as an index.
   */
  private NavigableMap<List<Long>, List<Shard>> shards = new TreeMap<>();

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

    return shards.computeIfAbsent(key, k -> createShards(scale, timeStart));
  }

  private List<Shard> createShards(Scale scale, long timeStart) {
    // TODO if the size is 1, use a simplified strategy
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
      shards.add(
          new ShardImpl(
              geometry,
              observation,
              nativeShardingStrategy,
              index++,
              timeStart,
              storageManager,
              scope.getConfiguration().getPersistence()));
    }

    return shards;
  }

  @Override
  public <T extends Scanner> List<T> scan(
      Scheduler.Event locator,
      Data.ShardingStrategy request,
      Class<T> scannerClass,
      boolean readOnly) {
    var shards = getNativeShards(locator);
    var nativeScanners = shards.stream().map(this::getNativeScanner).toList();
    return remapScanners(nativeScanners, request, scannerClass);
  }

  /**
   * Remap a list of scanners to those representing the requested sharding strategy. This may entail
   * creating temporary geometries and merging or splitting scanners to reflect them. Data types may
   * need to be cast to remap to a different compatible type.
   *
   * @param nativeScanners
   * @param request
   * @param scannerClass
   * @return
   * @param <T>
   */
  private <T extends Scanner> List<T> remapScanners(
      List<Scanner> nativeScanners, Data.ShardingStrategy request, Class<T> scannerClass) {
    // TODO!
    return (List<T>) nativeScanners;
  }

  /**
   * Get the native scanner for the passed shard.
   *
   * @param shard
   * @return a scanner over the data in the shard.
   */
  private Scanner getNativeScanner(Shard shard) {
    return null;
  }

  private <T extends Scanner> T mapShardToScanner(
      Shard shard, Data.ShardingStrategy request, Class<T> scannerClass) {
    // GAAAGH
    // get native scanner
    // map as needed
    return null;
  }

  @Override
  public Histogram getHistogram() {
    return null;
  }

  public static void main(String[] args) {

    var s = new StorageImpl();

    var original = Geometry.create(Geometries.CENTRAL_COLOMBIA);
    for (var g : s.getGeometries(original, -1, 65600, Long.MAX_VALUE)) {
      System.out.println(g);
    }
  }
}
