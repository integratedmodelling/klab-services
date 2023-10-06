package org.integratedmodelling.klab.runtime.scale.space;

import org.geotools.referencing.CRS;
import org.geotools.referencing.CRS.AxisOrder;
import org.integratedmodelling.klab.api.exceptions.KIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KValidationException;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Envelope;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import javax.measure.Unit;
import java.util.Objects;

public class ProjectionImpl implements Projection {

	private String code;
	private CoordinateReferenceSystem crs;

	private static Projection defaultProjection;
	private static Projection latlonProjection;
	private static Projection[][] utmProjections = new Projection[2][60];
	
	public ProjectionImpl(String definition) {
		try {
			this.crs = CRS.decode(definition, true);
			this.code = getSRS(this.crs);
		} catch (FactoryException e) {
			throw new KValidationException(e);
		}
	}

	public ProjectionImpl(CoordinateReferenceSystem crs) {
		this.crs = crs;
		this.code = getSRS(crs);
	}

	public ProjectionImpl(CoordinateReferenceSystem crs, String code) {
		this.crs = crs;
		this.code = code;
	}
	
	@Override
	public String getCode() {
		return this.code;
	}

	private String getSRS(CoordinateReferenceSystem crs) {
		try {
			Integer integer = CRS.lookupEpsgCode(crs, true);
			return "EPSG:" + integer;
		} catch (FactoryException e) {
			
		}
		throw new KIllegalStateException("projection CRS cannot be understood");
	}
	
	public CoordinateReferenceSystem getCRS() {
		return crs;
	}
	
	public int getSRID() {
		return Integer.parseInt(code.split(":")[1]);
	}

	@Override
	public boolean isMeters() {
		return getUnits().equals("m");
	}

	public boolean flipsCoordinates() {
		return !CRS.getAxisOrder(this.crs).equals(AxisOrder.EAST_NORTH);
	}

	@Override
	public String getUnits() {
		return getUnit().toString();
	}

	public Unit<?> getUnit() {
		return crs.getCoordinateSystem().getAxis(0).getUnit();
	}

	public String getWKTDefinition() {
		return crs.toWKT();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ProjectionImpl that = (ProjectionImpl) o;
		return Objects.equals(code, that.code);
	}

	@Override
	public int hashCode() {
		return Objects.hash(code);
	}

	/**
	 * Get the UTM projection most appropriate to geolocate the passed envelope,
	 * which can be in any projection.
	 * 
	 * @param envelope
	 * @return the projection corresponding to the best UTM zone for this envelope
	 */
	public static Projection getUTM(Envelope envelope) {

		Envelope standardized = envelope.transform(Projection.getLatLon(), true);
		double[] xy = standardized.getCenterCoordinates();
		// check longitude, in OpenLayer is possible to get out of range
		if (xy[0] > 180 || xy[0] < -180) {
			throw new IllegalArgumentException("Longitude is out of range (-180/180)");
		}
		if (xy[1] > 90 || xy[1] < -90) {
			throw new IllegalArgumentException("Latitude is out of range (90/-90)");
		}
		WGS84 wgs = new WGS84(xy[1], xy[0]);
		UTM utm = new UTM(wgs);

		int idx = 0;
		if (wgs.getHemisphere() == 'S') {
			idx = 1;
		}

		if (utmProjections[idx][utm.getZone() - 1] == null) {
			String code = "EPSG:32" + (wgs.getHemisphere() == 'S' ? "7" : "6") + String.format("%02d", utm.getZone());
			utmProjections[idx][utm.getZone() - 1] = new ProjectionImpl(code);
		}

		return utmProjections[idx][utm.getZone() - 1];
	}

	public CoordinateReferenceSystem getCoordinateReferenceSystem() {
		return this.crs;
	}

	public static ProjectionImpl promote(Projection projection) {
		if (projection instanceof ProjectionImpl) {
			return (ProjectionImpl)projection;
		}
		return new ProjectionImpl(projection.getCode());
	}

}
