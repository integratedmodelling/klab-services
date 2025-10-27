package org.integratedmodelling.klab.api.view.modeler.visualization;

import java.net.URL;
import java.util.List;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.mediation.classification.DataKey;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TemporalExtension;
import org.integratedmodelling.klab.api.scope.ContextScope;

/**
 * Bean that is passed to templates and report generators to create configurable visualization
 * pages. Full content and API TBD.
 */
public class VisualizationData {

  private String title;
  private String description;
  private RuntimeAsset asset;
  private URL url;
  private List<URL> urls;
  private TemporalExtension temporalExtension;
  private String visualizationType;
  private Geometry geometry;
  private DataKey dataKey;
  private Histogram histogram;
  private ContextScope contextScope;

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public RuntimeAsset getAsset() {
    return asset;
  }

  public void setAsset(RuntimeAsset asset) {
    this.asset = asset;
  }

  public URL getUrl() {
    return url;
  }

  public void setUrl(URL url) {
    this.url = url;
  }

  public String getVisualizationType() {
    return visualizationType;
  }

  public void setVisualizationType(String visualizationType) {
    this.visualizationType = visualizationType;
  }

  public Geometry getGeometry() {
    return geometry;
  }

  public void setGeometry(Geometry geometry) {
    this.geometry = geometry;
  }

  public DataKey getDataKey() {
    return dataKey;
  }

  public void setDataKey(DataKey dataKey) {
    this.dataKey = dataKey;
  }

  public Histogram getHistogram() {
    return histogram;
  }

  public void setHistogram(Histogram histogram) {
    this.histogram = histogram;
  }

  public ContextScope getContextScope() {
    return contextScope;
  }

  public void setContextScope(ContextScope contextScope) {
    this.contextScope = contextScope;
  }

  public List<URL> getUrls() {
    return urls;
  }

  public void setUrls(List<URL> urls) {
    this.urls = urls;
  }

  public TemporalExtension getTemporalExtension() {
    return temporalExtension;
  }

  public void setTemporalExtension(TemporalExtension temporalExtension) {
    this.temporalExtension = temporalExtension;
  }
}
