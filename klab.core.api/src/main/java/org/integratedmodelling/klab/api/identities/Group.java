package org.integratedmodelling.klab.api.identities;

import org.integratedmodelling.klab.api.authentication.CustomPropertyRest;

import java.util.List;
import java.util.Set;

public interface Group {
  String getId();

  String getName();

  String getDescription();

  String getSshKey();

  List<String> getProjectUrls();

  boolean isWorldview();

  String getIconUrl();

  long getMaxUpload();

  Set<CustomPropertyRest> getCustomProperties();

  long getDefaultExpirationTime();

  boolean isOptIn();

  boolean isComplimentary();

  List<String> getDependsOn();
}
