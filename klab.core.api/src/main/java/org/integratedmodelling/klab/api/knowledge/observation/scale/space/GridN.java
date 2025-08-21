/*
 * This file is part of k.LAB.
 *
 * k.LAB is free software: you can redistribute it and/or modify
 * it under the terms of the Affero GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root
 * directory of the k.LAB distribution (LICENSE.txt). If this cannot be found
 * see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2007-2025 integratedmodelling.org and any authors mentioned
 * in author tags. All rights reserved.
 */
package org.integratedmodelling.klab.api.knowledge.observation.scale.space;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.impl.GeometryImpl;
import org.integratedmodelling.klab.api.geometry.impl.GridNImpl;

import java.io.Serializable;
import java.util.List;
import java.util.ServiceConfigurationError;

/**
 * GridN is a generalization of {@link Grid} to an arbitrary number of dimensions (N ≥ 1). It
 * describes the geometry of a regularly tiled, axis-aligned N-dimensional grid. It can be complete
 * (with bounds) or partial (only cell sizes and optionally anchor points). When used as a
 * constraint, it can be passed to implementations to ensure alignment of grids across multiple
 * dimensions.
 *
 * <p>Notes on terminology and conventions:
 *
 * <ul>
 *   <li>"Cell size" refers to the step along each axis in the relevant coordinate units.
 *   <li>"Bounds" are an array of [min, max] pairs per dimension, i.e., bounds[d][0] = min_d,
 *       bounds[d][1] = max_d, or {@code null} if this grid does not specify an extent.
 *   <li>"Anchor points" are points in the N-D coordinate space that must align with cell vertices.
 *       If more than one anchor point is provided, they also establish the step along each
 *       dimension. If both anchor-derived steps and explicit cell sizes exist, they must be
 *       coherent.
 *   <li>For N = 2, the semantics correspond to those of {@link Grid}: the first dimension is the
 *       horizontal (W-E, X) axis and the second is the vertical (S-N, Y) axis; the resolution is
 *       the square root of the cell area.
 *   <li>For N > 2, the resolution is defined as the N-th root of the cell hyper-volume (i.e., the
 *       geometric mean of per-dimension cell sizes).
 *   <li>Projection is maintained for consistency with the spatial use-cases of Grid. It is
 *       meaningful for the spatial axes (commonly the first two). Non-spatial axes may ignore
 *       projection.
 * </ul>
 *
 * @author Ferd
 */
public interface GridN extends Serializable {

  /**
   * Number of dimensions in this grid (N ≥ 1).
   *
   * @return dimensions count
   */
  int getDimensions();

  /**
   * Per-dimension cell sizes. The returned array length must equal {@link #getDimensions()}.
   *
   * @return array of cell sizes per dimension
   */
  double[] getCellSizes();

  /**
   * Cell size along a specific dimension.
   *
   * @param dimension zero-based dimension index
   * @return cell size for the given dimension
   * @throws IndexOutOfBoundsException if the dimension index is invalid
   */
  double getCellSize(int dimension);

  /**
   * Actual resolution in projection/native units, defined as the N-th root of the cell hyper-volume
   * (i.e., the geometric mean of cell sizes across all dimensions). For N = 2 this equals the
   * square root of the cell area as in {@link Grid#resolution()}.
   *
   * @return resolution (geometric mean of cell sizes)
   */
  double resolution();

  /**
   * Total number of cells. A grid that only specifies cell sizes with no bounds has size() == 0.
   *
   * @return total cells or 0 if bounds are not specified
   */
  long size();

  /**
   * Produce a new grid with the same parameters but redefined to fit the passed bounds, with the
   * possible constraint of keeping hypercubic cells (which may redefine the bounds). If this grid
   * has anchor points, the resulting grid must also be exactly aligned to them.
   *
   * @param bounds per-dimension [min, max] pairs. If {@code null}, behavior is
   *     implementation-defined.
   * @return a new grid with the passed bounds
   */
  GridN locate(double[][] bounds);

  /**
   * Number of cells per dimension. This will be an array of zeros if there are no bounds. The
   * returned array length must equal {@link #getDimensions()}.
   *
   * @return per-dimension cell counts
   */
  long[] getCells();

  /**
   * Number of cells along a specific dimension. Returns 0 if there are no bounds.
   *
   * @param dimension zero-based dimension index
   * @return cell count for the given dimension
   * @throws IndexOutOfBoundsException if the dimension index is invalid
   */
  long getCells(int dimension);

  /**
   * If not null, the grid must have these points (in projection/native coordinates) exactly aligned
   * with a vertex of a cell. If more than one point is returned, they also establish the step of
   * the grid in all dimensions; if a step is also present (i.e., {@link #resolution()} != 0), it
   * must match the one reported by {@link #getCellSizes()}.
   *
   * @return a list of N-dimensional anchor points, each as a double[] of length {@link
   *     #getDimensions()}
   */
  List<double[]> getAnchorPoints();

  /**
   * Bounds as per-dimension [min, max] pairs. The result is null if the grid is only specified in
   * terms of cell size. The returned array length, when non-null, must equal {@link
   * #getDimensions()}.
   *
   * @return per-dimension bounds or null
   */
  double[][] getBounds();

  /**
   * Coordinate reference system. In an N-D context, this primarily applies to spatial axes
   * (commonly the first two). If bounds are non-null and represent spatial coordinates, the
   * projection in the grid and the spatial subset of bounds must be coherent; otherwise
   * implementations should throw an IllegalArgumentException.
   *
   * @return projection (never null for spatial grids)
   */
  Projection getProjection();

  /**
   * Return a new grid that is as close as possible to this one after aligning it with the passed
   * one. Alignment means that the cells of the resulting grid align with those of the incoming
   * grid, possibly with a multiplicity of cells in one corresponding to one cell of the other. This
   * may cause the projection and bounds of the result to differ from the original, as well as
   * invalidating the "hypercubic cells" constraint. If the passed grid has bounds, the result will
   * be intersected to be included in the incoming bounds.
   *
   * @param other another N-dimensional grid
   * @return aligned grid
   */
  GridN align(GridN other);

  /**
   * True if the grid has been explicitly constrained to have equal cell sizes on all dimensions
   * (hypercubes). The grid may return false and still have equal sizes if that was the result of
   * adapting to requested bounds.
   *
   * @return true if created with hypercubic cells option
   */
  boolean isHypercubicCells();

  /**
   * Split this grid into a number of adjacent sub-grids, preserving the same cell sizes as the
   * original. The returned grids collectively cover exactly the same bounds as this grid (i.e.,
   * their envelopes are adjacent, non-overlapping partitions of this grid's bounds), and their
   * number is as close as possible to the suggestedSplits value.
   *
   * <p>Implementations should: - Keep per-dimension cell size identical to this grid. - Partition
   * along cell boundaries (when possible), adjusting the number of cells per sub-grid so that the
   * union of all sub-grid envelopes equals this grid's bounds. - Respect edge cases: if bounds are
   * null, size is 0, or suggestedSplits <= 1, return a singleton list containing this grid; if
   * suggestedSplits is very large, cap to a reasonable number close to the total number of cells.
   *
   * @param suggestedSplits desired number of tiles
   * @return a list of adjacent grids whose coverage equals this grid's coverage
   */
  List<GridN> split(int suggestedSplits);

  /**
   * Merge a list of adjacent grids (such as those produced by {@link #split(int)}) into a single
   * grid that spans the entire N-dimensional range of the input grids. All input grids must be
   * compatible (same dimensionality, identical per-dimension cell sizes, same projection and
   * hypercubic flag). The returned grid preserves the common cell sizes and projection, uses the
   * union of bounds, and internally records the subgrids so that {@link #mapToSubgrid(long[])} can
   * map positions back to the corresponding subgrid.
   *
   * @param grids list of adjacent grids to merge; must be non-null and non-empty
   * @return a merged grid covering the union of the inputs
   * @throws IllegalArgumentException if grids are null/empty or incompatible
   */
  GridN merge(List<GridN> grids);

  /**
   * Map a cell position expressed as per-dimension offsets in this (possibly merged) grid to the
   * specific subgrid that contains it, returning both the subgrid and the offsets remapped to that
   * subgrid. For non-merged grids, this returns an identity mapping: Pair.of(this,
   * globalOffsetsClone).
   *
   * @param globalOffsets per-dimension offsets in this grid; length must equal {@link
   *     #getDimensions()}
   * @return a pair containing the target subgrid and the offsets remapped within it
   * @throws IllegalArgumentException if offsets are null, wrong length, or out of range
   */
  Pair<GridN, long[]> mapToSubgrid(long[] globalOffsets);



  Geometry.Dimension asDimension();

  /**
   * Return the grid corresponding to the passed space.
   *
   * @param dimension
   * @return
   */
  static GridN of(Geometry.Dimension dimension) {

    var projection =
        dimension.getParameters().get(GeometryImpl.PARAMETER_SPACE_PROJECTION, String.class);
    //      var proj = projection == null ? null : Projection.of(projection);
    var bbox = dimension.getParameters().get(GeometryImpl.PARAMETER_SPACE_BOUNDINGBOX, List.class);
    //      var ret = new GridNImpl(dimension.getDimensionality(), )
    var bounds = new double[dimension.getDimensionality()][2];
    var cellSizes = new double[dimension.getDimensionality()];
    int b = 0;
    for (int i = 0; i < dimension.getDimensionality(); i++) {
      bounds[i][0] = ((Number) bbox.get(b++)).doubleValue();
      bounds[i][1] = ((Number) bbox.get(b++)).doubleValue();
      cellSizes[i] = (bounds[i][1] - bounds[i][0]) / dimension.getShape().get(i);
    }
    return new GridNImpl(dimension.getDimensionality(), cellSizes, bounds, null, true, null);
  }
}
