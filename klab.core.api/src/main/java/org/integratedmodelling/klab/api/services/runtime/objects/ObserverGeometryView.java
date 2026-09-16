package org.integratedmodelling.klab.api.services.runtime.objects;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/** One observer snapshot with separate WGS84 GeoJSON geometries; missing space is null. */
public class ObserverGeometryView {
  private Observation observer;
  private String occupiedGeoJson;
  private String perceivedGeoJson;
  public Observation getObserver() { return observer; }
  public void setObserver(Observation observer) { this.observer = observer; }
  public String getOccupiedGeoJson() { return occupiedGeoJson; }
  public void setOccupiedGeoJson(String json) { this.occupiedGeoJson = json; }
  public String getPerceivedGeoJson() { return perceivedGeoJson; }
  public void setPerceivedGeoJson(String json) { this.perceivedGeoJson = json; }
}
