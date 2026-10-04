package org.integratedmodelling.klab.api.digitaltwin;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.integratedmodelling.klab.api.lang.Quantity;

/** Portable inline grid instructions, with stable identity independent of map ordering. */
public final class GridSpecification {
  private GridSpecification() {}

  public static Map<String, Object> copyOf(Map<String, ?> input) {
    Objects.requireNonNull(input, "Grid specification");
    var result = new TreeMap<String, Object>();
    input.forEach((key, value) -> result.put(Objects.requireNonNull(key), copy(value)));
    return Collections.unmodifiableMap(result);
  }

  private static Object copy(Object value) {
    if (value instanceof Quantity quantity) return quantity.getValue() + "." + quantity.getUnit();
    if (value instanceof Number number) {
      if (!Double.isFinite(number.doubleValue())) throw new IllegalArgumentException("Nonfinite grid value");
      return new BigDecimal(number.toString()).stripTrailingZeros();
    }
    if (value instanceof String || value instanceof Boolean || value == null) return value;
    if (value instanceof List<?> list) return Collections.unmodifiableList(list.stream().map(GridSpecification::copy).toList());
    throw new IllegalArgumentException("Grid values must be numbers, quantities, strings, booleans or lists");
  }

  public static String urn(Map<String, ?> input) {
    String text = code("inline", copyOf(input));
    try {
      return "urn:klab:grid:inline:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }

  /** k.IM definition preserving the requested units and normalization options. */
  public static String code(String urn, Map<String, ?> input) {
    String name = urn.substring(Math.max(urn.lastIndexOf('.'), urn.lastIndexOf(':')) + 1);
    if (!Character.isJavaIdentifierStart(name.charAt(0))) name = "grid_" + name;
    var text = new StringBuilder("define grid ").append(name).append(" as {\n");
    new TreeMap<>(input).forEach((key, value) -> text.append("    ").append(key).append(": ")
        .append(render(value, key.equals("span") || key.equals("resolutions"))).append('\n'));
    return text.append("};").toString();
  }

  private static String render(Object value, boolean quantity) {
    if (value instanceof Quantity q) return q.getValue() + "." + q.getUnit();
    if (value instanceof BigDecimal number) return number.toPlainString();
    if (value instanceof List<?> list) return "(" + String.join(" ", list.stream().map(v -> render(v, quantity)).toList()) + ")";
    if (value instanceof String text) {
      if (quantity) {
        var match = java.util.regex.Pattern.compile("^([+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:[eE][+-]?\\d+)?)[.\\s]+([a-zA-Z][a-zA-Z0-9_/*^.-]*)$").matcher(text.trim());
        if (match.matches()) return new BigDecimal(match.group(1)).stripTrailingZeros().toPlainString() + "." + match.group(2);
      }
      return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
    return Objects.toString(value, "null");
  }
}
