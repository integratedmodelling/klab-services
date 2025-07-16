package org.integratedmodelling.klab.services.configuration;

import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.data.Storage;

/** Configuration for runtime service */
public class RuntimeConfiguration extends ServiceConfiguration {

  private List<String> allowedGroups = new ArrayList<>();
  private String url = null;
  private Storage.Type numericStorageType = Storage.Type.DOUBLE;

  public List<String> getAllowedGroups() {
    return allowedGroups;
  }

  public void setAllowedGroups(List<String> allowedGroups) {
    this.allowedGroups = allowedGroups;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  /**
   * Default numeric storage type when not specified by the model or observable through a <code>
   * @storage</code> annotation.
   *
   * @return
   */
  public Storage.Type getNumericStorageType() {
    return numericStorageType;
  }

  public void setNumericStorageType(Storage.Type numericStorageType) {
    this.numericStorageType = numericStorageType;
  }
}
