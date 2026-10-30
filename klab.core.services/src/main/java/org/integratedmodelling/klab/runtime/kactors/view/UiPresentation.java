package org.integratedmodelling.klab.runtime.kactors.view;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Resolved presentation configuration. Resource loading is supplied by the authorized launcher. */
public record UiPresentation(
    String locale, String theme, List<String> stylesheets, Map<String, String> translations) {
  private static final ThreadLocal<UiPresentation> construction = new ThreadLocal<>();

  public static UiPresentation current() {
    return Objects.requireNonNullElseGet(construction.get(), UiPresentation::defaults);
  }

  public <T> T constructing(java.util.function.Supplier<T> supplier) {
    var previous = construction.get();
    construction.set(this);
    try {
      return supplier.get();
    } finally {
      if (previous == null) construction.remove();
      else construction.set(previous);
    }
  }

  public UiPresentation {
    locale = Locale.forLanguageTag(locale).toLanguageTag();
    if (!Set.of("light", "dark").contains(theme))
      throw new IllegalArgumentException("Unknown base theme");
    stylesheets = List.copyOf(stylesheets);
    translations = Map.copyOf(translations);
  }

  public static UiPresentation defaults() {
    return new UiPresentation("en", "light", List.of(), Map.of());
  }

  public String localize(String key) {
    String name = key.startsWith("#") ? key.substring(1) : key;
    return translations.getOrDefault(name, "#" + name);
  }

  /** Load UTF-8 properties in increasing specificity, without JVM-default-locale fallback. */
  public static Map<String, String> translations(
      String base, String locale, java.util.function.Function<String, InputStream> loader) {
    if (!base.matches("[a-zA-Z0-9_/-]+") || base.contains("..") || base.startsWith("/"))
      throw new IllegalArgumentException("Invalid translation bundle");
    var result = new LinkedHashMap<String, String>();
    var suffix = new StringBuilder();
    var names = new ArrayList<String>();
    names.add(base + ".properties");
    for (String part : Locale.forLanguageTag(locale).toLanguageTag().split("-")) {
      suffix.append('_').append(part);
      names.add(base + suffix + ".properties");
    }
    for (String name : names) {
      try (var stream = loader.apply(name)) {
        if (stream == null) continue;
        var properties = new Properties();
        properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        properties.forEach((key, value) -> result.put(key.toString(), value.toString()));
      } catch (IOException error) {
        throw new UncheckedIOException(error);
      }
    }
    return result;
  }
}
