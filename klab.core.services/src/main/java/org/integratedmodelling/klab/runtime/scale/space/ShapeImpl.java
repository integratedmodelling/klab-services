package org.integratedmodelling.klab.runtime.scale.space;

import org.geotools.geojson.GeoJSON;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geometry.jts.WKBReader;
import org.geotools.referencing.CRS;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.TopologicallyComparable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Envelope;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.klab.api.services.UnitService;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.utilities.Utils;
import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

public class ShapeImpl extends SpaceImpl implements Shape {

    private static final long serialVersionUID = 5154895981013940462L;

    protected Geometry geometry;
    transient private Geometry standardizedGeometry;
    public static WKBWriter wkbWriter = new WKBWriter();
    public static WKTReader wktReader = new WKTReader();
    public static org.integratedmodelling.klab.api.geometry.Geometry.Encoder wkbEncoder =
            new org.integratedmodelling.klab.api.geometry.Geometry.Encoder() {
        @Override
        public org.integratedmodelling.klab.api.geometry.Geometry.Dimension.Type dimension() {
            return org.integratedmodelling.klab.api.geometry.Geometry.Dimension.Type.SPACE;
        }

        @Override
        public String key() {
            return "shape";
        }

        @Override
        public String encode(Object value) {
            if (value instanceof org.locationtech.jts.geom.Geometry geom) {
                return WKBWriter.toHex(ShapeImpl.wkbWriter.write(geom));
            } else if (value instanceof String string && string.contains("(")) {
                var shape = ShapeImpl.create(string);
                return shape.asWKB();
            }
            return value == null ? null : value.toString();
        }
    };

    private EnvelopeImpl envelope;
    private Shape.Type geometryType = null;
    private ProjectionImpl projection;

    // to avoid multiple rounds of simplification
    private boolean simplified = false;

    private ShapeImpl() {
        setShape(List.of(1L));
    }

    public ShapeImpl(Geometry geometry, Projection projection) {
        setShape(List.of(1L));
        this.geometry = geometry;
        this.projection = ProjectionImpl.promote(projection);
        this.envelope = EnvelopeImpl.create(geometry.getEnvelopeInternal(), this.projection);
    }

    public boolean isSimplified() {
        return simplified;
    }

    public void setSimplified(boolean simplified) {
        this.simplified = simplified;
    }

    // these are used to speed up repeated point-in-polygon operations like
    // those that RasterActivationLayer does.
    private PreparedGeometry preparedShape;
    private boolean preparationAttempted;

    // same geometry in appropriate meters projection. Set or computed on demand.
    private Geometry metered;

    public static Shape empty() {
        return new ShapeImpl();
    }

    @Override
    public String toString() {
        return projection.getCode() + " " + geometry;
    }

    public Map<String, Object> asGeoJSON() {
        StringWriter writer = new StringWriter(1024);
        try {
            GeoJSON.write(geometry, writer);
            return Utils.Json.parseObject(writer.toString(), Map.class);
        } catch (IOException e) {
            throw new KlabIOException(e);
        }
    }

    public String asGeoJSONString() {
        StringWriter writer = new StringWriter(1024);
        try {
            GeoJSON.write(geometry, writer);
            return writer.toString();
        } catch (IOException e) {
            throw new KlabIOException(e);
        }
    }

    public static ShapeImpl create(String wkt) throws KlabValidationException {
        ShapeImpl ret = new ShapeImpl();
        ret.parseWkt(wkt);
        if (ret.geometry != null) {
            ret.envelope = EnvelopeImpl.create(ret.geometry.getEnvelopeInternal(), ret.projection);
        }
        return ret;
    }

    public static Shape create(String wkt, Projection projection) throws KlabValidationException {
        ShapeImpl ret = new ShapeImpl();
        ret.parseWkt(wkt);
        ret.projection = ProjectionImpl.promote(projection);
        if (ret.geometry != null) {
            ret.envelope = EnvelopeImpl.create(ret.geometry.getEnvelopeInternal(), ret.projection);
        }
        return ret;
    }

    public static Shape create(double x1, double y1, double x2, double y2, Projection projection) {
        ShapeImpl ret = new ShapeImpl();
        ret.geometry = makeCell(x1, y1, x2, y2);
        ret.projection = ProjectionImpl.promote(projection);
        ret.envelope = EnvelopeImpl.create(ret.geometry.getEnvelopeInternal(), ret.projection);
        ret.geometryType = Shape.Type.POLYGON;
        return ret;
    }

    public static Shape create(double x1, double y1, Projection projection) {
        ShapeImpl ret = new ShapeImpl();
        ret.geometry = makePoint(x1, y1);
        ret.projection = ProjectionImpl.promote(projection);
        ret.envelope = EnvelopeImpl.create(ret.geometry.getEnvelopeInternal(), ret.projection);
        ret.geometryType = Shape.Type.POINT;
        return ret;
    }

    public static Shape create(Envelope envelope) {
        return create(envelope.getMinX(), envelope.getMinY(), envelope.getMaxX(), envelope.getMaxY(),
                envelope.getProjection());
    }

    public static ShapeImpl create(Geometry geometry, Projection projection) {
        ShapeImpl ret = new ShapeImpl();
        ret.geometry = geometry;
        ret.projection = ProjectionImpl.promote(projection);
        ret.envelope = EnvelopeImpl.create(ret.geometry.getEnvelopeInternal(), ret.projection);
        return ret;
    }

    public static Shape create(Collection<Geometry> geometries, Projection projection) {

        if (geometries.isEmpty()) {
            return null;
        }

        if (geometries.size() == 1) {
            return create(geometries.iterator().next(), projection);
        }

        return create(
                SpaceImpl.gFactory.createGeometryCollection(geometries.toArray(new Geometry[0])),
                projection);
    }

    public static Geometry makeCell(double x1, double y1, double x2, double y2) {

        Coordinate[] pts = {new Coordinate(x1, y1), new Coordinate(x2, y1), new Coordinate(
                x2, y2), new Coordinate(x1
                , y2),
                            new Coordinate(x1, y1)};

        return SpaceImpl.gFactory.createPolygon(SpaceImpl.gFactory.createLinearRing(pts), null);
    }

    public static Geometry makePoint(double x1, double y1) {
        Coordinate coordinate = new Coordinate(x1, y1);
        return SpaceImpl.gFactory.createPoint(coordinate);
    }

    public static Geometry makePoint(Shape cell) {
        double[] xy = promote(cell).getCenter(false);
        return SpaceImpl.gFactory.createPoint(new Coordinate(xy[0], xy[1]));
    }

    protected ShapeImpl(ShapeImpl shape) {
        this.geometry = shape.geometry;
        this.projection = shape.projection;
        this.envelope = shape.envelope;
        this.geometryType = shape.geometryType;
    }

    @Override
    public Projection getProjection() {
        return projection;
    }

    private Geometry getMeteredShape() {
        if (metered == null) {
            if (projection.isMeters()) {
                metered = geometry;
            } else {
                metered = transform((ProjectionImpl) ProjectionImpl.getUTM(getEnvelope())).getJTSGeometry();
            }
        }
        return metered;
    }

    @Override
    public Shape.Type getGeometryType() {
        if (geometryType == null) {
            if (geometry instanceof Polygon) {
                geometryType = Shape.Type.POLYGON;
            } else if (geometry instanceof MultiPolygon) {
                geometryType = Shape.Type.MULTIPOLYGON;
            } else if (geometry instanceof Point) {
                geometryType = Shape.Type.POINT;
            } else if (geometry instanceof MultiLineString) {
                geometryType = Shape.Type.MULTILINESTRING;
            } else if (geometry instanceof LineString) {
                geometryType = Shape.Type.LINESTRING;
            } else if (geometry instanceof MultiPoint) {
                geometryType = Shape.Type.MULTIPOINT;
            }
        }
        return geometryType;
    }

    public double getNativeArea() {
        return geometry.getArea();
    }

    @Override
    public double getArea(Unit unit) {
        return unit.convert(
                           getMeteredShape().getArea(),
                           ServiceConfiguration.INSTANCE.getService(UnitService.class).squareMeters())
                   .doubleValue();
    }

    public Shape getCentroid() {
        return create(geometry.getCentroid(), projection);
    }

    @Override
    public boolean isEmpty() {
        return geometry == null || geometry.isEmpty();
    }

    public Geometry getJTSGeometry() {
        return geometry;
    }

    @Override
    public ShapeImpl transform(Projection otherProjection) throws KlabValidationException {

        if (this.projection.equals(otherProjection)) {
            return this;
        }
        Geometry g = null;

        try {
            g = JTS.transform(
                    geometry,
                    CRS.findMathTransform(
                            ProjectionImpl.promote(projection).getCoordinateReferenceSystem(),
                            ProjectionImpl.promote(otherProjection).getCoordinateReferenceSystem()));
        } catch (Exception e) {
            throw new KlabValidationException(e);
        }

        return ShapeImpl.create(g, otherProjection);
    }

    @Override
    public EnvelopeImpl getEnvelope() {
        return envelope;
    }

    @Override
    public Shape intersection(Shape other) {
        if ((projection != null || other.getProjection() != null) && !projection.equals(
                other.getProjection())) {
            try {
                other = other.transform(projection);
            } catch (KlabValidationException e) {
                return empty();
            }
        }
        return create(fix(geometry).intersection(fix(((ShapeImpl) other).geometry)), projection);
    }

    public Shape fixInvalid() {
        /*
         * TODO use next-level JTS functions now available when we can upgrade
         */
        Geometry geom = this.geometry.buffer(0);
        return create(geom, projection);
    }

    @Override
    public Shape union(Shape other) {
        if ((projection != null || other.getProjection() != null) && !projection.equals(
                other.getProjection())) {
            try {
                other = other.transform(projection);
            } catch (KlabValidationException e) {
                return empty();
            }
        }
        return create(fix(geometry).union(fix(((ShapeImpl) other).geometry)), projection);
    }

    public boolean containsCoordinates(double x, double y) {
        checkPreparedShape();
        return preparedShape == null
               ? geometry.contains(SpaceImpl.gFactory.createPoint(new Coordinate(x, y)))
               : preparedShape.contains(SpaceImpl.gFactory.createPoint(new Coordinate(x, y)));
    }

    private void checkPreparedShape() {
        if (this.preparedShape == null && !preparationAttempted) {
            preparationAttempted = true;
            try {
                this.preparedShape = PreparedGeometryFactory.prepare(geometry);
            } catch (Throwable t) {
            }
        }
    }

    public PreparedGeometry getPreparedGeometry() {
        checkPreparedShape();
        return preparedShape;
    }

    // public double getCoverage(Tile cell, boolean simpleIntersection) {
    //
    // checkPreparedShape();
    // if (preparedShape == null) {
    // if (simpleIntersection) {
    // Geometry gm = makePoint(cell);
    // return gm.intersects(geometry) ? 1.0 : 0.0;
    // }
    // Geometry gm = makeCell(cell.getEast(), cell.getSouth(), cell.getWest(), cell.getNorth());
    // return gm.covers(geometry) ? 1.0 : (gm.intersection(geometry).getArea() / gm.getArea());
    // }
    // if (simpleIntersection) {
    // return preparedShape.covers(makePoint(cell)) ? 1 : 0;
    // }
    // Geometry gm = makeCell(cell.getEast(), cell.getSouth(), cell.getWest(), cell.getNorth());
    // return preparedShape.covers(gm) ? 1.0 : (gm.intersection(geometry).getArea() / gm.getArea());
    // }

    private void parseWkt(String s) throws KlabValidationException {

        String pcode = Projection.DEFAULT_PROJECTION_CODE;
        Geometry geometry = null;
        boolean wkt = false;
        /*
         * first see if we start with a token that matches "EPSG:[0-9]*". If so, set the CRS from
         * it; otherwise it is null (not the plugin default).
         */
        if (s.startsWith("EPSG:") || s.startsWith("urn:")) {
            int n = s.indexOf(' ');
            pcode = s.substring(0, n);
            s = s.substring(n + 1);
        }
        try {
            if (s.contains("(")) {
                wkt = true;
                geometry = new WKTReader().read(s);
            } else {
                geometry = new WKBReader().read(WKBReader.hexToBytes(s));
            }
        } catch (ParseException e) {
            throw new KlabValidationException(
                    "error parsing " + (wkt ? "WKT" : "WBT") + ": " + e.getMessage());
        }

        this.projection = new ProjectionImpl(pcode);
        this.geometry = geometry;
    }

    public Geometry getStandardizedGeometry() {
        if (this.isEmpty() || this.projection.equals(Projection.getLatLon())) {
            return this.geometry;
        }
        ShapeImpl shape = this.transform(Projection.getLatLon());
        this.standardizedGeometry = shape.geometry;
        return this.standardizedGeometry;
    }

    @Override
    public int getRank() {
        // TODO Auto-generated method stub
        return 0;
    }

    // @Override
    // public Space mergeContext(Extent<?> extent) {
    // if (extent instanceof Space) {
    // return Space.createMergedExtent(this, (ISpace) extent);
    // }
    // throw new IllegalArgumentException("a Shape cannot merge an extent of type " +
    // extent.getType());
    // }

    @Override
    public long size() {
        return 1;
    }

    // @Override
    // public boolean contains(Extent<?> o) throws KlabException {
    // if (this.equals(o)) {
    // return true;
    // }
    // if (o instanceof ISpace) {
    // IShape shp = ((ISpace) o).getShape();
    // return this.getStandardizedGeometry().contains(((Shape) shp).getStandardizedGeometry());
    // }
    // return false;
    // }
    //
    // @Override
    // public boolean overlaps(IExtent o) throws KlabException {
    // if (this.equals(o)) {
    // return true;
    // }
    // // TODO Auto-generated method stub
    // return false;
    // }
    //
    // @Override
    // public boolean intersects(IExtent o) throws KlabException {
    // if (this.equals(o)) {
    // return true;
    // }
    // // TODO Auto-generated method stub
    // return false;
    // }

    @Override
    public double getCoverage() {
        return getStandardizedGeometry().getArea();
    }

    @Override
    public Iterator<Space> iterator() {
        List<Space> coll = new ArrayList<>();
        coll.add(this);
        return coll.iterator();
    }

    @Override
    public boolean isRegular() {
        return false;
    }

    @Override
    public int getDimensionality() {
        int ret = 0;
        switch (getGeometryType()) {
            case POINT:
            case MULTIPOINT:
            case EMPTY:
                break;
            case LINESTRING:
            case MULTILINESTRING:
                ret = 1;
                break;
            case MULTIPOLYGON:
            case POLYGON:
                ret = 2;
                break;
        }
        return ret;
    }

    public ReferencedEnvelope getJTSEnvelope() {
        return new ReferencedEnvelope(
                geometry.getEnvelopeInternal(), projection.getCoordinateReferenceSystem());
    }

    // @Override
    // public long[] shape() {
    // if (getDimensionality() == 2) {
    // return new long[] { 1, 1 };
    // } else if (getDimensionality() == 1) {
    // return new long[] { 1 };
    // }
    // return new long[] {};
    // }

    // @Override
    // public IExtent getExtent() {
    // return this;
    // }
    //
    // @Override
    // public int hashCode() {
    // final int prime = 31;
    // int result = 1;
    // result = prime * result + ((shapeGeometry == null) ? 0 : shapeGeometry.hashCode());
    // result = prime * result + ((projection == null) ? 0 : projection.hashCode());
    // return result;
    // }
    //
    // @Override
    // public boolean equals(Object obj) {
    //
    // if (obj == null) {
    // return false;
    // }
    // if (getClass() != obj.getClass()) {
    // return false;
    // }
    // Shape other = (Shape) obj;
    // if (shapeGeometry == null) {
    // if (other.shapeGeometry != null) {
    // return false;
    // }
    // } else if (other.getGeometryType() != getGeometryType()) {
    // return false;
    // } else if ((isMultiGeometry() && !other.isMultiGeometry()) || (!isMultiGeometry() &&
    // other.isMultiGeometry())) {
    // return false;
    // } else if (!fix(shapeGeometry).equals(fix(other.shapeGeometry))) {
    // return false;
    // }
    // if (projection == null) {
    // if (other.projection != null) {
    // return false;
    // }
    // } else if (!projection.equals(other.projection)) {
    // return false;
    // }
    // return true;
    // }

    // private boolean isMultiGeometry() {
    // return geometry instanceof GeometryCollection;
    // }

    // @Override
    // public Space merge(ITopologicallyComparable<?> other, LogicalConnector how, MergingOption...
    // options) {
    //
    // Shape shape = other instanceof Shape ? (Shape) other : null;
    // if (shape == null && other instanceof ISpace) {
    // shape = (Shape) ((ISpace) other).getShape();
    // }
    // if (shape == null) {
    // return copy();
    // }
    // if (how == LogicalConnector.UNION) {
    // return create(shapeGeometry.union(shape.transform(this.projection).getJTSGeometry()),
    // this.projection);
    // } else if (how == LogicalConnector.INTERSECTION) {
    // return create(shapeGeometry.intersection(shape.transform(this.projection).getJTSGeometry()),
    // this.projection);
    // } else if (how == LogicalConnector.EXCLUSION) {
    // return create(shapeGeometry.difference(shape.transform(this.projection).getJTSGeometry()),
    // this.projection);
    // }
    // throw new IllegalArgumentException("cannot merge a shape with " + other);
    // }

    // @Override
    // public IParameters<String> getParameters() {
    // return baseDimension.getParameters();
    // }

    /**
     * WKB code without projection
     *
     * @return the WKB code
     */
    public String getWKB() {
        return WKBWriter.toHex(wkbWriter.write(geometry));
    }

    /**
     * WKB code WITH projection
     *
     * @return the WKB code
     */
    public String asWKB() {
        return projection.getCode() + " " + WKBWriter.toHex(wkbWriter.write(geometry));
    }

    @Override
    public String encode() {
        return "s2(1,1){shape=" + promote(this).asWKB() + "}";
    }

    // @Override
    // public IScaleMediator getMediator(IExtent extent) {
    // Space other = (ISpace) extent;
    // if (other instanceof Space && ((Space) other).getGrid() != null) {
    // return new GridToShape(this, (Grid) ((Space) other).getGrid());
    // } else if (other instanceof Space && ((Space) other).getTessellation() != null) {
    // return new FeaturesToShape(this, ((Space) other).getTessellation());
    // } else {
    // return new ShapeToShape((Shape) other.getShape(), this);
    // }
    // }

    // @Override
    // public Extent mergeCoverage(IExtent other, LogicalConnector connector) {
    // return merge(other, connector);
    // }

    @Override
    public ShapeImpl copy() {
        // the geometry is immutable, so a shallow copy is OK
        return create((Geometry) geometry, projection);
    }

    // @Override
    // public IServiceCall getKimSpecification() {
    // List<Object> args = new ArrayList<>(2);
    // args.add("shape");
    // args.add(toString());
    // return new KimServiceCall("space", args.toArray());
    // }

    @Override
    public Shape getBoundingExtent() {
        return new ShapeImpl(getEnvelope().asJTSGeometry(), projection);
    }

    @Override
    public Collection<Shape> getHoles() {
        List<Shape> ret = new ArrayList<>();
        if (geometry instanceof Polygonal) {
            // scan all polygons in multipolygon, one in polygon
            // add all interior rings in each as a new shape
        }
        return ret;
    }

    // @Override
    // public SpatialExtent getExtentDescriptor() {
    // Envelope stdEnvelope = getEnvelope().transform(Projection.getLatLon(), true);
    // SpatialExtent ret = new SpatialExtent();
    // ret.setEast(stdEnvelope.getMaxX());
    // ret.setWest(stdEnvelope.getMinX());
    // ret.setSouth(stdEnvelope.getMinY());
    // ret.setNorth(stdEnvelope.getMaxY());
    // return ret.normalize();
    // }

    // @SuppressWarnings("unchecked")
    // @Override
    // public <T extends ILocator> T as(Class<T> cls) {
    // return null; // (T) envelope.asLocator();
    // }

    @Override
    public double getStandardizedVolume() {
        return Double.NaN;
    }

    @Override
    public double getStandardizedArea() {
        if (getGeometryType() == Shape.Type.POLYGON || getGeometryType() == Shape.Type.MULTIPOLYGON) {
            return getMeteredShape().getArea();
        }
        return 0;
    }

    @Override
    public double getStandardizedLength() {
        return getMeteredShape().getLength();
    }

    @Override
    public double getStandardizedWidth() {
        if (getGeometryType() == Shape.Type.POLYGON || getGeometryType() == Shape.Type.MULTIPOLYGON) {
            return getMeteredShape().getEnvelopeInternal().getWidth();
        }
        return 0;
    }

    @Override
    public double getStandardizedHeight() {
        if (getGeometryType() == Shape.Type.POLYGON || getGeometryType() == Shape.Type.MULTIPOLYGON) {
            return getMeteredShape().getEnvelopeInternal().getHeight();
        }
        return 0;
    }

    @Override
    public double getStandardizedDistance(Space space) {
        return getMeteredShape().distance(promote(space.getGeometricShape()).getMeteredShape());
    }

    public static ShapeImpl promote(Shape geometricShape) {
        if (geometricShape instanceof ShapeImpl) {
            return (ShapeImpl) geometricShape;
        }
        return create(geometricShape.toString());
    }

    @Override
    public double getStandardizedDepth() {
        return Double.NaN;
    }

    public Shape simplify(double simplifyFactor) {
        return getSimplified(simplifyFactor);
    }

    public Shape getSimplified(Quantity resolution) {

        UnitService units = ServiceConfiguration.INSTANCE.getService(UnitService.class);

        if (this.simplified) {
            return this;
        }
        Unit unit = units.getUnit(resolution.getUnit());
        if (unit == null || !units.meters().isCompatible(unit)) {
            throw new KlabIllegalArgumentException("Can't use a non-length unit to simplify a shape");
        }
        // in m
        double simplifyFactor = units.meters().convert(resolution.getValue(), unit).doubleValue();

        // convert to projection units. FIXME there's certainly a proper method that
        // doesn't require reprojection (ask Andrea)
        double proportion = simplifyFactor / getMeteredShape().getLength();
        simplifyFactor = geometry.getLength() * proportion;

        Geometry geom = TopologyPreservingSimplifier.simplify(geometry, simplifyFactor);
        ShapeImpl ret = create(geom, this.projection);
        ret.simplified = true;
        return ret;
    }

    public Shape getSimplified(double simplifyFactor) {
        if (this.simplified) {
            return this;
        }
        Geometry geom = TopologyPreservingSimplifier.simplify(geometry, simplifyFactor);
        ShapeImpl ret = create(geom, this.projection);
        ret.simplified = true;
        return ret;
    }

    public boolean containsPoint(double[] coordinates) {
        checkPreparedShape();
        Point point = geometry.getFactory().createPoint(new Coordinate(coordinates[0], coordinates[1]));
        return preparedShape != null ? preparedShape.contains(point) : geometry.contains(point);
    }

    @Override
    public Shape buffer(double distance) {
        Geometry geom = this.geometry.buffer(distance);
        return create(geom, projection);
    }

    @Override
    public Shape difference(Shape shape) {
        Geometry geom = fix(this.geometry).difference(fix(((ShapeImpl) shape).getJTSGeometry()));
        return create(geom, projection);
    }

    private Geometry fix(Geometry jtsGeometry) {
        // if geometry is a point or line, buffer return an empty polygon, so we must
        // check it
        // a double check for a well formed polygon is needed
        if ((jtsGeometry instanceof GeometryCollection && !(jtsGeometry instanceof MultiLineString
                || jtsGeometry instanceof MultiPoint)) || jtsGeometry instanceof LineString || jtsGeometry instanceof Point) {
            return jtsGeometry.buffer(0);
        }
        return jtsGeometry;
    }

    /**
     * If the number of coordinates is higher than a passed threshold, simplify to the distance that retains
     * the max number of subdivisions along the diagonal of the envelope.
     *
     * @param maxCoordinates
     * @param nDivisions
     * @return
     */
    public Shape simplifyIfNecessary(int maxCoordinates, int nDivisions) {
        if (geometry.getNumPoints() > maxCoordinates) {
            double distance = Math.sqrt(
                    Math.pow(getEnvelope().getWidth(), 2) + Math.pow(getEnvelope().getHeight(), 2))
                    / (double) nDivisions;
            return getSimplified(distance);
        }
        return this;
    }

    /**
     * Join the passed shapes into another shape of the passed type, optionally including or excluding the
     * shapes themselves.
     * <p>
     * Assumes both shapes have the same projection.
     *
     * @param a
     * @param b
     * @param type
     * @param includeSources
     * @return a new shape
     */
    public static Shape join(Shape a, Shape b, Shape.Type type, boolean includeSources) {

        Geometry aj = ((ShapeImpl) a).getStandardizedGeometry();
        Geometry bj = ((ShapeImpl) b).getStandardizedGeometry();
        Geometry merged = null;

        switch (type) {
            case LINESTRING:
                merged = aj.getFactory().createLineString(
                        new Coordinate[]{aj.getCentroid().getCoordinates()[0],
                                         bj.getCentroid().getCoordinates()[0]});
                break;
            case POLYGON:
            case MULTIPOLYGON:
                List<Coordinate> cloud = new ArrayList<>();
                cloud.addAll(Arrays.asList(aj.getBoundary().getCoordinates()));
                cloud.addAll(Arrays.asList(bj.getBoundary().getCoordinates()));
                ConvexHull hull = new ConvexHull(cloud.toArray(new Coordinate[0]), aj.getFactory());
                merged = hull.getConvexHull().buffer(0);
                break;
            default:
                throw new IllegalArgumentException(
                        "cannot join two shapes into a " + type.name().toLowerCase());
        }

        if (merged != null && !includeSources) {
            merged = merged.difference(aj);
            merged = merged.difference(bj);
        }

        return merged == null ? null : create(merged, a.getProjection());
    }

    @Override
    public double[] getCenter(boolean standardized) {
        Point centroid = standardized ? getStandardizedGeometry().getCentroid() : geometry.getCentroid();
        return new double[]{centroid.getCoordinate().x, centroid.getCoordinate().y};
    }

    @Override
    public boolean isGeneric() {
        return false;
    }

    // public void show() {
    // JFrame f = new JFrame();
    // f.getContentPane().add(new Paint());
    // f.setSize(700, 700);
    // f.setVisible(true);
    // }
    //
    // class Paint extends JPanel {
    //
    // private static final long serialVersionUID = 7826836352532417280L;
    //
    // public void paint(Graphics g) {
    // ShapeWriter sw = new ShapeWriter();
    // g.setColor(Utils.Colors.RED);
    // java.awt.Shape polyShape = sw.toShape(geometry);
    // ((Graphics2D) g).draw(polyShape);
    // }
    // }

    // @Override
    // public ExtentDimension getExtentDimension() {
    // return ExtentDimension.AREAL;
    // }

    @Override
    public double getStandardizedDimension(Locator locator) {
        // TODO ignoring the locator: should check and throw exceptions
        return getStandardizedArea();
    }

    @Override
    public <T extends TopologicallyComparable<T>> Extent<T> merge(Extent<T> other, LogicalConnector how) {

        Shape shape = other instanceof Shape ? (Shape) other : null;
        if (shape == null && other instanceof Space) {
            shape = ((Space) other).getGeometricShape();
        }
        if (shape == null) {
            return (Extent<T>) copy();
        }
        if (how == LogicalConnector.UNION) {
            return (Extent<T>) create(
                    geometry.union(promote(shape.transform(this.projection)).getJTSGeometry()),
                    this.projection);
        } else if (how == LogicalConnector.INTERSECTION) {
            return (Extent<T>) create(
                    geometry.intersection(promote(shape.transform(this.projection)).getJTSGeometry()),
                    this.projection);
        } else if (how == LogicalConnector.EXCLUSION) {
            return (Extent<T>) create(
                    geometry.difference(promote(shape.transform(this.projection)).getJTSGeometry()),
                    this.projection);
        }
        throw new IllegalArgumentException("cannot merge a shape with " + other);
    }

    // @Override
    // public Geometry getGeometry() {
    // return geometry;
    // }

    // @Override
    // public long getOffset(long... offsets) {
    // return 0;
    // }

    // @Override
    // public ShapeImpl at(Object... locators) {
    // if (locators != null && locators.length == 1) {
    // if (locators[0] instanceof Number && ((Number) locators[0]).longValue() == 0) {
    // return this;
    // }
    // if (locators[0] instanceof Cell) {
    // if (getEnvelope().intersects(((Cell) locators[0]).getEnvelope())) {
    // // TODO coverage
    // return this;
    // }
    // return null;
    // } else if (locators[0] instanceof IShape) {
    // if (getEnvelope().intersects(((Shape) locators[0]).getEnvelope())) {
    // // TODO coverage
    // return this;
    // }
    // return null;
    // }
    // }
    // throw new IllegalStateException("an individual shape cannot be further located");
    // }

    @Override
    public double[] getStandardizedCentroid() {
        return getCenter(true);
    }

    // @Override
    // public boolean isCovered(long stateIndex) {
    // return true; // stateIndex == 0;
    // }

    // @Override
    // public IExtent adopt(IExtent extent, IMonitor monitor) {
    // // TODO Auto-generated method stub
    // return this;
    // }

    @Override
    public Space getExtent(long stateIndex) {
        if (stateIndex != 0) {
            throw new IllegalArgumentException("cannot access state #" + stateIndex + " in a Shape");
        }
        return this;
    }

    // @Override
    // protected Extent contextualizeTo(IExtent other, IAnnotation constraint) {
    //
    // if (constraint != null && constraint.size() == 0) {
    // return this;
    // }
    // if (this.getDimensionality() < 2) {
    // // a point remains a point, a line remains a line. Maybe later we can buffer
    // // using an annotation and apply the rules.
    // return this;
    // }
    //
    // /*
    // * dimensionality >= 2; if we have a grid, apply it. TODO this will need to be
    // * revised to apply a 2D grid to 2D extents matched with a 3D grid, which we
    // * don't have for now.
    // */
    // Grid grid = null;
    // if (other instanceof Space) {
    // grid = (Grid) ((Space) other).getGrid();
    // }
    //
    // if (grid != null && grid.isConsistent()) {
    // return Space.create(this, grid, true);
    // }
    //
    // return this;
    // }

    @Override
    public boolean contains(double[] coordinate) {
        return this.geometry.intersects(makePoint(coordinate[0], coordinate[1]));
    }

    // @Override
    // public IMetadata getMetadata() {
    // if (this.metadata == null) {
    // this.metadata = new Metadata();
    // }
    // return this.metadata;
    // }
    //
    // @Override
    // public double getDimensionSize(Unit unit) {
    // return unit.convert(getStandardizedArea(), Units.INSTANCE.SQUARE_METERS).doubleValue();
    // }
    //
    // @Override
    // public boolean isDistributed() {
    // return false;
    // }

    /**
     * Returned shape is guaranteed to be a polygon
     *
     * @return
     */
    public String getStandardizedEnvelopeWKT() {
        return ((ShapeImpl) getBoundingExtent().transform(Projection.getLatLon())).geometry.toString();
    }

    @Override
    public double getComplexity() {
        // TODO improve
        return geometry.getNumPoints();
    }

    @Override
    public Space at(Locator locator) {
        if (locator instanceof Number && ((Number) locator).longValue() == 0) {
            return this;
        }
        if (locator instanceof Shape) {
            if (getEnvelope().intersects(((Shape) locator).getEnvelope())) {
                // TODO coverage
                return this;
            }
            return null;
        }
        throw new IllegalStateException("an individual shape cannot be further located");
    }

    @Override
    public Extent<Space> collapsed() {
        return this;
    }

    @Override
    public double getDimensionSize() {
        return getStandardizedArea();
    }

    @Override
    public Unit getDimensionUnit() {
        return ServiceConfiguration.INSTANCE.getService(UnitService.class).squareMeters();
    }

    @Override
    public boolean matches(Collection<Constraint> constraints) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public <T extends Locator> T as(Class<T> cls) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean contains(Space o) {
        return geometry.contains(promote(o.getGeometricShape().transform(this.projection)).getJTSGeometry());
    }

    @Override
    public boolean overlaps(Space o) {
        return geometry.overlaps(promote(o.getGeometricShape().transform(this.projection)).getJTSGeometry());
    }

    @Override
    public boolean intersects(Space o) {
        return geometry.intersects(
                promote(o.getGeometricShape().transform(this.projection)).getJTSGeometry());
    }

    @Override
    public Shape getGeometricShape() {
        return this;
    }

    protected ServiceCall encodeCall() {
        ServiceCallImpl ret = new ServiceCallImpl();
        // TODO
        return ret;
    }

    @Override
    public String encode(String language) {
        return encodeCall().encode(language);
    }

}
