package org.integratedmodelling.klab.runtime.scale.space;

import java.io.Serial;
import java.util.Arrays;
import java.util.List;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Grid;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Tile;
import org.integratedmodelling.klab.api.lang.KlabLanguage;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.locationtech.jts.geom.Geometry;

public class TileImpl extends ShapeImpl implements Tile {

  @Serial private static final long serialVersionUID = -645107030417341241L;
  private Grid grid;
  private long size = 1;
  private transient volatile org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator coverage;
  private transient volatile org.locationtech.jts.geom.Envelope coverageEnvelope;
  private transient boolean rectangularCoverage;
  private transient double[] coverageWorld;
  private transient ThreadLocal<org.locationtech.jts.geom.Coordinate> coveragePoint;

  private void prepareCoverage() {
    if (coverageEnvelope != null) return;
    synchronized (this) {
      if (coverageEnvelope != null) return;
      var geometry = getJTSGeometry();
      rectangularCoverage = geometry.isRectangle();
      coverageWorld = grid.getWorldBounds();
      if (!rectangularCoverage) {
        coverage = new org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator(geometry);
        coverage.locate(new org.locationtech.jts.geom.Coordinate());
        coveragePoint = ThreadLocal.withInitial(org.locationtech.jts.geom.Coordinate::new);
      }
      coverageEnvelope = geometry.getEnvelopeInternal();
    }
  }

  @Override public boolean isCellCovered(long x, long y) {
    if (x < 0 || y < 0 || x >= grid.getXCells() || y >= grid.getYCells())
      throw new IndexOutOfBoundsException("Cell outside tile");
    prepareCoverage();
    var e = grid.getEnvelope();
    double dx = (e.getMaxX()-e.getMinX())/grid.getXCells(), dy = (e.getMaxY()-e.getMinY())/grid.getYCells();
    double west=e.getMinX()+x*dx, east=e.getMinX()+(x+1)*dx;
    double south=e.getMinY()+y*dy, north=e.getMinY()+(y+1)*dy;
    var world=coverageWorld;
    if (world.length==4) { south=Math.max(south,world[2]); north=Math.min(north,world[3]); }
    double cx=west+(east-west)/2, cy=south+(north-south)/2;
    if (world.length==4) cx-=Math.floor((cx-world[0])/(world[1]-world[0]))*(world[1]-world[0]);
    if (rectangularCoverage) return coverageEnvelope.covers(cx,cy);
    var point=coveragePoint.get(); point.x=cx; point.y=cy;
    return coverage.locate(point) != org.locationtech.jts.geom.Location.EXTERIOR;
  }

  public TileImpl() {
    setShape(List.of(0L, 0L));
  }

  /**
   * The grid may contain constraints that change the projection or the extent.
   *
   * @param geometry
   * @param grid
   */
  public TileImpl(Geometry geometry, Projection projection, Grid grid) {
    this(ShapeImpl.create(geometry, projection), grid, true);
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

  public TileImpl(Shape shape, Grid grid, boolean adjust) {
    this(prepare(shape, grid, adjust));
  }

  private record Construction(ShapeImpl shape, Grid grid) {}

  private static Construction prepare(Shape shape, Grid grid, boolean adjust) {
    var located = adjust ? grid.locate(shape.getEnvelope()) : grid;
    return new Construction(adjust ? GridAlignmentSupport.rectangularSupport(shape,located)
        : ShapeImpl.promote(shape), located);
  }

  private TileImpl(Construction construction) {
    super(construction.shape());
    this.grid = construction.grid();
    this.size = this.grid.size();
    setShape(Arrays.asList(this.grid.getXCells(), this.grid.getYCells()));
    this.envelope = EnvelopeImpl.promote(this.grid.getEnvelope());
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
    ret.envelope = (EnvelopeImpl) ret.grid.getEnvelope();
    if (coverageEnvelope != null) {
      ret.rectangularCoverage = rectangularCoverage;
      ret.coverage = coverage;
      ret.coverageWorld = coverageWorld;
      ret.coveragePoint = coveragePoint;
      ret.coverageEnvelope = coverageEnvelope;
    }
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
  public String encode(KlabLanguage language) {
    ServiceCall ret = super.encodeCall();
    // TODO have ShapeImpl return a service call with a protected method, then use that and add
    // arguments
    return ret.encode(language);
  }

  @Override
  public boolean isRegular() {
    return true;
  }

  public static TileImpl create(Shape shape, Grid grid, boolean adjust) {
    return new TileImpl(shape, grid, adjust);
  }

  @Override
  public String encode() {
    return "S2("
        + grid.getXCells()
        + ","
        + grid.getYCells()
        + "){"
        + getEnvelope().encode()
        + ",proj="
        + this.getProjection().getCode()
        + (grid.getWorldBounds().length==4 ? ",world="+java.util.Arrays.toString(grid.getWorldBounds()).replace(",", "") : "")
        + ",shape="
        + promote(this).asWKB()
        + "}";
  }
}
