package org.integratedmodelling.klab.runtime.kactors.view;

import java.util.*;

/** Portable primitive configuration, shared by construction and authoritative input validation. */
public final class UiWidgetMetadata {
  private UiWidgetMetadata() {}

  public static void validate(String type, Map<String, Object> p) {
    for (String key :
        Set.of(
            "dense",
            "readonly",
            "required",
            "outlined",
            "flat",
            "round",
            "loading",
            "indeterminate")) {
      if (p.containsKey(key) && !(p.get(key) instanceof Boolean)) throw invalid(key);
    }
    for (String key : Set.of("label", "placeholder", "hint", "tooltip", "icon", "error")) {
      if (p.containsKey(key) && !(p.get(key) instanceof String)) throw invalid(key);
    }
    if (p.containsKey("color")
        && !Set.of(
                "primary", "secondary", "accent", "positive", "negative", "info", "warning", "dark")
            .contains(p.get("color"))) throw invalid("color");
    if (p.containsKey("size") && !Set.of("xs", "sm", "md", "lg", "xl").contains(p.get("size")))
      throw invalid("size");
    if (p.containsKey("type")
        && !Set.of("text", "email", "password", "tel", "url", "search", "textarea")
            .contains(p.get("type"))) throw invalid("type");
    integer(p, "maxlength", 1, 16384);
    integer(p, "debounce", 0, 2000);
    for (String key : Set.of("min", "max", "step")) {
      if (p.containsKey(key)
          && (!(p.get(key) instanceof Number n)
              || !Double.isFinite(n.doubleValue())
              || key.equals("step") && n.doubleValue() <= 0)) throw invalid(key);
    }
    if (p.get("min") instanceof Number min
        && p.get("max") instanceof Number max
        && min.doubleValue() > max.doubleValue()) throw invalid("min/max");
    if (type.equals("select")
        && (!(p.get("options") instanceof List<?> options)
            || options.size() > 10000
            || options.stream().anyMatch(v -> !(v instanceof String))
            || new HashSet<>(options).size() != options.size())) throw invalid("options");
    if (p.containsKey("value") && !configuredValue(type, p, p.get("value"))) throw invalid("value");
  }

  /** Empty authored/reset values are allowed; required is enforced when the user commits input. */
  public static boolean configuredValue(String type, Map<String, Object> p, Object value) {
    var configuration = new HashMap<>(p);
    configuration.put("required", false);
    return value(type, configuration, value);
  }

  public static boolean value(String type, Map<String, Object> p, Object value) {
    return switch (type) {
      case "checkbox" -> value instanceof Boolean;
      case "input" ->
          value instanceof String s
              && s.length() <= ((Number) p.getOrDefault("maxlength", 16384)).intValue()
              && (!Boolean.TRUE.equals(p.get("required")) || !s.isBlank());
      case "select" ->
          value instanceof String s
              && p.get("options") instanceof List<?> options
              && (s.isEmpty() ? !Boolean.TRUE.equals(p.get("required")) : options.contains(s));
      case "number" ->
          value instanceof Number n
              && Double.isFinite(n.doubleValue())
              && (!(p.get("min") instanceof Number min) || n.doubleValue() >= min.doubleValue())
              && (!(p.get("max") instanceof Number max) || n.doubleValue() <= max.doubleValue());
      case "progress" ->
          value instanceof Number n
              && Double.isFinite(n.doubleValue())
              && n.doubleValue() >= 0
              && n.doubleValue() <= 1;
      default -> true;
    };
  }

  private static void integer(Map<String, Object> p, String key, int min, int max) {
    if (p.containsKey(key)
        && (!(p.get(key) instanceof Number n)
            || n.doubleValue() != Math.rint(n.doubleValue())
            || n.doubleValue() < min
            || n.doubleValue() > max)) throw invalid(key);
  }

  private static IllegalArgumentException invalid(String key) {
    return new IllegalArgumentException("Invalid UI widget metadata: " + key);
  }
}
