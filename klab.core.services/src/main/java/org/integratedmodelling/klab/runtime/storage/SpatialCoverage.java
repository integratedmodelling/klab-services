package org.integratedmodelling.klab.runtime.storage;

import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.prep.*;

/** Cell-centre support, separate from payload validity. No arrays proportional to grid size. */
final class SpatialCoverage {
  /** Immutable polygon and prepared indexes shared by all partitions and task-local cursors. */
  static final class Support {
    final String encoding, projection;
    final org.locationtech.jts.geom.Geometry polygon;
    final PreparedGeometry prepared;
    final IndexedPointInAreaLocator points;
    final org.locationtech.jts.geom.Envelope envelope;
    final boolean rectangle;
    Support(String encoding, ShapeImpl shape) {
      this.encoding = encoding; projection = shape.getProjection().getCode();
      polygon = shape.getJTSGeometry().copy();
      if (polygon.getDimension() != 2 || !polygon.isValid())
        throw new IllegalArgumentException("Grid coverage must be a valid polygon or multipolygon");
      envelope = polygon.getEnvelopeInternal(); rectangle = polygon.isRectangle();
      prepared = rectangle ? null : PreparedGeometryFactory.prepare(polygon);
      points = rectangle ? null : new IndexedPointInAreaLocator(polygon);
      // Build the point index before publication to worker threads.
      if (points != null) points.locate(new Coordinate(envelope.getMinX(), envelope.getMinY()));
    }
  }

  static Support support(String encoding) {
    if (encoding == null) return null;
    var space = StorageScan.parseGeometry(encoding).dimension(Geometry.Dimension.Type.SPACE);
    if (space == null || space.getDimensionality() != 2 || space.getParameters().get("shape") == null) return null;
    var support = new Support(encoding, ShapeImpl.create(space.getParameters().get("shape").toString()));
    // Rectangles that contain every centre require no predicate, wrapper, or new descriptor version.
    if (support.rectangle && new SpatialCoverage(encoding, support.projection, Data.FillCurve.D2_XY, support).unrestricted)
      return null;
    return support;
  }

  static StorageScan.Mask metadata(Support source, Support target) {
    // Keep support in the descriptor even for rectangles: a snapped bounding grid may extend past it.
    return source == null && target == null ? null
        : new StorageScan.Mask(source == null ? null : source.encoding, target == null ? null : target.encoding);
  }

  final ConformantScan.Grid grid;
  final ConformantScan.Box box;
  final Data.FillCurve curve;
  final Support support;
  final double dx, dy;
  final boolean unrestricted;

  SpatialCoverage(String geometry, String inheritedProjection, Data.FillCurve curve, Support support) {
    grid = ConformantScan.Grid.read(geometry, inheritedProjection, false);
    box = new ConformantScan.Box(new long[grid.shape().length], grid.shape());
    ConformantScan.curve(curve, grid.shape().length);
    this.curve = curve; this.support = support;
    if (support != null && (grid.shape().length != 2 || !grid.projection().equals(support.projection)))
      throw new IllegalArgumentException("Coverage shape and grid must use the same two-dimensional CRS");
    dx = (grid.bounds()[1] - grid.bounds()[0]) / grid.shape()[0];
    dy = grid.shape().length == 2 ? (grid.bounds()[3] - grid.bounds()[2]) / grid.shape()[1] : 0;
    unrestricted = support == null || support.rectangle && classify(0, box.size, new long[2], new long[2]) == 1;
  }

  /** Physical centre: clip polar Y and wrap continuous logical longitude into its world domain. */
  double centre(long index, int axis) {
    double low = grid.bounds()[2 * axis], step = axis == 0 ? dx : dy;
    double a = low + index * step, b = low + (index + 1) * step;
    if (grid.world().length == 4 && axis == 1) {
      a = Math.max(a, grid.world()[2]); b = Math.min(b, grid.world()[3]);
    }
    return a + (b - a) / 2;
  }

  double wrap(double x) {
    if (grid.world().length != 4) return x;
    double west = grid.world()[0], period = grid.world()[1] - west;
    return x - Math.floor((x - west) / period) * period;
  }

  /** 1 means all centres covered, -1 disjoint, 0 means subdivide. Safe over-approximation of a
   * contiguous fill-curve range; crossing a row includes the full fast axis in the query. */
  int classify(long from, long end, long[] a, long[] b) {
    if (support == null) return 1;
    if (support.polygon.isEmpty()) return -1;
    box.decode(from, curve, a); box.decode(end - 1, curve, b);
    int fast = curve == Data.FillCurve.D2_YX ? 0 : 1, slow = 1 - fast;
    long minFast = Math.min(a[fast], b[fast]), maxFast = Math.max(a[fast], b[fast]);
    if (a[slow] != b[slow]) { minFast = 0; maxFast = box.shape[fast] - 1; }
    double[] low = new double[2], high = new double[2];
    low[fast] = centre(minFast, fast); high[fast] = centre(maxFast, fast);
    low[slow] = centre(Math.min(a[slow], b[slow]), slow);
    high[slow] = centre(Math.max(a[slow], b[slow]), slow);
    double width = high[0] - low[0], west = wrap(low[0]), east = west + width;
    if (grid.world().length == 4 && east >= grid.world()[1]) {
      int left = region(west, grid.world()[1], low[1], high[1]);
      int right = region(grid.world()[0], east - (grid.world()[1] - grid.world()[0]), low[1], high[1]);
      return left == right ? left : 0;
    }
    return region(west, east, low[1], high[1]);
  }

  private int region(double west, double east, double south, double north) {
    var e = support.envelope;
    if (east < e.getMinX() || west > e.getMaxX() || north < e.getMinY() || south > e.getMaxY()) return -1;
    if (support.rectangle)
      return west >= e.getMinX() && east <= e.getMaxX() && south >= e.getMinY() && north <= e.getMaxY() ? 1 : 0;
    var candidate = support.polygon.getFactory().toGeometry(new org.locationtech.jts.geom.Envelope(west, east, south, north));
    if (support.prepared.covers(candidate)) return 1;
    return support.prepared.intersects(candidate) ? 0 : -1;
  }

  boolean contains(long index, long[] point, Coordinate coordinate) {
    if (unrestricted) return true;
    box.decode(index, curve, point);
    coordinate.x = wrap(centre(point[0], 0)); coordinate.y = centre(point[1], 1);
    return support.rectangle ? support.envelope.covers(coordinate)
        : support.points.locate(coordinate) != Location.EXTERIOR;
  }

  /** Per-cursor accepted span. Recursion uses at most 63 frames, independently of state count. */
  final class Cursor {
    final long[] a = new long[2], b = new long[2];
    final Coordinate coordinate = new Coordinate();
    long acceptedStart = -1, acceptedEnd = -1;
    long rangesTested;
    long next(long offset) {
      if (offset == box.size || unrestricted) return offset;
      if (offset >= acceptedStart && offset < acceptedEnd) return offset;
      return search(offset, box.size);
    }
    private long search(long from, long end) {
      rangesTested++;
      int status = classify(from, end, a, b);
      if (status == -1) return box.size;
      if (status == 1 || end - from == 1 && contains(from, a, coordinate)) {
        acceptedStart = from; acceptedEnd = end; return from;
      }
      if (end - from == 1) return box.size;
      long middle = from + (end - from) / 2;
      long found = search(from, middle);
      return found < box.size ? found : search(middle, end);
    }
  }

  void coordinates(long offset, long[] target) { box.decode(offset, curve, target); }

  long coveredSize() {
    if (unrestricted) return box.size;
    var cursor = new Cursor();
    long count = 0, offset = 0;
    while ((offset = cursor.next(offset)) < box.size) {
      count = Math.addExact(count, cursor.acceptedEnd - offset);
      offset = cursor.acceptedEnd;
    }
    return count;
  }

  StorageScan.Cell cell(long offset) {
    if (grid.shape().length != 2) throw new UnsupportedOperationException("Cell footprints require a 2D spatial grid");
    long[] xy = new long[2]; coordinates(offset, xy);
    double west = grid.bounds()[0] + xy[0] * dx, east = grid.bounds()[0] + (xy[0] + 1) * dx;
    double south = grid.bounds()[2] + xy[1] * dy, north = grid.bounds()[2] + (xy[1] + 1) * dy;
    var bounds = new ArrayList<StorageScan.CellBounds>(2);
    if (grid.world().length == 4) {
      south = Math.max(south, grid.world()[2]); north = Math.min(north, grid.world()[3]);
      double shift = Math.floor((west - grid.world()[0]) / (grid.world()[1] - grid.world()[0]))
          * (grid.world()[1] - grid.world()[0]);
      west -= shift; east -= shift;
      if (east > grid.world()[1]) {
        bounds.add(new StorageScan.CellBounds(west, grid.world()[1], south, north));
        bounds.add(new StorageScan.CellBounds(grid.world()[0], east - (grid.world()[1] - grid.world()[0]), south, north));
      }
    }
    if (bounds.isEmpty()) bounds.add(new StorageScan.CellBounds(west, east, south, north));
    return new StorageScan.Cell(xy[0], xy[1], grid.projection(), bounds);
  }
}
