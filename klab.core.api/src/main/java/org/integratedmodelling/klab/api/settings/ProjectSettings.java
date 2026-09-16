package org.integratedmodelling.klab.api.settings;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Project-owned settings persisted in {@code META-INF/project.json}. */
public class ProjectSettings implements Serializable {
  private Map<String, Object> metadata = new LinkedHashMap<>();

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
  }
}
