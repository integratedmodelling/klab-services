package org.integratedmodelling.klab.runtime.scale.space;

import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.geotools.api.referencing.crs.GeographicCRS;
import org.integratedmodelling.klab.api.digitaltwin.GridAlignment;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.impl.GeometryImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Grid;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.lang.kim.KimLiteral;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.UnitService;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.scale.ScaleImpl;
import tech.units.indriya.unit.Units;

/** Shared definition validation and deterministic, metadata-only lattice normalization. */
public final class GridAlignmentSupport {
  private GridAlignmentSupport() {}

  public static GridAlignment resolve(String urn, Scope scope) {
    var resources = scope.getService(ResourcesService.class);
    if (!(scope instanceof org.integratedmodelling.klab.api.scope.UserScope userScope))
      throw new IllegalArgumentException("Grid resolution requires a user scope");
    var alignment = resources.retrieve(urn, GridAlignment.class, userScope);
    if (alignment == null || !urn.equals(alignment.definitionUrn()))
      throw new IllegalArgumentException("Grid definition must resolve uniquely: " + urn);
    return alignment;
  }

  private static Object literal(Object value) {
    return value instanceof KimLiteral lit ? lit.getUnparsedValue(Object.class) : value;
  }

  public static GridAlignment decode(KimSymbolDefinition definition) {
    return decode(definition, definition.getServiceId());
  }

  /** Decode a definition hosted by the given Resources service. */
  public static GridAlignment decode(KimSymbolDefinition definition, String serviceId) {
    if (!"grid".equals(definition.getDefineClass())) throw new IllegalArgumentException("Expected a define grid instruction");
    if (!(literal(definition.getValue()) instanceof Map<?,?> map))
      throw new IllegalArgumentException("Grid definition must be a map");
    return decode(definition.getUrn(), map, serviceId, definition.getAnnotations());
  }

  public static GridAlignment decode(Map<String, ?> specification) {
    var frozen = org.integratedmodelling.klab.api.digitaltwin.GridSpecification.copyOf(specification);
    return decode(org.integratedmodelling.klab.api.digitaltwin.GridSpecification.urn(frozen), frozen, null, List.of());
  }

  private static GridAlignment decode(String urn, Map<?, ?> map, String serviceId, java.util.Collection<org.integratedmodelling.klab.api.lang.Annotation> annotations) {
    for (var key : map.keySet())
      if (!Set.of("anchor","projection","span","strict","snap","resolutions").contains(key))
        throw new IllegalArgumentException("Unknown grid field: " + key);
    var projection = Projection.of(Objects.toString(literal(map.get("projection")),"EPSG:4326"));
    var anchor = ShapeImpl.create(projection.getCode()+" "+Objects.toString(literal(map.get("anchor")),""));
    if (!(anchor.getJTSGeometry() instanceof org.locationtech.jts.geom.Point point) || point.isEmpty())
      throw new IllegalArgumentException("A grid anchor must be one point");
    var transformed = anchor.transform(projection).getJTSGeometry().getCoordinate();
    Object strictValue = literal(map.get("strict"));
    if (strictValue != null && !(strictValue instanceof Boolean)) throw new IllegalArgumentException("Grid strict must be boolean");
    boolean strict = strictValue == null || (Boolean) strictValue;
    Object snapValue = literal(map.get("snap"));
    if (snapValue != null && !(snapValue instanceof Boolean)) throw new IllegalArgumentException("Grid snap must be boolean");
    boolean snap = snapValue == null || (Boolean) snapValue;
    var warnings = new ArrayList<String>();
    double[] step = steps(literal(map.get("span")),projection,anchor,warnings);
    if (!strict) {
      step[0] = decimal(step[0]); step[1] = decimal(step[1]);
    }
    var levels = new ArrayList<Double>();
    if (map.containsKey("resolutions")) {
      if (!(literal(map.get("resolutions")) instanceof List<?> resolutions) || resolutions.isEmpty())
        throw new IllegalArgumentException("Grid resolutions must be a nonempty list");
      for (var resolution : resolutions) {
        var requested = steps(literal(resolution),projection,anchor,new ArrayList<>());
        // Compare before rounding the common base: geographic X/Y remain on the same hierarchy.
        var base = steps(literal(map.get("span")),projection,anchor,new ArrayList<>());
        double ratio = requested[0] / base[0];
        if (Math.abs(requested[1] / base[1] - ratio) > 1e-9)
          throw new IllegalArgumentException("Grid resolutions must scale both axes equally");
        levels.add(ratio);
      }
      levels.sort(Double::compare);
      if (levels.stream().noneMatch(v -> Math.abs(v-1)<1e-9))
        throw new IllegalArgumentException("Grid resolutions must include span");
    } else {
      for (int exponent = -20; exponent <= 20; exponent++) levels.add(Math.scalb(1.0,exponent));
    }
    var world=worldBounds(projection);
    if (world.length==4) {
        double width=world[1]-world[0],height=world[3]-world[2];
        double nx = Math.max(1,Math.rint(width/step[0])), ny = Math.max(1,Math.rint(height/step[1]));
        if (!Double.isFinite(nx) || !Double.isFinite(ny) || nx>0x1p52 || ny>0x1p52)
          throw new IllegalArgumentException("World grid resolution exceeds coordinate precision");
        if (transformed.x < -width/2 || transformed.x > width/2 || transformed.y < -height/2 || transformed.y > height/2)
          throw new IllegalArgumentException("Grid anchor is outside the world");
        step[0] = width/nx; step[1] = height/ny;
        warnings.add("Global grid spacing normalized to whole cells over the world extent");
        if (snap) {
          double ix = Math.rint((transformed.x+width/2)/step[0]), iy = Math.rint((transformed.y+height/2)/step[1]);
          double x = -width/2+ix*step[0], y = -height/2+iy*step[1];
          if (x!=transformed.x || y!=transformed.y) warnings.add("Grid anchor snapped from ("+transformed.x+", "+transformed.y+") to ("+x+", "+y+") in "+projection.getCode());
          transformed.x=x; transformed.y=y;
          // Only retain levels that put both world edges on the same lattice as this anchor.
          levels.removeIf(level -> nx/level<1 || ny/level<1 || !integer(nx/level) || !integer(ny/level) || !integer(ix/level) || !integer(iy/level));
        } else {
          levels.removeIf(level -> nx/level<1 || ny/level<1 || !integer(nx/level) || !integer(ny/level));
        }
    }
    String canonical = urn+"|"+projection.getCode()+"|"+transformed.x+"|"+transformed.y
        +"|"+step[0]+"|"+step[1]+"|"+strict+"|"+snap+"|"+levels;
    String fingerprint;
    try { fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(canonical.getBytes(StandardCharsets.UTF_8))); }
    catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    var source = new TreeMap<String, Object>();
    map.forEach((key, value) -> source.put(key.toString(), unwrap(value)));
    return new GridAlignment(1,urn,fingerprint,projection.getCode(),transformed.x,
        transformed.y,step[0],step[1],strict,snap,levels,warnings,
        org.integratedmodelling.klab.api.digitaltwin.GridSpecification.code(urn, source), serviceId, new ArrayList<>(annotations));
  }

  private static Object unwrap(Object value) {
    value = literal(value);
    return value instanceof List<?> list ? list.stream().map(GridAlignmentSupport::unwrap).toList() : value;
  }

  /** Global domains supported by the rectangular lattice, expressed in working CRS coordinates. */
  public static double[] worldBounds(Projection projection) {
    var crs=ProjectionImpl.promote(projection).getCRS();
    if (crs instanceof GeographicCRS) {
      try {
        double x=Units.RADIAN.getConverterToAny(crs.getCoordinateSystem().getAxis(0).getUnit()).convert(Math.PI);
        double y=Units.RADIAN.getConverterToAny(crs.getCoordinateSystem().getAxis(1).getUnit()).convert(Math.PI/2);
        return new double[]{-x,x,-y,y};
      } catch (javax.measure.IncommensurableException e) { throw new IllegalArgumentException("Geographic grid requires angular axes",e); }
    }
    if (projection.getCode().equals("EPSG:3857")) {
      double edge=Math.PI*6378137;
      return new double[]{-edge,edge,-edge,edge};
    }
    return new double[0];
  }

  private static boolean integer(double value) {
    return Double.isFinite(value) && Math.abs(value-Math.rint(value)) <= 1e-8;
  }

  private static double decimal(double value) {
    return BigDecimal.valueOf(value).round(new MathContext(12)).doubleValue();
  }

  private static double[] steps(Object span, Projection projection, ShapeImpl anchor, List<String> warnings) {
    if (span instanceof Number number) return positive(number.doubleValue(),number.doubleValue());
    if (span instanceof String text) span = Quantity.create(text);
    if (!(span instanceof Quantity quantity)) throw new IllegalArgumentException("Grid span must be a number or quantity");
    var service = ServiceConfiguration.INSTANCE.getService(UnitService.class);
    var unit = service.getUnit(quantity.getUnit());
    boolean length = service.isCompatible(unit,service.meters());
    var radians = service.getUnit("rad");
    boolean angle = service.isCompatible(unit,radians);
    if (!length && !angle) throw new IllegalArgumentException("Grid span must express length or angle");
    double value = (length ? service.meters() : radians).convert(quantity.getValue(),unit).doubleValue();
    var crs = ProjectionImpl.promote(projection).getCRS();
    boolean geographic = crs instanceof GeographicCRS;
    try {
      if (length == !geographic) {
        var nativeUnit = geographic ? Units.RADIAN : Units.METRE;
        return positive(nativeUnit.getConverterToAny(crs.getCoordinateSystem().getAxis(0).getUnit()).convert(value),
            nativeUnit.getConverterToAny(crs.getCoordinateSystem().getAxis(1).getUnit()).convert(value));
      }
      warnings.add("Grid span units differ from the working CRS; spacing is approximated once at the anchor point");
      var latlon = anchor.transform(Projection.getLatLon());
      double latitude = Math.toRadians(latlon.getJTSGeometry().getCoordinate().y);
      var geographicCRS = (GeographicCRS) ProjectionImpl.promote(Projection.getLatLon()).getCRS();
      var ellipsoid = geographicCRS.getDatum().getEllipsoid();
      double a = ellipsoid.getSemiMajorAxis(), b = ellipsoid.getSemiMinorAxis();
      double e2 = 1-(b/a)*(b/a), d = 1-e2*Math.sin(latitude)*Math.sin(latitude);
      double parallel = a*Math.cos(latitude)/Math.sqrt(d), meridian = a*(1-e2)/Math.pow(d,1.5);
      if (geographic && Math.abs(latitude)==Math.PI/2) {
        // Longitude has zero ground length at a pole. Use the adjacent row's midpoint instead.
        double midpoint=Math.copySign(Math.max(0,Math.PI/2-value/(2*meridian)),latitude);
        parallel=a*Math.cos(midpoint)/Math.sqrt(1-e2*Math.sin(midpoint)*Math.sin(midpoint));
        warnings.add("Longitude ground spacing is undefined at the pole; using the adjacent row midpoint");
      }
      if (geographic) return positive(
          Units.RADIAN.getConverterToAny(crs.getCoordinateSystem().getAxis(0).getUnit()).convert(value/parallel),
          Units.RADIAN.getConverterToAny(crs.getCoordinateSystem().getAxis(1).getUnit()).convert(value/meridian));
      return positive(Units.METRE.getConverterToAny(crs.getCoordinateSystem().getAxis(0).getUnit()).convert(value*parallel),
          Units.METRE.getConverterToAny(crs.getCoordinateSystem().getAxis(1).getUnit()).convert(value*meridian));
    } catch (javax.measure.IncommensurableException e) { throw new IllegalArgumentException("Unsupported grid CRS units",e); }
  }

  private static double[] positive(double x, double y) {
    if (!Double.isFinite(x) || !Double.isFinite(y) || x<=0 || y<=0)
      throw new IllegalArgumentException("Grid span must be finite and positive");
    return new double[]{x,y};
  }

  public static Grid align(Grid requested, GridAlignment alignment) {
    var target = Projection.of(alignment.projection());
    var envelope = EnvelopeImpl.promote(requested.getEnvelope());
    if (!target.equals(envelope.getProjection())) {
      try { envelope = EnvelopeImpl.create(envelope.getJTSEnvelope().transform(ProjectionImpl.promote(target).getCRS(),true)); }
      catch (Exception e) { throw new IllegalArgumentException("Cannot project observation onto grid",e); }
    }
    Grid best = null;
    double bestError = Double.POSITIVE_INFINITY;
    long desired = Math.max(1,requested.size());
    var levels = alignment.strict() ? List.of(1.0) : alignment.levels();
    for (double level : levels) {
      var point = new org.locationtech.jts.geom.GeometryFactory().createPoint(
          new org.locationtech.jts.geom.Coordinate(alignment.anchorX(),alignment.anchorY()));
      var constraint = new GridImpl(point,target,alignment.stepX()*level);
      constraint.setyCellSize(alignment.stepY()*level);
      var world=worldBounds(target);
      if (world.length==4) constraint.setWorldBounds(world);
      Grid candidate;
      try { candidate = constraint.locate(envelope); }
      catch (IllegalArgumentException | ArithmeticException tooFine) { continue; }
      double error = Math.abs((double)candidate.size()-desired);
      // Ascending levels make equal errors prefer the finer grid deterministically.
      if (error < bestError) { best = candidate; bestError = error; }
    }
    if (best == null) throw new IllegalArgumentException("No representable anchored grid covers the observation");
    return best;
  }

  /** Rectangular requests cover their whole normalized grid, including rounded edge cells.
   * Explicit transported shape masks are preserved by their caller. */
  static ShapeImpl rectangularSupport(org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape shape, Grid grid) {
    var projected = ShapeImpl.promote(shape).transform(grid.getProjection());
    if (!projected.getJTSGeometry().isRectangle()) return projected;
    var bounds = grid.getEnvelope();
    double west = bounds.getMinX(), east = bounds.getMaxX(), south = bounds.getMinY(), north = bounds.getMaxY();
    var world = grid.getWorldBounds();
    var factory = projected.getJTSGeometry().getFactory();
    org.locationtech.jts.geom.Geometry footprint;
    if (world.length == 4) {
      south = Math.max(south, world[2]); north = Math.min(north, world[3]);
      double period = world[1] - world[0], width = east - west;
      if (width >= period) { west = world[0]; east = world[1]; }
      else { west -= Math.floor((west-world[0])/period)*period; east = west+width; }
      if (east > world[1]) {
        var left = factory.toGeometry(new org.locationtech.jts.geom.Envelope(west,world[1],south,north));
        var right = factory.toGeometry(new org.locationtech.jts.geom.Envelope(world[0],world[0]+east-world[1],south,north));
        footprint = left.union(right);
      } else footprint = factory.toGeometry(new org.locationtech.jts.geom.Envelope(west,east,south,north));
    } else footprint = factory.toGeometry(new org.locationtech.jts.geom.Envelope(west,east,south,north));
    return new ShapeImpl(footprint, grid.getProjection());
  }

  /** Apply before allocation/resolution; preserve time and nonrectangular coverage. */
  public static Geometry alignGeometry(Geometry geometry, GridAlignment alignment) {
    if (alignment == null || geometry == null || geometry.isEmpty() || geometry.isUniversal()) return geometry;
    var dimension = geometry.dimension(Geometry.Dimension.Type.SPACE);
    if (dimension == null || dimension.getDimensionality()!=2) return geometry;
    var input=org.integratedmodelling.klab.api.data.StorageScan.parseGeometry(geometry.encode());
    var sourceDimension=input.dimension(Geometry.Dimension.Type.SPACE);
    var sourceProjection=Projection.of(Objects.toString(sourceDimension.getParameters().get("proj"),"EPSG:4326"));
    var sourceWorld=worldBounds(sourceProjection);
    if (sourceWorld.length==4) sourceDimension.getParameters().put("world",Arrays.stream(sourceWorld).boxed().toList());
    var scale = new ScaleImpl(input);
    var space = scale.getSpace();
    if (space == null || space.getEnvelope()==null || space.getEnvelope().getWidth()<=0 || space.getEnvelope().getHeight()<=0) return geometry;
    Grid requested = space instanceof org.integratedmodelling.klab.api.knowledge.observation.scale.space.Tile tile
        ? tile.getGrid() : new GridImpl(space.getEnvelope(),null,1,1);
    var aligned = align(requested,alignment);
    var result = (GeometryImpl)Geometry.create(Geometry.forTransport(scale).encode());
    var bounds = aligned.getEnvelope();
    result.withProjection(alignment.projection()).withSpatialShape(List.of(aligned.getXCells(),aligned.getYCells()))
        .withBoundingBox(bounds.getMinX(),bounds.getMaxX(),bounds.getMinY(),bounds.getMaxY());
    if (space instanceof org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape shape)
      result.withShape(rectangularSupport(shape,aligned).asWKB());
    var spatial = result.dimension(Geometry.Dimension.Type.SPACE);
    spatial.getParameters().remove("sgrid");
    spatial.getParameters().remove("gridurn");
    spatial.getParameters().remove("world");
    spatial.getParameters().put("gridalignment",alignment.fingerprint());
    if (aligned.getWorldBounds().length==4) spatial.getParameters().put("world",java.util.Arrays.stream(aligned.getWorldBounds()).boxed().toList());
    // A shape-only object becomes a regular tile; use an explicitly regular dimension.
    var encoded = spatial.encode();
    if (!spatial.isRegular()) result = result.substituteDimension(Geometry.create("S"+encoded.substring(1)).dimension(Geometry.Dimension.Type.SPACE));
    return result;
  }
}
