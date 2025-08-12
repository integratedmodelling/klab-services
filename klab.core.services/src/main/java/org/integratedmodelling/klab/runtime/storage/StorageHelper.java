package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.GridN;

import java.util.List;

// TODO rename or merge with StorageManager
public class StorageHelper {

  // needs add, get, peek in native, non-boxing subclasses
  public interface Filler {
    long size();
  }

  /** New buffer API to substitute Storage.Buffer. */
  public interface Buffer {

    Geometry getGeometry();

    Data.FillCurve getFillCurve();

    <T extends Filler> T filler(Class<T> fillerClass);
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
  public List<Geometry> getGeometries(
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

    var s = new StorageHelper();
    var centralColombia =
        "τ0(1){ttype=LOGICAL,period=[1609459200000 1640995200000],tscope=1.0,"
            + "tunit=YEAR}S2(934,631){bbox=[-75.2281407807369 -72.67107290964314 3.5641500380320963 5"
            + ".302943221927137],"
            + "shape"
            + "=00000000030000000100000005C0522AF2DBCA0987400C8361185B1480C052CE99DBCA0987400C8361185B1480C052CE99DBCA098740153636BF7AE340C0522AF2DBCA098740153636BF7AE340C0522AF2DBCA0987400C8361185B1480,proj=EPSG:4326}";

    var original = Geometry.create(centralColombia);
    for (var g : s.getGeometries(original, -1, 120000, Long.MAX_VALUE)) {
      System.out.println(g);
    }
  }
}
