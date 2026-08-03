package org.integratedmodelling.klab.api.collections;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.collections.impl.DomainObjectImpl;
import org.integratedmodelling.klab.api.collections.impl.ParametersImpl;
import org.integratedmodelling.klab.api.data.Version;

/**
 * A simple domain object with properties and tree structure. Provides convenience methods for
 * accessing common properties.
 */
public interface DomainObject extends Parameters<String> {

  String TYPE = "type";
  String NAME = "name";
  String DESCRIPTION = "description";
  String URN = "urn";
  String VERSION = "version";
  String LABEL = "label";

  List<DomainObject> getChildren();

  default String name() {
    return get(NAME, String.class);
  }

  default String description() {
    return get(DESCRIPTION, String.class);
  }

  default String label() {
    return get(LABEL, String.class);
  }

  default String urn() {
    return get(URN, String.class);
  }

  default String type() {
    return get(TYPE, String.class);
  }

  default Version version() {
    return get(VERSION, Version.class);
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
