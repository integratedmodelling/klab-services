package org.integratedmodelling.klab.runtime.storage;

import java.util.*;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.referencing.CRS;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.runtime.storage.ConformantScan.Box;
import org.integratedmodelling.klab.runtime.storage.ConformantScan.Grid;
import org.integratedmodelling.klab.runtime.storage.ConformantScan.Lattice;

/**
 * Version-4 spatial policy: XY axes, binary64 arithmetic, half-open cells, no extrapolation. Only
 * axis-aligned rectangular grids are accepted. CRS resampling is deliberately limited to WGS84/Web
 * Mercator's monotonic domain, so transformed bounds can be pruned without approximating a
 * nonlinear edge by its endpoints in an arbitrary projection.
 */
final class SpatialScan implements ScanMapping {
  final ConformantScan source, target;
  final Lattice sourceLattice, targetLattice;
  final MathTransform transform;
  final StorageScan.Sampling sampling;
  final StorageScan.Coverage coverage;
  final int[][] dependencies;

  static UnsupportedOperationException disabled(String reason) {
    return new UnsupportedOperationException(
        "ACCEPT_LOSSY_MEDIATIONS=false: spatial extents do not match: " + reason);
  }

  static ScanMapping plan(
      List<StorageScan.SourceShard> sources,
      StorageScan.Request<?> request,
      String owner,
      boolean acceptsLossy) {
    try {
      return ConformantScan.compile(sources, request, owner);
    } catch (UnsupportedOperationException mismatch) {
      if (!acceptsLossy) throw disabled(mismatch.getMessage());
      if (request.sampling() == StorageScan.Sampling.EXACT) throw mismatch;
      return new SpatialScan(sources, request, owner);
    }
  }

  static StorageScan.Spatial metadata(ScanMapping mapping) {
    return mapping instanceof SpatialScan spatial
        ? new StorageScan.Spatial(
            spatial.sourceLattice.reference.projection(),
            spatial.targetLattice.reference.projection())
        : null;
  }

  static List<StorageScan.Operation> operations(
      StorageScan.Conversion conversion, StorageScan.Operation primitive) {
    return conversion == null
        ? List.of(StorageScan.Operation.SPATIAL_RESAMPLE, primitive)
        : List.of(
            StorageScan.Operation.SPATIAL_RESAMPLE,
            StorageScan.Operation.VALUE_CONVERSION,
            primitive);
  }

  static void validateConversion(ScanMapping mapping, StorageScan.Conversion conversion) {
    if (mapping instanceof SpatialScan spatial
        && conversion != null
        && (conversion.kind().equals("RANGE")
            || spatial.sampling == StorageScan.Sampling.CONSERVATIVE_TOTAL
                && conversion.offset() != 0))
      throw ConformantScan.unsupported(
          "resampling cannot compose with bounded ranges or affine extensive totals");
  }

  private SpatialScan(
      List<StorageScan.SourceShard> descriptors, StorageScan.Request<?> request, String owner) {
    sampling = request.sampling();
    coverage = request.coverage();
    var sourceLayout = descriptors.getFirst().layout();
    source =
        ConformantScan.compile(
            descriptors,
            new StorageScan.Request<>(
                request.slice(),
                sourceLayout,
                null,
                List.of(),
                null,
                Storage.Scanner.class,
                StorageScan.Access.READ_ONLY,
                StorageScan.Precision.LOSSLESS,
                StorageScan.Coverage.EXACT,
                StorageScan.Sampling.EXACT,
                request.budget()),
            owner);
    Grid ownerGrid = Grid.read(owner, null, false);
    Grid sourceGrid = Grid.read(descriptors.getFirst().geometry(), ownerGrid.projection());
    if (sourceGrid.shape().length != 2)
      throw ConformantScan.unsupported("spatial resampling requires a 2D grid");
    sourceLattice = new Lattice(sourceGrid);
    String targetGeometry = request.geometry();
    // Explicit output partitions define their own target support, not the input observation's
    // extent.
    if (targetGeometry == null && !request.partitions().isEmpty()) {
      Grid first = Grid.read(request.partitions().getFirst().geometry(), sourceGrid.projection());
      Lattice lattice = new Lattice(first);
      Box[] boxes =
          request.partitions().stream()
              .map(p -> lattice.box(Grid.read(p.geometry(), first.projection()), true))
              .toArray(Box[]::new);
      targetGeometry = lattice.geometry(ConformantScan.Directory.of(boxes).bounds);
    }
    if (targetGeometry == null) targetGeometry = owner;
    Grid targetGrid = Grid.read(targetGeometry, sourceGrid.projection(), false);
    if (targetGrid.shape().length != 2)
      throw ConformantScan.unsupported("spatial dimensionality differs");
    if (!targetGrid.others().isEmpty()
        && !targetGrid.others().equals(sourceGrid.others())
        && !targetGrid.others().equals(ownerGrid.others()))
      throw ConformantScan.unsupported(
          "non-spatial extents differ; temporal resampling is unsupported");
    if (request.partitions().isEmpty()
        && targetGrid.others().equals(ownerGrid.others())
        && !targetGrid.others().equals(sourceGrid.others())) {
      targetGeometry =
          sourceGrid.others()
              + targetGrid
                  .geometry()
                  .dimension(
                      org.integratedmodelling.klab.api.geometry.Geometry.Dimension.Type.SPACE)
                  .encode();
      targetGrid = Grid.read(targetGeometry, sourceGrid.projection());
    }
    if (targetGrid.others().isEmpty() && !sourceGrid.others().isEmpty()) {
      targetGeometry =
          sourceGrid.others()
              + targetGrid
                  .geometry()
                  .dimension(
                      org.integratedmodelling.klab.api.geometry.Geometry.Dimension.Type.SPACE)
                  .encode();
      targetGrid = Grid.read(targetGeometry, sourceGrid.projection());
    }
    targetLattice = new Lattice(targetGrid);
    // Fully qualify the virtual owner so omitted partition CRSs inherit the target, not the source.
    targetGeometry = targetLattice.geometry(new Box(new long[2], targetGrid.shape()));
    domain(sourceGrid);
    domain(targetGrid);
    // Validate every source shard, including those outside a target window.
    for (var descriptor : descriptors)
      domain(Grid.read(descriptor.geometry(), sourceGrid.projection()));
    var virtual =
        new StorageScan.SourceShard(
            "target-grid",
            targetGeometry,
            new Box(new long[2], targetGrid.shape()).size,
            0,
            0,
            request.layout());
    target =
        ConformantScan.compile(
            List.of(virtual),
            new StorageScan.Request<>(
                request.slice(),
                request.layout(),
                targetGeometry,
                request.partitions(),
                null,
                Storage.Scanner.class,
                StorageScan.Access.READ_ONLY,
                request.precision(),
                StorageScan.Coverage.EXACT,
                StorageScan.Sampling.EXACT,
                request.budget()),
            targetGeometry);
    boolean sameCRS = sourceGrid.projection().equals(targetGrid.projection());
    if (!sameCRS
        && !(Set.of("EPSG:4326", "EPSG:3857").contains(sourceGrid.projection())
            && Set.of("EPSG:4326", "EPSG:3857").contains(targetGrid.projection())))
      throw ConformantScan.unsupported("CRS resampling supports EPSG:4326 and EPSG:3857 only");
    if (!sameCRS && conservative())
      throw ConformantScan.unsupported("conservative overlap requires matching CRS");
    if (sampling == StorageScan.Sampling.MAJORITY && sourceLayout.type() != Storage.Type.KEYED)
      throw ConformantScan.unsupported("area-majority requires dictionary-bound KEYED data");
    if (sampling != StorageScan.Sampling.NEAREST && sampling != StorageScan.Sampling.MAJORITY
        && sourceLayout.type() != Storage.Type.DOUBLE
        && sourceLayout.type() != Storage.Type.FLOAT)
      throw ConformantScan.unsupported(
          "interpolation and numeric conservative aggregation require FLOAT/DOUBLE; categories require an area-majority dictionary policy");
    try {
      var sourceCRS = CRS.decode(sourceGrid.projection(), true);
      var targetCRS = CRS.decode(targetGrid.projection(), true);
      if (conservative()
          && !sourceGrid.projection().equals("EPSG:4326")
          && !(sourceCRS instanceof org.geotools.api.referencing.crs.ProjectedCRS))
        throw ConformantScan.unsupported("conservative geographic metrics require EPSG:4326");
      transform = sameCRS ? null : CRS.findMathTransform(targetCRS, sourceCRS, false);
    } catch (UnsupportedOperationException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot construct strict XY CRS transform", e);
    }
    dependencies = new int[target.targets.length][];
    long remaining = Math.multiplyExact((long) request.budget().maxPartitions(), 8);
    double[] lower = new double[2], upper = new double[2];
    for (int i = 0; i < target.targets.length; i++) {
      Box box = target.targets[i];
      world(box.start[0], box.start[1], lower);
      world(box.start[0] + box.shape[0], box.start[1] + box.shape[1], upper);
      long[] lo = new long[2], shape = new long[2];
      for (int d = 0; d < 2; d++) {
        double a = coordinate(lower[d], d), b = coordinate(upper[d], d);
        if (coverage == StorageScan.Coverage.EXACT
            && (a < source.directory.bounds.start[d] - 1e-8
                || b > source.directory.bounds.start[d] + source.directory.bounds.shape[d] + 1e-8))
          throw ConformantScan.unsupported(
              "incomplete source coverage for target partition " + target.partitions.get(i).id());
        lo[d] = floor(a) - 1;
        shape[d] = Math.max(1, Math.subtractExact(Math.addExact(floor(b), 2), lo[d]));
      }
      var links = new ArrayList<Integer>();
      source.directory.collect(new Box(lo, shape), links, remaining);
      dependencies[i] = links.stream().mapToInt(Integer::intValue).toArray();
      remaining -= links.size();
    }
  }

  boolean conservative() {
    return sampling == StorageScan.Sampling.MAJORITY || sampling == StorageScan.Sampling.CONSERVATIVE
        || sampling == StorageScan.Sampling.CONSERVATIVE_TOTAL;
  }

  private static void domain(Grid grid) {
    double[] b = grid.bounds();
    if (grid.projection().equals("EPSG:4326")
        && (b[0] < -180 || b[1] > 180 || b[2] <= -85.0511287798066 || b[3] >= 85.0511287798066))
      throw ConformantScan.unsupported(
          "geographic domain crosses wraparound or polar/Mercator limits");
    if (grid.projection().equals("EPSG:3857")
        && Arrays.stream(b).anyMatch(v -> Math.abs(v) >= 20037508.342789244))
      throw ConformantScan.unsupported(
          "Web Mercator domain crosses wraparound or projection limits");
    for (int d = 0; d < 2; d++) {
      double step = (b[2 * d + 1] - b[2 * d]) / grid.shape()[d];
      if (grid.shape()[d] > (1L << 52)
          || step <= 32 * Math.max(Math.ulp(b[2 * d]), Math.ulp(b[2 * d + 1])))
        throw ConformantScan.unsupported("grid coordinates cannot reliably distinguish cell edges");
    }
  }

  void world(double x, double y, double[] into) {
    into[0] = targetLattice.reference.bounds()[0] + x * targetLattice.step[0];
    into[1] = targetLattice.reference.bounds()[2] + y * targetLattice.step[1];
    if (transform != null)
      try {
        transform.transform(into, 0, into, 0, 1);
      } catch (Exception e) {
        throw new IllegalArgumentException("Spatial transformation failed", e);
      }
    if (!Double.isFinite(into[0]) || !Double.isFinite(into[1]))
      throw new IllegalArgumentException("Spatial transformation reached a singularity");
  }

  double coordinate(double value, int axis) {
    return (value - sourceLattice.reference.bounds()[2 * axis]) / sourceLattice.step[axis];
  }

  static long floor(double value) {
    if (!Double.isFinite(value) || Math.abs(value) > (1L << 52))
      throw ConformantScan.unsupported("unrepresentable source cell coordinate");
    return (long) Math.floor(value);
  }

  /** Relative spherical area in lon/lat, planar area otherwise; the common radius cancels. */
  double area(double x0, double x1, double y0, double y1) {
    if (sourceLattice.reference.projection().equals("EPSG:4326")) {
      double south = sourceLattice.reference.bounds()[2] + y0 * sourceLattice.step[1];
      double north = sourceLattice.reference.bounds()[2] + y1 * sourceLattice.step[1];
      return (x1 - x0) * (Math.sin(Math.toRadians(north)) - Math.sin(Math.toRadians(south)));
    }
    return (x1 - x0) * (y1 - y0);
  }

  public List<StorageScan.Partition> partitions() {
    return target.partitions;
  }

  public int[][] dependencies() {
    return dependencies;
  }

  public IndexedStorageReader reader(
      int partition, List<IndexedStorageReader> sources, int blockValues) {
    return new SpatialReader(this, partition, sources, blockValues);
  }
}
