package org.integratedmodelling.klab.api.geometry.impl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension;
import org.integratedmodelling.klab.api.geometry.impl.GeometryImpl.DimensionImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.Quantity;

/**
 * Builder for geometries to ease defining time and space extents in forms that can be marshalled through the
 * API and converted to scales. For now missing the object features and defining only space and time for the
 * main extent.
 *
 * @author ferdinando.villa
 */
public class GeometryBuilder {

    private DimensionImpl space;
    private DimensionImpl time;

    /**
     * Quickly check if the passed string looks like a WKT string in the k.LAB supported format (potentially
     * with a projection). No validation, just simple heuristics to discriminate URNs or other obviously
     * different strings.
     *
     * @param urn
     * @return
     */
    static boolean isWKT(String urn) {
        if ((urn.contains("POLYGON") || urn.contains("POINT") || urn.contains("LINESTRING")) && urn.contains("(")
                && urn.contains(")")) {
            return true;
        }
        return false;
    }

    public class TimeBuilder {

        TimeBuilder() {
            if (time == null) {
                time = new DimensionImpl();
                time.setType(Dimension.Type.TIME);
                time.getParameters().put(GeometryImpl.PARAMETER_TIME_REPRESENTATION, Time.Type.LOGICAL);
                time.setDimensionality(1);
            }
        }

        public TimeBuilder generic() {
            time.setGeneric(true);
            return this;
        }

        public TimeBuilder regular() {
            time.setRegular(true);
            return this;
        }

        public TimeBuilder covering(TimeInstant start, TimeInstant end) {
            time.getParameters().put(GeometryImpl.PARAMETER_TIME_COVERAGE_START, start.getMilliseconds());
            time.getParameters().put(GeometryImpl.PARAMETER_TIME_COVERAGE_END, end.getMilliseconds());
            return this;
        }

        public TimeBuilder covering(long startMs, long endMs) {
            time.getParameters().put(GeometryImpl.PARAMETER_TIME_COVERAGE_START, startMs);
            time.getParameters().put(GeometryImpl.PARAMETER_TIME_COVERAGE_END, endMs);
            return this;
        }

        public TimeBuilder step(Quantity quantity) {
            regular();
            time.getParameters().put(GeometryImpl.PARAMETER_TIME_REPRESENTATION, Time.Type.GRID);
            time.getParameters().put(GeometryImpl.PARAMETER_TIME_GRIDRESOLUTION, quantity);
            return this;
        }

        public TimeBuilder between(TimeInstant start, TimeInstant end) {
            start(start);
            end(end);
            time.getParameters().put(GeometryImpl.PARAMETER_TIME_REPRESENTATION, Time.Type.PHYSICAL);
            return this;
        }

        public TimeBuilder between(long start, long end) {
            start(start);
            end(end);
            time.getParameters().put(GeometryImpl.PARAMETER_TIME_REPRESENTATION, Time.Type.PHYSICAL);
            return this;
        }

        public TimeBuilder year(int year) {
            var start = TimeInstant.create(year);
            start(start.getMilliseconds());
            end(start.plus(1, Time.Resolution.of(1,
                    Time.Resolution.Type.YEAR)).getMilliseconds());
            resolution(Time.Resolution.of(1, Time.Resolution.Type.YEAR));
            time.getParameters().put(GeometryImpl.PARAMETER_TIME_REPRESENTATION, Time.Type.PHYSICAL);
            return this;
        }

        public TimeBuilder start(TimeInstant start) {
            time.getParameters().put(GeometryImpl.PARAMETER_TIME_START, start.getMilliseconds());
            return this;
        }

        public TimeBuilder start(long startMs) {
            time.getParameters().put(GeometryImpl.PARAMETER_TIME_START, startMs);
            return this;
        }

        public TimeBuilder end(TimeInstant start) {
            time.getParameters().put(GeometryImpl.PARAMETER_TIME_END, start.getMilliseconds());
            return this;
        }

        public TimeBuilder end(long endMs) {
            time.getParameters().put(GeometryImpl.PARAMETER_TIME_END, endMs);
            return this;
        }

        public TimeBuilder resolution(Time.Resolution resolution) {
            time.getParameters().put(GeometryImpl.PARAMETER_TIME_SCOPE, resolution.getMultiplier());
            time.getParameters().put(GeometryImpl.PARAMETER_TIME_SCOPE_UNIT,
                    resolution.getType().name().toLowerCase());
            return this;
        }

        public TimeBuilder resolution(Time.Resolution.Type resolution, double multiplier) {
            time.getParameters().put(GeometryImpl.PARAMETER_TIME_SCOPE, multiplier);
            time.getParameters().put(GeometryImpl.PARAMETER_TIME_SCOPE_UNIT, resolution.name().toLowerCase());
            return this;
        }

        public TimeBuilder size(long n) {
            time.setShape(List.of(n));
            time.setRegular(true);
            return this;
        }

        public SpaceBuilder space() {
            return new SpaceBuilder();
        }

        public GeometryBuilder build() {
            if (time.getParameters().get(GeometryImpl.PARAMETER_TIME_START) == null || time.getParameters().get(GeometryImpl.PARAMETER_TIME_START) == null) {
                generic();
                time.getParameters().put(GeometryImpl.PARAMETER_TIME_REPRESENTATION, Time.Type.LOGICAL);
            }
            return GeometryBuilder.this;
        }
    }

    public class SpaceBuilder {

        SpaceBuilder() {
            if (space == null) {
                space = new DimensionImpl();
                space.setType(Dimension.Type.SPACE);
                space.setDimensionality(2);
            }
        }

        public SpaceBuilder generic() {
            space.setGeneric(true);
            return this;
        }

        public SpaceBuilder regular() {
            space.setRegular(true);
            return this;
        }

        public SpaceBuilder size(long x, long y) {
            space.setShape(List.of(x, y));
            space.setRegular(true);
            return this;
        }

        public SpaceBuilder size(long n) {
            space.setShape(List.of(n));
            space.setRegular(false);
            return this;
        }

        /**
         * Bounding box as a double[]{minX, maxX, minY, maxY}; lat/lon use lon as x axis
         */
        public SpaceBuilder boundingBox(double x1, double x2, double y1, double y2) {
            space.getParameters().put(GeometryImpl.PARAMETER_SPACE_BOUNDINGBOX, GeometryImpl.encodeVal(new double[]{x1, x2, y1, y2}));
            return this;
        }

        public SpaceBuilder shape(String wktb) {
            space.getParameters().put(GeometryImpl.PARAMETER_SPACE_SHAPE, wktb);
            return this;
        }

        public SpaceBuilder urn(String urn) {
            space.getParameters().put(GeometryImpl.PARAMETER_SPACE_RESOURCE_URN, urn);
            return this;
        }

        public SpaceBuilder resolution(Quantity gridResolution) {
            space.setRegular(true);
            space.getParameters().put(GeometryImpl.PARAMETER_SPACE_GRIDRESOLUTION, gridResolution.toString());
            return this;
        }

        public SpaceBuilder resolution(String gridResolution) {
            space.setRegular(true);
            space.getParameters().put(GeometryImpl.PARAMETER_SPACE_GRIDRESOLUTION, gridResolution);
            return this;
        }

        public GeometryBuilder build() {
            return GeometryBuilder.this;
        }
    }

    /**
     * Create a spatial region from a resource URN (specifying a polygon). The string may also specify a WKT
     * polygon using the k.LAB conventions (preceded by the EPSG: projection). The resulting
     *
     * @param urn
     * @return
     */
    public GeometryBuilder region(String urn) {
        if (isWKT(urn)) {
            return space().shape(urn).size(1).build();
        }
        return space().urn(urn).size(1).build();
    }

    /**
     * Create a spatial polygon of multiplicity 1 from a lat/lon bounding box and a resolution. The box is
     * "straight" with the X axis specifying
     * <em>longitude</em>.
     *
     * @return
     */
    public GeometryBuilder grid(double x1, double x2, double y1, double y2) {
        return space().regular().boundingBox(x1, x2, y1, y2).build();
    }

    /**
     * Create a spatial grid from a resource URN (specifying a polygon) and a resolution. The string may also
     * specify a WKT polygon using the k.LAB conventions (preceded by the EPSG: projection).
     *
     * @param urn
     * @param resolution a string in the format "1.km"
     * @return
     */
    public GeometryBuilder grid(String urn, String resolution) {
        if (isWKT(urn)) {
            return space().regular().resolution(resolution).shape(urn).build();
        }
        return space().regular().resolution(resolution).urn(urn).build();
    }

    /**
     * Create a spatial grid from a lat/lon bounding box and a resolution. The box is "straight" with the X
     * axis specifying <em>longitude</em>.
     *
     * @param resolution a string in the format "1 km"
     * @return
     */
    public GeometryBuilder grid(double x1, double x2, double y1, double y2, String resolution) {
        return space().regular().resolution(resolution).boundingBox(x1, x2, y1, y2).build();
    }

    /**
     * Create a temporal extent in years. If one year is passed, build a single-year extent; otherwise build a
     * yearly grid from the first year to the second.
     *
     * @param years
     * @return
     */
    public GeometryBuilder years(int... years) {
        if (years != null) {
            if (years.length == 1) {
                return time().start(startOfYear(years[0])).end(startOfYear(years[0] + 1)).size(1).build();
            } else if (years.length == 2) {
                return time().start(startOfYear(years[0])).end(startOfYear(years[1])).size((long) (years[1] - years[0]))
                             .resolution(Time.Resolution.Type.YEAR, 1).build();
            }
            // TODO irregular coverage?
        }
        throw new KlabIllegalArgumentException("wrong year parameters passed to TimeBuilder.years");
    }

    private long startOfYear(int i) {
        ZonedDateTime date = LocalDateTime.of(i, 1, 1, 0, 0).atZone(ZoneOffset.UTC);
        return date.toInstant().toEpochMilli();
    }

    /**
     * Use a builder to build a spatial geometry piece by piece. Call build() on the returned value to obtain
     * the geometry builder back. Calling build() on the space builder is not necessary for the geometry to be
     * recorded.
     *
     * @return
     */
    public SpaceBuilder space() {
        return new SpaceBuilder();
    }

    public TimeBuilder time() {
        return new TimeBuilder();
    }

    public GeometryImpl build() {

        GeometryImpl ret = new GeometryImpl();

        if (space == null && time == null) {
            ret.setScalar(true);
            ret.setEmpty(false);
        }

        if (space != null || time != null) {
            ret.setScalar(false);
            ret.setEmpty(false);
        }

        if (space != null) {
            ret.addDimension(space);
        }

        if (time != null) {
            ret.addDimension(time);
        }

        return ret;
    }


    public static void main(String[] args) {
        System.out.println(GeometryImpl.builder().space().generic().build().build());
        System.out.println(GeometryImpl.builder().space().size(200, 339).build().build());
    }

}
