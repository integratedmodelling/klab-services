package org.integratedmodelling.klab.api.cli;

public class Option {

  private final String name;
  private final String shortName;
  private final String description;
  private final Class<?> valueClass;
  private final Object defaultValue;

  public Option(
      String name, String shortName, String description, Class<?> valueClass, Object defaultValue) {
    this.name = name;
    this.shortName = shortName;
    this.description = description;
    this.valueClass = valueClass;
    this.defaultValue = defaultValue;
  }
}
