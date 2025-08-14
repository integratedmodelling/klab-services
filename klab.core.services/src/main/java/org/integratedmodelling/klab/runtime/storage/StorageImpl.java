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
  private Type nativeType;

  /*
   * Buffer storage along slowest-varying dimensions. All dimensions except one (space) must have linear indexing along a "start" number
   *
   */
  private NavigableMap<List<Long>, List<Shard>> buffers = new TreeMap<>();

  /**
   * Create the storage for the observation. Settings will determine the specific native type for
   * numeric observations, and potentially other options related to storage and distribution.
   *
   * @param observation
   * @param contextScope
   * @param runtimeSettings
   */
  public StorageImpl(Observation observation, ContextScope contextScope, Settings runtimeSettings) {
    this.observation = observation;
    this.scope = contextScope;
    this.doNotParallelize =
        runtimeSettings.get(Setting.DO_NOT_PARALLELIZE_OBSERVATIONS, Boolean.class);
    // establish the default native type. Buffer request may override.
    this.nativeType =
        switch (observation.getObservable().getDescriptionType()) {
          case VOID,
              CHARACTERIZATION,
              ACKNOWLEDGEMENT,
              CONNECTION,
              CLASSIFICATION,
              INSTANTIATION,
              DETECTION,
              SIMULATION ->
              throw new KlabIllegalStateException(
                  "Cannot create storage for " + observation.getObservable());
          case QUANTIFICATION ->
              // TODO this should also consider any constraints in the observation distribution data
              runtimeSettings.get(Setting.USE_SHORT_FLOAT_REPRESENTATION, Boolean.class)
                  ? Type.FLOAT
                  : Type.DOUBLE;
          case CATEGORIZATION -> Type.KEYED;
          case VERIFICATION -> Type.BOOLEAN;
        };
  }

  @Override
  public Type getNativeType() {
    return nativeType;
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
  public List<Shard> getOrCreateShards(Scheduler.Event locator, Data.DistributionStrategy specs) {
    return List.of();
  }

  @Override
  public Histogram getHistogram() {
    return null;
  }

  /**
   * Wrap the buffers into a set of remapping buffers, built to match the passed geometry and other
   * parameters.
   *
   * @param shards
   * @param geometry
   * @param splits
   * @param minSize
   * @param maxSize
   * @param fillCurve
   * @return
   */
  public List<Shard> remap(
      List<Shard> shards,
      Geometry geometry,
      int splits,
      long minSize,
      long maxSize,
      Data.FillCurve fillCurve) {

    // TODO qui ti voglio
    return shards;
  }

  public static void main(String[] args) {

    var s = new StorageImpl(null, null, null);

    var original = Geometry.create(Geometries.CENTRAL_COLOMBIA);
    for (var g : s.getGeometries(original, -1, 65600, Long.MAX_VALUE)) {
      System.out.println(g);
    }
  }
}
