package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Geometries;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
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
  private Type nativeType;

  /*
   * Buffer storage along slowest-varying dimensions. All dimensions except one (space) must have linear indexing along a "start" number
   *
   */
  private NavigableMap<List<Long>, List<Buffer>> buffers = new TreeMap<>();

  public StorageImpl(Observation observation, ContextScope contextScope) {
    this.observation = observation;
    this.scope = contextScope;
    // TODO establish the default native type? This may be used before
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
  public List<Buffer> getNativeBuffers(Scheduler.Event event) {
    return List.of();
  }

  @Override
  public List<Buffer> getOrCreateBuffers(Scheduler.Event locator, Data.Access specs) {
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
   * @param buffers
   * @param geometry
   * @param splits
   * @param minSize
   * @param maxSize
   * @param fillCurve
   * @return
   */
  public List<Buffer> remap(
      List<Buffer> buffers,
      Geometry geometry,
      int splits,
      long minSize,
      long maxSize,
      Data.FillCurve fillCurve) {

    // TODO aha
    return buffers;
  }

  public static void main(String[] args) {

    var s = new StorageImpl(null, null);

    var original = Geometry.create(Geometries.CENTRAL_COLOMBIA);
    for (var g : s.getGeometries(original, -1, 65600, Long.MAX_VALUE)) {
      System.out.println(g);
    }
  }
}
