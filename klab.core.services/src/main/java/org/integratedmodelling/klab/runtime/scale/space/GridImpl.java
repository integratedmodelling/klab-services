package org.integratedmodelling.klab.runtime.scale.space;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.crs.GeographicCRS;
import org.geotools.api.referencing.crs.ProjectedCRS;
import org.geotools.referencing.GeodeticCalculator;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Envelope;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Grid;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.services.UnitService;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.locationtech.jts.geom.Point;
import tech.units.indriya.unit.Units;

/**
 * Rectangular grids with cell steps expressed in CRS units. Metre resolutions in geographic CRSs
 * approximate ground spacing at the envelope's midpoint; they do not imply equal ground cell
 * areas across latitudes. Native-unit anchors define a fixed coordinate lattice. Grids with world
 * bounds retain a continuous logical rectangle and expose wrapped or clipped physical cell bounds.
 */
public class GridImpl implements Grid {

  private final List<Pair<Double, Double>> anchorPoints = new ArrayList<>();

  public static GridImpl promote(Grid grid) {
    if (grid instanceof GridImpl grid1) {
      return grid1;
    }
    throw new KlabUnimplementedException("GridImpl::promote with external grid");
  }

  /** Positions a cell may be located at in a fully specified grid. */
  public static enum CellPositionClass {
    SW_CORNER,
    SE_CORNER,
    NW_CORNER,
    NE_CORNER,
    S_EDGE,
    E_EDGE,
    N_EDGE,
    W_EDGE,
    INTERNAL
  }

  @Serial private static final long serialVersionUID = -4637331840972669199L;

  private ProjectionImpl projection;
  private double declaredResolutionM;
  private long xCells, yCells;
  private EnvelopeImpl envelope;
  private double xCellSize, yCellSize;
  private long size;
  private double[] worldBounds = new double[0];
  private boolean nativeCellSizes;
  private boolean relocationRequired;
  private boolean squareCells;
  private Quantity assertedResolution;

  public static Grid create(double resolutionInM) {
    GridImpl ret = new GridImpl(resolutionInM);
    return ret;
  }

  public static Grid create(double resolutionInM, boolean squareCells) {
    GridImpl ret = new GridImpl(resolutionInM);
    ret.squareCells = squareCells;
    return ret;
  }

  public GridImpl copy() {
    GridImpl ret = new GridImpl();
    ret.projection = this.projection;
    ret.declaredResolutionM = this.declaredResolutionM;
    ret.envelope = this.envelope == null ? null : this.envelope.copy();
    ret.xCells = this.xCells;
    ret.yCells = this.yCells;
    ret.xCellSize = this.xCellSize;
    ret.yCellSize = this.yCellSize;
    ret.size = this.size;
    ret.assertedResolution = this.assertedResolution;
    ret.squareCells = this.squareCells;
    ret.nativeCellSizes = this.nativeCellSizes;
    ret.worldBounds = this.worldBounds.clone();
    ret.relocationRequired = this.relocationRequired;
    for (var anchor : this.anchorPoints) {
      ret.anchorPoints.add(Pair.of(anchor.getFirst(), anchor.getSecond()));
    }
    return ret;
  }

  public GridImpl() {}


  /**
   * Constructor for a fully specified, anchored grid
   *
   * @param shape
   * @param resolutionInM
   */
  public GridImpl(Shape shape, double resolutionInM, boolean makeCellsSquare) {
    if (shape == null) throw new IllegalArgumentException("Missing grid shape");
    validateResolution(resolutionInM);
    this.declaredResolutionM = resolutionInM;
    this.assertedResolution = Quantity.of(resolutionInM, "m");
    adjustEnvelope(shape.getEnvelope(), resolutionInM, makeCellsSquare);
  }

  /**
   * Constructor for a fully specified, anchored grid
   *
   * @param resolutionInM
   */
  public GridImpl(Envelope envelope, double resolutionInM, boolean makeCellsSquare) {
    validateResolution(resolutionInM);
    this.declaredResolutionM = resolutionInM;
    this.assertedResolution = Quantity.of(resolutionInM, "m");
    adjustEnvelope(envelope, resolutionInM, makeCellsSquare);
  }

  /**
   * One between envelope and shape may be null, in which case the other will be used. If both are
   * non-null, the envelope determines the boundaries and the shape is used only for masking. The
   * squareCells is computed by equality.
   *
   * @param envelope
   * @param shape
   * @param xCells
   * @param yCells
   */
  public GridImpl(Envelope envelope, Shape shape, long xCells, long yCells) {

    if (envelope == null) {
      if (shape == null) throw new IllegalArgumentException("A grid requires an envelope or shape");
      envelope = shape.getEnvelope();
    }
    validateEnvelope(envelope);
    if (xCells <= 0 || yCells <= 0) throw new IllegalArgumentException("Grid cell counts must be positive");
    this.xCells = xCells;
    this.yCells = yCells;
    this.xCellSize = (envelope.getMaxX() - envelope.getMinX()) / this.xCells;
    this.yCellSize = (envelope.getMaxY() - envelope.getMinY()) / this.yCells;
    validateResolution(this.xCellSize);
    validateResolution(this.yCellSize);
    this.squareCells = Utils.Numbers.equal(this.xCellSize, this.yCellSize);
    this.size = Math.multiplyExact(this.xCells, this.yCells);
    this.projection = ProjectionImpl.promote(envelope.getProjection());
    this.envelope = EnvelopeImpl.promote(envelope).copy();
    this.nativeCellSizes = true;
  }

  /** Constructor for a fully specified, anchored grid */
  public GridImpl(Envelope envelope, Quantity quantity, boolean makeCellsSquare) {
    this(envelope,quantity,makeCellsSquare,new double[0]);
  }

  public GridImpl(Envelope envelope, Quantity quantity, boolean makeCellsSquare, double[] world) {
    if (world.length==4) setWorldBounds(world);
    if (quantity == null) throw new IllegalArgumentException("Missing grid resolution");
    setAssertedResolution(quantity);
    adjustEnvelope(envelope, this.declaredResolutionM, makeCellsSquare);
  }

  public GridImpl(double resolutionInM) {
    validateResolution(resolutionInM);
    this.declaredResolutionM = resolutionInM;
    this.assertedResolution = Quantity.of(resolutionInM, "m");
  }

  public GridImpl(Point anchorPoint, double resolutionInM) {
    this(resolutionInM);
    if (anchorPoint == null || anchorPoint.isEmpty()) throw new IllegalArgumentException("Missing grid anchor");
    this.projection = ProjectionImpl.promote(anchorPoint.getSRID() > 0
        ? Projection.of("EPSG:" + anchorPoint.getSRID()) : Projection.getDefault());
    this.squareCells = true;
    addAnchor(anchorPoint);
  }

  @Override
  public boolean isSquareCells() {
    return squareCells;
  }

  public GridImpl(Point anchorPoint, Projection projection, double resolutionInProjectionUnits) {
    validateResolution(resolutionInProjectionUnits);
    if (projection == null) throw new IllegalArgumentException("Missing grid projection");
    this.projection = ProjectionImpl.promote(projection);
    this.xCellSize = this.yCellSize = resolutionInProjectionUnits;
    this.nativeCellSizes = true;
    this.squareCells = true;
    addAnchor(anchorPoint);
  }

  private void addAnchor(Point point) {
    if (point == null || point.isEmpty() || !Double.isFinite(point.getX()) || !Double.isFinite(point.getY()))
      throw new IllegalArgumentException("Grid anchor must have finite coordinates");
    if (point.getSRID() > 0 && !projection.getCode().equals("EPSG:" + point.getSRID()))
      throw new IllegalArgumentException("Grid anchor CRS differs from grid CRS");
    anchorPoints.add(Pair.of(point.getX(), point.getY()));
  }

  @Override
  public Grid locate(Envelope envelope) {
    GridImpl ret = copy();
    validateEnvelope(envelope);
    if (!relocationRequired && anchorPoints.isEmpty() && this.envelope != null
        && this.envelope.getProjection().equals(envelope.getProjection())
        && this.envelope.getMinX() == envelope.getMinX() && this.envelope.getMaxX() == envelope.getMaxX()
        && this.envelope.getMinY() == envelope.getMinY() && this.envelope.getMaxY() == envelope.getMaxY())
      return ret;
    ret.adjustEnvelope(envelope, declaredResolutionM, this.squareCells);
    return ret;
  }

  /**
   * Adjust the envelope if necessary.
   *
   * <p>Depending on the requested resolution and the configuration, this can change the envelope or
   * just adapt the resolution to best fit the region context.
   *
   * @param envelope the envelope, which may be reprojected to the projection in this grid
   * @param squareRes the resolution to use in meters.
   */
  private void adjustEnvelope(Envelope envelope, double squareRes, boolean forceSquareCells) {

    validateEnvelope(envelope);
    this.relocationRequired = false;
    Projection prj = projection == null ? envelope.getProjection() : projection;
    if (!prj.equals(envelope.getProjection())) {
      try {
        envelope = EnvelopeImpl.create(EnvelopeImpl.promote(envelope).getJTSEnvelope()
            .transform(ProjectionImpl.promote(prj).getCRS(), true));
      } catch (Exception e) {
        throw new IllegalArgumentException("Cannot transform grid envelope to " + prj.getCode(), e);
      }
    }
    validateEnvelope(envelope);
    CoordinateReferenceSystem crs = ProjectionImpl.promote(prj).getCoordinateReferenceSystem();

    if (nativeCellSizes) {
      locateNative(envelope, xCellSize, yCellSize, prj);
      return;
    }
    validateResolution(squareRes);
    boolean geographic = crs instanceof GeographicCRS;
    if (!geographic && !(crs instanceof ProjectedCRS))
      throw new IllegalArgumentException("Grid CRS must be geographic or projected");
    if (!geographic) {
      this.squareCells = forceSquareCells;
      double stepX, stepY;
      try {
        stepX = Units.METRE.getConverterToAny(crs.getCoordinateSystem().getAxis(0).getUnit()).convert(squareRes);
        stepY = Units.METRE.getConverterToAny(crs.getCoordinateSystem().getAxis(1).getUnit()).convert(squareRes);
      } catch (Exception e) {
        throw new IllegalArgumentException("Grid CRS axes must use length units", e);
      }
      if (forceSquareCells || !anchorPoints.isEmpty()) {
        locateNative(envelope, stepX, stepY, prj);
      } else {
        this.projection = ProjectionImpl.promote(prj);
        this.envelope = EnvelopeImpl.promote(envelope).copy();
        this.squareCells = false;
        this.xCells = axisCount(envelope.getMinX(), envelope.getMaxX(), stepX);
        this.yCells = axisCount(envelope.getMinY(), envelope.getMaxY(), stepY);
        this.xCellSize = envelope.getWidth() / xCells;
        this.yCellSize = envelope.getHeight() / yCells;
        this.size = Math.multiplyExact(xCells, yCells);
      }
      return;
    }
    if (!anchorPoints.isEmpty())
      throw new KlabUnimplementedException("Metre-based geographic grid anchors require an angular lattice policy");

    double minX = envelope.getMinX(), maxX = envelope.getMaxX();
    double minY = envelope.getMinY(), maxY = envelope.getMaxY();
    var geographicCRS = (GeographicCRS) crs;
    double xRadians, yRadians, semiMajor, semiMinor;
    try {
      xRadians = crs.getCoordinateSystem().getAxis(0).getUnit().getConverterToAny(Units.RADIAN).convert(1);
      yRadians = crs.getCoordinateSystem().getAxis(1).getUnit().getConverterToAny(Units.RADIAN).convert(1);
      var ellipsoid = geographicCRS.getDatum().getEllipsoid();
      var metres = ellipsoid.getAxisUnit().getConverterToAny(Units.METRE);
      semiMajor = metres.convert(ellipsoid.getSemiMajorAxis());
      semiMinor = metres.convert(ellipsoid.getSemiMinorAxis());
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot determine geographic grid units", e);
    }
    double longitudeLimit = Math.PI / xRadians, latitudeLimit = Math.PI / (2 * yRadians);
    if ((worldBounds.length==0 && (minX < -longitudeLimit || maxX > longitudeLimit)) || maxX-minX>2*longitudeLimit || minY < -latitudeLimit || maxY > latitudeLimit)
      throw new IllegalArgumentException("Geographic grid bounds are outside the longitude/latitude domain");
    double latitude = (minY + (maxY - minY) / 2) * yRadians;
    double eccentricitySquared = 1 - (semiMinor / semiMajor) * (semiMinor / semiMajor);
    // Length along the midpoint parallel, rather than the shortest geodesic chord. This also
    // handles regions wider than 180 degrees and full-world grids without reversing their width.
    double width = (maxX - minX) * xRadians * semiMajor * Math.cos(latitude)
        / Math.sqrt(1 - eccentricitySquared * Math.sin(latitude) * Math.sin(latitude));
    var calculator = new GeodeticCalculator(crs);
    double longitude = Math.toDegrees((minX + (maxX - minX) / 2) * xRadians);
    if (worldBounds.length==4) longitude=((longitude+180)%360+360)%360-180;
    calculator.setStartingGeographicPoint(longitude, Math.toDegrees(minY * yRadians));
    calculator.setDestinationGeographicPoint(longitude, Math.toDegrees(maxY * yRadians));
    double height = calculator.getOrthodromicDistance();
    long nx = cellCount(width / squareRes), ny = cellCount(height / squareRes);
    double east = maxX, north = maxY;
    if (forceSquareCells) {
      // A rectangular angular grid approximates metre spacing at its midpoint. Expand by whole
      // cells without wrapping the antimeridian or reflecting across a pole.
      east = worldBounds.length==0 ? Math.min(longitudeLimit, minX + nx * ((maxX - minX) / width) * squareRes)
          : minX + nx * ((maxX-minX)/width)*squareRes;
      north = Math.min(latitudeLimit, minY + ny * ((maxY - minY) / height) * squareRes);
      east = Math.max(maxX, east);
      north = Math.max(maxY, north);
    }
    this.projection = ProjectionImpl.promote(prj);
    this.envelope = EnvelopeImpl.create(minX, east, minY, north, prj);
    this.squareCells = forceSquareCells;
    this.xCells = nx;
    this.yCells = ny;
    this.xCellSize = (east - minX) / nx;
    this.yCellSize = (north - minY) / ny;
    this.size = Math.multiplyExact(nx, ny);
  }

  private static void validateResolution(double resolution) {
    if (!Double.isFinite(resolution) || resolution <= 0)
      throw new IllegalArgumentException("Grid resolution must be finite and positive");
  }

  private static void validateEnvelope(Envelope envelope) {
    if (envelope == null || envelope.getProjection() == null
        || !Double.isFinite(envelope.getMinX()) || !Double.isFinite(envelope.getMaxX())
        || !Double.isFinite(envelope.getMinY()) || !Double.isFinite(envelope.getMaxY())
        || !Double.isFinite(envelope.getWidth()) || !Double.isFinite(envelope.getHeight())
        || envelope.getWidth() <= 0 || envelope.getHeight() <= 0)
      throw new IllegalArgumentException("Grid envelope must have finite, positive spans and a CRS");
  }

  private static long cellCount(double count) {
    double rounded = Math.rint(count);
    // Do not add an extra cell for arithmetic noise at an exactly aligned boundary.
    if (rounded >= 1 && Math.abs(count - rounded) <= Math.min(1e-7, 8 * Math.ulp(count))) count = rounded;
    count = Math.ceil(count);
    if (!Double.isFinite(count) || count < 1 || count >= 0x1p63)
      throw new IllegalArgumentException("Grid cell count is outside the supported long range");
    return (long) count;
  }

  private static long axisCount(double min, double max, double step) {
    double count = (max - min) / step;
    double nearest = Math.rint(count);
    double tolerance = Math.min(1e-7,
        Math.max(8 * Math.ulp(count), 8 * Math.max(Math.ulp(min), Math.ulp(max)) / step));
    if (nearest >= 1 && Math.abs(count - nearest) <= tolerance) count = nearest;
    return cellCount(count);
  }

  private void locateNative(Envelope requested, double dx, double dy, Projection prj) {
    validateResolution(dx);
    validateResolution(dy);
    double west = requested.getMinX(), south = requested.getMinY();
    double east = requested.getMaxX(), north = requested.getMaxY();
    var anchor = anchorPoints.isEmpty() ? null : anchorPoints.getFirst();
    if (anchor != null) {
      west = anchor.getFirst() + Math.floor(snap((west - anchor.getFirst()) / dx)) * dx;
      south = anchor.getSecond() + Math.floor(snap((south - anchor.getSecond()) / dy)) * dy;
      for (var other : anchorPoints) {
        if (snap((other.getFirst() - anchor.getFirst()) / dx) != Math.rint((other.getFirst() - anchor.getFirst()) / dx)
            || snap((other.getSecond() - anchor.getSecond()) / dy) != Math.rint((other.getSecond() - anchor.getSecond()) / dy))
          throw new IllegalArgumentException("Grid anchors do not share a cell lattice");
      }
    }
    long nx = axisCount(west, east, dx), ny = axisCount(south, north, dy);
    if (worldBounds.length == 4) {
      long period = Math.round((worldBounds[1]-worldBounds[0])/dx);
      if (period < 1 || Math.abs(period*dx/(worldBounds[1]-worldBounds[0])-1)>1e-9)
        throw new IllegalArgumentException("Longitude cell step must divide the world period");
      nx = Math.min(nx,period); // A seam cell is stored once, even when it has two footprints.
    }
    double newEast = west + nx * dx, newNorth = south + ny * dy;
    if (!Double.isFinite(newEast) || !Double.isFinite(newNorth) || newEast <= west || newNorth <= south
        || west + dx == west || newEast - dx == newEast || south + dy == south || newNorth - dy == newNorth)
      throw new IllegalArgumentException("Grid cell edges cannot be represented at these coordinates");
    this.envelope = EnvelopeImpl.create(west, newEast, south, newNorth, prj);
    if (worldBounds.length == 0 && ProjectionImpl.promote(prj).getCRS() instanceof GeographicCRS geographic) {
      try {
        double xLimit = Math.PI / geographic.getCoordinateSystem().getAxis(0).getUnit()
            .getConverterToAny(Units.RADIAN).convert(1);
        double yLimit = Math.PI / (2 * geographic.getCoordinateSystem().getAxis(1).getUnit()
            .getConverterToAny(Units.RADIAN).convert(1));
        if (west < -xLimit || newEast > xLimit || south < -yLimit || newNorth > yLimit)
          throw new IllegalArgumentException("Anchored grid would cross the geographic coordinate domain");
      } catch (javax.measure.IncommensurableException e) {
        throw new IllegalArgumentException("Geographic grid axes must use angular units", e);
      }
    }
    this.projection = ProjectionImpl.promote(prj);
    this.xCells = nx;
    this.yCells = ny;
    this.xCellSize = dx;
    this.yCellSize = dy;
    this.size = Math.multiplyExact(nx, ny);
  }

  private static double snap(double value) {
    double rounded = Math.rint(value);
    return Math.abs(value - rounded) <= Math.min(1e-7, 8 * Math.ulp(value)) ? rounded : value;
  }

  @Override
  public long getXCells() {
    return xCells;
  }

  @Override
  public long getYCells() {
    return yCells;
  }

  @Override
  public List<Pair<Double, Double>> getAnchorPoints() {
    return this.anchorPoints;
  }

  @Override
  public Envelope getEnvelope() {
    return envelope;
  }

  @Override
  public long size() {
    return size;
  }

  @Override
  public double getXCellSize() {
    return xCellSize;
  }

  @Override
  public double getYCellSize() {
    return yCellSize;
  }

  @Override
  public Projection getProjection() {
    return projection == null ? Projection.getDefault() : projection;
  }

  /** Logical bounds remain uniform; only physical cell footprints wrap or clip. */
  public void setWorldBounds(double[] bounds) {
    if (bounds.length != 4 || !java.util.Arrays.stream(bounds).allMatch(Double::isFinite)
        || bounds[1]<=bounds[0] || bounds[3]<=bounds[2]) throw new IllegalArgumentException("Invalid world bounds");
    worldBounds=bounds.clone();
  }

  @Override
  public double[] getWorldBounds() { return worldBounds.clone(); }

  @Override
  public List<Envelope> getCellBounds(long x, long y) {
    if (x<0 || y<0 || x>=xCells || y>=yCells) throw new IndexOutOfBoundsException("Cell outside grid");
    double west=envelope.getMinX()+x*xCellSize, east=envelope.getMinX()+(x+1)*xCellSize;
    double south=envelope.getMinY()+y*yCellSize, north=envelope.getMinY()+(y+1)*yCellSize;
    if (worldBounds.length==0) return List.of(EnvelopeImpl.create(west,east,south,north,projection));
    south=Math.max(south,worldBounds[2]); north=Math.min(north,worldBounds[3]);
    if (north<=south) return List.of();
    double period=worldBounds[1]-worldBounds[0];
    double shift=Math.floor((west-worldBounds[0])/period)*period;
    west-=shift;east-=shift;
    if (east<=worldBounds[1]) return List.of(EnvelopeImpl.create(west,east,south,north,projection));
    return List.of(EnvelopeImpl.create(west,worldBounds[1],south,north,projection),
        EnvelopeImpl.create(worldBounds[0],worldBounds[0]+east-worldBounds[1],south,north,projection));
  }

  @Override
  public Grid align(org.integratedmodelling.klab.api.digitaltwin.GridAlignment instruction) {
    return GridAlignmentSupport.align(this,instruction);
  }

  @Override
  public Grid align(Grid other) {
    if (other==null || envelope==null) throw new IllegalArgumentException("Alignment requires a reference and located grid");
    validateResolution(other.getXCellSize()); validateResolution(other.getYCellSize());
    var anchor=other.getAnchorPoints().isEmpty()
        ? Pair.of(other.getEnvelope()==null ? 0.0 : other.getEnvelope().getMinX(),
                  other.getEnvelope()==null ? 0.0 : other.getEnvelope().getMinY()) : other.getAnchorPoints().getFirst();
    var levels=new ArrayList<Double>();
    for (int i=-20;i<=20;i++) levels.add(Math.scalb(1.0,i));
    return align(new org.integratedmodelling.klab.api.digitaltwin.GridAlignment(1,"inline:grid","inline",
        other.getProjection().getCode(),anchor.getFirst(),anchor.getSecond(),other.getXCellSize(),other.getYCellSize(),false,false,levels,List.of()));
  }

  @Override
  public double resolution() {
    return xCellSize == 0 || yCellSize == 0 ? 0 : Math.sqrt(xCellSize) * Math.sqrt(yCellSize);
  }

  public long getxCells() {
    return xCells;
  }

  public void setxCells(long xCells) {
    if (xCells < 0) throw new IllegalArgumentException("Grid cell counts cannot be negative");
    long newSize = Math.multiplyExact(xCells, yCells);
    this.xCells = xCells;
    this.size = newSize;
    refreshCellSizes();
  }

  public long getyCells() {
    return yCells;
  }

  public void setyCells(long yCells) {
    if (yCells < 0) throw new IllegalArgumentException("Grid cell counts cannot be negative");
    long newSize = Math.multiplyExact(xCells, yCells);
    this.yCells = yCells;
    this.size = newSize;
    refreshCellSizes();
  }

  public double getxCellSize() {
    return xCellSize;
  }

  public void setxCellSize(double xCellSize) {
    if (!Double.isFinite(xCellSize) || xCellSize < 0)
      throw new IllegalArgumentException("Grid cell size must be finite and nonnegative");
    this.xCellSize = xCellSize;
    this.relocationRequired = true;
    if (declaredResolutionM == 0) this.nativeCellSizes = true;
  }

  public double getyCellSize() {
    return yCellSize;
  }

  public void setyCellSize(double yCellSize) {
    if (!Double.isFinite(yCellSize) || yCellSize < 0)
      throw new IllegalArgumentException("Grid cell size must be finite and nonnegative");
    this.yCellSize = yCellSize;
    this.relocationRequired = true;
    if (declaredResolutionM == 0) this.nativeCellSizes = true;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    if (size < 0 || (xCells > 0 && yCells > 0 && size != Math.multiplyExact(xCells,yCells)))
      throw new IllegalArgumentException("Grid size must agree with its cell counts");
    this.size = size;
  }

  public void setProjection(ProjectionImpl projection) {
    if (envelope != null && !envelope.getProjection().equals(projection))
      throw new IllegalArgumentException("Grid CRS differs from its envelope CRS");
    this.projection = projection;
  }

  public void setEnvelope(EnvelopeImpl envelope) {
    if (envelope != null) {
      validateEnvelope(envelope);
      if (projection != null && !projection.equals(envelope.getProjection()))
        throw new IllegalArgumentException("Grid CRS differs from its envelope CRS");
      this.projection = ProjectionImpl.promote(envelope.getProjection());
    }
    this.envelope = envelope == null ? null : envelope.copy();
    if (envelope == null) {
      this.xCells = this.yCells = this.size = 0;
    } else {
      refreshCellSizes();
    }
  }

  private void refreshCellSizes() {
    if (envelope != null) {
      if (xCells > 0) this.xCellSize = envelope.getWidth() / xCells;
      if (yCells > 0) this.yCellSize = envelope.getHeight() / yCells;
      if (declaredResolutionM == 0 && xCells > 0 && yCells > 0) this.nativeCellSizes = true;
    }
  }

  public Quantity getAssertedResolution() {
    return assertedResolution;
  }

  public void setAssertedResolution(Quantity assertedResolution) {
    if (assertedResolution == null) {
      this.assertedResolution = null;
      this.declaredResolutionM = 0;
      this.nativeCellSizes = xCellSize > 0 && yCellSize > 0;
      this.relocationRequired = true;
      return;
    }
    if (assertedResolution.getValue() == null || assertedResolution.getUnit() == null)
      throw new IllegalArgumentException("A grid resolution requires a value and length unit");
    var unitService = ServiceConfiguration.INSTANCE.getService(UnitService.class);
    var originalUnit = unitService.getUnit(assertedResolution.getUnit());
    double metres = unitService.meters().convert(assertedResolution.getValue(),originalUnit).doubleValue();
    validateResolution(metres);
    this.assertedResolution = assertedResolution;
    this.declaredResolutionM = metres;
    this.nativeCellSizes = false;
    this.relocationRequired = true;
  }
}
