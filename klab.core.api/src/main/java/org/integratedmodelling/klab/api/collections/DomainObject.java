package org.integratedmodelling.klab.api.collections;

import org.integratedmodelling.klab.api.collections.impl.DomainObjectImpl;
import org.integratedmodelling.klab.api.collections.impl.ParametersImpl;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A simple domain object with properties and tree structure. Provides convenience methods for
 * accessing common properties.
 */
public interface DomainObject extends Parameters<String> {

  List<DomainObject> getChildren();

  default String name() {
    return get("name", String.class);
  }

  default String description() {
    return get("description", String.class);
  }

  default String label() {
    return get("label", String.class);
  }

  default String urn() {
    return get("urn", String.class);
  }

  default String type() {
    return get("type", String.class);
  }

  default Version version() {
    return get("version", Version.class);
  }

  /**
   * For fluent chaining.
   *
   * @param o
   * @return
   */
  default DomainObject add(Object o) {
    var child = DomainObject.create(o);
    getChildren().add(child);
    return this;
  }

  static DomainObject create(Object... o) {
    List<DomainObject> children = new ArrayList<>();
    var inp = new LinkedHashMap<String, Object>();
    if (o != null) {
      for (int i = 0; i < o.length; i++) {
        if (o[i] instanceof DomainObject child) {
          children.add(child);
          continue;
        }
        if (o[i] instanceof Map) {
          inp.putAll((Map) o[i]);
        } else if (o[i] != null) {
          if (!ParametersImpl.IGNORED_PARAMETER.equals(o[i])) {
            inp.put((String) o[i], o[i + 1]);
          }
          i++;
        }
      }
    }
    return new DomainObjectImpl(inp, children);
  }
}
