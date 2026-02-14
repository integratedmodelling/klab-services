package org.integratedmodelling.klab.runtime.data.stac.model;

import java.util.List;

public abstract class STACGeometry<T> {
    protected final STACGeometryType type;
    protected T coordinates;
    protected List<STACGeometry<T>> geometries;

    public static STACGeometry<?> create(STACGeometryType type) {
        STACGeometry<?> result;
        switch (type) {
            case Point:
                result = new Point();
                break;
            case MultiPoint:
                result = new MultiPoint();
                break;
            case LineString:
                result = new LineString();
                break;
            case MultiLineString:
                result = new MultiLineString();
                break;
            case MultiPolygon:
                result = new MultiPolygon();
                break;
            case Polygon:
            default:
                result = new Polygon();
                break;
        }
        return result;
    }

    protected STACGeometry(STACGeometryType type) {
        this.type = type;
    }

    public STACGeometryType getType() {
        return type;
    }

    public abstract int getRank();

    public T getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(T coordinates) {
        if (this.type != null && this.type == STACGeometryType.GeometryCollection) {
            throw new IllegalArgumentException("Coordinates not allowed on a geometry collection");
        }
        this.coordinates = coordinates;
    }

    public List<STACGeometry<T>> getGeometries() {
        return geometries;
    }

    public void setGeometries(List<STACGeometry<T>> geometries) {
        if (this.type != null && this.type != STACGeometryType.GeometryCollection) {
            throw new IllegalArgumentException("Geometries not allowed on a simple geometry");
        }
        this.geometries = geometries;
    }

    public static class Point extends STACGeometry<double[]> {
        public Point() {
            super(STACGeometryType.Point);
        }

        @Override
        public int getRank() {
            return 1;
        }
    }

    public static class MultiPoint extends STACGeometry<double[][]> {
        public MultiPoint() {
            super(STACGeometryType.MultiPoint);
        }

        @Override
        public int getRank() {
            return 2;
        }
    }

    public static class LineString extends STACGeometry<double[][]> {
        public LineString() {
            super(STACGeometryType.LineString);
        }

        @Override
        public int getRank() {
            return 2;
        }
    }

    public static class MultiLineString extends STACGeometry<double[][][]> {
        public MultiLineString() {
            super(STACGeometryType.MultiLineString);
        }

        @Override
        public int getRank() {
            return 3;
        }
    }

    public static class Polygon extends STACGeometry<double[][][]> {
        public Polygon() {
            super(STACGeometryType.Polygon);
        }

        @Override
        public int getRank() {
            return 3;
        }
    }

    public static class MultiPolygon extends STACGeometry<double[][][][]> {
        public MultiPolygon() {
            super(STACGeometryType.MultiPolygon);
        }

        @Override
        public int getRank() {
            return 4;
        }
    }
}
