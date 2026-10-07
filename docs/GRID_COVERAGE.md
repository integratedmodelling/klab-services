# Shape coverage and scanner cell access

A grid defines a dense coordinate lattice. When a grid resolution or alignment instruction rounds
a rectangular request outward, its shape expands to cover the resulting grid, preserving the
unmasked rectangular path. Explicit shape masks carried with an already specified grid retain
their coverage. Nonrectangular polygons and multipolygons, including holes, retain their requested
shape independently of the grid's rectangular bounding box. Computation visits cells
whose **physical centre is covered by the shape**. Polygon and hole boundaries count as covered;
a cell that merely intersects the shape without containing a covered centre is excluded. This
preserves the legacy point-in-polygon approach rather than introducing an area-fraction rule.

Initialization writers, planned reads, and temporal writers and reads follow this rule. Scanners
skip excluded consumer cells. Source cells outside the source's own shape are missing, even when
the consumer's shape includes them. Resampling consults source coverage through validity. A covered
cell can still hold a missing value: coverage and payload validity are separate.

The output shape supplies the traversal mask for contextualizer bindings. Inputs and outputs with
the same consumer partition and fill curve visit the same dense indices, even when an input has
holes in its own support. Such inputs yield missing values at those locations. Partition identities,
payload indices, and allocated rectangular buffer sizes remain stable.

## Contextualizer API

`position()` is the dense partition-local index of the **next** value, never the count of covered
cells visited. `size()` remains the dense bounding-grid size. Iterate with `hasNext()` rather than
calling `get()` exactly `size()` times.

```java
long[] xy = new long[2]; // Reuse throughout the computation.
while (output.hasNext()) {
    long offset = output.position();
    output.spatialCoordinates(xy); // Allocation-free; respects the fill curve.
    boolean valid = input.isValid();
    double value = input.get();     // Same consumer cell; advances the input.
    output.add(valid ? compute(value, xy[0], xy[1]) : Double.NaN);
}
```

Optional accessors allocate only when requested:

* `cell()` returns partition-local X/Y, the CRS, and physical cell bounds.
* `cell().wkt()` returns plain polygon or multipolygon WKT.
* `cell().shape()` or `spatialExtent()` returns the spatial sub-extent as a `Shape`.
* `location()` retains the dense index and planned view metadata.

The extent describes the cell itself, not its intersection with the coverage polygon. Use shape
intersection APIs explicitly if needed; ordinary traversal does not intersect polygons per cell.
For anchored world grids, longitude wraps and polar bounds clip before testing the physical centre.
A seam cell remains one stored cell with two footprint fragments, exposed by the cell accessors.

`seek(offset)` selects the first covered dense index at or after the offset, or `size()` at exhaustion.
`nextLong()` returns the visited dense index. Inspect `position()` after seeking for exact indexed
access. Storage text/point access returns `null` for an excluded requested index, rather than the
value of a later covered cell. Coordinate and extent access throw at exhaustion.

`Tile.isCellCovered(x, y)` exposes the same centre rule. Copies share the tile's immutable coverage
indexes; transport retains the original shape and reconstructs indexes locally.

## Efficiency and persistence

Fully covered rectangles retain their original primitive scanner, with no coverage decorator,
point predicate, bitmap, or new descriptor version. Rectangular support smaller than a snapped
bounding grid uses coordinate comparisons to exclude exterior centres.

For irregular coverage, the storage instance shares an immutable polygon and prepared geometry
among partitions and sessions. A task-local cursor classifies contiguous fill-curve index ranges
using a conservative bounding rectangle of their centres. Fully covered ranges are accepted;
disjoint ranges are skipped; intersecting ranges subdivide down to indexed point-in-polygon tests
at boundary cells. Accepted spans are cached, so primitive reads and writes inside them allocate
nothing per cell. Source readers cache covered/excluded spans for random spatial sampling too.

Subdivision needs at most 63 stack frames for long indices. Memory depends on polygon complexity
and active cursors, not grid size. Complex boundary portions incur geometry queries and temporary
objects; covered interiors and large empty regions do not incur those costs. Payload storage stays
dense: this optimizes computation and traversal, not storage compression.

Histograms omit excluded cells. Created temporal observations must explicitly fill every covered
cell, rather than every cell in the bounding rectangle. Commit and restore preserve shape support
and dense index mapping. Scan description **version 7** includes frozen source/target support in
`mask` and in its fingerprint; versions 1â€“6 keep their previous fingerprints and contracts.
Prepared JTS objects are local indexes, not serialized transport state.

Tests compare against independent point-in-polygon checks for triangles, holes, and disconnected
shapes across XY, YX, inverted-Y curves, partitions, and primitive types. They also cover paired
contextualizer bindings, temporal persistence, world boundaries, and allocation. A metadata-only
trillion-cell grid selects two distant cells with fewer than 300 range queries and no cell bitmap.
