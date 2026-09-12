package org.integratedmodelling.klab.api.view.modeler.visualization;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Annotation;

/**
 * Platform-independent evaluator of the {@code @colormap} contract in docs/ANNOTATIONS.md. Colors
 * are returned as packed ARGB. Instances are immutable and can be shared by renderers.
 */
public final class ColorRamp {
  private static final Map<String, int[]> PALETTES = palettes();
  private static final Map<String, String> NAMES =
      Map.ofEntries(
          Map.entry("black", "000000"),
          Map.entry("white", "ffffff"),
          Map.entry("red", "ff0000"),
          Map.entry("green", "008000"),
          Map.entry("blue", "0000ff"),
          Map.entry("lime", "00ff00"),
          Map.entry("yellow", "ffff00"),
          Map.entry("cyan", "00ffff"),
          Map.entry("aqua", "00ffff"),
          Map.entry("magenta", "ff00ff"),
          Map.entry("fuchsia", "ff00ff"),
          Map.entry("gray", "808080"),
          Map.entry("grey", "808080"),
          Map.entry("silver", "c0c0c0"),
          Map.entry("orange", "ffa500"),
          Map.entry("purple", "800080"),
          Map.entry("navy", "000080"),
          Map.entry("teal", "008080"),
          Map.entry("olive", "808000"),
          Map.entry("maroon", "800000"));
  private final int[] colors;
  private final double[] stops;
  private final Map<String, Integer> values;
  private final Double min, max, center;
  private final int nodata, unknown;

  private ColorRamp(
      int[] colors,
      double[] stops,
      Map<String, Integer> values,
      Double min,
      Double max,
      Double center,
      int nodata,
      int unknown) {
    this.colors = colors;
    this.stops = stops;
    this.values = Map.copyOf(values);
    this.min = min;
    this.max = max;
    this.center = center;
    this.nodata = nodata;
    this.unknown = unknown;
  }

  public static Set<String> paletteNames() {
    return PALETTES.keySet();
  }

  public boolean isCategorical() {
    return !values.isEmpty();
  }

  public int unknownArgb() {
    return unknown;
  }

  /** Uses the effective observation annotation; an unannotated observation uses viridis. */
  public static ColorRamp fromObservation(Observation observation) {
    Annotation selected = null;
    if (observation.getAnnotations() != null) {
      for (var annotation : observation.getAnnotations()) {
        if ("colormap".equals(annotation.getName())) selected = annotation;
      }
    }
    return fromAnnotation(selected);
  }

  public static ColorRamp fromAnnotation(Annotation annotation) {
    Map<String, Object> options = new HashMap<>();
    if (annotation != null) {
      if (!"colormap".equals(annotation.getName())) throw invalid("Expected @colormap");
      Set<String> allowed =
          Set.of(
              "palette", "colors", "stops", "values", "min", "max", "center", "reverse", "nodata",
              "unknown", "value");
      for (var key : annotation.keySet()) {
        if (key.startsWith("#") || annotation.getUnnamedKeys().contains(key)) continue;
        if (!allowed.contains(key)) throw invalid("Unknown parameter: " + key);
        options.put(key, annotation.get(key));
      }
      var unnamed = annotation.getUnnamedArguments();
      if (unnamed.size() > 1) throw invalid("Use colors=(...) for multiple colors");
      Object primary = options.remove("value");
      if (!unnamed.isEmpty()) {
        if (primary != null) throw invalid("Duplicate unnamed palette");
        primary = unnamed.getFirst();
      }
      if (primary != null) {
        if (options.putIfAbsent("palette", primary) != null) throw invalid("Duplicate palette");
      }
    }
    long definitions =
        List.of("palette", "colors", "stops", "values").stream()
            .filter(options::containsKey)
            .count();
    if (definitions > 1) throw invalid("Choose one of palette, colors, stops or values");
    Double min = number(options.get("min")), max = number(options.get("max"));
    Double center = number(options.get("center"));
    if ((min == null) != (max == null) || (min != null && min >= max))
      throw invalid("min and max must be supplied together, with min < max");
    if (center != null && min != null && !(min < center && center < max))
      throw invalid("center must lie strictly between min and max");
    int nodata = color(options.getOrDefault("nodata", "transparent"));
    int unknown = color(options.getOrDefault("unknown", "transparent"));
    Object reverseOption = options.getOrDefault("reverse", false);
    if (!(reverseOption instanceof Boolean)) throw invalid("reverse must be boolean");
    boolean reverse = (Boolean) reverseOption;
    Map<String, Integer> values = new LinkedHashMap<>();
    int[] colors;
    double[] stops = null;
    if (options.containsKey("values")) {
      if (min != null || center != null || options.containsKey("reverse"))
        throw invalid("values cannot use bounds, center or reverse");
      for (var entry : pairs(options.get("values"))) {
        if (values.put(key(entry.getKey()), color(entry.getValue())) != null)
          throw invalid("Duplicate category: " + entry.getKey());
      }
      if (values.isEmpty()) throw invalid("values must not be empty");
      colors = new int[0];
    } else if (options.containsKey("stops")) {
      if (min != null || center != null)
        throw invalid("stops already specify the absolute data domain");
      var sorted = new TreeMap<Double, Integer>();
      for (var entry : pairs(options.get("stops"))) {
        var value = number(entry.getKey());
        if (value == null || sorted.put(value, color(entry.getValue())) != null)
          throw invalid("Invalid or duplicate stop: " + entry.getKey());
      }
      if (sorted.size() < 2) throw invalid("At least two distinct stops are required");
      stops = sorted.keySet().stream().mapToDouble(Double::doubleValue).toArray();
      colors = sorted.values().stream().mapToInt(Integer::intValue).toArray();
    } else if (options.containsKey("colors")) {
      if (!(options.get("colors") instanceof List<?> list) || list.size() < 2)
        throw invalid("colors must contain at least two colors");
      colors = list.stream().mapToInt(ColorRamp::color).toArray();
    } else {
      String name = options.getOrDefault("palette", "viridis").toString().toLowerCase(Locale.ROOT);
      if (!PALETTES.containsKey(name)) throw invalid("Unknown palette: " + name);
      colors = PALETTES.get(name).clone();
    }
    if (reverse) {
      for (int i = 0; i < colors.length / 2; i++) {
        int opposite = colors.length - i - 1, swap = colors[i];
        colors[i] = colors[opposite];
        colors[opposite] = swap;
      }
    }
    return new ColorRamp(colors, stops, values, min, max, center, nodata, unknown);
  }

  /** Finite observed bounds are used only for relative ramps without explicit bounds. */
  public int argb(Object value, double observedMin, double observedMax) {
    if (value == null || value instanceof Number n && !Double.isFinite(n.doubleValue()))
      return nodata;
    if (isCategorical()) return values.getOrDefault(key(value), unknown);
    if (!(value instanceof Number number)) return unknown;
    double v = number.doubleValue();
    if (stops != null) {
      int index = Arrays.binarySearch(stops, v);
      if (index >= 0) return colors[index];
      int right = -index - 1;
      if (right == 0) return colors[0];
      if (right == stops.length) return colors[colors.length - 1];
      return blend(colors[right - 1], colors[right], fraction(v, stops[right - 1], stops[right]));
    }
    double lo = min == null ? observedMin : min, hi = max == null ? observedMax : max;
    if (!Double.isFinite(lo) || !Double.isFinite(hi) || lo > hi) return nodata;
    if (center != null && min == null) {
      double radius = Math.max(Math.abs(lo - center), Math.abs(hi - center));
      lo = center - radius;
      hi = center + radius;
    }
    double position =
        lo == hi
            ? 0.5
            : center == null
                ? fraction(v, lo, hi)
                : v <= center ? 0.5 * fraction(v, lo, center) : 0.5 + 0.5 * fraction(v, center, hi);
    position = Math.max(0, Math.min(1, position)) * (colors.length - 1);
    int left = Math.min((int) position, colors.length - 2);
    return blend(colors[left], colors[left + 1], position - left);
  }

  private static double fraction(double value, double lo, double hi) {
    if (value <= lo) return 0;
    if (value >= hi) return 1;
    // Halving first avoids overflowing the span for opposite extreme finite doubles.
    return Double.isFinite(hi - lo)
        ? (value - lo) / (hi - lo)
        : (value / 2 - lo / 2) / (hi / 2 - lo / 2);
  }

  private static int blend(int a, int b, double t) {
    int result = 0;
    for (int shift = 24; shift >= 0; shift -= 8) {
      int x = a >>> shift & 255, y = b >>> shift & 255;
      result |= (int) Math.round(x + (y - x) * t) << shift;
    }
    return result;
  }

  private static List<Map.Entry<?, ?>> pairs(Object object) {
    List<Map.Entry<?, ?>> result = new ArrayList<>();
    if (object instanceof Map<?, ?> map) result.addAll(map.entrySet());
    else if (object instanceof List<?> list) {
      for (var item : list) {
        if (!(item instanceof List<?> pair) || pair.size() != 2)
          throw invalid("Expected a list of (value color) pairs");
        result.add(new AbstractMap.SimpleImmutableEntry<>(pair.get(0), pair.get(1)));
      }
    } else throw invalid("Expected a map or a list of pairs");
    return result;
  }

  private static String key(Object value) {
    if (value == null) throw invalid("Null category");
    if (value instanceof KlabAsset asset) return asset.getUrn();
    if (value instanceof Number n) {
      if (!Double.isFinite(n.doubleValue())) throw invalid("Non-finite category");
      return new java.math.BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
    }
    return value.toString();
  }

  private static Double number(Object value) {
    if (value == null) return null;
    try {
      double result =
          value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString());
      if (Double.isFinite(result)) return result;
    } catch (NumberFormatException ignored) {
    }
    throw invalid("Expected a finite number: " + value);
  }

  /**
   * CSS hex (#RGB, #RGBA, #RRGGBB, #RRGGBBAA), rgb()/rgba(), named colors, or RGB(A) lists. List
   * channels are integers 0..255, including alpha; rgba() alpha is in 0..1.
   */
  public static int color(Object specification) {
    if (specification instanceof List<?> channels) return channels(channels);
    if (!(specification instanceof String text)) throw invalid("Invalid color: " + specification);
    String value = text.trim().toLowerCase(Locale.ROOT);
    if (value.equals("transparent")) return 0;
    if (value.startsWith("rgb(") || value.startsWith("rgba(")) {
      boolean alpha = value.startsWith("rgba(");
      if (!value.endsWith(")")) throw invalid("Invalid color: " + text);
      String[] parts = value.substring(value.indexOf('(') + 1, value.length() - 1).split(",");
      if (parts.length != (alpha ? 4 : 3)) throw invalid("Invalid color: " + text);
      List<Number> channels = new ArrayList<>();
      for (int i = 0; i < 3; i++) channels.add(number(parts[i].trim()));
      if (alpha) {
        double a = number(parts[3].trim());
        if (a < 0 || a > 1) throw invalid("rgba alpha must be in 0..1");
        channels.add(Math.round(a * 255));
      }
      return channels(channels);
    }
    String hex = value.startsWith("#") ? value.substring(1) : NAMES.get(value);
    if (hex == null || !hex.matches("[0-9a-f]+")) throw invalid("Unknown color: " + text);
    if (hex.length() == 3 || hex.length() == 4) {
      StringBuilder expanded = new StringBuilder();
      for (char c : hex.toCharArray()) expanded.append(c).append(c);
      hex = expanded.toString();
    }
    if (hex.length() == 6) return 0xff000000 | Integer.parseInt(hex, 16);
    if (hex.length() == 8) {
      long rgba = Long.parseLong(hex, 16);
      return (int) ((rgba & 255) << 24 | rgba >>> 8);
    }
    throw invalid("Invalid hex color: " + text);
  }

  private static int channels(List<?> channels) {
    if (channels.size() != 3 && channels.size() != 4)
      throw invalid("Use three or four RGB(A) channels");
    int result = channels.size() == 3 ? 0xff000000 : 0;
    for (int i = 0; i < channels.size(); i++) {
      Double v = number(channels.get(i));
      if (v == null || v < 0 || v > 255 || v != Math.rint(v))
        throw invalid("RGB(A) channels must be integers in 0..255");
      result |= v.intValue() << (i == 3 ? 24 : 16 - 8 * i);
    }
    return result;
  }

  private static IllegalArgumentException invalid(String message) {
    return new IllegalArgumentException("@colormap: " + message);
  }

  private static Map<String, int[]> palettes() {
    Map<String, int[]> result = new LinkedHashMap<>();
    for (String name : List.of("viridis", "magma", "inferno", "plasma")) {
      try (var stream = ColorRamp.class.getResourceAsStream("palettes/" + name + ".rgb")) {
        if (stream == null) throw new IllegalStateException("Missing palette " + name);
        try (var reader =
            new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
          result.put(
              name,
              reader
                  .lines()
                  .filter(line -> !line.startsWith("#") && !line.isBlank())
                  .mapToInt(
                      line -> {
                        var rgb = line.split("\\s+");
                        return 0xff000000
                            | (int) Math.round(Double.parseDouble(rgb[0]) * 255) << 16
                            | (int) Math.round(Double.parseDouble(rgb[1]) * 255) << 8
                            | (int) Math.round(Double.parseDouble(rgb[2]) * 255);
                      })
                  .toArray());
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    result.put("gray", new int[] {0xff000000, 0xffffffff});
    result.put("red-white-blue", new int[] {0xffff0000, 0xffffffff, 0xff0000ff});
    result.put("red-white-green", new int[] {0xffff0000, 0xffffffff, 0xff008000});
    result.put("heat", new int[] {0xff000000, 0xffff0000, 0xffffff00, 0xffffffff});
    // The middle coastal color represents sea level when used with center=0.
    result.put(
        "terrain",
        new int[] {
          0xff081d58,
          0xff225ea8,
          0xff1d91c0,
          0xff7fcdbb,
          0xffe8e6b5,
          0xff78a75a,
          0xffb5a16b,
          0xff896a52,
          0xfff5f5f5
        });
    return Collections.unmodifiableMap(result);
  }
}
