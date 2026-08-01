package org.integratedmodelling.klab.api.collections.impl;

import org.integratedmodelling.klab.api.collections.DomainObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DomainObjectImpl extends ParametersImpl<String> implements DomainObject {

  private List<DomainObject> children = new ArrayList<>();

  public DomainObjectImpl() {}

  public DomainObjectImpl(Map<String, Object> delegate, List<DomainObject> children) {
    super(delegate);
    this.children = children;
  }

  @Override
  public List<DomainObject> getChildren() {
    return children;
  }

  public void setChildren(List<DomainObject> children) {
    this.children = children;
  }
}
