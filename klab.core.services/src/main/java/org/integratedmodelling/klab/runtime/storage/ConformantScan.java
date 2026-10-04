package org.integratedmodelling.klab.runtime.storage;

import java.util.*;
import org.integratedmodelling.klab.api.data.Data.FillCurve;
import org.integratedmodelling.klab.api.data.StorageScan;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;

/** Immutable, metadata-only regular-grid mapping. All payload indices remain long. */
final class ConformantScan implements ScanMapping {
  public List<StorageScan.Partition> partitions() { return partitions; }
  public int[][] dependencies() { return dependencies; }
  public IndexedStorageReader reader(int partition, List<IndexedStorageReader> readers, int blockValues) {
    return new ConformantReader(this, partition, readers, blockValues);
  }
  final List<StorageScan.Partition> partitions;
  final Box[] sources;
  final Box[] targets;
  final int[][] dependencies;
  final Directory directory;
  final FillCurve sourceCurve, targetCurve;
  final long longitudePeriod;
  final String projection;

  private ConformantScan(List<StorageScan.Partition> partitions, Box[] sources, Box[] targets,
      int[][] dependencies, Directory directory, FillCurve sourceCurve, FillCurve targetCurve, long longitudePeriod, String projection) {
    this.partitions = List.copyOf(partitions); this.sources = sources; this.targets = targets;
    this.dependencies = dependencies; this.directory = directory;
    this.sourceCurve = sourceCurve; this.targetCurve = targetCurve; this.longitudePeriod=longitudePeriod;
    this.projection = projection;
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

  record Grid(Geometry geometry, long[] shape, double[] bounds, String projection, String others, double[] world) {
    static Grid read(String encoding, String inheritedProjection) {
      return read(encoding, inheritedProjection, true);
    }
    static Grid read(String encoding, String inheritedProjection, boolean located) {
      Geometry geometry = StorageScan.parseGeometry(encoding);
      var space = geometry.dimension(Geometry.Dimension.Type.SPACE);
      if (space == null || !space.isRegular() || space.isGeneric()
          || space.getDimensionality() < 1 || space.getDimensionality() > 3)
        throw unsupported("regular spatial grids of one to three dimensions are required");
      // Concrete bounds and cell counts are authoritative; sgrid is only a resolution hint.
      // Never ignore an unknown transform/rotation or external grid definition when remapping.
      for (String key : space.getParameters().keySet())
        if (!Set.of("proj", "bbox", "shape", "sgrid", "gridalignment", "world").contains(key))
          throw unsupported("unsupported spatial grid parameter: " + key);
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
        if (shape.length != 2 || polygon.getJTSGeometry().getDimension() != 2 || !polygon.getJTSGeometry().isValid())
          throw unsupported("invalid areal coverage");
        var envelope = polygon.getEnvelope();
        double[] polygonBounds = {envelope.getMinX(), envelope.getMaxX(), envelope.getMinY(), envelope.getMaxY()};
        if (bounds == null) bounds = polygonBounds;
        // TileImpl retains the original rectangular support when GridImpl adjusts its envelope
        // to the cell lattice. The explicit bbox describes the stored cells, including boundary
        // cells; the support envelope must not replace it or prevent a lossless index remap.
      }
      if (bounds == null || bounds.length != shape.length * 2) throw unsupported("missing grid bounds");
      for (int d = 0; d < shape.length; d++)
        if (!Double.isFinite(bounds[2*d]) || !Double.isFinite(bounds[2*d+1])
            || bounds[2*d+1] <= bounds[2*d]) throw unsupported("invalid grid bounds");
      double[] world = new double[0];
      if (space.getParameters().get("world") != null) {
        String[] parts=space.getParameters().get("world").toString().replace('[',' ').replace(']',' ').trim().split("[\\s,]+");
        world=Arrays.stream(parts).mapToDouble(Double::parseDouble).toArray();
        if (world.length!=4 || shape.length!=2 || !Arrays.stream(world).allMatch(Double::isFinite)
            || world[0]>=world[1] || world[2]>=world[3]) throw unsupported("invalid world bounds");
        // World metadata authorizes clipping/wrapping; it must match the declared CRS domain.
        try {
          double[] expected=org.integratedmodelling.klab.runtime.scale.space.GridAlignmentSupport.worldBounds(
              org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection.of(projection));
          if (expected.length!=4) throw unsupported("world boundary metadata requires a supported global CRS");
          for(int axis=0;axis<4;axis++) if(Math.abs(world[axis]-expected[axis])>1e-9*Math.max(1,Math.abs(expected[axis])))
            throw unsupported("world bounds differ from CRS domain");
          double dx=(bounds[1]-bounds[0])/shape[0],dy=(bounds[3]-bounds[2])/shape[1];
          double period=(world[1]-world[0])/dx;
          if (Math.abs(period-Math.rint(period))>1e-7 || period>0x1p52 || shape[0]>Math.rint(period)
              || bounds[2]<=world[2]-dy || bounds[3]>=world[3]+dy
              || bounds[2]>=world[3] || bounds[3]<=world[2]) throw unsupported("world grid contains duplicate or empty boundary cells");
        } catch (UnsupportedOperationException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("Cannot validate world grid",e); }
      }
      return new Grid(geometry, shape, bounds, projection,
          geometry.getDimensions().stream().filter(d -> d.getType() != Geometry.Dimension.Type.SPACE)
              .map(Geometry.Dimension::encode).collect(java.util.stream.Collectors.joining()),world);
    }
  }

  static final class Lattice {
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
      text.append("]");
      if (reference.world.length==4) text.append(",world=").append(Arrays.toString(reference.world).replace(",", ""));
      return text.append("}").toString();
    }
  }

  static ConformantScan compile(List<StorageScan.SourceShard> descriptors,
      StorageScan.Request<?> request, String observationGeometry) {
    var ownerSpace = StorageScan.parseGeometry(observationGeometry).dimension(Geometry.Dimension.Type.SPACE);
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
    Grid ownerGrid = Grid.read(observationGeometry, inherited, false);
    Box ownerCoverage = lattice.box(ownerGrid, false);
    verifyCover(sources, directory, ownerCoverage);
    long period = ownerGrid.world.length==4 ? lattice.snap((ownerGrid.world[1]-ownerGrid.world[0])/lattice.step[0]) : 0;
    Box requestedCoverage = directory.bounds;
    if (request.geometry() != null) {
      Grid requestedGrid = Grid.read(request.geometry(), inherited, false);
      // The unchanged observation time describes coverage; Slice selects the actual revision.
      boolean ownerContext = requestedGrid.others.equals(ownerGrid.others);
      Box coverage = lattice.box(Grid.read(request.geometry(), inherited, !ownerContext), !ownerContext);
      if (!equivalent(coverage, directory.bounds,period)) throw unsupported("requested coverage differs");
      requestedCoverage=coverage;
    }
    List<StorageScan.Partition> partitions = request.partitions();
    Box[] targets;
    if (partitions.isEmpty()) {
      List<Box> split = split(requestedCoverage, request.layout(), request.budget().maxPartitions());
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
    verifyCover(targets, Directory.of(targets), requestedCoverage);
    int[][] dependencies = new int[targets.length][];
    long remainingLinks = Math.multiplyExact((long) request.budget().maxPartitions(), 8);
    for (int i = 0; i < targets.length; i++) {
      if (request.layout().maxSize() > 0 && targets[i].size > request.layout().maxSize())
        throw new IllegalArgumentException("Consumer partition exceeds maximum state count");
      var links = new ArrayList<Integer>();
      if (period==0) directory.collect(targets[i],links,remainingLinks);
      else {
        // At most two translated target windows intersect a source interval no wider than a period.
        Box t=targets[i];
        long shift=Math.floorDiv(directory.bounds.start[0]-t.start[0],period);
        for (long delta=shift;delta<=shift+1;delta++) {
          long[] start=t.start.clone(); start[0]=Math.addExact(start[0],Math.multiplyExact(delta,period));
          var translated=new ArrayList<Integer>(); directory.collect(new Box(start,t.shape),translated,remainingLinks);
          for (int link:translated) if (!links.contains(link)) links.add(link);
        }
        if (links.size()>remainingLinks) throw new IllegalArgumentException("Source-link budget exceeded");
      }
      dependencies[i] = links.stream().mapToInt(Integer::intValue).toArray(); remainingLinks -= links.size();
    }
    return new ConformantScan(partitions, sources, targets, dependencies, directory, sourceCurve, targetCurve,period,first.projection());
  }

  /** Periodic intervals describe the same cells when their phase matches, or both cover a full turn. */
  private static boolean equivalent(Box a, Box b, long period) {
    if (period==0) return same(a,b);
    return Arrays.equals(a.shape,b.shape) && a.start[1]==b.start[1]
        && (a.shape[0]==period || Math.floorMod(a.start[0]-b.start[0],period)==0);
  }

  void locatePeriodic(long[] point) {
    if (longitudePeriod>0) point[0]=directory.bounds.start[0]+Math.floorMod(point[0]-directory.bounds.start[0],longitudePeriod);
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
