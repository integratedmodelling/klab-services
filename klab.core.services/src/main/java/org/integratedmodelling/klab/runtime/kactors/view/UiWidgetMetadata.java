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
    if (Set.of("select", "radio", "multiselect", "tabs", "stepper", "breadcrumbs").contains(type)
        && (!(p.get("options") instanceof List<?> options)
            || options.size() > 10000
            || options.stream().anyMatch(v -> !(v instanceof String))
            || new HashSet<>(options).size() != options.size())) throw invalid("options");
    extended(type, p);
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
      case "checkbox", "switch" -> value instanceof Boolean;
      case "input", "textarea" ->
          value instanceof String s
              && s.length() <= ((Number) p.getOrDefault("maxlength", 16384)).intValue()
              && (!Boolean.TRUE.equals(p.get("required")) || !s.isBlank());
      case "select", "radio", "tabs", "stepper", "breadcrumbs" ->
          value instanceof String s
              && p.get("options") instanceof List<?> options
              && (s.isEmpty() ? !Boolean.TRUE.equals(p.get("required")) : options.contains(s));
      case "number", "slider", "pagination" ->
          value instanceof Number n
              && Double.isFinite(n.doubleValue())
              && (!type.equals("pagination") || n.doubleValue() == n.intValue() && n.intValue() >= 1)
              && (!(p.get("min") instanceof Number min) || n.doubleValue() >= min.doubleValue())
              && (!(p.get("max") instanceof Number max) || n.doubleValue() <= max.doubleValue());
      case "multiselect" -> value instanceof List<?> values
          && values.size() <= 10000 && new HashSet<>(values).size() == values.size()
          && values.stream().allMatch(v -> v instanceof String)
          && p.get("options") instanceof List<?> options && options.containsAll(values)
          && (!Boolean.TRUE.equals(p.get("required")) || !values.isEmpty());
      case "range" -> value instanceof Map<?, ?> range && range.size() == 2
          && value("number", p, range.get("min")) && value("number", p, range.get("max"))
          && ((Number) range.get("min")).doubleValue() <= ((Number) range.get("max")).doubleValue();
      case "date", "time", "color" -> value instanceof String text
          && (text.isEmpty() && !Boolean.TRUE.equals(p.get("required")) || formatted(type, text));
      case "list", "table", "tree" -> value instanceof String id
          && (id.isEmpty() && !Boolean.TRUE.equals(p.get("required")) || itemIds(p.get("items")).contains(id));
      case "progress" ->
          value instanceof Number n
              && Double.isFinite(n.doubleValue())
              && n.doubleValue() >= 0
              && n.doubleValue() <= 1;
      default -> true;
    };
  }

  public static Set<String> events(String type) {
    return switch (type) {
      case "button", "iconbutton", "link", "download" -> Set.of("click");
      case "submit" -> Set.of("submit");
      case "reset" -> Set.of("reset");
      case "list" -> Set.of("select", "activate");
      case "table" -> Set.of("select", "activate", "sort", "page");
      case "tree" -> Set.of("select", "activate", "expand");
      case "audio", "video" -> Set.of("play", "pause", "ended");
      default -> Set.of("change");
    };
  }

  public static Object defaultValue(String type, Map<String, Object> p) {
    return switch (type) {
      case "checkbox", "switch" -> false;
      case "number", "slider" -> p.getOrDefault("min", 0);
      case "pagination" -> p.getOrDefault("min", 1);
      case "range" -> Map.of("min", p.getOrDefault("min", 0), "max", p.getOrDefault("max", 100));
      case "multiselect" -> List.of();
      case "input", "textarea", "select", "radio", "date", "time", "color", "tabs", "stepper",
          "breadcrumbs", "list", "table", "tree" -> "";
      default -> null;
    };
  }

  public static boolean eventValue(String type, Map<String, Object> p, String event, Object v) {
    if (Set.of("button", "iconbutton", "link").contains(type)) return v == null;
    if (Set.of("audio", "video").contains(type))
      return v instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue() >= 0 && n.doubleValue() <= 1e9;
    if (event.equals("sort")) return v instanceof Map<?, ?> sort && sort.size() == 2
        && p.get("columns") instanceof List<?> columns && columns.contains(sort.get("column"))
        && sort.get("descending") instanceof Boolean;
    if (event.equals("page")) return v instanceof Number n && n.doubleValue() == n.intValue()
        && n.intValue() >= 1 && n.intValue() <= Math.max(1,
          (int) Math.ceil(((List<?>) p.get("items")).size() / ((Number) p.getOrDefault("pagesize", 10)).doubleValue()));
    if (event.equals("expand")) return v instanceof List<?> expanded && expanded.size() <= 10000
        && new HashSet<>(expanded).size() == expanded.size() && itemIds(p.get("items")).containsAll(expanded);
    return value(type, p, v);
  }

  private static boolean formatted(String type, String text) {
    try {
      return switch (type) {
        case "date" -> text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
            && java.time.LocalDate.parse(text) != null;
        case "time" -> text.matches("[0-9]{2}:[0-9]{2}") && java.time.LocalTime.parse(text) != null;
        case "color" -> text.matches("#[0-9a-fA-F]{6}");
        default -> false;
      };
    } catch (java.time.DateTimeException invalid) { return false; }
  }

  public static boolean safeSource(Object source) {
    if (!(source instanceof String s) || s.isBlank() || s.length() > 2048
        || s.chars().anyMatch(c -> c < 32 || c == 127) || s.contains("\\")) return false;
    try {
      var uri = java.net.URI.create(s);
      return uri.getRawUserInfo() == null && (uri.isAbsolute()
          ? "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
          : uri.getRawAuthority() == null && !s.startsWith("//"));
    } catch (IllegalArgumentException invalid) { return false; }
  }

  private static void extended(String type, Map<String, Object> p) {
    for (String key : Set.of("descending", "controls", "loop", "muted"))
      if (p.containsKey(key) && !(p.get(key) instanceof Boolean)) throw invalid(key);
    for (String key : Set.of("field", "alt", "accept", "sort"))
      if (p.containsKey(key) && !(p.get(key) instanceof String)) throw invalid(key);
    if (p.containsKey("field") && !p.get("field").toString().matches("[a-zA-Z][a-zA-Z0-9_-]{0,79}")) throw invalid("field");
    if (p.containsKey("form") && !(p.get("form") instanceof Boolean || p.get("form") instanceof String)) throw invalid("form");
    for (String key : Set.of("src", "href"))
      if (p.containsKey(key) && !safeSource(p.get(key))) throw invalid(key);
    if (Set.of("image", "audio", "video").contains(type) && !safeSource(p.get("src"))) throw invalid("src");
    integer(p, "pagesize", 1, 1000);
    integer(p, "page", 1, 10000);
    integer(p, "maxbytes", 1, 4194304);
    if (type.equals("pagination")) {
      integer(p, "min", 1, 10000); integer(p, "max", 1, 10000);
      if (!p.containsKey("max")) throw invalid("max");
      if (p.containsKey("value") && p.get("value") instanceof Number n && n.doubleValue() != n.intValue()) throw invalid("value");
    }
    if (Set.of("list", "table", "tree").contains(type)) {
      var ids = itemIds(p.get("items"));
      if (ids == null) throw invalid("items");
      if (type.equals("table") && (!(p.get("columns") instanceof List<?> columns)
          || columns.isEmpty() || columns.size() > 100 || columns.stream().anyMatch(c -> !(c instanceof String))
          || new HashSet<>(columns).size() != columns.size())) throw invalid("columns");
      if (p.containsKey("expanded") && !eventValue(type, p, "expand", p.get("expanded"))) throw invalid("expanded");
      if (p.containsKey("page") && !eventValue(type, p, "page", p.get("page"))) throw invalid("page");
      if (p.containsKey("sort") && (!(p.get("columns") instanceof List<?> columns) || !columns.contains(p.get("sort")))) throw invalid("sort");
    }
  }

  private static Set<String> itemIds(Object items) {
    var ids = new HashSet<String>();
    return collectIds(items, ids, 0) ? ids : null;
  }

  private static boolean collectIds(Object items, Set<String> ids, int depth) {
    if (!(items instanceof List<?> list) || depth > 32 || list.size() > 10000) return false;
    for (var item : list) {
      if (!(item instanceof Map<?, ?> map) || !(map.get("id") instanceof String id)
          || id.isBlank() || id.length() > 160 || !ids.add(id) || ids.size() > 10000
          || !(map.get("label") instanceof String)) return false;
      if (map.containsKey("children") && !collectIds(map.get("children"), ids, depth + 1)) return false;
    }
    return true;
  }

  public static boolean accepts(String accept, String name, String mediaType) {
    if (accept.isBlank()) return true;
    return Arrays.stream(accept.split(",")).map(String::trim).anyMatch(rule ->
        rule.startsWith(".") ? name.toLowerCase(Locale.ROOT).endsWith(rule.toLowerCase(Locale.ROOT))
        : rule.endsWith("/*") ? mediaType.startsWith(rule.substring(0, rule.length() - 1))
        : rule.equalsIgnoreCase(mediaType));
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
