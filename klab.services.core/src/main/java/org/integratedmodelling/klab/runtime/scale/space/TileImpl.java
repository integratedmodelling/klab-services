package org.integratedmodelling.klab.runtime.scale.space;

import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Grid;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Tile;
import org.locationtech.jts.geom.Geometry;

public class TileImpl extends ShapeImpl implements Tile {

	private static final long serialVersionUID = -645107030417341241L;
	private Grid grid;

	/**
	 * The grid may contain constraints that change the projection or the extent.
	 * 
	 * @param geometry
	 * @param grid
	 */
	public TileImpl(Geometry geometry, Projection projection, Grid grid) {
		super(geometry, projection);
		this.grid = grid;
	}

	public TileImpl(Shape shape, Grid grid) {
		super(ShapeImpl.promote(shape));
		this.grid = grid;
	}

	@Override
	public TileImpl at(Object... locators) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Grid getGrid() {
		return this.grid;
	}

	@Override
	public long size() {
		return grid.size();
	}

	public static TileImpl create(Shape shape, Grid grid) {
		return new TileImpl(shape, grid);
	}

}
