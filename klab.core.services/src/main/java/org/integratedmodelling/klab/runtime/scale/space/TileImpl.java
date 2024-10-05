package org.integratedmodelling.klab.runtime.scale.space;

import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Grid;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Tile;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBWriter;

import java.util.Arrays;

public class TileImpl extends ShapeImpl implements Tile {

    private static final long serialVersionUID = -645107030417341241L;
    private Grid grid;
    private long size = 1;

    /**
     * The grid may contain constraints that change the projection or the extent.
     *
     * @param geometry
     * @param grid
     */
    public TileImpl(Geometry geometry, Projection projection, Grid grid) {
        super(ShapeImpl.create(geometry, projection));
        this.grid = grid.locate(getEnvelope());
        this.size = this.grid.size();
        setShape(Arrays.asList(this.grid.getXCells(), this.grid.getYCells()));
    }

    /**
     * Constructor without a grid, internal, not enough to create a fully specified object.
     *
     * @param geometry
     * @param projection
     */
    private TileImpl(Geometry geometry, Projection projection) {
        super(ShapeImpl.create(geometry, projection));
    }

    public TileImpl(Shape shape, Grid grid) {
        super(ShapeImpl.promote(shape));
        this.grid = grid.locate(this.getEnvelope());
        this.size = this.grid.size();
        setShape(Arrays.asList(this.grid.getXCells(), this.grid.getYCells()));
    }

    @Override
    public TileImpl at(Locator locator) {
        // TODO Auto-generated method stub - must create a cell if covered
        return null;
    }

    @Override
    public TileImpl copy() {
        TileImpl ret = new TileImpl(getJTSGeometry(), getProjection());
        ret.grid = GridImpl.promote(grid).copy();
        ret.size = ret.grid.size();
        ret.setShape(Arrays.asList(ret.grid.getXCells(), ret.grid.getYCells()));
        return ret;

    }

    @Override
    public Grid getGrid() {
        return this.grid;
    }

    @Override
    public long size() {
        return this.size;
    }

    @Override
    public String encode(String language) {
        ServiceCall ret = super.encodeCall();
        // TODO have ShapeImpl return a service call with a protected method, then use that and add arguments
        return ret.encode(language);
    }

    public static TileImpl create(Shape shape, Grid grid) {
        return new TileImpl(shape, grid);
    }

    @Override
    public String encode() {
        return "S2(" + grid.getXCells() + "," + grid.getYCells() + "){" + getEnvelope().encode()
                + ",shape=" + promote(this).asWKB() + "}";

    }
}
