package org.integratedmodelling.klab.api.settings;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Project-owned metadata persisted in {@code META-INF/manifest.json}, plus service-owned rights in transport. */
public class ProjectSettings implements Serializable {
  private Map<String, Object> metadata = new LinkedHashMap<>();
  private String permissions;
  private String definedWorldview;

  /** Administrator-only manifest update. Null leaves it unchanged; blank removes the declaration.
   * This field updates the manifest only when explicitly supplied. */
  public String getDefinedWorldview() { return definedWorldview; }
  public void setDefinedWorldview(String definedWorldview) { this.definedWorldview = definedWorldview; }

  /** Service-owned access rights, transported with settings but never persisted in the manifest.
   * Null leaves rights unchanged; an empty string requests owner-only access. */
  public String getPermissions() { return permissions; }

  public void setPermissions(String permissions) { this.permissions = permissions; }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
  }
}
