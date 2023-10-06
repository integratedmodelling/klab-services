package org.integratedmodelling.klab.runtime.scale.space;

import org.geotools.referencing.GeodeticCalculator;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Envelope;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Grid;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.locationtech.jts.geom.Point;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class GridImpl implements Grid {

    private List<Pair<Double, Double>> anchorPoints = new ArrayList<>();

    public static GridImpl promote(Grid grid) {
        if (grid instanceof GridImpl grid1) {
            return grid1;
        }
        throw new KUnimplementedException("GridImpl::promote with external grid");
    }

    /**
     * Positions a cell may be located at in a fully specified grid.
     */
    public static enum CellPositionClass {
        SW_CORNER, SE_CORNER, NW_CORNER, NE_CORNER, S_EDGE, E_EDGE, N_EDGE, W_EDGE, INTERNAL
    }

    private static final long serialVersionUID = -4637331840972669199L;
    private static final double EQUATOR_LENGTH_METERS = 40075000;

    private ProjectionImpl projection;
    private double declaredResolutionM;
    private long xCells = 1, yCells = 1;
    private EnvelopeImpl envelope;
    private double xCellSize, yCellSize;
    private long size = 1;
    private boolean squareCells;

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
        ret.envelope = this.envelope == null? null : this.envelope.copy();
        ret.xCells = this.xCells;
        ret.yCells = this.yCells;
        ret.xCellSize = this.xCellSize;
        ret.yCellSize = this.yCellSize;
        ret.size = this.size;
        ret.squareCells = this.squareCells;
        ret.anchorPoints.addAll(this.anchorPoints);
        return ret;
    }

    public GridImpl() {

    }

    public GridImpl(Parameters<String> definition) {

    }

    /**
     * Constructor for a fully specified, anchored grid
     *
     * @param shape
     * @param resolutionInM
     */
    public GridImpl(Shape shape, double resolutionInM, boolean makeCellsSquare) {
        this.declaredResolutionM = resolutionInM;
        adjustEnvelope(shape.getEnvelope(), resolutionInM, makeCellsSquare);
    }

    public GridImpl(double resolutionInM) {
        this.declaredResolutionM = resolutionInM;
    }

    public GridImpl(Point anchorPoint, double resolutionInM) {
        this.declaredResolutionM = resolutionInM;
    }

    @Override
    public boolean isSquareCells() {
        return squareCells;
    }

    public GridImpl(Point anchorPoint, Projection projection, double resolutionInProjectionUnits) {
        this.declaredResolutionM = resolutionInProjectionUnits;
        this.projection = ProjectionImpl.promote(projection);
    }

    @Override
    public Grid locate(Envelope envelope) {
        GridImpl ret = copy();
        ret.adjustEnvelope(envelope, declaredResolutionM, this.squareCells);
        return ret;
    }

    /**
     * Adjust the envelope if necessary.
     * <p>
     * Depending on the requested resolution and the configuration, this can change the envelope or just adapt the
     * resolution to best fit the region context.
     *
     * @param envelope  the envelope, which may be reprojected to the projection in this grid
     * @param squareRes the resolution to use in meters.
     */
    private void adjustEnvelope(Envelope envelope, double squareRes, boolean forceSquareCells) {

        Projection prj = getProjection() == null ? envelope.getProjection() : getProjection();
        envelope = envelope.transform(prj, true);
        CoordinateReferenceSystem crs = ProjectionImpl.promote(prj).getCoordinateReferenceSystem();

        this.squareCells = forceSquareCells;
        this.envelope = EnvelopeImpl.promote(envelope);
        this.projection = ProjectionImpl.promote(envelope.getProjection());

        double minX = envelope.getMinX();
        double maxX = envelope.getMaxX();
        double minY = envelope.getMinY();
        double maxY = envelope.getMaxY();

        if (forceSquareCells) {
            if (prj.isMeters()) {
                double newMaxX = minX + (Math.ceil((maxX - minX) / squareRes) * squareRes);
                double newMaxY = minY + (Math.ceil((maxY - minY) / squareRes) * squareRes);
                double cellsX = (newMaxX - minX) / squareRes;
                double cellsY = (newMaxY - minY) / squareRes;
                this.envelope = EnvelopeImpl.create(minX, newMaxX, minY, newMaxY, prj);
                this.xCells = (long) cellsX;
                this.yCells = (long) cellsY;
                this.xCellSize = squareRes;
                this.yCellSize = squareRes;
            } else {

                minX = clamp(envelope.getMinX(), -180, 180);
                maxX = clamp(envelope.getMaxX(), -180, 180);
                minY = clamp(envelope.getMinY(), -90, 90);
                maxY = clamp(envelope.getMaxY(), -90, 90);

                GeodeticCalculator gc = new GeodeticCalculator(crs);
                gc.setStartingGeographicPoint(minX, (maxY - minY) / 2.0);
                gc.setDestinationGeographicPoint(maxX, (maxY - minY) / 2.0);
                double width = (minX == -180 && maxX == 180) ? EQUATOR_LENGTH_METERS : gc.getOrthodromicDistance();
                gc = new GeodeticCalculator(crs);
                gc.setStartingGeographicPoint((maxX - minX) / 2.0, minY);
                gc.setDestinationGeographicPoint((maxX - minX) / 2.0, maxY);
                double height = gc.getOrthodromicDistance();

                double restX = width % squareRes;
                double restY = height % squareRes;

                double newWidth = width - restX + squareRes;
                double newHeight = height - restY + squareRes;

                gc = new GeodeticCalculator(crs);
                gc.setStartingGeographicPoint(minX, minY);
                gc.setDirection(0.0, newHeight);
                Point2D destY = gc.getDestinationGeographicPoint();
                double newMaxY = destY.getY();

                gc = new GeodeticCalculator(crs);
                gc.setStartingGeographicPoint(minX, minY);
                gc.setDirection(90.0, newWidth);
                Point2D destX = gc.getDestinationGeographicPoint();
                double newMaxX = destX.getX();

                this.envelope = EnvelopeImpl.create(minX, newMaxX, minY, newMaxY, prj);
                this.xCells = (long) (newWidth / squareRes);
                this.yCells = (long) (newHeight / squareRes);
                this.xCellSize = (newMaxX - minX) / this.xCells;
                this.yCellSize = (newMaxY - minY) / this.yCells;
            }

        } else {
            long x = 0, y = 0;
            if (prj.isMeters()) {
                double height = envelope.getHeight();
                double width = envelope.getWidth();
                x = (long) Math.ceil(width / squareRes);
                y = (long) Math.ceil(height / squareRes);
            } else {

                minX = clamp(envelope.getMinX(), -180, 180);
                maxX = clamp(envelope.getMaxX(), -180, 180);
                minY = clamp(envelope.getMinY(), -90, 90);
                maxY = clamp(envelope.getMaxY(), -90, 90);

                GeodeticCalculator gc = new GeodeticCalculator(crs);
                gc.setStartingGeographicPoint(minX, (maxY - minY) / 2.0);
                gc.setDestinationGeographicPoint(maxX, (maxY - minY) / 2.0);
                // yes, we mean the other way around
                double width = (minX == -180 && maxX == 180) ? EQUATOR_LENGTH_METERS : gc.getOrthodromicDistance();
                gc = new GeodeticCalculator(crs);
                gc.setStartingGeographicPoint((maxX - minX) / 2.0, minY);
                gc.setDestinationGeographicPoint((maxX - minX) / 2.0, maxY);
                double height = gc.getOrthodromicDistance();
                x = (long) Math.ceil(width / squareRes);
                y = (long) Math.ceil(height / squareRes);
            }
            this.xCells = x;
            this.yCells = y;
            this.xCellSize = getEnvelope().getWidth() / xCells;
            this.yCellSize = getEnvelope().getHeight() / yCells;
        }
        this.size = this.xCells * this.yCells;
    }

    private static double clamp(double x, double min, double max) {
        if (x < min) {
            x = min;
        }
        if (x > max) {
            x = max;
        }
        return x;
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
        return projection;
    }

    @Override
    public Grid align(Grid other) {
        return null;
    }

    @Override
    public double resolution() {
        return xCellSize == 0 || yCellSize == 0 ? 0 : Math.sqrt(xCellSize * yCellSize);
    }

    public long getxCells() {
        return xCells;
    }

    public void setxCells(long xCells) {
        this.xCells = xCells;
    }

    public long getyCells() {
        return yCells;
    }

    public void setyCells(long yCells) {
        this.yCells = yCells;
    }

    public double getxCellSize() {
        return xCellSize;
    }

    public void setxCellSize(double xCellSize) {
        this.xCellSize = xCellSize;
    }

    public double getyCellSize() {
        return yCellSize;
    }

    public void setyCellSize(double yCellSize) {
        this.yCellSize = yCellSize;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setProjection(ProjectionImpl projection) {
        this.projection = projection;
    }

    public void setEnvelope(EnvelopeImpl envelope) {
        this.envelope = envelope;
    }


}
