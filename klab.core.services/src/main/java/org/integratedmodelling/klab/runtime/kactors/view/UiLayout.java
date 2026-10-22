package org.integratedmodelling.klab.runtime.kactors.view;

import java.util.*;
import org.integratedmodelling.klab.api.view.application.UiNode;

/** Initial portable flex contract. CSS text and executable properties are never accepted. */
public final class UiLayout {
  private UiLayout() {}

  private static final Set<String> LENGTHS =
      Set.of(
          "width",
          "height",
          "minwidth",
          "maxwidth",
          "minheight",
          "maxheight",
          "gap",
          "rowgap",
          "columngap",
          "basis",
          "fontsize",
          "radius");
  private static final Set<String> FLAGS =
      Set.of("hidden", "disabled", "invisible", "fill", "hfill", "vfill");
  private static final Set<String> ENUMS =
      Set.of(
          "layout",
          "justify",
          "align",
          "self",
          "wrap",
          "direction",
          "overflow",
          "overflowx",
          "overflowy",
          "aligncontent");
  private static final Map<String, Set<String>> VALUES =
      Map.ofEntries(
          Map.entry("layout", Set.of("row", "column")),
          Map.entry(
              "justify",
              Set.of("start", "end", "center", "space-between", "space-around", "space-evenly")),
          Map.entry("align", Set.of("start", "end", "center", "stretch", "baseline")),
          Map.entry("self", Set.of("auto", "start", "end", "center", "stretch", "baseline")),
          Map.entry("wrap", Set.of("nowrap", "wrap", "wrap-reverse")),
          Map.entry("direction", Set.of("normal", "reverse")),
          Map.entry("overflow", Set.of("visible", "hidden", "auto", "scroll")),
          Map.entry(
              "aligncontent",
              Set.of(
                  "start",
                  "end",
                  "center",
                  "stretch",
                  "space-between",
                  "space-around",
                  "space-evenly")));

  public static Map<String, Object> normalize(Map<?, ?> metadata) {
    var result = new LinkedHashMap<String, Object>();
    metadata.forEach(
        (raw, input) -> {
          String key = raw.toString().replaceFirst("^[:+!]", "");
          Object value =
              raw.toString().startsWith("+")
                  ? true
                  : raw.toString().startsWith("!") ? false : input;
          if (key.equals("tag")) return;
          if (FLAGS.contains(key) && !(value instanceof Boolean)) throw invalid(key);
          if (LENGTHS.contains(key) && !length(value)) throw invalid(key);
          if (Set.of("padding", "margin").contains(key)) {
            if (!(value instanceof Number && length(value))) {
              if (!(value instanceof String s)
                  || s.split(" +").length > 4
                  || Arrays.stream(s.split(" +"))
                      .anyMatch(
                          v ->
                              !length(
                                  key.equals("margin") && v.startsWith("-") ? v.substring(1) : v)))
                throw invalid(key);
            }
          }
          if (ENUMS.contains(key)
              && !VALUES.get(key.startsWith("overflow") ? "overflow" : key).contains(value))
            throw invalid(key);
          if (Set.of("grow", "shrink", "order").contains(key)
              && (!(value instanceof Number n)
                  || !Double.isFinite(n.doubleValue())
                  || (!key.equals("order") && n.doubleValue() < 0))) throw invalid(key);
          if (key.equals("order")
              && value instanceof Number n
              && n.doubleValue() != Math.rint(n.doubleValue())) throw invalid(key);
          if (key.equals("class")
              && (!(value instanceof String s) || !s.matches("[a-zA-Z0-9_ -]{0,512}")))
            throw invalid(key);
          if (key.equals("style") || key.toLowerCase(Locale.ROOT).startsWith("on"))
            throw invalid(key);
          result.put(key, UiNode.immutableValue(value));
        });
    return result;
  }

  private static boolean length(Object value) {
    return value instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue() >= 0
        || value instanceof String s
            && (s.equals("auto")
                || s.equals("none")
                || s.matches("[0-9]+(?:\\.[0-9]+)?(?:px|%|rem|vw|vh)?"));
  }

  private static IllegalArgumentException invalid(String key) {
    return new IllegalArgumentException("Invalid UI metadata: " + key);
  }
}
