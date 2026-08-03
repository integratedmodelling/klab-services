package org.integratedmodelling.klab.api.collections.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.collections.DomainObject;
import org.integratedmodelling.klab.api.utils.Utils;

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

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    addRecursively(this, sb, 0);
    return sb.toString();
  }

  private void addRecursively(DomainObjectImpl domainObject, StringBuilder sb, int offset) {

    var spacer = Utils.Strings.spaces(offset);

    for (var key : domainObject.keySet()) {
      sb.append(spacer);
      sb.append(key);
      sb.append(": ");
      sb.append(domainObject.get(key));
      sb.append("\n");
    }

    for (DomainObject child : domainObject.getChildren()) {
      addRecursively((DomainObjectImpl) child, sb, offset + 3);
    }
  }
}
