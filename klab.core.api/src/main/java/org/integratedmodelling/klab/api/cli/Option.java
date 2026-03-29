package org.integratedmodelling.klab.api.cli;

public class Option {
  private final String name;
  private final String shortDescription;
  private final String longDescription;
  private final boolean hasValue;
  private final Object defaultValue;
  private final Class<?> valueClass;

  public Option(
      String name,
      String shortName,
      String description,
      String shortDescription,
      Class<?> valueClass,
      Object defaultValue) {
    this.name = name;
    this.shortDescription = shortDescription;
    this.longDescription = description;
    this.hasValue = valueClass != null;
    this.valueClass = valueClass;
    this.defaultValue = defaultValue;
  }

  public String getName() {
    return name;
  }

  public String getShortDescription() {
    return shortDescription;
  }

  public String getLongDescription() {
    return longDescription;
  }

  public String getDescription() {
    return shortDescription;
  }

  public boolean hasValue() {
    return hasValue;
  }

  public boolean isHasValue() {
    return hasValue;
  }

  public Object getDefaultValue() {
    return defaultValue;
  }

  public Class<?> getValueClass() {
    return valueClass;
  }
}
