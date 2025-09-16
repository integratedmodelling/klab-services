package org.integratedmodelling.klab.api.view.modeler.visualization;

import org.integratedmodelling.klab.api.data.mediation.classification.Classifier;
import org.integratedmodelling.klab.api.scope.Scope;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple colormap definition. May be asked for an observation using {@link
 * org.integratedmodelling.klab.api.services.KlabService#retrieveAsset(String, org.integratedmodelling.klab.api.digitaltwin.Scheduler.Event, Class, Scope)}.
 */
public class Colormap implements Serializable {

  public static class Entry {
    private String color;
    private Classifier classifier;
    private String label;

    public String getColor() {
      return color;
    }

    public void setColor(String color) {
      this.color = color;
    }

    public Classifier getClassifier() {
      return classifier;
    }

    public void setClassifier(Classifier classifier) {
      this.classifier = classifier;
    }

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }
  }

  public enum Type {
    VALUES,
    INTERVALS,
    RAMP
  }

  private Type type;
  private List<Entry> entries = new ArrayList<>();

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public List<Entry> getEntries() {
    return entries;
  }

  public void setEntries(List<Entry> entries) {
    this.entries = entries;
  }
}
