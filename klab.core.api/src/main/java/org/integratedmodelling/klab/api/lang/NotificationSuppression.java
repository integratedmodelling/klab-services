package org.integratedmodelling.klab.api.lang;

import java.util.Collection;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Warning-only suppression shared by validation and runtime emission. Unknown selectors fail open. */
public final class NotificationSuppression {
  private NotificationSuppression() {}
  public static boolean suppresses(Collection<Annotation> annotations, Notification.Level level) {
    if (level != Notification.Level.Warning || annotations == null) return false;
    for (var annotation : annotations) {
      if (!"suppress".equals(annotation.getName())) continue;
      if (Boolean.TRUE.equals(annotation.get("warnings"))) return true;
      if (annotation.containsKey("warnings")) continue;
      var values = annotation.getUnnamedArguments();
      if (annotation.containsKey("value")) values = java.util.Collections.singletonList(annotation.get("value"));
      if (!values.isEmpty()) {
        if (values.stream().anyMatch(NotificationSuppression::warningSelector)) return true;
      } else if (annotation.keySet().stream().allMatch(key -> key.startsWith("#"))) return true;
    }
    return false;
  }
  private static boolean warningSelector(Object value) {
    if (value instanceof org.integratedmodelling.klab.api.collections.Constant constant) value = constant.getValue();
    return value != null && ("warning".equalsIgnoreCase(value.toString()) || "warnings".equalsIgnoreCase(value.toString()));
  }
  public static boolean suppresses(Notification.Level level, Object... sources) {
    if (sources != null) for (var source : sources) {
      if (source instanceof KlabAsset asset && suppresses(asset.getAnnotations(),level)) return true;
    }
    return false;
  }
}
