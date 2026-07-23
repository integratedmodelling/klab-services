package org.integratedmodelling.klab.api.collections.impl;

import org.integratedmodelling.klab.api.collections.Constant;

public class ConstantImpl implements Constant {

  String value;

  @Override
  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return value;
  }
}
