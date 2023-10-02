package org.integratedmodelling.klab.api.knowledge.observation.scale.space;

/**
 * <p>>A <code>Tile</code> is a regularly tiled shape, including the single cell.Covers raster grids and any other
 * regular subdivition, Named Tile to reflect the fact that implementation may provide tiles of different shape than
 * rectangular. </p
 *
 * <p>A Tile is an Iterable<Tile> therefore multiple levels of tiling are supported, each implementing a given
 * {@link Grid} specification.</p>
 *
 * @author Ferd
 */
public interface Tile extends Shape {

    Grid getGrid();

//
//	public interface Cell extends Space {
//
//		/**
//		 * Cell to the North, or null.
//		 * 
//		 * @return
//		 */
//		Cell N();
//
//		/**
//		 * 
//		 * @return
//		 */
//		Cell S();
//
//		/**
//		 * 
//		 * @return
//		 */
//		Cell E();
//
//		/**
//		 * 
//		 * @return
//		 */
//		Cell W();
//
//		/**
//		 * 
//		 * @return
//		 */
//		Cell NE();
//
//		/**
//		 * 
//		 * @return
//		 */
//		Cell NW();
//
//		/**
//		 * 
//		 * @return
//		 */
//		Cell SE();
//
//		/**
//		 * 
//		 * @return
//		 */
//		Cell SW();
//
//		/**
//		 * 
//		 * @return
//		 */
//		Collection<Cell> getNeighbors();
//
//		/**
//		 * 
//		 * @param orientation
//		 * @return
//		 */
//		Cell getNeighbor(Orientation orientation);
//
//		/**
//		 * 
//		 * @param xOffset
//		 * @param yOffset
//		 * @return
//		 */
//		Cell getNeighbor(long xOffset, long yOffset);
//
//		/**
//		 * 
//		 * @return
//		 */
//		long getX();
//
//		/**
//		 * 
//		 * @return
//		 */
//		long getY();
//
//		/**
//		 * Create a new cell in a position offset by the passed number of cells in each
//		 * dimension; return null if cell is off the grid.
//		 * 
//		 * @param xOfs
//		 * @param yOfs
//		 * @return moved cell
//		 */
//		Cell move(long xOfs, long yOfs);
//
//		/**
//		 * 
//		 * @return
//		 */
//		double getEast();
//
//		/**
//		 * 
//		 * @return
//		 */
//		double getWest();
//
//		/**
//		 * 
//		 * @return
//		 */
//		double getSouth();
//
//		/**
//		 * 
//		 * @return
//		 */
//		double getNorth();
//
//		/**
//		 * 
//		 * @return
//		 */
//		long getOffsetInGrid();
//
//		/**
//		 * 
//		 * @param cell
//		 * @return
//		 */
//		boolean isAdjacent(Cell cell);
//
//		/**
//		 * World coordinates of center, horizontal-first.
//		 * 
//		 * @return world coordinates of center
//		 */
//		double[] getCenter();
//
//		/**
//		 * Return the grid as a shape.
//		 */
//		Shape getGridShape();
//
//		/**
//		 * Get the geometry this grid is part of.
//		 */
//		Geometry getGeometry();

//
//	/**
//	 * A grid mask defines the active or inactive state of each cell. It is used by
//	 * the cell iterator to skip inactive cells. Masks with inactive cells result
//	 * from grids created with shapes that don't cover the entire rectangular grid.
//	 * 
//	 * @author ferdinando.villa
//	 *
//	 */
//	public interface Mask {
//
//		/**
//		 * Merge with another mask. Changes the contents of the mask.
//		 * 
//		 * @param other     another mask of the same shape. No check is made for
//		 *                  compatibility and exceptions will only be thrown if the
//		 *                  total size is different.
//		 * @param connector only {@link LogicalConnector#UNION} and
//		 *                  {@link LogicalConnector#INTERSECTION} are supported.
//		 */
//		void merge(Mask other, LogicalConnector connector);
//
//		/**
//		 * Check status of cell
//		 * 
//		 * @param x
//		 * @param y
//		 * @return true if cell at x,y is active
//		 */
//		boolean isActive(long x, long y);
//
//		/**
//		 * Activate the cell at x, y
//		 * 
//		 * @param x
//		 * @param y
//		 */
//		void activate(long x, long y);
//
//		/**
//		 * Deactivate the cell at x,y
//		 * 
//		 * @param x
//		 * @param y
//		 */
//		void deactivate(long x, long y);
//
//		/**
//		 * Total active cells
//		 * 
//		 * @return number of active cells
//		 */
//		long totalActiveCells();
//
//		/**
//		 * Next active cell at or above the passed offset, <strong>including</strong>
//		 * the passed offset.
//		 * 
//		 * @param fromOffset
//		 * @return the next active offset using the natural ordering, or -1
//		 */
//		long nextActiveOffset(long fromOffset);
//
//		/**
//		 * Invert the status of each cell
//		 */
//		void invert();
//
//		/**
//		 * Set every flag to inactive;
//		 */
//		void deactivate();
//
//		/**
//		 * Set every cell to active;
//		 */
//		void activate();
//
//	}
//
//	/**
//	 * 
//	 * @param xCoordinate
//	 * @param direction
//	 * @return the new X coordinate
//	 */
//	double snapX(double xCoordinate, Direction direction);
//
//	/**
//	 * 
//	 * @param yCoordinate
//	 * @param direction
//	 * @return the new Y coordinate
//	 */
//	double snapY(double yCoordinate, Direction direction);
//
//	/**
//	 * Number of cells on horizontal (W-E) axis.
//	 * 
//	 * @return Y cells
//	 */
//	long getYCells();
//
//	/**
//	 * Number of cells on vertical (S-N) axis.
//	 * 
//	 * @return X cells
//	 */
//	long getXCells();
//
//	/**
//	 * Total number of cells.
//	 * 
//	 * @return total cells
//	 */
//	long getCellCount();
//
//	/**
//	 * Convert to linear index.
//	 * 
//	 * @param x
//	 * @param y
//	 * @return linear offset
//	 */
//	long getOffset(long x, long y);
//
//	/**
//	 * Return whether the grid cell at the passed coordinates is part of the active
//	 * area of the grid.
//	 * 
//	 * @param x
//	 * @param y
//	 * @return true if active
//	 */
//	boolean isActive(long x, long y);
//
//	double getEast();
//
//	double getWest();
//
//	double getSouth();
//
//	double getNorth();
//
//	/**
//	 * Get the linear index of the cell where the passed point is located, using
//	 * world coordinates in the projection we're in. Use w-e, s-n coordinates no
//	 * matter the projection.
//	 * 
//	 * @param lon
//	 * @param lat
//	 * @return linear offset
//	 */
//	long getOffsetFromWorldCoordinates(double lon, double lat);
//
//	/**
//	 * Convert from linear index.
//	 * 
//	 * @param index
//	 * @return xy offsets from linear
//	 */
//	long[] getXYOffsets(long index);
//
//	/**
//	 * Get the most accurate geospatial coordinates (w-e, s-n) for the linear offset
//	 * passed, corresponding to the center of the correspondent cell. Use current
//	 * coordinate reference system.
//	 * 
//	 * @param index
//	 * @return world coordinates for linear offset
//	 */
//	double[] getCoordinates(long index);
//
//	double getCellWidth();
//
//	double getCellHeight();
//
//	double[] getWorldCoordinatesAt(long x, long y);
//
//	long[] getGridCoordinatesAt(double x, double y);
//
//	/**
//	 * 
//	 * @param coordinates
//	 * @param isStandardProjection
//	 * @return
//	 */
//	Cell getCellAt(double[] coordinates, boolean isStandardProjection);
//
//	/**
//	 * 
//	 * @param offset
//	 * @return
//	 */
//	Cell getCell(long offset);
//
//	Projection getProjection();
}
