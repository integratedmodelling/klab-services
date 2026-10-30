package org.integratedmodelling.klab.runtime.storage;

import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Tile;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;

/** Bounded rectangular-grid oracle independent of the production index-remapping codec. */
public final class GridInspector {
  public static final int MAX_CELLS = 65_536;
  private GridInspector() {}

  private record Grid(long nx, long ny, double west, double east, double south, double north,
      String projection) {
    long size() { return Math.multiplyExact(nx, ny); }
    double dx() { return (east - west) / nx; }
    double dy() { return (north - south) / ny; }
  }

  private static boolean equal(double a, double b) {
    return Double.isFinite(a) && Double.isFinite(b)
        && Math.abs(a - b) <= Math.max(1e-10, 8 * Math.max(Math.ulp(a), Math.ulp(b)));
  }

  private static Grid expected(String encoding) {
    var geometry = Geometry.create(encoding);
    var space = geometry.dimension(Geometry.Dimension.Type.SPACE);
    if (geometry.getDimensions().size() != 1 || space == null || !space.isRegular()
        || space.isGeneric() || space.getDimensionality() != 2 || space.getShape().size() != 2
        || !Set.of("proj", "bbox").containsAll(space.getParameters().keySet()))
      throw new IllegalArgumentException("Expected one explicit regular 2D spatial bbox and CRS");
    Object box = space.getParameters().get("bbox"), projection = space.getParameters().get("proj");
    if (box == null || projection == null || projection.toString().isBlank())
      throw new IllegalArgumentException("Expected grid requires bbox and CRS");
    var values = Arrays.stream(box.toString().replace('[', ' ').replace(']', ' ').trim()
        .split("[\\s,]+" )).mapToDouble(Double::parseDouble).toArray();
    if (values.length != 4 || Arrays.stream(values).anyMatch(v -> !Double.isFinite(v))
        || values[1] <= values[0] || values[3] <= values[2]
        || space.getShape().get(0) <= 0 || space.getShape().get(1) <= 0)
      throw new IllegalArgumentException("Expected grid has invalid bounds or dimensions");
    var result = new Grid(space.getShape().get(0), space.getShape().get(1),
        values[0], values[1], values[2], values[3], projection.toString());
    if (result.size() > MAX_CELLS)
      throw new IllegalArgumentException("Grid inspector limit is " + MAX_CELLS + " cells");
    return result;
  }

  private static Grid actual(Geometry geometry) {
    var declared = geometry.dimension(Geometry.Dimension.Type.SPACE);
    if (declared == null || declared.getParameters().get("proj") == null)
      throw new UnsupportedOperationException("Owning observation must declare its grid CRS");
    var space = Scale.create(geometry).getSpace();
    if (!(space instanceof Tile tile) || tile.getGrid() == null
        || !ShapeImpl.promote(tile.getGeometricShape()).getJTSGeometry().isRectangle())
      throw new UnsupportedOperationException("Grid inspector requires rectangular tiled support");
    var grid = tile.getGrid(); var envelope = grid.getEnvelope();
    return new Grid(grid.getXCells(), grid.getYCells(), envelope.getMinX(), envelope.getMaxX(),
        envelope.getMinY(), envelope.getMaxY(), grid.getProjection().getCode());
  }

  private static boolean same(Grid a, Grid b) {
    return a.nx == b.nx && a.ny == b.ny && a.projection.equals(b.projection)
        && equal(a.west, b.west) && equal(a.east, b.east)
        && equal(a.south, b.south) && equal(a.north, b.north);
  }

  /** Shard/consumer descriptors may inherit their CRS from the owning observation. */
  private static Grid part(Geometry geometry, Grid owner) {
    var space = geometry.dimension(Geometry.Dimension.Type.SPACE);
    if (space == null || !space.isRegular() || space.isGeneric() || space.getDimensionality() != 2
        || space.getShape().size() != 2
        || !Set.of("proj", "bbox", "shape", "sgrid", "gridalignment", "world")
            .containsAll(space.getParameters().keySet()))
      throw new UnsupportedOperationException("Unsupported shard grid descriptor");
    String projection = Objects.toString(space.getParameters().get("proj"), owner.projection);
    double[] bounds;
    if (space.getParameters().get("bbox") instanceof Object box) {
      bounds = Arrays.stream(box.toString().replace('[', ' ').replace(']', ' ').trim()
          .split("[\\s,]+" )).mapToDouble(Double::parseDouble).toArray();
    } else if (space.getParameters().get("shape") instanceof Object definition) {
      var shape = ShapeImpl.create(definition.toString());
      if (!projection.equals(shape.getProjection().getCode()))
        throw new UnsupportedOperationException("Shard shape and grid CRS disagree");
      var envelope = shape.getEnvelope();
      bounds = new double[] {envelope.getMinX(), envelope.getMaxX(), envelope.getMinY(), envelope.getMaxY()};
    } else throw new UnsupportedOperationException("Shard has no physical bounds");
    if (bounds.length != 4 || Arrays.stream(bounds).anyMatch(v -> !Double.isFinite(v))
        || bounds[1] <= bounds[0] || bounds[3] <= bounds[2]
        || space.getShape().get(0) <= 0 || space.getShape().get(1) <= 0)
      throw new IllegalArgumentException("Invalid shard grid bounds");
    return new Grid(space.getShape().get(0), space.getShape().get(1),
        bounds[0], bounds[1], bounds[2], bounds[3], projection);
  }

  /** Explicit documented curve formulas: no ConformantScan, mapper, or scanner coordinates used. */
  private static long[] coordinates(long index, Grid grid, Data.FillCurve curve) {
    return switch (curve) {
      case D2_XY -> new long[] {index / grid.ny, index % grid.ny};
      case D2_YX -> new long[] {index % grid.nx, index / grid.nx};
      case D2_XInvY -> new long[] {index / grid.ny, grid.ny - 1 - index % grid.ny};
      default -> throw new UnsupportedOperationException("Unsupported grid inspector curve " + curve);
    };
  }

  private static int global(Grid whole, Grid part, long[] xy) {
    if (!whole.projection.equals(part.projection) || !equal(whole.dx(), part.dx())
        || !equal(whole.dy(), part.dy())) return -1;
    double ox = (part.west - whole.west) / whole.dx();
    double oy = (part.south - whole.south) / whole.dy();
    long x = Math.round(ox) + xy[0], y = Math.round(oy) + xy[1];
    if (!equal(ox, Math.rint(ox)) || !equal(oy, Math.rint(oy))
        || x < 0 || y < 0 || x >= whole.nx || y >= whole.ny) return -1;
    return Math.toIntExact(x * whole.ny + y);
  }

  private static boolean cell(Storage.Scanner scanner, Grid whole, long[] local, int index) {
    long[] actual = new long[2]; scanner.spatialCoordinates(actual);
    if (!Arrays.equals(actual, local)) return false;
    var cell = scanner.cell();
    if (cell.x() != local[0] || cell.y() != local[1] || !whole.projection.equals(cell.projection())
        || cell.bounds().size() != 1) return false;
    long x = index / whole.ny, y = index % whole.ny;
    var box = cell.bounds().getFirst();
    return equal(box.minX(), whole.west + x * whole.dx())
        && equal(box.maxX(), whole.west + (x + 1) * whole.dx())
        && equal(box.minY(), whole.south + y * whole.dy())
        && equal(box.maxY(), whole.south + (y + 1) * whole.dy());
  }

  private static boolean finite(Storage.Scanner scanner) {
    return !scanner.isValid() || switch (scanner) {
      case Storage.DoubleScanner d -> Double.isFinite(d.peek());
      case Storage.FloatScanner f -> Float.isFinite(f.peek());
      case Storage.KeyScanner<?> ignored -> throw new UnsupportedOperationException("Keyed grid values are unsupported");
      default -> true;
    };
  }

  /**
   * Check every INIT cell against committed native payloads, then all XY/YX/inverted-Y views with
   * 1, 3 and 7 partitions. Values stay in native units; unitcheck supplies an independent mediation
   * expectation. No storage shards are created and the caller's data and layout are never changed.
   */
  public static boolean check(ContextScope scope, Observation observation, String expectedSpatialGeometry) {
    var grid = expected(expectedSpatialGeometry);
    var source = StorageReads.source(observation, scope);
    if (source.getId() <= 0 || !same(grid, actual(source.getGeometry()))) return false;
    if (!StorageScan.semantics(source.getObservable()).equals(StorageScan.semantics(observation.getObservable())))
      throw new IllegalArgumentException("Grid check expects native semantics; use unitcheck for unit views");
    var storage = scope.getDigitalTwin().getStorageManager().getStorage(source);
    var original = StorageScan.Layout.of(storage.getNativeShardingStrategy());
    var semantics = StorageScan.semantics(source.getObservable());
    var descriptors = scope.getDigitalTwin().getKnowledgeGraph().query(Storage.Shard.class, scope)
        .source(source).along(GraphModel.Relationship.HAS_DATA).run(scope).stream()
        .filter(shard -> shard.getTimestamp() == 0).toList();
    if (descriptors.isEmpty()) return false;
    var values = new String[Math.toIntExact(grid.size())];
    var nativeSeen = new BitSet(values.length); var urns = new HashSet<String>();
    boolean hasFiniteValue = false;
    for (var shard : descriptors) {
      if (!urns.add(shard.getUrn())) return false;
      var part = part(shard.getGeometry(), grid); var scanner = storage.getNativeScanner(shard);
      if (scanner.size() != part.size() || scanner.size() > grid.size()) return false;
      for (long p = 0; p < part.size(); p++) {
        var xy = coordinates(p, part, shard.getShardingStrategy().getCurve());
        int index = global(grid, part, xy);
        if (index < 0 || nativeSeen.get(index) || !scanner.hasNext() || scanner.position() != p
            || !finite(scanner)) return false;
        values[index] = StorageScan.textValue(scanner); nativeSeen.set(index);
        hasFiniteValue |= scanner.isValid();
        if (scanner.nextLong() != p) return false;
      }
      if (scanner.hasNext()) return false;
    }
    if (nativeSeen.cardinality() != values.length || !hasFiniteValue) return false;
    for (var curve : List.of(Data.FillCurve.D2_XY, Data.FillCurve.D2_YX, Data.FillCurve.D2_XInvY))
      for (int splits : new int[] {1, 3, 7}) {
        var layout = new Data.ShardingStrategy(curve, splits, 0, 0, null);
        var plan = storage.plan(StorageReads.request(observation, Scheduler.Event.initialization(),
            layout, List.of(), Storage.Scanner.class));
        var seen = new BitSet(values.length);
        try (var session = storage.open(plan)) {
          for (var scanner : session.scanners()) {
            var part = part(Geometry.create(scanner.view().partition().geometry()), grid);
            if (scanner.size() != part.size()) return false;
            for (long p = 0; p < part.size(); p++) {
              var xy = coordinates(p, part, curve); int index = global(grid, part, xy);
              if (index < 0 || seen.get(index) || !scanner.hasNext() || scanner.position() != p
                  || !cell(scanner, grid, xy, index) || !finite(scanner)
                  || !Objects.equals(values[index], StorageScan.textValue(scanner))) return false;
              seen.set(index); if (scanner.nextLong() != p) return false;
            }
            if (scanner.hasNext()) return false;
          }
        }
        if (seen.cardinality() != values.length) return false;
      }
    return original.equals(StorageScan.Layout.of(storage.getNativeShardingStrategy()))
        && semantics.equals(StorageScan.semantics(source.getObservable()));
  }
}
