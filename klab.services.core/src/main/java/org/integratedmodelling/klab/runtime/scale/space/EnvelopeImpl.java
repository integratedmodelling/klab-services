package org.integratedmodelling.klab.runtime.scale.space;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Envelope;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.locationtech.jts.geom.Geometry;
import org.opengis.referencing.operation.TransformException;

public class EnvelopeImpl implements Envelope {

	private ReferencedEnvelope envelope;
	private ProjectionImpl projection;
	private Integer scaleRank = null;

	/**
	 * Same as {@link #getResolutionForZoomLevel(int, double) using a default of 5
	 * meters and the default multiplier of 4.
	 * 
	 * @param roundTo meters to round to. Also the minimum resolution if we can't
	 *                get enough screen pixels.
	 * @return resolution and the correspondent unit string.
	 */
	public Pair<Integer, String> getResolutionForZoomLevel() {
		return getResolutionForZoomLevel(DEFAULT_MIN_RESOLUTION, 4.0);
	}

	/**
	 * Same as {@link #getResolutionForZoomLevel(int, double) using a default
	 * multiplier of 4.
	 * 
	 * @param roundTo meters to round to. Also the minimum resolution if we can't
	 *                get enough screen pixels.
	 * @return resolution and the correspondent unit string.
	 */
	public Pair<Integer, String> getResolutionForZoomLevel(int roundTo) {
		return getResolutionForZoomLevel(roundTo, 4.0);
	}

	/**
	 * Return a "good" grid resolution by zoom level including both the actual
	 * meters per pixel and the readable unit string for it. For defaults and used
	 * when observing raw datasets. Reasoning: give the envelope enough resolution
	 * to show enough square pixels to occupy half the screen by computing the grid
	 * size against the m/pixel for the zoom level.
	 * <p>
	 * Meters per pixel at zoom level z are equal to 156412/(2^z).
	 * 
	 * @param roundTo    meters to round to. Also the minimum resolution if we can't
	 *                   get enough screen pixels.
	 * @param multiplier multiplier for the literal resolution. A good value for
	 *                   modern screens is around 4.
	 * 
	 * @return resolution in meters and the natural unit to use in displaying it.
	 */
	public Pair<Integer, String> getResolutionForZoomLevel(int roundTo, double multiplier) {

		int zoomLevel = getScaleRank();
		int metersPerPixel = 156412 / (int) (Math.pow(2., (double) zoomLevel) * multiplier);
		int gridRounded = ((metersPerPixel + (roundTo / 2)) / roundTo) * roundTo;

		String unit = "m";
		if (gridRounded > 2000) {
			gridRounded = (((gridRounded) + 500) / 1000);
			unit = "km";
			// gridRounded *= 1000;
		} else if (gridRounded < roundTo) {
			gridRounded = roundTo;
			unit = "m";
		}

		return Pair.of(gridRounded, unit);
	}

	@Override
	public int getScaleRank() {

		if (this.scaleRank == null) {
			Envelope envelope = transform(Projection.getLatLon(), true);

			int zoomLevel;
			double latDiff = envelope.getHeight();
			double lngDiff = envelope.getWidth();

			double maxDiff = (lngDiff > latDiff) ? lngDiff : latDiff;
			if (maxDiff < 360 / Math.pow(2, 20)) {
				zoomLevel = 21;
			} else {
				zoomLevel = (int) (-1 * ((Math.log(maxDiff) / Math.log(2)) - (Math.log(360) / Math.log(2))));
				if (zoomLevel < 1) {
					zoomLevel = 1;
				}
			}
			this.scaleRank = zoomLevel;
		}

		return this.scaleRank;
	}

	public ReferencedEnvelope getJTSEnvelope() {
		return envelope;
	}

	public boolean intersects(Envelope envelope) {
		return this.envelope.intersects((org.locationtech.jts.geom.Envelope) ((EnvelopeImpl) envelope).envelope);
	}

	@Override
	public double[] getCenterCoordinates() {
		return new double[] { envelope.getMedian(0), envelope.getMedian(1) };
	}

	/**
	 * Encoding suitable for geometry specs
	 * 
	 * @return the boundary specifications
	 */
	public String encode() {
		return "bbox=[" + getMinX() + " " + getMaxX() + " " + getMinY() + " " + getMaxY() + "]";
	}

	@Override
	public Envelope standard() {
		return transform(Projection.getDefault(), true);
	}

	@Override
	public Projection getProjection() {
		return projection;
	}

	@Override
	public double getMinX() {
		return envelope.getMinX();
	}

	@Override
	public double getMaxX() {
		return envelope.getMaxX();
	}

	@Override
	public double getMinY() {
		return envelope.getMinY();
	}

	@Override
	public double getMaxY() {
		return envelope.getMaxY();
	}

	@Override
	public double getWidth() {
		return envelope.getWidth();
	}

	@Override
	public double getHeight() {
		return envelope.getHeight();
	}

	@Override
	public Shape asShape() {
		return ShapeImpl.create(this);
	}

	@Override
	public Envelope transform(Projection projection, boolean lenient) {
		// TODO Auto-generated method stub
		return null;
	}

	public static EnvelopeImpl create(org.locationtech.jts.geom.Envelope envelope, ProjectionImpl projection) {
		EnvelopeImpl ret = new EnvelopeImpl();
		ret.envelope = new ReferencedEnvelope(envelope, projection.getCoordinateReferenceSystem());
		ret.projection = projection;
		return ret;
	}

	public static EnvelopeImpl create(org.opengis.geometry.Envelope envelope, ProjectionImpl projection) {
		EnvelopeImpl ret = new EnvelopeImpl();
		ret.envelope = new ReferencedEnvelope(envelope);
		ret.projection = projection;
		return ret;
	}

	public static EnvelopeImpl create(ReferencedEnvelope envelope) {
		EnvelopeImpl ret = new EnvelopeImpl();
		ret.envelope = envelope;
		ret.projection = new ProjectionImpl(envelope.getCoordinateReferenceSystem());
		return ret;
	}

	public static EnvelopeImpl create(double minX, double maxX, double minY, double maxY, ProjectionImpl projection) {
		return create(new ReferencedEnvelope(minX, maxX, minY, maxY, projection.getCoordinateReferenceSystem()));
	}

	public EnvelopeImpl copy() {
		return create(new ReferencedEnvelope(envelope.getMinX(), envelope.getMaxX(), envelope.getMinY(),
				envelope.getMaxY(), envelope.getCoordinateReferenceSystem()));
	}

	public String toString() {
		return envelope.toString();
	}

	@Override
	public double metersToDistance(double metersDistance) {
		if (getProjection().isMeters()) {
			return metersDistance;
		}
		double cMeters = (getMaxX() - getMinX()) / asShape().getStandardizedWidth();
		return metersDistance * cMeters;
	}

	@Override
	public double distanceToMeters(double originalDistance) {
		if (getProjection().isMeters()) {
			return originalDistance;
		}
		double cMeters = (getMaxX() - getMinX()) / asShape().getStandardizedWidth();
		return originalDistance / cMeters;
	}

	public static Envelope create(ReferencedEnvelope envelope, boolean swapXY) {
		EnvelopeImpl ret = new EnvelopeImpl();
		ret.envelope = swapXY
				? new ReferencedEnvelope(envelope.getMinY(), envelope.getMaxY(), envelope.getMinX(), envelope.getMaxX(),
						envelope.getCoordinateReferenceSystem())
				: new ReferencedEnvelope(envelope.getMinX(), envelope.getMaxX(), envelope.getMinY(), envelope.getMaxY(),
						envelope.getCoordinateReferenceSystem());
		ret.projection = new ProjectionImpl(envelope.getCoordinateReferenceSystem());
		return ret;
	}

	@Override
	public Envelope grow(double factor) {
		if (factor != 1) {
			double xgrow = ((getWidth() * factor) - getWidth()) / 2.0;
			double ygrow = ((getHeight() * factor) - getHeight()) / 2.0;
			return create(getMinX() - xgrow, getMaxX() + xgrow, getMinY() - ygrow, getMaxY() + ygrow, this.projection);
		}
		return this;
	}

	@Override
	public boolean overlaps(Envelope other) {
		try {
			return this.envelope.intersects(((EnvelopeImpl) other).getJTSEnvelope().toBounds(this.projection.getCRS()));
		} catch (TransformException e) {
			return false;
		}
	}

	public static EnvelopeImpl promote(Envelope envelope) {
		if (envelope instanceof EnvelopeImpl) {
			return (EnvelopeImpl) envelope;
		}
		return create(envelope.getMinX(), envelope.getMaxX(), envelope.getMinY(), envelope.getMaxY(),
				ProjectionImpl.promote(envelope.getProjection()));

	}

	public Geometry asJTSGeometry() {
		return ShapeImpl.makeCell(this.envelope.getMinX(), this.envelope.getMinY(), this.envelope.getMaxX(),
				this.envelope.getMaxY());
	}
}
