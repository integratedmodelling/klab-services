package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Geometries;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;

import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

// TODO this will become the next Storage implementation
public class StorageImpl implements Storage {

  private final Observation observation;
  private final ContextScope scope;
  private final boolean doNotParallelize;
  private final Data.ShardingStrategy shardingStrategy;

    /*
   * Buffer storage along slowest-varying dimensions. All dimensions except one (space) must have
   * linear indexing and come from the scheduler event that serves as an index.
   */
  private NavigableMap<List<Long>, List<Shard>> buffers = new TreeMap<>();

  /**
   * Create the storage container for the observation according to the observation's own sharding
   * strategy, which determines the specific native type for numeric observations, and all options
   * related to storage and distribution. Shards are created upon first access and reinterpreted
   * according to the requesting sharding strategy.
   *
   * @param observation
   * @param contextScope
   * @param runtimeSettings
   */
  public StorageImpl(Observation observation, Data.ShardingStrategy shardingStrategy, ContextScope contextScope, Settings runtimeSettings) {
    this.observation = observation;
    this.shardingStrategy = shardingStrategy;
    this.scope = contextScope;
    this.doNotParallelize =
        runtimeSettings.get(Setting.DO_NOT_PARALLELIZE_OBSERVATIONS, Boolean.class);
    // TODO prepare shard descriptors from any existing shards in the knowledge graph!
  }

  // used only for testing, won't work for anything else
  private StorageImpl() {
    observation = null;
    scope = null;
    doNotParallelize = false;
    shardingStrategy = null;
  }

  @Override
  public Type getNativeType() {
    return shardingStrategy.getDataType();
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
    return List.of();
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
    return List.of();
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
