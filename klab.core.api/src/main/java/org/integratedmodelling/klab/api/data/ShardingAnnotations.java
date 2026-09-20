package org.integratedmodelling.klab.api.data;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import org.integratedmodelling.klab.api.lang.Annotation;

/** Shared k.IM sharding decoder. Positional and named {@code value} arguments are equivalent. */
public final class ShardingAnnotations {
  private ShardingAnnotations() {}
  public static final Set<String> NAMES = Set.of("type", "split", "maxsize", "minsplitsize", "fillcurve");

  public static Data.ShardingStrategy parse(Collection<Annotation> annotations) {
    var result = Data.ShardingStrategy.neutral();
    for (var annotation : annotations) {
      String name = annotation.getName();
      if (!NAMES.contains(name)) {
        if (name != null && NAMES.contains(name.toLowerCase(Locale.ROOT)))
          throw new IllegalArgumentException("Sharding annotation names must be lowercase: @" + name);
        continue;
      }
      var positional = annotation.getUnnamedArguments();
      Object named = annotation.get(Annotation.VALUE_PARAMETER_KEY);
      if (positional.size() > 1 || named != null && !positional.isEmpty())
        throw new IllegalArgumentException("@" + name + " requires exactly one value");
      Object value = named != null ? named : positional.isEmpty() ? null : positional.getFirst();
      if (value == null) throw new IllegalArgumentException("@" + name + " requires a value");
      try {
        switch (name) {
          case "type" -> result.setDataType(enumValue(Storage.Type.class, value));
          case "fillcurve" -> result.setCurve(enumValue(Data.FillCurve.class, value));
          case "split" -> result.setSuggestedSplits(Integer.parseInt(value.toString()));
          case "maxsize" -> result.setMaxBufferSize(Long.parseLong(value.toString()));
          case "minsplitsize" -> result.setMinSplitSize(Long.parseLong(value.toString()));
        }
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Invalid @" + name + " value: " + value, e);
      }
    }
    return result.validate();
  }

  private static <E extends Enum<E>> E enumValue(Class<E> type, Object value) {
    for (E candidate : type.getEnumConstants())
      if (candidate.name().equalsIgnoreCase(value.toString())) return candidate;
    throw new IllegalArgumentException("Unknown " + type.getSimpleName() + ": " + value);
  }
}
