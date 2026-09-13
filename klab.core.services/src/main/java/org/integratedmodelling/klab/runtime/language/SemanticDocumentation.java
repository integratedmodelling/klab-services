package org.integratedmodelling.klab.runtime.language;

import java.beans.Introspector;
import java.lang.reflect.Method;
import java.util.*;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;

/** Markdown snapshots of language/knowledge beans. New bean properties are included automatically. */
public final class SemanticDocumentation {
  private SemanticDocumentation() {}

  public static String code(Object value) {
    String text = String.valueOf(value).replace("\r", "").replace("\n", " ↵ ");
    String fence = "`";
    while (text.contains(fence)) fence += "`";
    return fence + " " + text + " " + fence;
  }

  public static String describe(String title, Object bean) {
    var result = new StringBuilder("## " + title + "\n\n");
    walk(bean, "", new IdentityHashMap<>(), result, new TreeSet<>());
    return result.append('\n').toString();
  }

  /** All named references, including clause fillers and operands, without parsing URN text. */
  public static Set<String> references(Object bean) {
    Set<String> names = new TreeSet<>();
    walk(bean, "", new IdentityHashMap<>(), null, names);
    return names;
  }

  private static void entry(StringBuilder out, String path, Object value) {
    if (out != null) out.append("- ").append(code(path.isEmpty() ? "value" : path))
        .append(": ").append(value).append('\n');
  }

  private static void walk(Object value, String path, IdentityHashMap<Object, String> seen,
      StringBuilder out, Set<String> references) {
    if (value == null) { entry(out, path, "Not specified"); return; }
    if (value instanceof KimConcept concept && concept.getName() != null) {
      references.add(concept.getName());
    }
    if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean
        || value instanceof Enum<?> || value instanceof Character) {
      entry(out, path, code(value)); return;
    }
    if (seen.containsKey(value)) {
      entry(out, path, "See " + code(seen.get(value))); return;
    }
    seen.put(value, path.isEmpty() ? "root" : path);
    if (value instanceof Map<?, ?> map) {
      if (map.isEmpty()) entry(out, path, "None");
      map.entrySet().stream().sorted(Comparator.comparing(e -> String.valueOf(e.getKey())))
          .forEach(e -> walk(e.getValue(), child(path, String.valueOf(e.getKey())), seen, out, references));
    } else if (value.getClass().isArray()) {
      int length = java.lang.reflect.Array.getLength(value);
      if (length == 0) entry(out, path, "None");
      for (int i = 0; i < length; i++)
        walk(java.lang.reflect.Array.get(value, i), path + "[" + (i + 1) + "]", seen, out, references);
    } else if (value instanceof Collection<?> collection) {
      if (collection.isEmpty()) entry(out, path, "None");
      var items = new ArrayList<>(collection);
      if (value instanceof Set<?>) items.sort(Comparator.comparing(String::valueOf));
      for (int i = 0; i < items.size(); i++) {
        walk(items.get(i), path + "[" + (i + 1) + "]", seen, out, references);
      }
    } else if (value.getClass().getPackageName().startsWith("org.integratedmodelling")) {
      try {
        var properties = new TreeMap<String, Method>();
        if (value.getClass().isRecord()) {
          for (var component : value.getClass().getRecordComponents())
            properties.put(component.getName(), component.getAccessor());
        } else {
          for (var property : Introspector.getBeanInfo(value.getClass(), Object.class).getPropertyDescriptors())
            if (property.getReadMethod() != null) properties.put(property.getName(), property.getReadMethod());
        }
        if (properties.isEmpty()) entry(out, path, code(value));
        for (var property : properties.entrySet()) {
          var propertyPath = child(path, property.getKey());
          try {
            walk(property.getValue().invoke(value), propertyPath, seen, out, references);
          } catch (ReflectiveOperationException | RuntimeException e) {
            entry(out, propertyPath, "Unavailable (" + e.getClass().getSimpleName() + ")");
          }
        }
      } catch (java.beans.IntrospectionException e) {
        entry(out, path, "Unavailable (bean introspection failed)");
      }
    } else {
      entry(out, path, code(value));
    }
  }

  private static String child(String path, String property) {
    return path.isEmpty() ? property : path + "." + property;
  }
}
