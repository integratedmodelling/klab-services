package org.integratedmodelling.klab.api.authentication;

/**
 * Custom properties with visibility field
 *
 * @author Enrico Girotto
 */
public class CustomPropertyRest {

  private String key;
  private String value;
  private boolean onlyAdmin;

  public CustomPropertyRest() {}

  public CustomPropertyRest(String key, String value, boolean onlyAdmin) {
    this.key = key;
    this.value = value;
    this.onlyAdmin = onlyAdmin;
  }

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
