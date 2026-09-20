package org.integratedmodelling.klab.runtime.storage;

import java.util.*;
import org.integratedmodelling.klab.api.data.Data.FillCurve;
import org.integratedmodelling.klab.api.data.StorageScan;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;

/** Immutable, metadata-only regular-grid mapping. All payload indices remain long. */
final class ConformantScan {
  final List<StorageScan.Partition> partitions;
  final Box[] sources;
  final Box[] targets;
  final int[][] dependencies;
  final Directory directory;
  final FillCurve sourceCurve, targetCurve;

  private ConformantScan(List<StorageScan.Partition> partitions, Box[] sources, Box[] targets,
      int[][] dependencies, Directory directory, FillCurve sourceCurve, FillCurve targetCurve) {
    this.partitions = List.copyOf(partitions); this.sources = sources; this.targets = targets;
    this.dependencies = dependencies; this.directory = directory;
    this.sourceCurve = sourceCurve; this.targetCurve = targetCurve;
  }

  static UnsupportedOperationException unsupported(String reason) {
    return new UnsupportedOperationException("Non-conformant scan: " + reason);
  }

  /** Checked mixed-radix codec, independent of the legacy int-indexed curve API. */
  static final class Box {
    final long[] start, shape;
    final long size;
    Box(long[] start, long[] shape) {
      this.start = start.clone(); this.shape = shape.clone();
      long volume = 1;
      for (int d = 0; d < shape.length; d++) {
        if (shape[d] < 1) throw unsupported("mapping requires nonempty grid axes");
        Math.addExact(start[d], shape[d]);
        volume = Math.multiplyExact(volume, shape[d]);
      }
      size = volume;
    }
    boolean contains(long[] point) {
      for (int d = 0; d < shape.length; d++)
        if (point[d] < start[d] || point[d] - start[d] >= shape[d]) return false;
      return true;
    }
    boolean overlaps(Box other) {
      for (int d = 0; d < shape.length; d++)
        if (start[d] >= other.start[d] + other.shape[d]
            || other.start[d] >= start[d] + shape[d]) return false;
      return true;
    }
    void decode(long index, FillCurve curve, long[] point) {
      if (index < 0 || index >= size) throw new IndexOutOfBoundsException("View offset " + index);
      if (curve == FillCurve.D2_YX) {
        point[0] = start[0] + index % shape[0];
        point[1] = start[1] + index / shape[0];
      } else {
        for (int d = shape.length - 1; d >= 0; d--) {
          long coordinate = index % shape[d]; index /= shape[d];
          if (curve == FillCurve.D2_XInvY && d == 1) coordinate = shape[d] - 1 - coordinate;
          point[d] = start[d] + coordinate;
        }
      }
    }
    long encode(long[] point, FillCurve curve) {
      if (!contains(point)) throw new IndexOutOfBoundsException("Cell outside source shard");
      if (curve == FillCurve.D2_YX)
        return (point[1] - start[1]) * shape[0] + point[0] - start[0];
      long index = 0;
      for (int d = 0; d < shape.length; d++) {
        long coordinate = point[d] - start[d];
        if (curve == FillCurve.D2_XInvY && d == 1) coordinate = shape[d] - 1 - coordinate;
        index = index * shape[d] + coordinate;
      }
      return index; // bounded by the checked volume
    }
  }

  static void curve(FillCurve curve, int dimensions) {
    if (curve == FillCurve.D1_LINEAR) return;
    if (dimensions == 2 && (curve == FillCurve.D2_XY || curve == FillCurve.D2_YX
        || curve == FillCurve.D2_XInvY)) return;
    if (dimensions == 3 && curve == FillCurve.D3_XYZ) return;
    throw unsupported("unsupported curve/dimensionality: " + curve + "/" + dimensions);
  }

  /** Balanced bounding-volume directory; no cell-sized maps, no per-lookup allocations. */
  static final class Directory {
    final Box bounds;
    final int source;
    final Directory left, right;
    Directory(Box[] boxes, Integer[] order, int from, int to) {
      long[] low = boxes[order[from]].start.clone();
      long[] high = low.clone();
      for (int i = from; i < to; i++) for (int d = 0; d < low.length; d++) {
        var box = boxes[order[i]];
        low[d] = Math.min(low[d], box.start[d]);
        high[d] = Math.max(high[d], box.start[d] + box.shape[d]);
      }
      long[] shape = new long[low.length]; int axis = 0;
      for (int d = 0; d < low.length; d++) {
        shape[d] = Math.subtractExact(high[d], low[d]);
        if (shape[d] > shape[axis]) axis = d;
      }
      bounds = new Box(low, shape);
      if (to - from == 1) { source = order[from]; left = right = null; }
      else {
        int sortAxis = axis;
        Arrays.sort(order, from, to, Comparator.comparingLong(i -> boxes[i].start[sortAxis]));
        int middle = (from + to) >>> 1;
        left = new Directory(boxes, order, from, middle);
        right = new Directory(boxes, order, middle, to); source = -1;
      }
    }
    static Directory of(Box[] boxes) {
      Integer[] order = new Integer[boxes.length];
      for (int i = 0; i < boxes.length; i++) order[i] = i;
      return new Directory(boxes, order, 0, order.length);
    }
    int find(long[] point) {
      if (!bounds.contains(point)) return -1;
      if (source >= 0) return source;
      int found = left.find(point);
      return found >= 0 ? found : right.find(point);
    }
    boolean overlapsOther(Box box, int self) {
      if (!bounds.overlaps(box)) return false;
      return source >= 0 ? source != self
          : left.overlapsOther(box, self) || right.overlapsOther(box, self);
    }
    void collect(Box box, List<Integer> result, long limit) {
      if (!bounds.overlaps(box)) return;
      if (source >= 0) {
        if (result.size() >= limit) throw new IllegalArgumentException("Source-link budget exceeded");
        result.add(source);
      } else { left.collect(box, result, limit); right.collect(box, result, limit); }
    }
  }

  private record Grid(Geometry geometry, long[] shape, double[] bounds, String projection, String others) {
    static Grid read(String encoding, String inheritedProjection) {
      return read(encoding, inheritedProjection, true);
    }
    static Grid read(String encoding, String inheritedProjection, boolean located) {
      Geometry geometry = StorageScan.parseGeometry(encoding);
      var space = geometry.dimension(Geometry.Dimension.Type.SPACE);
      if (space == null || !space.isRegular() || space.isGeneric()
          || space.getDimensionality() < 1 || space.getDimensionality() > 3)
        throw unsupported("regular spatial grids of one to three dimensions are required");
      long[] shape = space.getShape().stream().mapToLong(Long::longValue).toArray();
      if (shape.length != space.getDimensionality()) throw unsupported("unspecified grid shape");
      new Box(new long[shape.length], shape);
      for (var dim : geometry.getDimensions())
        if (located && dim.getType() != Geometry.Dimension.Type.SPACE && dim.size() != 1)
          throw unsupported("non-spatial dimensions must be located to one state");
      String projection = Objects.toString(space.getParameters().get("proj"), inheritedProjection);
      if (projection == null || projection.isBlank()) throw unsupported("missing CRS definition");
      double[] bounds = null;
      Object bbox = space.getParameters().get("bbox");
      if (bbox != null) {
        String text = bbox.toString().replace('[', ' ').replace(']', ' ').trim();
        String[] parts = text.split("[\\s,]+"); bounds = new double[parts.length];
        for (int i = 0; i < parts.length; i++) bounds[i] = Double.parseDouble(parts[i]);
      }
      Object shapeDefinition = space.getParameters().get("shape");
      if (shapeDefinition != null) {
        var polygon = ShapeImpl.create(shapeDefinition.toString());
        if (!projection.equals(polygon.getProjection().getCode())) throw unsupported("shape CRS differs from grid CRS");
        if (shape.length != 2 || !polygon.getJTSGeometry().isRectangle())
          throw unsupported("masked or nonrectangular coverage");
        var envelope = polygon.getEnvelope();
        double[] polygonBounds = {envelope.getMinX(), envelope.getMaxX(), envelope.getMinY(), envelope.getMaxY()};
        if (bounds == null) bounds = polygonBounds;
        // Shape can describe the un-clipped grid's support; differing bounds need mask mediation.
        else if (!Arrays.equals(bounds, polygonBounds)) throw unsupported("shape and grid bounds differ");
      }
      if (bounds == null || bounds.length != shape.length * 2) throw unsupported("missing grid bounds");
      for (int d = 0; d < shape.length; d++)
        if (!Double.isFinite(bounds[2*d]) || !Double.isFinite(bounds[2*d+1])
            || bounds[2*d+1] <= bounds[2*d]) throw unsupported("invalid grid bounds");
      return new Grid(geometry, shape, bounds, projection,
          geometry.getDimensions().stream().filter(d -> d.getType() != Geometry.Dimension.Type.SPACE)
              .map(Geometry.Dimension::encode).collect(java.util.stream.Collectors.joining()));
    }
  }

  private static final class Lattice {
    final Grid reference;
    final double[] step;
    Lattice(Grid reference) {
      this.reference = reference; step = new double[reference.shape.length];
      for (int d = 0; d < step.length; d++) {
        step[d] = (reference.bounds[2*d+1] - reference.bounds[2*d]) / reference.shape[d];
        if (!Double.isFinite(step[d]) || step[d] <= 0) throw unsupported("invalid cell size");
      }
    }
    long snap(double value) {
      double nearest = Math.rint(value);
      double tolerance = Math.max(1e-8, 8 * Math.ulp(value));
      if (!Double.isFinite(value) || Math.abs(value) > (1L << 52) || tolerance > 1e-4
          || Math.abs(value - nearest) > tolerance) throw unsupported("cell edges are not aligned");
      return (long) nearest;
    }
    Box box(Grid grid, boolean checkOthers) {
      if (!grid.projection.equals(reference.projection)) throw unsupported("CRS differs");
      if (grid.shape.length != step.length) throw unsupported("spatial dimensionality differs");
      if (checkOthers && !grid.others.isEmpty() && !grid.others.equals(reference.others)) throw unsupported("non-spatial extents differ: " + grid.others + " vs " + reference.others);
      long[] start = new long[step.length];
      for (int d = 0; d < step.length; d++) {
        double resolution = (grid.bounds[2*d+1] - grid.bounds[2*d]) / grid.shape[d];
        if (Math.abs(resolution / step[d] - 1) > 1e-9) throw unsupported("cell resolution differs");
        start[d] = snap((grid.bounds[2*d] - reference.bounds[2*d]) / step[d]);
        long end = snap((grid.bounds[2*d+1] - reference.bounds[2*d]) / step[d]);
        if (end != Math.addExact(start[d], grid.shape[d])) throw unsupported("cell counts differ");
      }
      return new Box(start, grid.shape);
    }
    String geometry(Box box) {
      var text = new StringBuilder(reference.others);
      text.append('S').append(step.length).append('(');
      for (int d = 0; d < step.length; d++) { if (d > 0) text.append(','); text.append(box.shape[d]); }
      text.append("){proj=").append(reference.projection).append(",bbox=[");
      for (int d = 0; d < step.length; d++) {
        if (d > 0) text.append(' ');
        text.append(reference.bounds[2*d] + box.start[d] * step[d]).append(' ')
            .append(reference.bounds[2*d] + (box.start[d] + box.shape[d]) * step[d]);
      }
      return text.append("]}").toString();
    }
  }

  static ConformantScan compile(List<StorageScan.SourceShard> descriptors,
      StorageScan.Request<?> request, String observationGeometry) {
    var ownerSpace = StorageScan.parseGeometry(observationGeometry).dimension(Geometry.Dimension.Type.SPACE);
    if (ownerSpace != null && ownerSpace.getParameters().get("shape") != null
        && !ShapeImpl.create(ownerSpace.getParameters().get("shape").toString()).getJTSGeometry().isRectangle())
      throw unsupported("observation has masked or nonrectangular coverage");
    String inherited = ownerSpace == null ? null : Objects.toString(ownerSpace.getParameters().get("proj"), null);
    Grid first = Grid.read(descriptors.getFirst().geometry(), inherited);
    Lattice lattice = new Lattice(first);
    FillCurve sourceCurve = descriptors.getFirst().layout().curve(), targetCurve = request.layout().curve();
    curve(sourceCurve, first.shape.length); curve(targetCurve, first.shape.length);
    Box[] sources = new Box[descriptors.size()];
    for (int i = 0; i < sources.length; i++) {
      sources[i] = lattice.box(Grid.read(descriptors.get(i).geometry(), inherited), true);
      if (sources[i].size != descriptors.get(i).size()) throw unsupported("source geometry size differs");
    }
    Directory directory = Directory.of(sources);
    Box ownerCoverage = lattice.box(Grid.read(observationGeometry, inherited, false), false);
    verifyCover(sources, directory, ownerCoverage);
    if (request.geometry() != null) {
      boolean fullObservation = request.geometry().equals(StorageScan.parseGeometry(observationGeometry).encode());
      Box coverage = lattice.box(Grid.read(request.geometry(), inherited, !fullObservation), !fullObservation);
      if (!same(coverage, directory.bounds)) throw unsupported("requested coverage differs");
    }
    List<StorageScan.Partition> partitions = request.partitions();
    Box[] targets;
    if (partitions.isEmpty()) {
      List<Box> split = split(directory.bounds, request.layout(), request.budget().maxPartitions());
      targets = split.toArray(Box[]::new);
      var generated = new ArrayList<StorageScan.Partition>();
      for (int i = 0; i < targets.length; i++)
        generated.add(new StorageScan.Partition("view-" + i, lattice.geometry(targets[i]), targets[i].size));
      partitions = generated;
    } else {
      targets = new Box[partitions.size()];
      for (int i = 0; i < targets.length; i++)
        targets[i] = lattice.box(Grid.read(partitions.get(i).geometry(), inherited), true);
    }
    verifyCover(targets, Directory.of(targets), directory.bounds);
    int[][] dependencies = new int[targets.length][];
    long remainingLinks = Math.multiplyExact((long) request.budget().maxPartitions(), 8);
    for (int i = 0; i < targets.length; i++) {
      if (request.layout().maxSize() > 0 && targets[i].size > request.layout().maxSize())
        throw new IllegalArgumentException("Consumer partition exceeds maximum state count");
      var links = new ArrayList<Integer>(); directory.collect(targets[i], links, remainingLinks);
      dependencies[i] = links.stream().mapToInt(Integer::intValue).toArray(); remainingLinks -= links.size();
    }
    return new ConformantScan(partitions, sources, targets, dependencies, directory, sourceCurve, targetCurve);
  }

  private static boolean same(Box a, Box b) {
    return Arrays.equals(a.start, b.start) && Arrays.equals(a.shape, b.shape);
  }
  private static void verifyCover(Box[] boxes, Directory directory, Box coverage) {
    long total = 0;
    for (int i = 0; i < boxes.length; i++) {
      if (directory.overlapsOther(boxes[i], i)) throw unsupported("overlapping partitions");
      total = Math.addExact(total, boxes[i].size);
    }
    if (!same(directory.bounds, coverage) || total != coverage.size)
      throw unsupported("partition coverage has gaps or a different extent");
  }

  private static List<Box> split(Box coverage, StorageScan.Layout layout, int budget) {
    long softCount = layout.splits() == -1 ? (layout.minSize() > 0 ? Math.max(1, coverage.size / layout.minSize()) : 1) : layout.splits();
    if (layout.minSize() > 0) softCount = Math.min(softCount, Math.max(1, coverage.size / layout.minSize()));
    softCount = Math.min(softCount, coverage.size);
    if (softCount > budget) throw new IllegalArgumentException("Consumer partition budget exceeded");
    var queue = new PriorityQueue<Box>(Comparator.comparingLong((Box b) -> b.size).reversed()
        .thenComparingLong(b -> b.start[0]));
    queue.add(coverage);
    while (queue.size() < softCount || layout.maxSize() > 0 && queue.peek().size > layout.maxSize()) {
      if (queue.size() >= budget) throw new IllegalArgumentException("Consumer partition budget exceeded");
      Box box = queue.remove(); int axis = 0;
      for (int d = 1; d < box.shape.length; d++) if (box.shape[d] > box.shape[axis]) axis = d;
      if (box.shape[axis] < 2) throw new IllegalArgumentException("Cannot split a single cell");
      long[] lowSize = box.shape.clone(), highSize = box.shape.clone(), highStart = box.start.clone();
      lowSize[axis] /= 2; highSize[axis] -= lowSize[axis]; highStart[axis] += lowSize[axis];
      queue.add(new Box(box.start, lowSize)); queue.add(new Box(highStart, highSize));
    }
    var result = new ArrayList<>(queue);
    result.sort((a, b) -> Arrays.compare(a.start, b.start));
    return result;
  }
}
