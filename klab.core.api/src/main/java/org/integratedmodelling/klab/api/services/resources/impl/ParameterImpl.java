package org.integratedmodelling.klab.api.services.resources.impl;

import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ParameterImpl implements Adapter.Parameter {

  private String name;
  private String description;
  private boolean optional;
  private Artifact.Type type;
  private List<String> enumValues = new ArrayList<>();

  public ParameterImpl() {}

  public ParameterImpl(
      String name, String description, boolean optional, Artifact.Type type, String[] enumValues) {
    this.name = name;
    this.description = description;
    this.optional = optional;
    this.type = type;
    this.enumValues.addAll(Arrays.asList(enumValues));
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Artifact.Type getType() {
    return type;
  }

  @Override
  public List<String> getEnumValues() {
    return enumValues;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public boolean isOptional() {
    return optional;
  }
}
