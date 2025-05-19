package org.integratedmodelling.klab.api.authentication;

import java.util.Objects;

/**
 * Custom properties with visibility field
 *
 * @author Enrico Girotto
 */
public class CustomProperty {

  private String key;
  private String value;
  private boolean onlyAdmin;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public boolean isOnlyAdmin() {
    return onlyAdmin;
  }

  public void setOnlyAdmin(boolean onlyAdmin) {
    this.onlyAdmin = onlyAdmin;
  }

  @Override
  public String toString() {
    return "CustomProperty [key=" + key + ", value=" + value + ", onlyAdmin=" + onlyAdmin + "]";
  }
}
