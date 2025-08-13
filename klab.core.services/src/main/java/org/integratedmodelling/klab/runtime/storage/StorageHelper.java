package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Geometries;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;

import java.util.List;
import java.util.NavigableMap;
import java.util.PrimitiveIterator;
import java.util.TreeMap;

// TODO this will become the next Storage implementation
public class StorageHelper implements Storage {

  private final Observation observation;
  private final ContextScope scope;
  private Type nativeType;

  /*
   * Buffer storage along slowest-varying dimensions. All dimensions except one (space) must have linear indexing along a "start" number
   *
   */
  private NavigableMap<List<Long>, List<Buffer>> buffers = new TreeMap<>();

  public StorageHelper(Observation observation, ContextScope contextScope) {
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

  /**
   * Create or retrieve buffers for the observation we represent, honoring any requests in terms of
   * splits and fill curve. If the buffers do not exist for the observation, they will be created
   * and stored in the knowledge graph transaction as the "native" buffers. If already existing and
   * not honoring the geometry parameters, the buffers returned will be remapping the original ones
   * to the passed geometry, fill curve and splits, to accommodate the configuration expected by the
   * requesting contextualizer or adapter.
   *
   * <p>The overall constraint is the original observation geometry, which all buffers must
   * ultimately cover exactly, and its dimensionality, which must be preserved at all times.
   *
   * @param locator the geometry that sets the boundaries for the buffers. There must be only one
   *     varying dimension, normally space.
   * @param fillCurve
   * @param splits
   * @param minSize
   * @param maxSize
   * @param transaction
   * @return
   */
  public List<Buffer> getOrCreateBuffers(
      Geometry locator,
      Data.FillCurve fillCurve,
      int splits,
      long minSize,
      long maxSize,
      DigitalTwin.Transaction transaction) {
    return List.of();
  }

  @Override
  public Histogram getHistogram() {
    return null;
  }

  /**
   * Create a buffer for the passed geometry and space filling curve. At this point the commitment
   * to the buffer is done and any further adaptation must create a remapping buffer.
   *
   * @param geometry
   * @param fillingCurve
   * @return
   */
  public Buffer createBuffer(Geometry geometry, Data.FillCurve fillingCurve) {
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

    var s = new StorageHelper(null, null);

    var original = Geometry.create(Geometries.CENTRAL_COLOMBIA);
    for (var g : s.getGeometries(original, -1, 65600, Long.MAX_VALUE)) {
      System.out.println(g);
    }
  }
}
