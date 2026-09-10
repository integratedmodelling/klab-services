package org.integratedmodelling.klab.runtime.kactors;

import org.integratedmodelling.klab.api.collections.Constant;

/** Conversions shared by static Java-verb validation and runtime argument binding. */
public final class JavaArgumentConversions {
  private JavaArgumentConversions() {}

  public static Object enumValue(Object value, Class<?> target) {
    if (!target.isEnum()) throw new IllegalArgumentException("Not an enum: " + target.getName());
    if (target.isInstance(value)) return value;
    String name = value instanceof Constant constant ? constant.getValue()
        : value instanceof CharSequence text ? text.toString() : null;
    Object match = null;
    for (Object candidate : target.getEnumConstants()) {
      if (((Enum<?>) candidate).name().equalsIgnoreCase(name)) {
        if (match != null) throw new IllegalArgumentException(
            "Ambiguous enum name '" + name + "' for " + target.getName());
        match = candidate;
      }
    }
    if (match == null) throw new IllegalArgumentException(
        "Unknown enum name '" + name + "' for " + target.getName());
    return match;
  }
}
