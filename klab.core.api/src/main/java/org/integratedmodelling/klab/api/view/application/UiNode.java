package org.integratedmodelling.klab.api.view.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Toolkit-neutral UI description. IDs are instance-local, opaque, and assigned by the server. */
public record UiNode(
    String id,
    String type,
    Map<String, Object> properties,
    Set<String> events,
    List<UiNode> children) {

  public UiNode {
    requireName(id);
    requireName(type);
    properties = immutableProperties(properties);
    events = Set.copyOf(events);
    events.forEach(UiNode::requireName);
    children = List.copyOf(children);
  }

  /** Copy a JSON value deeply; arbitrary Java objects must never reach a renderer. */
  public static Object immutableValue(Object value) {
    if (value == null
        || value instanceof String
        || value instanceof Boolean
        || value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long
        || value instanceof java.math.BigInteger
        || value instanceof java.math.BigDecimal) {
      return value;
    }
    if (value instanceof Double number && Double.isFinite(number)) return value;
    if (value instanceof Float number && Float.isFinite(number)) return value;
    if (value instanceof List<?> list) {
      // JSON arrays may contain null, unlike List.copyOf().
      return Collections.unmodifiableList(list.stream().map(UiNode::immutableValue).toList());
    }
    if (value instanceof Map<?, ?> map) {
      var copy = new LinkedHashMap<String, Object>();
      map.forEach(
          (key, item) -> {
            if (!(key instanceof String name)) {
              throw new IllegalArgumentException("UI property keys must be strings");
            }
            copy.put(name, immutableValue(item));
          });
      return Collections.unmodifiableMap(copy);
    }
    throw new IllegalArgumentException("UI values must be finite JSON values");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> immutableProperties(Map<String, Object> properties) {
    return (Map<String, Object>) immutableValue(Objects.requireNonNull(properties));
  }

  private static void requireName(String name) {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("Empty UI name");
  }
}
