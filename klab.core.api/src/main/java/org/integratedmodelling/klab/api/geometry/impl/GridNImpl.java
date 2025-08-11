package org.integratedmodelling.klab.api.geometry.impl;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.GridN;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Minimal implementation of the GridN interface, modeled after GridImpl but generalized
 * to N dimensions. This class focuses on carrying the grid specification and simple
 * computations (resolution, size, cell counts) without attempting complex spatial
 * adjustments. Alignment is currently a no-op placeholder that returns this instance.
 */
public class GridNImpl implements GridN {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int dimensions;
    private final double[] cellSizes; // length == dimensions
    private final double[][] bounds;  // nullable; when non-null, length == dimensions and bounds[d] = [min,max]
    private final long[] cells;       // derived from bounds and cellSizes; zeros if bounds == null
    private final List<double[]> anchorPoints; // points of length==dimensions
    private final Projection projection; // may be null for non-spatial usages
    private final boolean hypercubicCells;
    private final List<GridN> subGrids; // non-null only for merged grids

    public GridNImpl(int dimensions,
                     double[] cellSizes,
                     double[][] bounds,
                     Projection projection,
                     boolean hypercubicCells,
                     List<double[]> anchorPoints) {
        this(dimensions, cellSizes, bounds, projection, hypercubicCells, anchorPoints, null);
    }

    private GridNImpl(int dimensions,
                      double[] cellSizes,
                      double[][] bounds,
                      Projection projection,
                      boolean hypercubicCells,
                      List<double[]> anchorPoints,
                      List<GridN> subGrids) {
        if (dimensions < 1) {
            throw new IllegalArgumentException("dimensions must be >= 1");
        }
        this.dimensions = dimensions;
        this.cellSizes = normalizeCellSizes(dimensions, cellSizes);
        this.bounds = normalizeBounds(dimensions, bounds);
        this.projection = projection;
        this.hypercubicCells = hypercubicCells;
        this.anchorPoints = anchorPoints == null ? Collections.emptyList() : copyAndValidateAnchors(dimensions, anchorPoints);
        this.cells = computeCells(this.bounds, this.cellSizes);
        this.subGrids = (subGrids == null || subGrids.isEmpty()) ? null : Collections.unmodifiableList(new ArrayList<>(subGrids));
    }

    public static GridNImpl of(double[] cellSizes, double[][] bounds, Projection projection, boolean hypercubicCells) {
        int dims = cellSizes != null ? cellSizes.length : (bounds != null ? bounds.length : 0);
        if (dims < 1) {
            throw new IllegalArgumentException("Unable to infer dimensions: provide cellSizes and/or bounds");
        }
        return new GridNImpl(dims, cellSizes, bounds, projection, hypercubicCells, null);
    }

    private static double[] normalizeCellSizes(int dims, double[] sizes) {
        double[] out = new double[dims];
        if (sizes != null) {
            if (sizes.length != dims) {
                throw new IllegalArgumentException("cellSizes length must equal dimensions");
            }
            System.arraycopy(sizes, 0, out, 0, dims);
        } else {
            // default to 0 indicating unspecified sizes
            for (int i = 0; i < dims; i++) out[i] = 0d;
        }
        return out;
    }

    private static double[][] normalizeBounds(int dims, double[][] b) {
        if (b == null) return null;
        if (b.length != dims) {
            throw new IllegalArgumentException("bounds length must equal dimensions");
        }
        double[][] out = new double[dims][2];
        for (int d = 0; d < dims; d++) {
            double[] dim = b[d];
            if (dim == null || dim.length != 2) {
                throw new IllegalArgumentException("bounds[" + d + "] must be a [min,max] pair");
            }
            double min = dim[0];
            double max = dim[1];
            if (Double.isNaN(min) || Double.isNaN(max) || max < min) {
                throw new IllegalArgumentException("invalid bounds for dimension " + d);
            }
            out[d][0] = min;
            out[d][1] = max;
        }
        return out;
    }

    private static List<double[]> copyAndValidateAnchors(int dims, List<double[]> anchors) {
        List<double[]> list = new ArrayList<>(anchors.size());
        for (double[] p : anchors) {
            if (p == null || p.length != dims) {
                throw new IllegalArgumentException("each anchor point must be a double[" + dims + "]");
            }
            double[] copy = new double[dims];
            System.arraycopy(p, 0, copy, 0, dims);
            list.add(copy);
        }
        return Collections.unmodifiableList(list);
    }

    private static long[] computeCells(double[][] bounds, double[] cellSizes) {
        if (bounds == null) {
            return new long[cellSizes.length]; // zeros
        }
        int dims = cellSizes.length;
        long[] out = new long[dims];
        for (int d = 0; d < dims; d++) {
            double size = cellSizes[d];
            if (size <= 0) {
                out[d] = 0; // unspecified size -> cannot compute
            } else {
                double span = bounds[d][1] - bounds[d][0];
                out[d] = (long) Math.ceil(span / size);
            }
        }
        return out;
    }

    @Override
    public int getDimensions() {
        return dimensions;
    }

    @Override
    public double[] getCellSizes() {
        double[] copy = new double[cellSizes.length];
        System.arraycopy(cellSizes, 0, copy, 0, cellSizes.length);
        return copy;
    }

    @Override
    public double getCellSize(int dimension) {
        Objects.checkIndex(dimension, dimensions);
        return cellSizes[dimension];
    }

    @Override
    public double resolution() {
        if (dimensions == 0) return 0;
        double logSum = 0;
        int count = 0;
        for (double s : cellSizes) {
            if (s <= 0) return 0; // unspecified
            logSum += Math.log(s);
            count++;
        }
        if (count == 0) return 0;
        return Math.exp(logSum / count);
    }

    @Override
    public long size() {
        if (bounds == null) return 0;
        long prod = 1;
        for (long c : cells) {
            if (c <= 0) return 0;
            prod = Math.multiplyExact(prod, c);
        }
        return prod;
    }

    @Override
    public GridN locate(double[][] newBounds) {
        // Keep projection, cell sizes, anchors, and hypercubic flag; only change bounds and derived cells
        return new GridNImpl(dimensions, cellSizes, newBounds, projection, hypercubicCells, anchorPoints);
    }

    @Override
    public long[] getCells() {
        long[] copy = new long[cells.length];
        System.arraycopy(cells, 0, copy, 0, cells.length);
        return copy;
    }

    @Override
    public long getCells(int dimension) {
        Objects.checkIndex(dimension, dimensions);
        return cells[dimension];
    }

    @Override
    public List<double[]> getAnchorPoints() {
        return anchorPoints;
    }

    @Override
    public double[][] getBounds() {
        if (bounds == null) return null;
        double[][] copy = new double[dimensions][2];
        for (int d = 0; d < dimensions; d++) {
            copy[d][0] = bounds[d][0];
            copy[d][1] = bounds[d][1];
        }
        return copy;
    }

    @Override
    public Projection getProjection() {
        return projection;
    }

    @Override
    public GridN align(GridN other) {
        // Minimal placeholder: if both have bounds, intersect per-dimension; otherwise return this.
        if (other == null) return this;
        double[][] b1 = this.bounds;
        double[][] b2 = other.getBounds();
        if (b1 == null || b2 == null) {
            return this;
        }
        if (other.getDimensions() != this.dimensions) {
            return this; // incompatible; conservative choice
        }
        double[][] ib = new double[dimensions][2];
        for (int d = 0; d < dimensions; d++) {
            double min = Math.max(b1[d][0], b2[d][0]);
            double max = Math.min(b1[d][1], b2[d][1]);
            if (max < min) {
                // empty intersection -> return this unchanged (or could return zero-size)
                return this;
            }
            ib[d][0] = min;
            ib[d][1] = max;
        }
        return locate(ib);
    }

    @Override
    public boolean isHypercubicCells() {
        return hypercubicCells;
    }

    @Override
    public List<GridN> split(int suggestedSplits) {
        // Edge cases
        if (suggestedSplits <= 1 || bounds == null) {
            return List.of(this);
        }
        // If any dimension has zero or unspecified cells, cannot split
        for (long c : cells) {
            if (c <= 0) {
                return List.of(this);
            }
        }

        // Determine target number of tiles (cap by total number of cells, avoid overflow)
        long maxTiles = 1L;
        boolean overflow = false;
        for (long c : cells) {
            if (c <= 0) {
                return List.of(this);
            }
            long prev = maxTiles;
            long prod = prev * c;
            if (prev != 0 && prod / prev != c) {
                overflow = true;
                break;
            }
            maxTiles = prod;
        }
        if (overflow) {
            maxTiles = Long.MAX_VALUE; // clamp if absurdly large
        }
        long target = suggestedSplits < 1 ? 1 : suggestedSplits;
        if (maxTiles != Long.MAX_VALUE) {
            target = Math.min(target, maxTiles);
        }
        if (target <= 1) {
            return List.of(this);
        }

        // Greedy allocation of per-dimension splits s[d]
        int n = dimensions;
        long[] s = new long[n];
        for (int i = 0; i < n; i++) s[i] = 1;
        long product = 1L;
        long prevProduct = 1L;
        int lastIncDim = -1;

        while (product < target) {
            // choose dimension to increment: the one with highest c[d]/s[d], respecting s[d] < cells[d]
            int bestDim = -1;
            double bestScore = -1.0;
            for (int d = 0; d < n; d++) {
                if (s[d] < cells[d]) {
                    double score = (double) cells[d] / (double) s[d];
                    if (score > bestScore) {
                        bestScore = score;
                        bestDim = d;
                    }
                }
            }
            if (bestDim < 0) {
                break; // cannot increase further
            }
            // track previous
            prevProduct = product;
            long old = s[bestDim];
            long newVal = old + 1;
            // update product carefully avoiding overflow
            long newProduct;
            if (product == 0) {
                newProduct = 0;
            } else {
                long tmp = product / old;
                // guard division by zero (old is >=1)
                long mul = tmp * newVal;
                if (tmp != 0 && mul / tmp != newVal) {
                    // overflow, stop
                    break;
                }
                newProduct = mul;
            }
            s[bestDim] = newVal;
            product = newProduct == 0 ? product : newProduct;
            lastIncDim = bestDim;
            if (newProduct == 0) {
                // if we failed to update due to overflow logic, break
                break;
            }
        }
        // If we overshot target and the previous product was closer, revert last increment
        if (product >= target && lastIncDim >= 0) {
            long diffOver = product - target;
            long diffUnder = target - prevProduct;
            if (prevProduct >= 1 && diffUnder <= diffOver) {
                s[lastIncDim] -= 1;
                product = prevProduct;
            }
        }

        // If we could not split (product still 1), return singleton
        long totalSplits = 1L;
        for (long v : s) {
            totalSplits *= v;
        }
        if (totalSplits <= 1) {
            return List.of(this);
        }

        // Precompute per-dimension segment sizes (in cells) and starting offsets
        int[][] segCells = new int[n][];
        for (int d = 0; d < n; d++) {
            long cd = cells[d];
            int parts = (int) s[d];
            segCells[d] = new int[parts];
            long base = cd / parts;
            long rem = cd % parts;
            for (int k = 0; k < parts; k++) {
                segCells[d][k] = (int) (base + (k < rem ? 1 : 0));
            }
        }

        // Iterate over all combinations to build sub-bounds
        List<GridN> out = new ArrayList<>((int) Math.min(Integer.MAX_VALUE, Math.max(1L, totalSplits)));

        // index counters per dimension
        int[] idx = new int[n];
        long[] startCell = new long[n]; // starting cell index per dimension
        boolean done = false;
        while (!done) {
            // Build bounds for current indices
            double[][] tb = new double[n][2];
            for (int d = 0; d < n; d++) {
                double min = bounds[d][0] + startCell[d] * cellSizes[d];
                int segC = segCells[d][idx[d]];
                double maxCandidate = min + segC * cellSizes[d];
                // ensure last segment ends exactly at original bound max
                boolean isLastSeg = (idx[d] == segCells[d].length - 1);
                double max = isLastSeg ? bounds[d][1] : maxCandidate;
                tb[d][0] = min;
                tb[d][1] = max;
            }
            out.add(new GridNImpl(dimensions, cellSizes, tb, projection, hypercubicCells, anchorPoints));

            // Advance indices in mixed radix and update startCell
            for (int d = 0; d < n; d++) {
                // increment along dimension 0 first (like row-major)
                int next = idx[d] + 1;
                if (next < segCells[d].length) {
                    // advance on this dimension and reset all lower dimensions
                    // update start cell for this dimension
                    startCell[d] += segCells[d][idx[d]];
                    idx[d] = next;
                    // reset lower dimensions
                    for (int k = 0; k < d; k++) {
                        startCell[k] = 0;
                        idx[k] = 0;
                    }
                    // break to emit next tile
                    break;
                } else if (d == n - 1) {
                    done = true;
                }
            }
        }

        return out;
    }

    @Override
    public GridN merge(List<GridN> grids) {
        if (grids == null || grids.isEmpty()) {
            throw new IllegalArgumentException("grids must be non-null and non-empty");
        }
        GridN ref = grids.get(0);
        int dims = ref.getDimensions();
        double[] refSizes = ref.getCellSizes();
        Projection refPrj = ref.getProjection();
        boolean refHyper = ref.isHypercubicCells();
        double[][] union = new double[dims][2];
        for (int d = 0; d < dims; d++) {
            union[d][0] = Double.POSITIVE_INFINITY;
            union[d][1] = Double.NEGATIVE_INFINITY;
        }
        for (GridN g : grids) {
            if (g == null) throw new IllegalArgumentException("null grid in list");
            if (g.getDimensions() != dims) throw new IllegalArgumentException("incompatible dimensions");
            if (!approxEqualArrays(refSizes, g.getCellSizes())) throw new IllegalArgumentException("incompatible cell sizes");
            if ((refPrj == null) != (g.getProjection() == null)) throw new IllegalArgumentException("incompatible projections");
            if (refPrj != null && !Objects.equals(refPrj.getCode(), g.getProjection().getCode())) throw new IllegalArgumentException("different projection codes");
            if (refHyper != g.isHypercubicCells()) throw new IllegalArgumentException("incompatible hypercubic flag");
            double[][] b = g.getBounds();
            if (b == null) throw new IllegalArgumentException("all grids must have bounds");
            for (int d = 0; d < dims; d++) {
                union[d][0] = Math.min(union[d][0], b[d][0]);
                union[d][1] = Math.max(union[d][1], b[d][1]);
            }
        }
        return new GridNImpl(dims, refSizes, union, refPrj, refHyper, null, grids);
    }

    @Override
    public Pair<GridN, long[]> mapToSubgrid(long[] globalOffsets) {
        if (globalOffsets == null) throw new IllegalArgumentException("globalOffsets cannot be null");
        if (globalOffsets.length != dimensions) throw new IllegalArgumentException("wrong offsets dimensionality");
        for (int d = 0; d < dimensions; d++) {
            long c = this.cells[d];
            if (c <= 0) throw new IllegalArgumentException("grid has no cells/bounds");
            if (globalOffsets[d] < 0 || globalOffsets[d] >= c) throw new IllegalArgumentException("offset out of range at dimension " + d);
        }
        if (subGrids == null || subGrids.isEmpty()) {
            long[] copy = new long[dimensions];
            System.arraycopy(globalOffsets, 0, copy, 0, dimensions);
            return Pair.of(this, copy);
        }
        // Precompute start cell indices for each subgrid
        for (GridN sg : subGrids) {
            double[][] sb = sg.getBounds();
            long[] start = new long[dimensions];
            boolean contains = true;
            for (int d = 0; d < dimensions; d++) {
                long s = Math.round((sb[d][0] - bounds[d][0]) / cellSizes[d]);
                start[d] = s;
                long len = sg.getCells(d);
                long off = globalOffsets[d];
                if (off < s || off >= s + len) {
                    contains = false;
                    break;
                }
            }
            if (contains) {
                long[] local = new long[dimensions];
                for (int d = 0; d < dimensions; d++) {
                    long s = Math.round((sb[d][0] - bounds[d][0]) / cellSizes[d]);
                    local[d] = globalOffsets[d] - s;
                }
                return Pair.of(sg, local);
            }
        }
        throw new IllegalArgumentException("No subgrid contains the specified offsets");
    }

    private static boolean approxEqualArrays(double[] a, double[] b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > 1e-9) return false;
        }
        return true;
    }
}
