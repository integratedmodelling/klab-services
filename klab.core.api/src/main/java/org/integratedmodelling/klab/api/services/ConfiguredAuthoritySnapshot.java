package org.integratedmodelling.klab.api.services;

import java.util.Objects;

/** Bounded immutable configured state. This is not a persistent response-cache entry. */
public record ConfiguredAuthoritySnapshot(
    String format, String providerUrn, Authority.ConfigurationRequest request, byte[] state) {
  public static final int MAX_STATE_BYTES = 32 * 1024 * 1024;

  public ConfiguredAuthoritySnapshot {
    if (format == null || !format.matches("[a-zA-Z0-9_.:-]{1,128}")
        || providerUrn == null || providerUrn.isBlank())
      throw new IllegalArgumentException("Snapshot format and provider identity are required");
    Objects.requireNonNull(request, "Snapshot configuration is required");
    String requested = (String) request.parameters().get("urn");
    if (!providerUrn.equals(requested) && !requested.startsWith(providerUrn + "@"))
      throw new IllegalArgumentException("Snapshot provider differs from configuration");
    if (state == null || state.length == 0 || state.length > MAX_STATE_BYTES)
      throw new IllegalArgumentException("Configured authority snapshot exceeds bounds");
    state = state.clone();
    @SuppressWarnings("unchecked")
    var frozen = (java.util.Map<String, Object>) freeze(request.parameters(), 0);
    request = new Authority.ConfigurationRequest(request.worldview(), request.name(), request.rootIdentity(), frozen);
  }

  @Override public byte[] state() { return state.clone(); }

  @Override public boolean equals(Object other) {
    return other instanceof ConfiguredAuthoritySnapshot that && format.equals(that.format)
        && providerUrn.equals(that.providerUrn) && request.equals(that.request)
        && java.util.Arrays.equals(state, that.state);
  }

  @Override public int hashCode() {
    return Objects.hash(format, providerUrn, request, java.util.Arrays.hashCode(state));
  }

  private static Object freeze(Object value, int depth) {
    if (depth > 16) throw new IllegalArgumentException("Snapshot parameters exceed nesting bound");
    if (value instanceof java.util.Map<?, ?> map) {
      if (map.size() > 4096) throw new IllegalArgumentException("Snapshot parameters exceed entry bound");
      var result = new java.util.LinkedHashMap<String, Object>();
      map.forEach((key, entry) -> {
        if (!(key instanceof String)) throw new IllegalArgumentException("Snapshot parameter keys must be strings");
        result.put((String) key, freeze(entry, depth + 1));
      });
      return java.util.Collections.unmodifiableMap(result);
    }
    if (value instanceof java.util.List<?> list) {
      if (list.size() > 4096) throw new IllegalArgumentException("Snapshot parameters exceed entry bound");
      return java.util.Collections.unmodifiableList(list.stream().map(v -> freeze(v, depth + 1)).toList());
    }
    if (value == null || value instanceof String || value instanceof Boolean
        || value instanceof Byte || value instanceof Short || value instanceof Integer
        || value instanceof Long || value instanceof java.math.BigInteger
        || value instanceof java.math.BigDecimal || value instanceof Float || value instanceof Double) return value;
    throw new IllegalArgumentException("Snapshot parameters must be JSON values");
  }
}
