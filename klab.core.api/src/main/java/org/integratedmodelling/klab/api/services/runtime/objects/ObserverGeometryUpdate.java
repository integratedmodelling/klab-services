package org.integratedmodelling.klab.api.services.runtime.objects;

/** Replace perceived space with a WGS84 rectangle, preserving all other perceived dimensions.
 * expectedGeometry is the last read perceived geometry encoding; null means no perceived geometry.
 */
public class ObserverGeometryUpdate {
  private long observerId;
  private String expectedGeometry;
  private double west, south, east, north;

  public long getObserverId() { return observerId; }
  public void setObserverId(long observerId) { this.observerId = observerId; }
  public String getExpectedGeometry() { return expectedGeometry; }
  public void setExpectedGeometry(String expectedGeometry) { this.expectedGeometry = expectedGeometry; }
  public double getWest() { return west; }
  public void setWest(double west) { this.west = west; }
  public double getSouth() { return south; }
  public void setSouth(double south) { this.south = south; }
  public double getEast() { return east; }
  public void setEast(double east) { this.east = east; }
  public double getNorth() { return north; }
  public void setNorth(double north) { this.north = north; }

  public void validate() {
    if (observerId <= 0 || !Double.isFinite(west) || !Double.isFinite(east)
        || !Double.isFinite(south) || !Double.isFinite(north)
        || west < -180 || east > 180 || south < -90 || north > 90
        || west >= east || south >= north) {
      throw new IllegalArgumentException("Choose a nonempty rectangle within longitude -180..180 and latitude -90..90");
    }
  }
}
